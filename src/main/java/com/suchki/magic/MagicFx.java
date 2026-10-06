package com.suchki.magic;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
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
 * Частицы, звуки и эффекты магии, которые видят и слышат все (отправляются с сервера).
 * Частицы шлются «издалека» (до 512 блоков), чтобы удар в 128 блоках был виден. Большие формы — круги, лучи,
 * сферы, столпы — шлются пакетами {@link FxNet} и рисуются клиентом.
 * Эффекты у руки во время зарядки рисует клиент сам — по состоянию каста.
 */
public final class MagicFx {
	/** Кому слать эффекты в мире: игрокам не дальше этого от точки. */
	public static final double FX_RANGE = 256.0;

	private MagicFx() {}

	// --- Примитивы ---

	public static void burst(ServerLevel level, Vec3 at, ParticleOptions p, int count, double spread, double speed) {
		level.sendParticles(p, true, false, at.x, at.y, at.z, count, spread, spread, spread, speed);
	}

	/** Облако частиц разного разброса по осям. */
	public static void cloud(ServerLevel level, Vec3 at, ParticleOptions p, int count, double sx, double sy, double sz, double speed) {
		level.sendParticles(p, true, false, at.x, at.y, at.z, count, sx, sy, sz, speed);
	}

	/** Одна частица со скоростью (dx, dy, dz) — у кругов это направление. */
	public static void one(ServerLevel level, Vec3 at, ParticleOptions p, double dx, double dy, double dz) {
		level.sendParticles(p, true, false, at.x, at.y, at.z, 0, dx, dy, dz, 1.0);
	}

