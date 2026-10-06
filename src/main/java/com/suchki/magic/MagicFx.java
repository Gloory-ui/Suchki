package com.suchki.magic;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Частицы и звуки магии, которые видят и слышат все (отправляются с сервера).
 * Эффекты у руки во время зарядки рисует клиент сам — по состоянию каста.
 */
public final class MagicFx {
	private MagicFx() {}

	// --- Примитивы ---

	public static void burst(ServerLevel level, Vec3 at, ParticleOptions p, int count, double spread, double speed) {
		level.sendParticles(p, at.x, at.y, at.z, count, spread, spread, spread, speed);
	}

	/** Одна частица со скоростью (dx, dy, dz) — у кругов это направление. */
	public static void one(ServerLevel level, Vec3 at, ParticleOptions p, double dx, double dy, double dz) {
		level.sendParticles(p, at.x, at.y, at.z, 0, dx, dy, dz, 1.0);
	}

	public static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
	}

	/** Кольцо частиц по горизонтали; {@code outward} — скорость наружу. */
	public static void ring(ServerLevel level, Vec3 center, double radius, ParticleOptions p, int points, double outward) {
		for (int i = 0; i < points; i++) {
			double a = Math.PI * 2 * i / points;
			double x = Math.cos(a), z = Math.sin(a);
			one(level, center.add(x * radius, 0, z * radius), p, x * outward, 0.0, z * outward);
		}
	}

	/** Линия частиц от точки до точки. */
	public static void line(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions p, double step) {
		Vec3 d = to.subtract(from);
		double len = d.length();
		int n = Math.max(1, (int) (len / step));
		for (int i = 0; i <= n; i++) {
			Vec3 at = from.add(d.scale(i / (double) n));
			one(level, at, p, 0, 0, 0);
		}
	}

	/** Зигзаг-молния от точки до точки (на сервере — чтобы все видели одинаково). */
	public static void lightning(ServerLevel level, Vec3 from, Vec3 to, int color) {
		Vec3 d = to.subtract(from);
		int segments = Math.max(3, (int) (d.length() * 1.5));
		Vec3 prev = from;
		for (int i = 1; i <= segments; i++) {
			Vec3 next = from.add(d.scale(i / (double) segments));
			if (i < segments) {
				next = next.add((level.random.nextDouble() - 0.5) * 0.6, (level.random.nextDouble() - 0.5) * 0.6,
						(level.random.nextDouble() - 0.5) * 0.6);
			}
			line(level, prev, next, MagicParticles.spark(color, 0.12F, 6), 0.18);
			prev = next;
		}
		line(level, from, to, MagicParticles.spark(0xFFFFFF, 0.06F, 4), 0.5);
	}

	// --- Готовые эффекты ---

	static void castStart(ServerLevel level, ServerPlayer player, Spell spell) {
		if (spell.style == CastStyle.RITUAL) {
			one(level, player.position().add(0, 0.05, 0), MagicParticles.rune(spell.school.color, 1.4F, 40), 0, 1, 0);
		}
	}

	static void fizzle(ServerLevel level, ServerPlayer player) {
		burst(level, Cast.handOf(player), ParticleTypes.SMOKE, 8, 0.1, 0.02);
	}

	static void learned(ServerLevel level, ServerPlayer player, Spell spell) {
		Vec3 c = player.position().add(0, 1.0, 0);
		for (int i = 0; i < 24; i++) {
			double a = i * 0.5;
			Vec3 at = c.add(Math.cos(a) * 0.9, i * 0.06 - 0.6, Math.sin(a) * 0.9);
			one(level, at, MagicParticles.mote(spell.school.color, 0.12F, 30), 0, 0.02, 0);
		}
		one(level, player.position().add(0, 0.05, 0), MagicParticles.rune(spell.school.color, 1.2F, 40), 0, 1, 0);
		burst(level, c, ParticleTypes.ENCHANT, 40, 0.6, 0.6);
		sound(level, c, MagicSounds.LEARN, 0.9F, 1.0F);
	}

	static void spectralVanish(ServerLevel level, Vec3 at) {
		burst(level, at, MagicParticles.spark(School.CONJURATION.color, 0.15F, 14), 12, 0.25, 0.04);
		sound(level, at, MagicSounds.VANISH, 0.6F, 1.4F);
	}

	static void undyingSaved(ServerLevel level, LivingEntity entity) {
		Vec3 c = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
		burst(level, c, ParticleTypes.TOTEM_OF_UNDYING, 60, 0.4, 0.6);
		one(level, entity.position().add(0, 0.05, 0), MagicParticles.ring(School.RESTORATION.light, 3.0F, 20), 0, 1, 0);
		burst(level, c, MagicParticles.spark(0xFFD24A, 0.4F, 16), 20, 0.5, 0.08);
		sound(level, c, SoundEvents.TOTEM_USE, 0.8F, 1.1F);
	}

	static void candleMote(ServerLevel level, ServerPlayer p) {
		if (level.random.nextInt(3) != 0) return;
		float yaw = p.getYRot() * Mth.DEG_TO_RAD;
		Vec3 at = p.getEyePosition().add(-Mth.cos(yaw) * 0.6, 0.55, -Mth.sin(yaw) * 0.6);
		one(level, at, MagicParticles.spark(School.LIGHT.light, 0.28F, 12), 0, 0.005, 0);
	}

	/** Существо исчезло (призыв кончился). */
	public static void poof(ServerLevel level, Entity e, int color) {
		Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
		burst(level, c, MagicParticles.spark(color, 0.25F, 18), 16, e.getBbWidth() * 0.5, 0.05);
		burst(level, c, ParticleTypes.POOF, 8, e.getBbWidth() * 0.4, 0.02);
		sound(level, c, MagicSounds.VANISH, 0.7F, 1.0F);
	}

	/** Существо призвано: круг под ним и столб искр. */
	public static void summoned(ServerLevel level, Entity e, int color) {
		one(level, e.position().add(0, 0.05, 0), MagicParticles.rune(color, Math.max(0.8F, e.getBbWidth()), 30), 0, 1, 0);
		for (int i = 0; i < 12; i++) {
			one(level, e.position().add((level.random.nextDouble() - 0.5) * e.getBbWidth(), level.random.nextDouble() * e.getBbHeight(),
					(level.random.nextDouble() - 0.5) * e.getBbWidth()), MagicParticles.spark(color, 0.2F, 20), 0, 0.06, 0);
		}
	}
}
