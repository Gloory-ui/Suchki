package com.suchki.magic.spells;

import com.suchki.magic.Cast;
import com.suchki.magic.Magic;
import com.suchki.magic.MagicDamage;
import com.suchki.magic.MagicFx;
import com.suchki.magic.MagicParticles;
import com.suchki.magic.MagicRenderColors;
import com.suchki.magic.MagicSounds;
import com.suchki.magic.School;
import com.suchki.magic.Terrain;
import com.suchki.magic.Zones;
import java.util.List;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Общие приёмы заклинаний всех стихий: взрыв с затуханием урона и воронкой, удар стихии, круг-предупреждение
 * и удар следом, звук выпуска по рангу. Заклинания стихий собраны из этих кирпичиков, чтобы выглядеть и
 * работать одинаково честно (урон по союзникам не идёт, блоки ломаются только если разрешено).
 */
public final class SpellKit {
	public static final Vec3 UP = new Vec3(0, 1, 0);
	public static final Vec3 DOWN = new Vec3(0, -1, 0);

	private SpellKit() {}

	/** Вид урона стихии по умолчанию. */
	public static MagicDamage damageOf(School school) {
		return switch (school) {
			case FIRE -> MagicDamage.FIRE;
			case WATER -> MagicDamage.WATER;
			case WIND -> MagicDamage.WIND;
			case EARTH -> MagicDamage.EARTH;
			case LIGHTNING -> MagicDamage.SHOCK;
			case LIGHT -> MagicDamage.HOLY;
			case DARK -> MagicDamage.DARK;
			case SPACE -> MagicDamage.VOID;
			case LIFE -> MagicDamage.ARCANE;
			case MIND -> MagicDamage.PSYCHIC;
			case SUMMON, FUSION -> MagicDamage.ARCANE;
		};
	}

	/** Сила тряски камеры у выпуска по рангу. */
	public static float rankShake(int tier) {
		return 0.15F + 0.25F * tier;
	}

	// --- Выпуск ---

	/**
	 * Звук и отдача выпуска: звук стихии у руки, у рангов 6–7 — вспышка и тряска у мага. Удобно вызывать в начале
	 * действия заклинания (у мультикаста — только у главной копии).
	 */
	public static void release(Cast c) {
		if (c.copy > 0) {
			MagicFx.sound(c.level, c.hand(), MagicSounds.castOf(c.spell), 0.6F, 1.1F + 0.05F * c.copy);
			return;
		}
		MagicFx.soundFar(c.level, c.hand(), MagicSounds.castOf(c.spell), 16 + 8 * c.spell.tier, 1.0F + 0.1F * (4 - c.spell.tier) * 0.25F);
		if (c.spell.tier >= 6) {
			MagicFx.flash(c.level, c.eye(), 24, 0.35F, c.lightColor(), 8);
			Magic.shake(c.level, c.eye(), rankShake(c.spell.tier), 12, 24);
		}
	}

	// --- Удар и взрыв ---

	/** Небольшой удар стихии в точке: частицы, кольцо, звук попадания. {@code size} ~0.5 для стрелы, 2 для удара. */
	public static void impact(Cast c, Vec3 at, float size) {
		School s = c.spell.fusion() ? c.spell.combo.get(0) : c.spell.school;
		int color = c.color();
		MagicFx.burst(c.level, at, MagicParticles.spark(color, 0.25F + 0.15F * size, 14), Math.round(6 + 8 * size), 0.15 * size, 0.06 * size);
		MagicFx.burst(c.level, at, matter(s), Math.round(8 + 10 * size), 0.2 * size, 0.06 * size);
		if (size >= 1.0F) MagicFx.shockwave(c.level, at, UP, 0.2F, 1.4F * size, color, 10);
		MagicFx.sound(c.level, at, MagicSounds.impactOf(c.spell), Math.min(2.0F, 0.6F + 0.3F * size), 1.25F - 0.1F * size);
	}

