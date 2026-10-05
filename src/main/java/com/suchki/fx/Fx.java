package com.suchki.fx;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Звуки и частицы Suchki: тихие и короткие, чтобы не резать глаза и уши.
 * Повторяющиеся эффекты ограничены по частоте на игрока (не чаще раза в N тиков).
 */
public final class Fx {
	private static final Map<String, Long> LAST = new HashMap<>();

	private Fx() {}

	/** true, если эффект {@code kind} для игрока можно показать (прошло {@code cooldownTicks} с прошлого раза). */
	private static boolean ready(ServerLevel level, UUID who, String kind, int cooldownTicks) {
		String key = who + kind;
		long now = level.getGameTime();
		Long last = LAST.get(key);
		if (last != null && now - last < cooldownTicks) return false;
		LAST.put(key, now);
		if (LAST.size() > 4096) LAST.clear();
		return true;
	}

	private static void burst(ServerLevel level, Vec3 at, ParticleOptions particle, int count, double spread, double speed) {
		level.sendParticles(particle, at.x, at.y, at.z, count, spread, spread, spread, speed);
	}

	private static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
	}

	private static Vec3 center(BlockPos pos) {
		return Vec3.atCenterOf(pos);
	}

	/** Сработал шансовый навык (двойная добыча, двойняшки, сохранённая стрела): голубые искры и тихий звон. */
	public static void skillProc(ServerLevel level, Player player, Vec3 at) {
		if (!ready(level, player.getUUID(), "proc", 6)) return;
		burst(level, at, ParticleTypes.ENCHANTED_HIT, 8, 0.25, 0.05);
		sound(level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 0.5F, 1.4F + level.random.nextFloat() * 0.3F);
	}

	public static void skillProc(ServerLevel level, Player player, BlockPos pos) {
		skillProc(level, player, center(pos));
	}

	/** Автоплавка: огонёк и дымок на месте руды. */
	public static void smelted(ServerLevel level, Player player, BlockPos pos) {
		Vec3 c = center(pos);
		burst(level, c, ParticleTypes.FLAME, 4, 0.2, 0.01);
		burst(level, c, ParticleTypes.SMOKE, 2, 0.2, 0.01);
		if (ready(level, player.getUUID(), "smelt", 10)) sound(level, c, SoundEvents.FURNACE_FIRE_CRACKLE, 0.35F, 1.2F);
	}

	/** Удача V: самоцвет из руды. */
	public static void gem(ServerLevel level, BlockPos pos) {
		Vec3 c = center(pos);
		burst(level, c, ParticleTypes.HAPPY_VILLAGER, 6, 0.3, 0.02);
		sound(level, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 1.6F);
	}

	/** Отражение: звук щита и искры в точке отскока. */
	public static void deflected(ServerLevel level, Vec3 at) {
		burst(level, at, ParticleTypes.CRIT, 10, 0.15, 0.2);
		sound(level, at, SoundEvents.SHIELD_BLOCK.value(), 0.6F, 1.3F);
	}

	/** Садовник: искорки на подросшем ростке (как от костной муки, но меньше). */
	public static void cropGrew(ServerLevel level, BlockPos pos) {
		burst(level, Vec3.atBottomCenterOf(pos).add(0, 0.4, 0), ParticleTypes.HAPPY_VILLAGER, 2, 0.2, 0.0);
	}

	/** Новый уровень роли: мягкие искры вокруг игрока. */
	public static void levelUp(ServerLevel level, ServerPlayer player) {
		burst(level, player.position().add(0, 1.0, 0), ParticleTypes.END_ROD, 14, 0.4, 0.04);
	}

	/** Изучен навык: немного магических частиц у игрока. */
	public static void skillLearned(ServerLevel level, ServerPlayer player) {
		burst(level, player.position().add(0, 1.2, 0), ParticleTypes.ENCHANT, 20, 0.5, 0.6);
	}

	/** Прочность X: изношенный предмет — надпись над хотбаром, не чаще раза в секунду. */
	public static void wornOut(ServerLevel level, ServerPlayer player) {
		if (ready(level, player.getUUID(), "worn", 20)) {
			player.displayClientMessage(Component.translatable("suchki.unbreaking.worn_out"), true);
		}
	}
}
