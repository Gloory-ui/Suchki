package com.suchki.skill;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Постоянные эффекты навыков: ночное зрение в пещерах, подсветка мобов, рост посевов, двойная добыча с мобов. */
public final class SkillEffects {
	private SkillEffects() {}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 40 != 0) return;
			for (ServerPlayer p : server.getPlayerList().getPlayers()) tick(p);
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer sp && entity instanceof Mob mob
					&& entity.level() instanceof ServerLevel level && Skills.roll(sp, Perk.MOB_LOOT_EXTRA)) {
				((com.suchki.mixin.LivingEntityInvoker) mob).suchki$dropFromLootTable(level, source, true);
			}
		});
	}

	private static void tick(ServerPlayer player) {
		PlayerSkills s = Skills.get(player);
		if (s.current() == null) return;
		ServerLevel level = player.level();

		// Шахтёр: ночное зрение в тёмных пещерах ниже Y=40 (обновляется, пока условие выполняется).
		if (s.perk(Perk.NIGHT_VISION_UNDERGROUND) > 0 && player.getBlockY() < 40
				&& level.getMaxLocalRawBrightness(player.blockPosition()) < 8) {
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 15 * 20, 0, true, false, true));
		}

		// Охотник: враждебные мобы рядом подсвечиваются.
		double radius = s.perk(Perk.HUNTER_SENSE);
		if (radius > 0) {
			for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(player.blockPosition()).inflate(radius),
					m -> m instanceof Enemy && m.isAlive())) {
				mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, true, false, false));
			}
		}

		// Фермер: несколько посевов в радиусе 4 блоков подрастают на одну стадию.
		int attempts = (int) s.perk(Perk.CROP_AURA);
		for (int i = 0; i < attempts * 3; i++) {
			BlockPos pos = player.blockPosition().offset(level.random.nextInt(9) - 4, level.random.nextInt(3) - 1,
					level.random.nextInt(9) - 4);
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state)) {
				level.setBlock(pos, crop.getStateForAge(crop.getAge(state) + 1), 2);
				attempts--;
				if (attempts <= 0) break;
			}
		}
	}
}
