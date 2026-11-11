package com.suchki.magic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Серверный журнал общих бюджетов снарядов. NBT сущности содержит снимок для диагностики, но именно этот журнал
 * хранит монотонный максимум между выгрузками чанков и рестартами, поэтому устаревший снаряд не возвращает лимит.
 */
final class TerrainBudgetLedger extends SavedData {
	/** Давно выгруженный снаряд после этого срока безопасно теряет разрушение местности. */
	static final long RETENTION_TICKS = 72_000L;

	private static final Codec<UUID> UUID_CODEC = Codec.STRING.comapFlatMap(value -> {
		try {
			return DataResult.success(UUID.fromString(value));
		} catch (IllegalArgumentException invalid) {
			return DataResult.error(() -> "Invalid terrain budget UUID: " + value);
		}
	}, UUID::toString);

	record StoredBudget(UUID id, int used, long lastSeen) {
		static final Codec<StoredBudget> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				UUID_CODEC.fieldOf("id").forGetter(StoredBudget::id),
				Codec.INT.fieldOf("used").forGetter(StoredBudget::used),
				Codec.LONG.fieldOf("last_seen").forGetter(StoredBudget::lastSeen)
		).apply(instance, StoredBudget::new));
	}

	private static final Codec<TerrainBudgetLedger> CODEC = StoredBudget.CODEC.listOf()
			.xmap(TerrainBudgetLedger::new, TerrainBudgetLedger::snapshot);
	private static final SavedDataType<TerrainBudgetLedger> TYPE = new SavedDataType<>(
			"suchki_magic_terrain_budgets", TerrainBudgetLedger::new, CODEC, null);

	private final Map<UUID, StoredBudget> entries = new HashMap<>();
	/** Общий объект для одновременно загруженных siblings; на диск попадают только компактные StoredBudget. */
	private final Map<UUID, Cast.TerrainBudget> live = new HashMap<>();

	TerrainBudgetLedger() {}

	TerrainBudgetLedger(List<StoredBudget> stored) {
		for (StoredBudget entry : stored) {
			if (entry.used() < 0 || entry.lastSeen() < 0) continue;
			entries.merge(entry.id(), entry, (left, right) -> new StoredBudget(left.id(),
					Math.max(left.used(), right.used()), Math.max(left.lastSeen(), right.lastSeen())));
		}
	}

	static TerrainBudgetLedger get(ServerLevel level) {
		return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	synchronized Cast.TerrainBudget bind(Cast.TerrainBudget candidate, long now) {
		prune(now);
		StoredBudget stored = entries.get(candidate.id);
		int used = Math.max(candidate.used, stored == null ? 0 : stored.used());
		Cast.TerrainBudget shared = live.get(candidate.id);
		if (shared == null) {
			shared = candidate;
			live.put(candidate.id, shared);
		}
		shared.used = Math.max(shared.used, used);
		shared.ledger = this;
		entries.put(candidate.id, new StoredBudget(candidate.id, shared.used, now));
		setDirty();
		return shared;
	}

	synchronized Cast.TerrainBudget restore(UUID id, int nbtUsed, long now) {
		if (nbtUsed < 0) return Cast.exhaustedTerrainBudget();
		prune(now);
		StoredBudget stored = entries.get(id);
		if (stored == null) return Cast.exhaustedTerrainBudget();
		int used = Math.max(stored.used(), nbtUsed);
		Cast.TerrainBudget shared = live.computeIfAbsent(id, key -> new Cast.TerrainBudget(key, used));
		shared.used = Math.max(shared.used, used);
		shared.ledger = this;
		entries.put(id, new StoredBudget(id, shared.used, now));
		setDirty();
		return shared;
	}

	synchronized void record(Cast.TerrainBudget budget, long now) {
		prune(now);
		StoredBudget stored = entries.get(budget.id);
		if (stored == null) {
			budget.used = Integer.MAX_VALUE;
			budget.ledger = null;
			return;
		}
		int used = Math.max(stored.used(), budget.used);
		budget.used = used;
		entries.put(budget.id, new StoredBudget(budget.id, used, now));
		setDirty();
	}

	synchronized void prune(long now) {
		List<UUID> expired = new ArrayList<>();
		for (StoredBudget entry : entries.values()) {
			if (now >= entry.lastSeen() && now - entry.lastSeen() > RETENTION_TICKS) expired.add(entry.id());
		}
		if (expired.isEmpty()) return;
		expired.forEach(entries::remove);
		expired.forEach(live::remove);
		setDirty();
	}

	synchronized List<StoredBudget> snapshot() {
		return entries.values().stream()
				.sorted(Comparator.comparing(entry -> entry.id().toString()))
				.toList();
	}
}