	/** «Материя» стихии для частиц. */
	public static ParticleOptions matter(School s) {
		return switch (s) {
			case FIRE -> ParticleTypes.FLAME;
			case WATER -> ParticleTypes.SPLASH;
			case WIND -> ParticleTypes.CLOUD;
			case EARTH -> new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState());
			case LIGHTNING -> ParticleTypes.ELECTRIC_SPARK;
			case LIGHT -> ParticleTypes.END_ROD;
			case DARK -> ParticleTypes.SQUID_INK;
			case SPACE -> ParticleTypes.REVERSE_PORTAL;
			case LIFE -> ParticleTypes.HAPPY_VILLAGER;
			case MIND -> ParticleTypes.WITCH;
			case SUMMON -> ParticleTypes.SOUL_FIRE_FLAME;
			case FUSION -> ParticleTypes.FIREWORK;
		};
	}

	/**
	 * Взрыв: урон {@code damage} в центре, к краю радиуса — половина; отбрасывание; поджог/заморозка по стихии;
	 * воронка радиуса {@code crater} (0 — без разрушения); волна, сфера, вспышка и тряска по размеру.
	 * Возвращает задетых.
	 */
	public static List<LivingEntity> blast(Cast c, Vec3 at, double radius, float damage, MagicDamage kind, double crater, Terrain.Rim rim) {
		List<LivingEntity> hit = c.foesAround(at, radius);
		for (LivingEntity e : hit) {
			double dist = Cast.center(e).distanceTo(at);
			float k = (float) (1.0 - 0.5 * Math.min(1.0, dist / radius));
			c.hurt(e, damage * k, kind);
			if (kind == MagicDamage.FIRE) e.igniteForTicks(c.duration(80));
			if (kind == MagicDamage.FROST) e.setTicksFrozen(Math.max(e.getTicksFrozen(), 200));
			Cast.knock(e, at, 0.5 + 0.12 * radius * k, 0.25 + 0.05 * radius * k);
		}
		int color = c.color();
		int light = c.lightColor();
		MagicFx.sphere(c.level, at, (float) radius * 0.15F, (float) radius * 1.1F, color, light, 10 + (int) radius);
		MagicFx.shockwave(c.level, at.add(0, 0.2, 0), UP, 0.5F, (float) radius * 1.6F, MagicRenderColors.lighten(color, 0.3F), 14);
		MagicFx.burst(c.level, at, ParticleTypes.EXPLOSION_EMITTER, radius >= 6 ? 3 : 1, radius * 0.25, 0);
		MagicFx.burst(c.level, at, MagicParticles.spark(color, 0.5F, 18), (int) (16 + radius * 4), radius * 0.3, 0.15);
		MagicFx.burst(c.level, at, ParticleTypes.LARGE_SMOKE, (int) (8 + radius * 2), radius * 0.3, 0.05);
		if (radius >= 8) {
			MagicFx.soundFar(c.level, at, MagicSounds.EXPLOSION_HUGE, 128, 1.0F);
			MagicFx.flash(c.level, at, (float) radius * 10, Math.min(1.0F, (float) radius / 16.0F), light, 14);
		} else {
			MagicFx.soundFar(c.level, at, MagicSounds.EXPLOSION, 32 + (float) radius * 8, 0.9F + c.level.random.nextFloat() * 0.2F);
		}
		Magic.shake(c.level, at, (float) Math.min(4.0, 0.6 + radius * 0.25), 12 + (int) radius, radius * 8);
		if (crater > 0) Terrain.crater(c, at, crater, 0.75, rim);
		return hit;
	}

	/** Луч-удар: урон всему вдоль отрезка толщиной {@code width}; возвращает задетых. */
	public static List<LivingEntity> lineStrike(Cast c, Vec3 from, Vec3 to, double width, float damage, MagicDamage kind) {
		List<LivingEntity> hit = c.along(from, to, width, c::isFoe);
		for (LivingEntity e : hit) c.hurt(e, damage, kind);
		return hit;
	}

	// --- Предупреждение и удар следом ---

	/**
	 * Круг-предупреждение на земле в {@code at} радиуса {@code radius}: рисуется {@code ticks} тиков, потом
	 * (через {@code ticks}) выполняется {@code strike}. Пока рисуется — гул, на ударе круг гаснет.
	 */
	public static void telegraph(Cast c, Vec3 at, float radius, int ticks, Runnable strike) {
		MagicFx.circle(c.level, c.spell, at.add(0, 0.08, 0), UP, radius, ticks, 6, 14);
		Zones.later(c, ticks, strike);
	}

	/** Круг в небе над точкой, рисуется {@code ticks} тиков; удар следом. */
	public static void skyTelegraph(Cast c, Vec3 ground, float radius, float height, int ticks, Runnable strike) {
		MagicFx.circle(c.level, c.spell, ground.add(0, height, 0), DOWN, radius, Math.max(2, c.spell.layers), 1.4F, ticks, 10, 20);
		if (radius >= 6) MagicFx.soundFar(c.level, ground.add(0, height, 0), MagicSounds.CIRCLE_BIG, 96, 1.0F);
		Zones.later(c, ticks, strike);
	}

	/** Случайная точка в круге радиуса {@code r} вокруг {@code center} (по горизонтали). */
	public static Vec3 inDisc(RandomSource rnd, Vec3 center, double r) {
		double a = rnd.nextDouble() * Math.PI * 2;
		double d = Math.sqrt(rnd.nextDouble()) * r;
		return center.add(Math.cos(a) * d, 0, Math.sin(a) * d);
	}

	/** Повторять {@code action} каждые {@code every} тиков {@code times} раз (первый — сразу). */
	public static void repeat(Cast c, int times, int every, java.util.function.IntConsumer action) {
		for (int i = 0; i < times; i++) {
			int n = i;
			Zones.later(c, i * every, () -> action.accept(n));
		}
	}

	// --- Частые эффекты на существ ---

	public static void slow(Cast c, LivingEntity e, int ticks, int amp) {
		e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, c.duration(ticks), amp), c.caster);
	}

	public static void freeze(Cast c, LivingEntity e, int ticks) {
		e.setTicksFrozen(Math.max(e.getTicksFrozen(), ticks));
		slow(c, e, ticks / 2, 2);
	}

	/** Подбросить вверх (с учётом сопротивления отбрасыванию). */
	public static void launch(LivingEntity e, double up) {
		Cast.knock(e, e.position(), 0, up);
	}
}
