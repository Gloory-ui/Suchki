package com.suchki.skill;

import com.mojang.serialization.Codec;
import com.suchki.Suchki;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Откуда роли получают опыт. Опыт идёт только активной роли игрока. */
public final class SkillXp {
	/** Биомы, которые Странник уже открыл (чтобы за каждый давать опыт один раз). */
	public static final AttachmentType<List<String>> BIOMES = AttachmentRegistry.<List<String>>builder()
			.persistent(Codec.STRING.listOf())
			.copyOnDeath()
			.initializer(List::of)
			.buildAndRegister(Identifier.fromNamespaceAndPath(Suchki.MOD_ID, "visited_biomes"));

	private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();
	private static final Map<UUID, Double> WALKED = new HashMap<>();

	private SkillXp() {}

	static void register() {
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
			if (player instanceof ServerPlayer sp && !sp.isCreative()) onBreak(sp, state);
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (!(source.getEntity() instanceof ServerPlayer sp) || !(entity instanceof Mob) || taken <= 0) return;
			Skills.addXp(sp, source.getDirectEntity() instanceof Projectile ? "archer" : "warrior", taken);
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer sp && entity instanceof Mob) {
				Skills.addXp(sp, "hunter", Math.max(1.0, entity.getMaxHealth() / 2.0));
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 != 0) return;
			for (ServerPlayer p : server.getPlayerList().getPlayers()) wander(p);
		});
	}

	private static void onBreak(ServerPlayer player, BlockState state) {
		if (state.is(ConventionalBlockTags.ORES)) Skills.addXp(player, "miner", 6);
		else if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) Skills.addXp(player, "miner", 1);
		if (state.is(BlockTags.LOGS)) Skills.addXp(player, "lumberjack", 2);
		if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) Skills.addXp(player, "farmer", 2);
	}

	/** Странник: опыт за пройденный путь (не в транспорте и не в полёте) и за каждый новый биом. */
	private static void wander(ServerPlayer player) {
		UUID id = player.getUUID();
		Vec3 now = player.position();
		Vec3 before = LAST_POS.put(id, now);
		if (before != null && !player.isPassenger() && !player.isFallFlying() && !player.getAbilities().flying) {
			double dx = now.x - before.x, dz = now.z - before.z;
			double step = Math.sqrt(dx * dx + dz * dz);
			if (step < 40) { // телепорт не считаем
				double total = WALKED.getOrDefault(id, 0.0) + step;
				int xp = (int) (total / 16.0);
				WALKED.put(id, total - xp * 16.0);
				if (xp > 0) Skills.addXp(player, "wanderer", xp);
			}
		}
		player.level().getBiome(player.blockPosition()).unwrapKey().ifPresent(key -> {
			String biome = key.identifier().toString();
			List<String> seen = player.getAttachedOrElse(BIOMES, List.of());
			if (!seen.contains(biome)) {
				List<String> next = new ArrayList<>(seen);
				next.add(biome);
				player.setAttached(BIOMES, List.copyOf(next));
				if (!seen.isEmpty()) Skills.addXp(player, "wanderer", 40); // стартовый биом не считаем
			}
		});
	}
}
