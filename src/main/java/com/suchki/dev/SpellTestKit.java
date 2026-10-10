package com.suchki.dev;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.suchki.SuchkiConfig;
import com.suchki.magic.Cast;
import com.suchki.magic.Magic;
import com.suchki.magic.MagicItems;
import com.suchki.magic.Minions;
import com.suchki.magic.Spell;
import com.suchki.magic.Spells;
import com.suchki.magic.Terrain;
import com.suchki.magic.Zones;
import com.suchki.skill.PlayerSkills;
import com.suchki.skill.Skills;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Помощники самопроверки заклинаний: площадка, маг, мишени, прокрутка времени (зоны, отложенные удары, разрушение),
 * подсчёт блоков, переключатель разрушения. Общие для {@link MagicTests} и проверок каждой стихии.
 */
final class SpellTestKit {
	private SpellTestKit() {}

	/** Ровная каменная площадка радиуса {@code r} с воздухом высотой {@code h} над ней. */
	static void arena(ServerLevel level, BlockPos o, int r, int h) {
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				level.setBlock(o.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 2);
				for (int dy = 0; dy <= h; dy++) level.setBlock(o.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
			}
		}
	}

	static void arena(ServerLevel level, BlockPos o) {
		arena(level, o, 14, 8);
	}

	/**
	 * Маг 50 уровня со всеми заклинаниями, полной маной и посохом первоначал (тянет любой ранг); смотрит на юг (+Z).
	 * Мультикаст — 1, перезарядки сняты.
	 */
	static FakePlayer mage(ServerLevel level, BlockPos o) {
		hold(level, o, 40);
		arena(level, o);
		FakePlayer p = DevTests.player(level, o, new ItemStack(MagicItems.PRIMORDIAL_STAFF), 0);
		p.setAttached(Skills.DATA, new PlayerSkills(Optional.of(Magic.ROLE), Map.of(Magic.ROLE, new PlayerSkills.Progress(50, 0, List.of())), 0L));
		Skills.applyAttributes(p);
		Magic.forgetAll(p);
		for (Spell s : Spells.all()) Magic.teach(p, s);
		Magic.setMulticast(p, 1);
		Magic.resetCooldowns(p);
		p.setAttached(Magic.MANA, Magic.maxMana(p));
		return p;
	}

	/** Чанки, удерживаемые проверками; отпускаются после каждой проверки ({@link #releaseChunks}). */
	private static final Set<Long> HELD = new HashSet<>();

	/**
	 * Загрузить и удержать чанки в радиусе {@code r} вокруг {@code c}. У ненастоящего игрока чанки вокруг никто не держит,
	 * и поиск существ их не видит, пока сервер не прогонит отложенные задачи, — прогоняем их сразу.
	 */
	static void hold(ServerLevel level, BlockPos c, int r) {
		boolean added = false;
		for (int cx = (c.getX() - r) >> 4; cx <= (c.getX() + r) >> 4; cx++) {
			for (int cz = (c.getZ() - r) >> 4; cz <= (c.getZ() + r) >> 4; cz++) {
				if (HELD.add(ChunkPos.asLong(cx, cz))) {
					level.setChunkForced(cx, cz, true);
					level.getChunk(cx, cz);
					added = true;
				}
			}
		}
		if (!added) return;
		long until = System.nanoTime() + 40_000_000L;
		while (System.nanoTime() < until) {
			int n = 0;
			while (n < 10_000 && level.getChunkSource().pollTask()) n++;
			Thread.onSpinWait();
		}
	}

	static void releaseChunks(ServerLevel level) {
		for (long c : HELD) level.setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), false);
		HELD.clear();
	}

	/** Поставить мага в точку и повернуть взгляд (голова тоже — прицел идёт по ней). */
	static void face(FakePlayer p, BlockPos o, float yaw, float pitch) {
		p.snapTo(o.getX() + 0.5, o.getY(), o.getZ() + 0.5, yaw, pitch);
		p.setYHeadRot(yaw);
		p.yHeadRotO = yaw;
		p.yBodyRot = yaw;
	}

	static void face(FakePlayer p, BlockPos o, float pitch) {
		face(p, o, 0, pitch);
	}

	static Spell spell(String id) {
		Spell s = Spells.get(id);
		if (s == null) throw new IllegalStateException("нет заклинания " + id);
		return s;
	}

	/** Мишень без ИИ; у зомби — шлем (не горит на солнце) и 200 здоровья, чтобы урон было видно. */
	static <T extends Mob> T dummy(ServerLevel level, EntityType<T> type, BlockPos at) {
		T e = DevTests.spawn(level, type, at);
		e.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		var hp = e.getAttribute(Attributes.MAX_HEALTH);
		if (hp != null && hp.getBaseValue() < 200) {
			hp.setBaseValue(200);
			e.setHealth(200);
		}
		e.setPersistenceRequired();
		return e;
	}

	static EntityHitResult hitOn(Entity e) {
		return new EntityHitResult(e, Cast.center(e));
	}

	static boolean hurt(LivingEntity e) {
		return e.getHealth() < e.getMaxHealth();
	}

	static String hp(LivingEntity e) {
		return String.format("%.1f/%.1f", e.getHealth(), e.getMaxHealth());
	}

	/** Прокрутить время заклинаний: зоны и отложенные удары на {@code ticks} тиков вперёд, доломать блоки. */
	static void run(int ticks) {
		Zones.runNow(ticks);
		Terrain.flushNow();
		Minions.processNow();
	}

	/** Убрать следы заклинаний между проверками. */
	static void clean() {
		Zones.clear();
		Terrain.flushNow();
		Terrain.restoreAll();
	}

	/** Сколько твёрдых (не воздух) блоков в кубе радиуса {@code r}. */
	static int solid(ServerLevel level, BlockPos c, int r) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
			BlockState s = level.getBlockState(p);
			// Огонь на краях воронок — не блок: иначе пламя на выжженной земле «добавляет» блоков.
			if (!s.isAir() && s.getFluidState().isEmpty() && !(s.getBlock() instanceof BaseFireBlock)) n++;
		}
		return n;
	}

	/** Залить куб камнем (для проверки разрушения). */
	static void fill(ServerLevel level, BlockPos c, int r) {
		for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
			level.setBlock(p, Blocks.STONE.defaultBlockState(), 2);
		}
	}

	/** Выполнить с включённым/выключенным разрушением блоков и вернуть настройку. */
	static void griefing(boolean on, Runnable r) {
		boolean was = SuchkiConfig.INSTANCE.magicBlockDamage;
		SuchkiConfig.INSTANCE.magicBlockDamage = on;
		try {
			r.run();
		} finally {
			SuchkiConfig.INSTANCE.magicBlockDamage = was;
		}
	}

	/**
	 * Отдельное место для разрушительной проверки номер {@code i}, далеко от общей площадки. Все проверки идут в одном
	 * мире, поэтому у каждой стихии свои номера: общие проверки 0–9, жизнь 10–19, тьма 20–29, пространство 30–39,
	 * огонь 40–59, вода 60–79, земля 80–99, ветер 100–129, разум 140–159, призыв 160–179, слияние 180–219,
	 * молния 220–239, свет 240–259, «каждое заклинание» 300 и дальше.
	 */
	static BlockPos far(BlockPos o, int i) {
		return o.offset(200 + 60 * (i % 8), 0, 200 + 60 * (i / 8));
	}

	static List<LivingEntity> living(ServerLevel level, BlockPos c, int r) {
		return level.getEntitiesOfClass(LivingEntity.class, new AABB(c).inflate(r), LivingEntity::isAlive);
	}

	// --- Тексты (читаются из ресурсов мода) ---

	private static JsonObject ru;
	private static JsonObject en;

	static JsonObject lang(String code) {
		if ("ru_ru".equals(code) && ru != null) return ru;
		if ("en_us".equals(code) && en != null) return en;
		try (InputStream in = SpellTestKit.class.getResourceAsStream("/assets/suchki/lang/" + code + ".json")) {
			JsonObject o = in == null ? new JsonObject()
					: JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			if ("ru_ru".equals(code)) ru = o;
			else en = o;
			return o;
		} catch (Exception e) {
			return new JsonObject();
		}
	}

	static String text(String code, String key) {
		JsonObject o = lang(code);
		return o.has(key) ? o.get(key).getAsString() : null;
	}

	static boolean resource(String path) {
		try (InputStream in = SpellTestKit.class.getResourceAsStream(path)) {
			return in != null;
		} catch (Exception e) {
			return false;
		}
	}
}