	public static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
	}

	/** Громкий звук, слышный на {@code range} блоков (громкость выше 1 в Minecraft — это дальность). */
	public static void soundFar(ServerLevel level, Vec3 at, SoundEvent sound, float range, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, Math.max(1.0F, range / 16.0F), pitch);
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

	/** Зигзаг-молния из частиц (короткие разряды; большие молнии — {@link #lightningBolt}). */
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

	// --- Эффекты, которые рисует клиент ---

	private static void send(ServerLevel level, Vec3 a, Vec3 b, double range, CustomPacketPayload payload) {
		double r2 = range * range;
		for (ServerPlayer p : level.players()) {
			if (p.position().distanceToSqr(a) <= r2 || (b != null && p.position().distanceToSqr(b) <= r2)) {
				ServerPlayNetworking.send(p, payload);
			}
		}
	}

	/** Круг заклинания: узор стихии и ранга, цвета заклинания. */
	public static void circle(ServerLevel level, Spell spell, Vec3 pos, Vec3 normal, float radius, int draw, int hold, int fade) {
		circle(level, spell, pos, normal, radius, 1, 0, draw, hold, fade);
	}

	public static void circle(ServerLevel level, Spell spell, Vec3 pos, Vec3 normal, float radius, int layers, float gap, int draw,
			int hold, int fade) {
		School design = spell.fusion() ? spell.combo.get(level.random.nextInt(spell.combo.size())) : spell.school;
		circle(level, design, spell.tier, spell.color(), spell.lightColor(), pos, normal, radius, layers, gap, draw, hold, fade,
				0.15F + 0.05F * (8 - spell.tier));
	}

	/** Круг любого узора и цвета. {@code spin} — оборотов в секунду (знак — направление). */
	public static void circle(ServerLevel level, School design, int tier, int color, int light, Vec3 pos, Vec3 normal, float radius,
			int layers, float gap, int draw, int hold, int fade, float spin) {
		send(level, pos, null, FX_RANGE, new FxNet.CircleFx(pos, normal.normalize(), radius, color, light, design.ordinal(), tier,
				Math.max(1, layers), gap, Math.max(1, draw), Math.max(0, hold), Math.max(1, fade), spin, level.random.nextInt()));
	}

	public static void fx(ServerLevel level, int kind, Vec3 a, Vec3 b, float r0, float r1, int color, int light, int ticks) {
		send(level, a, kind == FxNet.WorldFx.BEAM || kind == FxNet.WorldFx.LIGHTNING || kind == FxNet.WorldFx.HELIX
				|| kind == FxNet.WorldFx.COMET ? b : null, FX_RANGE,
				new FxNet.WorldFx(kind, a, b, r0, r1, color, light, Math.max(1, ticks), level.random.nextInt()));
	}

	/** Луч света/энергии из {@code from} в {@code to}. */
	public static void beam(ServerLevel level, Vec3 from, Vec3 to, float width, int color, int light, int ticks) {
		fx(level, FxNet.WorldFx.BEAM, from, to, width, 0, color, light, ticks);
	}

	/** Большая ветвистая молния (рисует клиент). */
	public static void lightningBolt(ServerLevel level, Vec3 from, Vec3 to, float width, int color, int ticks) {
		fx(level, FxNet.WorldFx.LIGHTNING, from, to, width, 0, color, 0xFFFFFF, ticks);
	}

	/** Расширяющаяся сфера (взрывная волна). */
	public static void sphere(ServerLevel level, Vec3 at, float from, float to, int color, int light, int ticks) {
		fx(level, FxNet.WorldFx.SPHERE, at, at, from, to, color, light, ticks);
	}

	/** Купол на земле (адское пламя, атомный взрыв). */
	public static void dome(ServerLevel level, Vec3 at, float radius, int color, int light, int ticks) {
		fx(level, FxNet.WorldFx.DOME, at, at, radius, radius, color, light, ticks);
	}

	/** Чёрная дыра: тёмное ядро и светящееся кольцо. */
	public static void core(ServerLevel level, Vec3 at, float radius, float ring, int color, int light, int ticks) {
		fx(level, FxNet.WorldFx.CORE, at, at, radius, ring, color, light, ticks);
	}

	/** Ударное кольцо. */
	public static void shockwave(ServerLevel level, Vec3 at, Vec3 normal, float from, float to, int color, int ticks) {
		fx(level, FxNet.WorldFx.RING, at, normal, from, to, color, 0xFFFFFF, ticks);
	}

	/** Столп света вверх от точки. */
	public static void pillar(ServerLevel level, Vec3 base, float radius, float height, int color, int light, int ticks) {
		fx(level, FxNet.WorldFx.PILLAR, base, base, radius, height, color, light, ticks);
	}

	/** Вспышка экрана у игроков в радиусе. */
	public static void flash(ServerLevel level, Vec3 at, float radius, float strength, int color, int ticks) {
		send(level, at, null, radius, new FxNet.WorldFx(FxNet.WorldFx.FLASH, at, at, radius, strength, color, color, ticks, 0));
	}

	/** Смерч. */
	public static void tornado(ServerLevel level, Vec3 base, float radius, float height, int color, int light, int ticks) {
		fx(level, FxNet.WorldFx.TORNADO, base, base, radius, height, color, light, ticks);
	}

	/** Летящий серп энергии. */
	public static void slash(ServerLevel level, Vec3 from, Vec3 dir, float width, float distance, int color, int light, int ticks) {
		fx(level, FxNet.WorldFx.SLASH, from, dir.normalize(), width, distance, color, light, ticks);
	}

	/** Спиральный луч. */
	public static void helix(ServerLevel level, Vec3 from, Vec3 to, float width, int color, int light, int ticks) {
		fx(level, FxNet.WorldFx.HELIX, from, to, width, 0, color, light, ticks);
	}

	/** Полупрозрачная сфера-барьер (щит, «расширение территории»). */
	public static void barrier(ServerLevel level, Vec3 at, float radius, int color, int light, int ticks) {
		fx(level, FxNet.WorldFx.BARRIER, at, at, radius, radius, color, light, ticks);
	}

	/** Светящийся след падающей звезды/кометы из {@code from} в {@code to}. */
	public static void comet(ServerLevel level, Vec3 from, Vec3 to, float width, int color, int light, int ticks) {
		fx(level, FxNet.WorldFx.COMET, from, to, width, 0, color, light, ticks);
	}

	// --- Готовые эффекты ---

	static void castStart(ServerLevel level, ServerPlayer player, Spell spell) {
		if (spell.tier >= 5) {
			soundFar(level, player.position(), spell.tier >= 6 ? MagicSounds.RISER_LONG : MagicSounds.RISER_SHORT, 48, 1.0F);
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
			one(level, at, MagicParticles.mote(spell.color(), 0.12F, 30), 0, 0.02, 0);
		}
		circle(level, spell, player.position().add(0, 0.05, 0), new Vec3(0, 1, 0), 1.3F, 14, 10, 12);
		burst(level, c, ParticleTypes.ENCHANT, 40, 0.6, 0.6);
		sound(level, c, MagicSounds.LEARN, 0.9F, 1.0F);
	}

	static void spectralVanish(ServerLevel level, Vec3 at) {
		burst(level, at, MagicParticles.spark(School.SUMMON.color, 0.15F, 14), 12, 0.25, 0.04);
		sound(level, at, MagicSounds.VANISH, 0.6F, 1.4F);
	}

	static void undyingSaved(ServerLevel level, LivingEntity entity) {
		Vec3 c = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
		burst(level, c, ParticleTypes.TOTEM_OF_UNDYING, 60, 0.4, 0.6);
		shockwave(level, entity.position().add(0, 0.1, 0), new Vec3(0, 1, 0), 0.3F, 3.0F, School.LIFE.light, 16);
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
		circle(level, School.SUMMON, 3, color, MagicRenderColors.lighten(color, 0.6F), e.position().add(0, 0.05, 0), new Vec3(0, 1, 0),
				Math.max(0.9F, e.getBbWidth() * 1.2F), 1, 0, 8, 12, 10, 0.3F);
		for (int i = 0; i < 12; i++) {
			one(level, e.position().add((level.random.nextDouble() - 0.5) * e.getBbWidth(), level.random.nextDouble() * e.getBbHeight(),
					(level.random.nextDouble() - 0.5) * e.getBbWidth()), MagicParticles.spark(color, 0.2F, 20), 0, 0.06, 0);
		}
		sound(level, e.position(), MagicSounds.SUMMON, 0.8F, 1.0F);
	}
}
