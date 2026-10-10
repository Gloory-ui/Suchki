package com.suchki.magic.spells;

import static com.suchki.magic.spells.SpellKit.UP;

import com.suchki.magic.Anchor;
import com.suchki.magic.Cast;
import com.suchki.magic.CastStyle;
import com.suchki.magic.CircleLayout;
import com.suchki.magic.Magic;
import com.suchki.magic.MagicDamage;
import com.suchki.magic.MagicEffects;
import com.suchki.magic.MagicFx;
import com.suchki.magic.MagicParticles;
import com.suchki.magic.MagicSounds;
import com.suchki.magic.School;
import com.suchki.magic.Spell;
import com.suchki.magic.SpellProjectile;
import com.suchki.magic.Terrain;
import com.suchki.magic.Trail;
import com.suchki.magic.Zones;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Стихия «Вода и лёд»: ледяные шипы и копья, вальс копий вокруг мага, ледник вдоль линии, хрустальные темницы,
 * водяной дракон, градовый молот с неба и божественный потоп Левиафана.
 *
 * <p>Лёд всегда временный ({@link Terrain#temp}): корни у ног, темницы, гряды и шипы тают сами, даже если
 * разрушение выключено. Ломают блоки только «Рёв Владыки Пучины», «Градовый молот» и «Потоп Левиафана».
 */
public final class WaterSpells {
	/** Лёд: снаряды, искры, кольца. */
	static final int ICE = 0x8FD8FF;
	/** Иней: вспышки и белые кольца. */
	static final int FROST = 0xE8F8FF;
	/** Глубокая вода: дракон, волна. */
	static final int DEEP = 0x1E5FD8;
	/** Проклятый хрусталь: руны в темницах. */
	static final int CURSE = 0xB070FF;

	private static final BlockState PACKED = Blocks.PACKED_ICE.defaultBlockState();
	private static final BlockState BLUE = Blocks.BLUE_ICE.defaultBlockState();
	private static final BlockState WATER = Blocks.WATER.defaultBlockState();
	private static final ParticleOptions SHARDS = new BlockParticleOption(ParticleTypes.BLOCK, PACKED);
	private static final ParticleOptions BLUE_SHARDS = new BlockParticleOption(ParticleTypes.BLOCK, BLUE);

	/** Попадания «Ледяного клюва» по одной цели: UUID → {сколько, когда первое}. */
	private static final Map<UUID, long[]> BEAK_HITS = new HashMap<>();

	private WaterSpells() {}

	public static void register() {
		// ---------------- Ранг 1 — Начальный ----------------

		Spell.of("ice_shard", School.WATER, 1, CastStyle.BOLT).mana(10).charge(10).cooldown(12).range(48)
				.anchor(Anchor.HAND).layers(1).circle(0.55F).icon("packed_ice").model("packed_ice").size(0.28F).trail(Trail.ICE)
				.action(c -> {
					SpellKit.release(c);
					SpellProjectile.shoot(c, 2.2F).gravity(0.005).pierce(1).life(26);
					MagicFx.burst(c.level, c.hand(), ParticleTypes.SNOWFLAKE, 8, 0.12, 0.04);
					freezeOnWater(c, c.hand(), c.direction(), 48, 2.2);
					return true;
				})
				.hit((c, p, r) -> {
					if (r instanceof EntityHitResult e && e.getEntity() instanceof LivingEntity t && c.canHit(t)) {
						c.hurt(t, c.amount(6.0F), MagicDamage.FROST, p);
						chill(t, 80);
						t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, c.duration(60), 1), c.caster);
						t.clearFire();
					}
					Terrain.freezeWater(c, r.getLocation(), 1.5, 200);
					iceBurst(c, r.getLocation(), 0.6F);
				})
				.register();

		Spell.of("deep_breath", School.WATER, 1, CastStyle.SELF).mana(12).charge(10).cooldown(40).range(6)
				.anchor(Anchor.FEET).layers(1).circle(1.2F).icon("heart_of_the_sea").multicast(4)
				.action(c -> {
					LivingEntity who = c.caster;
					if (c.copy > 0) {
						// Копия мультикаста дарит то же дыхание союзнику рядом (своему у каждой копии).
						List<LivingEntity> allies = c.alliesAround(Cast.center(c.caster), 6);
						allies.remove(c.caster);
						allies.sort(Comparator.comparingDouble(e -> e.distanceToSqr(c.caster)));
						if (c.copy - 1 >= allies.size()) return true;
						who = allies.get(c.copy - 1);
						MagicFx.beam(c.level, Cast.center(c.caster), Cast.center(who), 0.08F, c.color(), c.lightColor(), 10);
					} else {
						SpellKit.release(c);
					}
					breathe(c, who);
					return true;
				})
				.register();

		// ---------------- Ранг 2 — Средний ----------------

		Spell.of("frost_beak", School.WATER, 2, CastStyle.BOLT).mana(20).charge(14).cooldown(30).range(48)
				.anchor(Anchor.BACK).layers(1).icon("prismarine_shard").model("packed_ice").size(0.3F).trail(Trail.ICE)
				.action(c -> {
					SpellKit.release(c);
					Vec3 look = c.look();
					Vec3 off = CircleLayout.copyOffset(c.spell, look, c.copy, c.copies);
					for (int j = 0; j < 3; j++) {
						Vec3 slot = CircleLayout.backSlot(c.caster.position(), look, j, 3, 2.7F).add(off);
						float pitch = 1.3F + 0.12F * j;
						MagicFx.burst(c.level, slot, ParticleTypes.SNOWFLAKE, 6, 0.15, 0.02);
						Zones.later(c, j * 4, () -> {
							Vec3 to = c.aimPoint().subtract(slot);
							Vec3 dir = to.lengthSqr() < 1.0E-4 ? c.look() : to.normalize();
							SpellProjectile.spawn(c, slot, dir.scale(2.2)).homing(0.08).life(30);
							MagicFx.burst(c.level, slot, MagicParticles.spark(ICE, 0.3F, 10), 8, 0.12, 0.05);
							MagicFx.burst(c.level, slot, ParticleTypes.POOF, 4, 0.1, 0.02);
							MagicFx.sound(c.level, slot, MagicSounds.castOf(c.spell), 0.7F, pitch);
						});
					}
					return true;
				})
				.hit((c, p, r) -> {
					if (r instanceof EntityHitResult e && e.getEntity() instanceof LivingEntity t && c.canHit(t)) {
						c.hurt(t, c.amount(7.0F), MagicDamage.FROST, p);
						chill(t, 140);
						t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, c.duration(60), 0), c.caster);
						if (beakCombo(c.level, t)) {
							// Все три копья в одну цель — она лопается ледяными осколками.
							c.hurt(t, c.amount(4.0F), MagicDamage.FROST, p);
							iceBurst(c, Cast.center(t), 1.4F);
							MagicFx.sound(c.level, Cast.center(t), MagicSounds.SHATTER, 1.0F, 1.2F);
						}
					} else if (r instanceof BlockHitResult b && r.getType() == HitResult.Type.BLOCK) {
						// Копьё вмерзает в землю ледяным шипом.
						Terrain.temp(c, b.getBlockPos().relative(b.getDirection()), PACKED, 100, false);
					}
					iceBurst(c, r.getLocation(), 0.7F);
				})
				.register();

		// ---------------- Ранг 3 — Продвинутый ----------------

		Spell.of("frost_nova", School.WATER, 3, CastStyle.SELF).mana(38).charge(18).cooldown(160).range(6)
				.anchor(Anchor.FEET).layers(2).circle(2.4F).icon("powder_snow_bucket").multicast(4)
				.action(c -> {
					if (c.copy == 0) SpellKit.release(c);
					nova(c);
					return true;
				})
				.register();

		Spell.of("frost_waltz", School.WATER, 3, CastStyle.SELF).mana(40).charge(24).cooldown(240).range(14)
				.anchor(Anchor.ORBIT).layers(1).circle(0.5F).icon("ice").model("packed_ice").size(0.26F).trail(Trail.ICE).multicast(4)
				.action(c -> {
					if (c.copy == 0) SpellKit.release(c);
					waltz(c);
					return true;
				})
				.hit((c, p, r) -> {
					if (r instanceof EntityHitResult e && e.getEntity() instanceof LivingEntity t && c.canHit(t)) {
						c.hurt(t, c.amount(7.0F), MagicDamage.FROST, p);
						chill(t, 100);
						t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, c.duration(60), 1), c.caster);
					}
					iceBurst(c, r.getLocation(), 0.6F);
				})
				.register();

		// ---------------- Ранг 4 — Святой ----------------

		Spell.of("glacier_ridge", School.WATER, 4, CastStyle.BOLT).mana(58).charge(30).cooldown(300).range(40)
				.anchor(Anchor.LINE).layers(5).icon("blue_ice")
				.action(c -> {
					SpellKit.release(c);
					ridge(c);
					return true;
				})
				.register();

		// ---------------- Ранг 5 — Королевский ----------------

		Spell.of("cursed_crystal_prison", School.WATER, 5, CastStyle.RITUAL).mana(100).charge(50).cooldown(700).range(96)
				.anchor(Anchor.TARGET).layers(2).circle(6.0F).icon("amethyst_shard")
				.action(c -> {
					SpellKit.release(c);
					crystalPrison(c);
					return true;
				})
				.register();

		Spell.of("abyssal_wyrm_roar", School.WATER, 5, CastStyle.BOLT).mana(95).charge(46).cooldown(700).range(56)
				.anchor(Anchor.LINE).layers(3).circle(2.2F).icon("trident").destroys()
				.action(c -> {
					SpellKit.release(c);
					wyrm(c);
					return true;
				})
				.register();

		// ---------------- Ранг 6 — Императорский ----------------

		Spell.of("hail_hammer", School.WATER, 6, CastStyle.RITUAL).mana(165).charge(72).cooldown(2200).range(128)
				.anchor(Anchor.SKY).layers(3).circle(10.0F).sky(30).icon("snow_block").model("blue_ice").size(0.5F).trail(Trail.ICE)
				.destroys()
				.action(c -> {
					SpellKit.release(c);
					hailHammer(c);
					return true;
				})
				.register();

		// ---------------- Ранг 7 — Божественный ----------------

		Spell.of("tidal_leviathan", School.WATER, 7, CastStyle.RITUAL).mana(280).charge(140).cooldown(7200).range(128)
				.anchor(Anchor.LINE).layers(5).circle(3.2F).icon("conduit").destroys()
				.action(c -> {
					SpellKit.release(c);
					leviathan(c);
					return true;
				})
				.register();
	}

	// =====================================================================================================
	// Ранги 1–2
	// =====================================================================================================

	/**
	 * Шип летит над водой — гладь под ним схватывается льдом (3×3, 10 с). Снаряд воду не задевает, поэтому
	 * место встречи с водой ищем лучом заранее и замораживаем, когда шип туда долетит.
	 */
	private static void freezeOnWater(Cast c, Vec3 from, Vec3 dir, double range, double speed) {
		Vec3 to = from.add(dir.scale(range));
		BlockHitResult water = c.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.SOURCE_ONLY, c.caster));
		if (water.getType() != HitResult.Type.BLOCK || !c.level.getFluidState(water.getBlockPos()).is(FluidTags.WATER)) return;
		double dist = water.getLocation().distanceTo(from);
		LivingEntity foe = c.target().entity() instanceof LivingEntity l ? l : null;
		if (foe != null && Cast.center(foe).distanceTo(from) < dist) return;
		Vec3 at = water.getLocation();
		Zones.later(c, Math.max(1, (int) (dist / speed)), () -> {
			if (Terrain.freezeWater(c, at, 1.5, 200) > 0) {
				MagicFx.burst(c.level, at.add(0, 0.6, 0), ParticleTypes.SNOWFLAKE, 16, 0.8, 0.02);
				MagicFx.sound(c.level, at, MagicSounds.FREEZE, 0.7F, 1.4F);
			}
		});
	}

	/** Дыхание глубин: подводное дыхание, грация дельфина, сила проводника (и ночное зрение при силе ×1,2). */
	private static void breathe(Cast c, LivingEntity who) {
		int dur = c.duration(3600);
		who.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, dur, 0), c.caster);
		who.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, dur, 0), c.caster);
		who.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, dur, 0), c.caster);
		if (c.power >= 1.2F) who.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, dur, 0), c.caster);
		who.clearFire();
		// Пузырьки спиралью вверх по телу, мягкое сияние у головы.
		Vec3 feet = who.position();
		for (int i = 0; i < 20; i++) {
			double a = i * 0.7;
			Vec3 at = feet.add(Math.cos(a) * 0.6, i * 0.1, Math.sin(a) * 0.6);
			MagicFx.one(c.level, at, ParticleTypes.BUBBLE_POP, 0, 0.05, 0);
			if (i % 2 == 0) MagicFx.one(c.level, at, MagicParticles.mote(0x5FD8FF, 0.14F, 24), 0, 0.03, 0);
		}
		MagicFx.burst(c.level, who.getEyePosition(), MagicParticles.spark(c.lightColor(), 0.35F, 20), 10, 0.3, 0.01);
		MagicFx.burst(c.level, Cast.center(who), ParticleTypes.SPLASH, 20, 0.4, 0.1);
		MagicFx.shockwave(c.level, feet.add(0, 0.1, 0), UP, 0.3F, 1.8F, c.color(), 12);
		MagicFx.sound(c.level, feet, SoundEvents.AMBIENT_UNDERWATER_ENTER, 0.6F, 1.3F);
		MagicFx.sound(c.level, feet, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 0.8F, 1.0F);
	}

	/** Три копья в одну цель за полсекунды — правда, когда это третье. */
	private static boolean beakCombo(ServerLevel level, LivingEntity t) {
		long now = level.getGameTime();
		if (BEAK_HITS.size() > 64) BEAK_HITS.values().removeIf(v -> now - v[1] > 40);
		long[] h = BEAK_HITS.get(t.getUUID());
		if (h == null || now - h[1] > 10) {
			BEAK_HITS.put(t.getUUID(), new long[] {1, now});
			return false;
		}
		if (++h[0] >= 3) {
			BEAK_HITS.remove(t.getUUID());
			return true;
		}
		return false;
	}

	// =====================================================================================================
	// Ранг 3
	// =====================================================================================================

	/**
	 * Белая Луна: удар холода по кругу у ног. Главная копия — радиус 6, 9 урона, ноги врагов вмерзают в лёд на 3 с;
	 * копии мультикаста — эхо-кольца, каждое на 2 блока шире и вполовину слабее.
	 */
	private static void nova(Cast c) {
		Vec3 feet = c.caster.position();
		double r = c.radius(6.0) + 2.0 * c.copy;
		float dmg = c.amount(9.0F) * (c.copy == 0 ? 1.0F : 0.5F);
		for (LivingEntity e : c.foesAround(feet.add(0, 1, 0), r)) {
			c.hurt(e, dmg, MagicDamage.FROST);
			e.setTicksFrozen(Math.max(e.getTicksFrozen(), 240));
			if (boss(e)) {
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, c.duration(80), 1), c.caster);
				continue;
			}
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, c.duration(80), 2), c.caster);
			if (c.copy == 0) root(c, e, 60);
			MagicFx.burst(c.level, e.position().add(0, 0.3, 0), SHARDS, 10, 0.3, 0.05);
		}
		if (c.copy == 0) Terrain.freezeWater(c, feet, 8, 200);
		// Столп белого света из круга, ледяное кольцо, кольца снежинок.
		if (c.copy == 0) {
			MagicFx.pillar(c.level, feet, 1.4F, 9.0F, FROST, 0xFFFFFF, 18);
			MagicFx.circle(c.level, c.spell, feet.add(0, 0.07, 0), UP, (float) r, 2, 0.3F, 4, 10, 16);
			Magic.shake(c.level, feet, 0.6F, 8, 14);
		}
		MagicFx.shockwave(c.level, feet.add(0, 0.2, 0), UP, 0.5F, (float) r, c.copy == 0 ? FROST : ICE, 14);
		MagicFx.ring(c.level, feet.add(0, 0.3, 0), 0.8, ParticleTypes.SNOWFLAKE, 40, r / 10.0);
		MagicFx.ring(c.level, feet.add(0, 0.7, 0), 0.8, MagicParticles.spark(ICE, 0.35F, 14), 28, r / 12.0);
		MagicFx.burst(c.level, feet.add(0, 1, 0), ParticleTypes.ITEM_SNOWBALL, 30, 0.6, 0.2);
		MagicFx.sound(c.level, feet, MagicSounds.castOf(c.spell), 1.0F, 0.8F);
		MagicFx.sound(c.level, feet, MagicSounds.FREEZE, 1.0F, c.copy == 0 ? 0.9F : 1.1F + 0.1F * c.copy);
	}

	/** Сколько копий в одном кольце «Вальса». */
	static final int LANCES = 6;

	/**
	 * Вальс ледяных копий: 6 копий кружат вокруг мага (оборот за 2 с) 20 с; раз в полсекунды одно летит в ближайшего
	 * видимого врага в 14 блоках; вражеский снаряд у копья разбивается вместе с ним; оставшиеся копья в конце
	 * разлетаются осколками (3 урона в радиусе 3). Копии мультикаста — ещё кольца, шире и в обратную сторону.
	 */
	private static void waltz(Cast c) {
		int life = c.duration(400);
		double ring = 1.8 + 0.8 * c.copy;
		double height = 1.4 + 0.25 * c.copy;
		int spin = c.copy % 2 == 0 ? 1 : -1;
		double phase = c.copy * 0.5;
		boolean[] alive = new boolean[LANCES];
		java.util.Arrays.fill(alive, true);
		Zones.add(c, c.caster.position(), 14, life, (z, age) -> {
			if (!c.caster.isAlive() || c.caster.level() != c.level) {
				z.stop();
				return;
			}
			Vec3 feet = c.caster.position();
			z.center = feet;
			int formed = Math.min(LANCES, age / 3 + 1);
			Vec3[] pos = new Vec3[LANCES];
			Vec3[] radial = new Vec3[LANCES];
			double base = phase + spin * age * (Math.PI * 2 / 40);
			for (int k = 0; k < LANCES; k++) {
				double a = base + k * Math.PI * 2 / LANCES;
				radial[k] = new Vec3(Math.cos(a), 0, Math.sin(a));
				pos[k] = feet.add(radial[k].scale(ring)).add(0, height + 0.15 * Math.sin(a * 2 + age * 0.1), 0);
			}
			// Копья появляются по одному: маленький круг, иней, нота «музыкальной шкатулки».
			if (age % 3 == 1 && age / 3 < LANCES) {
				int k = age / 3;
				MagicFx.circle(c.level, School.WATER, 3, ICE, FROST, pos[k], radial[k], 0.5F, 1, 0, 3, 2, 6, 0.6F);
				MagicFx.burst(c.level, pos[k], ParticleTypes.SNOWFLAKE, 6, 0.2, 0.02);
				MagicFx.sound(c.level, pos[k], SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 1.2F + 0.12F * k);
			}
			if (age % 2 == 0) {
				for (int k = 0; k < formed; k++) if (alive[k]) drawLance(c, pos[k], radial[k]);
			}
			if (age % 40 == 0) MagicFx.sound(c.level, feet, MagicSounds.LOOP_WIND, 0.25F, 1.8F);
			// Вражеские снаряды, задевшие копьё, разбиваются вместе с ним.
			if (age % 2 == 1) {
				AABB box = new AABB(feet, feet).inflate(ring + 2.0).expandTowards(0, height + 1, 0);
				for (Projectile pr : c.level.getEntitiesOfClass(Projectile.class, box, pr -> pr.isAlive() && !(pr.getOwner() != null
						&& (pr.getOwner() == c.caster || c.isAlly(pr.getOwner()))) && pr.getDeltaMovement().lengthSqr() > 0.01)) {
					for (int k = 0; k < formed; k++) {
						if (alive[k] && pr.position().distanceToSqr(pos[k]) < 1.5 * 1.5) {
							alive[k] = false;
							pr.discard();
							iceBurst(c, pos[k], 0.7F);
							MagicFx.sound(c.level, pos[k], MagicSounds.SHATTER, 0.8F, 1.5F);
							break;
						}
					}
				}
			}
			// Раз в полсекунды — бросок в ближайшего видимого врага.
			if (age >= 3 && age % 10 == 0) {
				LivingEntity foe = null;
				double best = Double.MAX_VALUE;
				for (LivingEntity e : c.enemiesAround(feet.add(0, 1, 0), 14)) {
					double d = e.distanceToSqr(c.caster);
					if (d < best && c.caster.hasLineOfSight(e)) {
						best = d;
						foe = e;
					}
				}
				if (foe != null) {
					for (int k = 0; k < formed; k++) {
						if (!alive[k]) continue;
						alive[k] = false;
						Vec3 dir = Cast.center(foe).subtract(pos[k]).normalize();
						SpellProjectile.spawn(c, pos[k], dir.scale(2.2)).life(12);
						MagicFx.line(c.level, pos[k], pos[k].add(dir.scale(1.5)), MagicParticles.spark(FROST, 0.2F, 8), 0.3);
						MagicFx.sound(c.level, pos[k], MagicSounds.castOf(c.spell), 0.8F, 1.6F);
						break;
					}
				}
			}
			boolean any = false;
			for (boolean b : alive) any |= b;
			if (!any && age > 3 * LANCES) {
				z.stop();
				return;
			}
			// Конец вальса: оставшиеся копья разлетаются осколками.
			if (age >= life && any) {
				for (LivingEntity e : c.foesAround(feet.add(0, 1, 0), 3.0)) {
					c.hurt(e, c.amount(3.0F), MagicDamage.FROST);
					chill(e, 60);
				}
				for (int k = 0; k < LANCES; k++) if (alive[k]) iceBurst(c, pos[k], 0.6F);
				MagicFx.shockwave(c.level, feet.add(0, height, 0), UP, 0.5F, 3.5F, ICE, 10);
				MagicFx.sound(c.level, feet, MagicSounds.SHATTER, 1.0F, 1.3F);
			}
		});
	}

	/** Копьё из искр: наклонено наружу, остриё вверх. */
	private static void drawLance(Cast c, Vec3 at, Vec3 radial) {
		Vec3 axis = radial.scale(0.35).add(0, 0.55, 0);
		MagicFx.one(c.level, at.add(axis), MagicParticles.spark(FROST, 0.16F, 4), 0, 0, 0);
		MagicFx.one(c.level, at, MagicParticles.spark(ICE, 0.22F, 4), 0, 0, 0);
		MagicFx.one(c.level, at.subtract(axis), MagicParticles.spark(ICE, 0.18F, 4), 0, 0, 0);
		if (c.level.random.nextInt(3) == 0) MagicFx.one(c.level, at, ParticleTypes.SNOWFLAKE, 0, -0.02, 0);
	}

	// =====================================================================================================
	// Ранг 4
	// =====================================================================================================

	/**
	 * Ледниковая гряда: ледник бежит вперёд 3 блока за тик на 40 блоков (шириной 5, до 6 высотой, треугольный
	 * в сечении) и тает через 10 с. Кого задел фронт в 3 блоках от оси — 14 урона холодом, вмерзание на 4 с
	 * (боссы — без вмерзания), подброс. Ставится только в воздух, воду и снег — ничего не ломает.
	 */
	private static void ridge(Cast c) {
		Vec3 feet = c.caster.position();
		Vec3 main = flat(c.look());
		Vec3 dir;
		if (c.copy > 0 && c.target().entity() == null) {
			// Лишние гряды без своей цели расходятся веером ±20°.
			int side = c.copy % 2 == 1 ? 1 : -1;
			dir = rotY(main, side * 20 * ((c.copy + 1) / 2));
		} else {
			Vec3 to = c.aimGround().subtract(feet);
			dir = new Vec3(to.x, 0, to.z).lengthSqr() < 1.0 ? main : new Vec3(to.x, 0, to.z).normalize();
		}
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		double len = 40.0;
		int baseY = Mth.floor(feet.y);
		Set<Integer> struck = new HashSet<>();
		double[] front = {1.5};
		RandomSource rnd = c.level.random;
		MagicFx.shockwave(c.level, feet.add(0, 0.2, 0), UP, 0.5F, 4.0F, FROST, 12);
		MagicFx.soundFar(c.level, feet, MagicSounds.FREEZE, 48, 0.7F);
		Zones.add(c, feet, len, 20, (z, age) -> {
			double from = front[0], until = Math.min(len, 1.5 + 3.0 * age);
			Vec3 a = feet.add(dir.scale(from)).add(0, 1, 0), b = feet.add(dir.scale(until)).add(0, 1, 0);
			// Сначала — кого задел фронт (чтобы лёд не встал внутрь них).
			for (LivingEntity e : c.along(a, b, 3.0, c::isFoe)) {
				if (!struck.add(e.getId())) continue;
				c.hurt(e, c.amount(14.0F), MagicDamage.FROST);
				e.setTicksFrozen(Math.max(e.getTicksFrozen(), 300));
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, c.duration(80), 3), c.caster);
				if (!boss(e)) root(c, e, 80);
				SpellKit.launch(e, 0.5);
				MagicFx.burst(c.level, Cast.center(e), BLUE_SHARDS, 16, 0.4, 0.1);
			}
			List<LivingEntity> near = c.level.getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(4.0), LivingEntity::isAlive);
			for (double d = Math.floor(from) + 1; d <= until; d += 1.0) {
				double taper = Math.min(1.0, Math.min(d / 5.0, (len - d) / 4.0 + 0.3));
				for (int w = -2; w <= 2; w++) {
					Vec3 q = feet.add(dir.scale(d)).add(side.scale(w));
					int top = surface(c.level, Mth.floor(q.x), baseY, Mth.floor(q.z), 3, 6);
					if (top == Integer.MIN_VALUE) continue;
					int hmax = w == 0 ? 6 : Math.abs(w) == 1 ? 4 : 2;
					int h = Math.max(1, (int) Math.round((hmax - rnd.nextInt(hmax / 2 + 1)) * taper));
					for (int dy = 1; dy <= h; dy++) {
						BlockPos p = BlockPos.containing(q.x, top + dy, q.z);
						if (occupied(near, p)) continue;
						Terrain.temp(c, p, w == 0 && dy <= h - 1 ? BLUE : PACKED, 200 + rnd.nextInt(30), false);
					}
				}
				if (((int) d) % 2 == 0) Terrain.freezeWater(c, feet.add(dir.scale(d)), 3, 200);
			}
			// Фронт: снежные выбросы, осколки, холодный туман.
			Vec3 tip = feet.add(dir.scale(until)).add(0, 1.5, 0);
			MagicFx.burst(c.level, tip, ParticleTypes.SNOWFLAKE, 30, 1.2, 0.08);
			MagicFx.burst(c.level, tip, SHARDS, 24, 1.0, 0.15);
			MagicFx.cloud(c.level, tip.add(0, -0.8, 0), ParticleTypes.CLOUD, 10, 2.0, 0.3, 2.0, 0.02);
			MagicFx.burst(c.level, tip, MagicParticles.spark(ICE, 0.45F, 14), 10, 1.0, 0.06);
			if (age % 2 == 1) MagicFx.soundFar(c.level, tip, MagicSounds.FREEZE, 40, 0.8F + 0.04F * age);
			front[0] = until;
			if (until >= len) {
				MagicFx.shockwave(c.level, tip.add(0, -1.2, 0), UP, 0.5F, 6.0F, FROST, 14);
				MagicFx.soundFar(c.level, tip, MagicSounds.impactOf(c.spell), 48, 0.6F);
				MagicFx.sound(c.level, tip, MagicSounds.SHATTER, 1.2F, 0.6F);
				z.stop();
			}
		});
	}

	// =====================================================================================================
	// Ранг 5
	// =====================================================================================================

	/** Хрустальная темница вокруг одного существа. */
	private static final class Prison {
		final LivingEntity who;
		final List<BlockPos> blocks;
		final Vec3 center;
		final boolean hold;
		int left;
		float lastHp;
		float taken;
		boolean done;

		Prison(LivingEntity who, List<BlockPos> blocks, int left, boolean hold) {
			this.who = who;
			this.blocks = blocks;
			this.center = Cast.center(who);
			this.left = left;
			this.hold = hold;
			this.lastHp = who.getHealth();
		}
	}

	/**
	 * Склеп Проклятого Хрусталя: волна инея сходится к центру (0,5 с), затем каждый враг в радиусе 6 получает
	 * 20 урона холодом и вмерзает в полую хрустальную темницу на 8 с (обездвижен, 4 урона в секунду). Темница
	 * лопается по сроку или от сильного удара (10+ урона): 18 урона в радиусе 2,5 и соседние темницы следом.
	 * Боссы — 2 с скованности и раскол; высокие (выше 2,5) — Замедление V без темницы.
	 */
	private static void crystalPrison(Cast c) {
		Vec3 center = c.aimGround();
		double r = c.copy == 0 ? c.radius(6.0) : c.radius(4.0);
		// Внутренний круг крутится навстречу внешнему; иней ползёт внутрь.
		MagicFx.circle(c.level, School.WATER, 5, CURSE, FROST, center.add(0, 0.14, 0), UP, (float) r * 0.66F, 1, 0, 10, 150, 20, -0.4F);
		MagicFx.shockwave(c.level, center.add(0, 0.3, 0), UP, (float) r + 1, 0.3F, FROST, 10);
		MagicFx.soundFar(c.level, center, MagicSounds.FREEZE, 64, 0.6F);
		Zones.add(c, center, r, 10, (z, age) -> {
			double rr = r * (1.0 - age / 10.0) + 0.3;
			MagicFx.ring(c.level, center.add(0, 0.3, 0), rr, ParticleTypes.SNOWFLAKE, 24, -0.05);
			MagicFx.ring(c.level, center.add(0, 0.6, 0), rr, MagicParticles.spark(ICE, 0.3F, 8), 12, -0.03);
			if (age % 3 == 0) MagicFx.burst(c.level, center.add(0, 1.5, 0), MagicParticles.glyph(CURSE, 0.3F, 30), 4, r * 0.5, 0.01);
		});
		Zones.later(c, 10, () -> {
			List<Prison> prisons = new ArrayList<>();
			for (LivingEntity e : c.foesAround(center.add(0, 1, 0), r)) {
				c.hurt(e, c.amount(20.0F), MagicDamage.FROST);
				if (!e.isAlive()) continue;
				if (boss(e)) {
					e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 4), c.caster);
					chill(e, 140);
					prisons.add(new Prison(e, List.of(), 40, false));
				} else if (e instanceof Player || e.getBbHeight() > 2.5F || e.getBbWidth() > 1.5F) {
					e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, c.duration(160), 4), c.caster);
					e.setTicksFrozen(Math.max(e.getTicksFrozen(), 140));
				} else {
					List<BlockPos> shell = shell(c, e, 160 + 20, true);
					e.addEffect(new MobEffectInstance(MagicEffects.PARALYSIS, 160, 0), c.caster);
					prisons.add(new Prison(e, shell, 160, true));
					Vec3 at = e.position();
					MagicFx.pillar(c.level, at, 1.4F, e.getBbHeight() + 1.5F, ICE, FROST, 16);
					MagicFx.burst(c.level, Cast.center(e), MagicParticles.glyph(CURSE, 0.35F, 50), 8, 0.4, 0.01);
					MagicFx.burst(c.level, Cast.center(e), BLUE_SHARDS, 20, 0.6, 0.15);
				}
			}
			// Земля промерзает: вода — в лёд, на земле — снег.
			Terrain.freezeWater(c, center, r + 2, 200);
			RandomSource rnd = c.level.random;
			for (int i = 0; i < 24; i++) {
				Vec3 q = SpellKit.inDisc(rnd, center, r);
				int top = surface(c.level, Mth.floor(q.x), Mth.floor(center.y), Mth.floor(q.z), 2, 4);
				if (top != Integer.MIN_VALUE) {
					BlockPos p = BlockPos.containing(q.x, top + 1, q.z);
					if (c.level.getBlockState(p.below()).isFaceSturdy(c.level, p.below(), Direction.UP)) {
						Terrain.temp(c, p, Blocks.SNOW.defaultBlockState(), 160 + rnd.nextInt(20), false);
					}
				}
			}
			MagicFx.shockwave(c.level, center.add(0, 0.2, 0), UP, 0.5F, (float) r + 2, FROST, 12);
			MagicFx.cloud(c.level, center.add(0, 1, 0), ParticleTypes.SNOWFLAKE, 60, r * 0.6, 1.0, r * 0.6, 0.05);
			MagicFx.soundFar(c.level, center, MagicSounds.SHATTER, 64, 0.7F);
			MagicFx.soundFar(c.level, center, MagicSounds.impactOf(c.spell), 64, 0.6F);
			Magic.shake(c.level, center, 1.2F, 12, 40);
			if (prisons.isEmpty()) return;
			Zones.add(c, center, r, 200, (z, age) -> {
				boolean any = false;
				for (Prison p : prisons) {
					if (p.done) continue;
					any = true;
					LivingEntity e = p.who;
					p.left--;
					float hp = e.getHealth();
					p.taken += Math.max(0, p.lastHp - hp);
					if (!e.isAlive() || p.left <= 0 || p.taken >= 10.0F) {
						shatterPrison(c, p, prisons);
						continue;
					}
					if (p.hold) {
						e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0);
						e.setTicksFrozen(Math.max(e.getTicksFrozen(), e.getTicksRequiredToFreeze() + 20));
					}
					if (age % 20 == 0) {
						c.hurt(e, c.amount(4.0F), MagicDamage.FROST);
						MagicFx.burst(c.level, Cast.center(e), MagicParticles.glyph(CURSE, 0.3F, 30), 3, 0.3, 0.01);
					}
					if (age % 40 == 5) MagicFx.sound(c.level, p.center, SoundEvents.AMETHYST_BLOCK_CHIME, 0.9F, 0.6F + 0.2F * c.level.random.nextFloat());
					p.lastHp = e.getHealth();
				}
				if (!any) z.stop();
			});
		});
	}

	/** Темница лопается: лёд исчезает, 18 урона холодом в радиусе 2,5, соседние темницы трескаются следом. */
	private static void shatterPrison(Cast c, Prison p, List<Prison> all) {
		p.done = true;
		unshell(c, p.blocks);
		if (p.who.isAlive()) p.who.removeEffect(MagicEffects.PARALYSIS);
		Vec3 at = p.who.isAlive() ? Cast.center(p.who) : p.center;
		for (LivingEntity e : c.foesAround(at, 2.5)) {
			c.hurt(e, c.amount(18.0F), MagicDamage.FROST);
			chill(e, 120);
		}
		for (Prison o : all) {
			if (!o.done && o.center.distanceToSqr(p.center) < 4.5 * 4.5) o.left = Math.min(o.left, 3);
		}
		MagicFx.burst(c.level, at, BLUE_SHARDS, 40, 0.7, 0.3);
		MagicFx.burst(c.level, at, SHARDS, 30, 0.6, 0.25);
		MagicFx.burst(c.level, at, MagicParticles.spark(CURSE, 0.4F, 16), 14, 0.5, 0.1);
		MagicFx.shockwave(c.level, p.who.position().add(0, 0.3, 0), UP, 0.4F, 3.5F, FROST, 10);
		MagicFx.soundFar(c.level, at, MagicSounds.SHATTER, 40, 0.9F + 0.2F * c.level.random.nextFloat());
		MagicFx.sound(c.level, at, SoundEvents.GLASS_BREAK, 1.0F, 0.7F);
	}

	/**
	 * Рёв Владыки Пучины: водяной дракон из кругов несётся по взгляду 3 блока за тик на 56 блоков (шириной 4).
	 * Кого задел — 22 урона водой (×2 по ифритам, магмовым кубам, лавомеркам и эндерменам), гасит огонь,
	 * Замедление II на 4 с и уносит потоком. Лава на пути — в обсидиан (текущая — в булыжник), огонь гаснет,
	 * местами остаются лужи (10 с). С разрушением — сносит хрупкое (листву, траву, стекло, факелы, снег).
	 */
	private static void wyrm(Cast c) {
		Vec3 from = c.hand();
		Vec3 dir = c.direction();
		double len = 56.0;
		Set<Integer> struck = new HashSet<>();
		Map<LivingEntity, Integer> carried = new HashMap<>();
		double[] head = {1.0};
		boolean[] stopped = {false};
		RandomSource rnd = c.level.random;
		MagicFx.soundFar(c.level, from, SoundEvents.ENDER_DRAGON_GROWL, 64, 1.3F);
		MagicFx.soundFar(c.level, from, MagicSounds.RELEASE_HEAVY, 64, 0.8F);
		MagicFx.burst(c.level, from, ParticleTypes.SPLASH, 60, 0.6, 0.4);
		MagicFx.shockwave(c.level, from, dir, 0.4F, 3.5F, c.color(), 10);
		Zones.add(c, from, len, 40, (z, age) -> {
			// Унесённых потоком тащит ещё несколько тиков.
			Iterator<Map.Entry<LivingEntity, Integer>> it = carried.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<LivingEntity, Integer> en = it.next();
				LivingEntity e = en.getKey();
				double k = 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
				if (e.isAlive() && k > 0) {
					e.setDeltaMovement(dir.scale(1.4 * k).add(0, 0.12, 0));
					e.hurtMarked = true;
				}
				if (en.setValue(en.getValue() - 1) <= 1) it.remove();
			}
			if (stopped[0]) {
				if (carried.isEmpty()) z.stop();
				return;
			}
			double s0 = head[0], s1 = Math.min(len, s0 + 3.0);
			for (double s = s0 + 1.0; s <= s1 + 1.0E-6; s += 1.0) {
				Vec3 p = from.add(dir.scale(s));
				BlockPos bp = BlockPos.containing(p);
				BlockState st = c.level.getBlockState(bp);
				if (wall(c.level, bp, st)) {
					s1 = s - 1.0;
					stopped[0] = true;
					break;
				}
				quench(c, bp, 2);
				if (Terrain.griefing(c.level)) Terrain.shatter(c, p, 1.5);
				if (rnd.nextFloat() < 0.2F) puddle(c, p);
			}
			Vec3 a = from.add(dir.scale(s0)), b = from.add(dir.scale(s1));
			for (LivingEntity e : c.along(a, b, 2.0, c::isFoe)) {
				if (!struck.add(e.getId())) continue;
				float dmg = c.amount(22.0F) * (fearsWater(e) ? 2.0F : 1.0F);
				c.hurt(e, dmg, MagicDamage.WATER);
				e.clearFire();
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, c.duration(80), 1), c.caster);
				double k = 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
				e.push(dir.x * 2.0 * k, 0.35, dir.z * 2.0 * k);
				e.hurtMarked = true;
				carried.put(e, 4);
				MagicFx.burst(c.level, Cast.center(e), ParticleTypes.SPLASH, 30, 0.5, 0.3);
			}
			// Голова дракона: пена и брызги, два светящихся глаза, за ней — лента реки.
			Vec3 side = CircleLayout.right(dir);
			Vec3 up = CircleLayout.up(dir);
			MagicFx.helix(c.level, a, b, 1.7F, DEEP, c.lightColor(), 16);
			MagicFx.comet(c.level, a, b, 1.3F, c.color(), FROST, 8);
			MagicFx.burst(c.level, b, ParticleTypes.SPLASH, 40, 1.1, 0.35);
			MagicFx.burst(c.level, b, ParticleTypes.BUBBLE_POP, 14, 1.0, 0.1);
			MagicFx.burst(c.level, b, ParticleTypes.CLOUD, 6, 0.9, 0.03);
			MagicFx.burst(c.level, b, MagicParticles.spark(DEEP, 0.7F, 10), 8, 0.8, 0.05);
			MagicFx.one(c.level, b.add(side.scale(0.9)).add(up.scale(0.6)), MagicParticles.spark(0xFFFFFF, 0.45F, 6), 0, 0, 0);
			MagicFx.one(c.level, b.add(side.scale(-0.9)).add(up.scale(0.6)), MagicParticles.spark(0xFFFFFF, 0.45F, 6), 0, 0, 0);
			MagicFx.line(c.level, a, b, ParticleTypes.FALLING_WATER, 0.5);
			if (age % 6 == 1) MagicFx.soundFar(c.level, b, MagicSounds.LOOP_WATER, 40, 1.0F);
			head[0] = s1;
			if (s1 >= len - 1.0E-6) stopped[0] = true;
			if (stopped[0]) {
				// Удар в конце: тяжёлый всплеск куполом.
				MagicFx.dome(c.level, b, 3.5F, c.color(), FROST, 16);
				MagicFx.burst(c.level, b, ParticleTypes.SPLASH, 90, 1.6, 0.6);
				MagicFx.sound(c.level, b, SoundEvents.GENERIC_SPLASH, 2.0F, 0.6F);
				MagicFx.soundFar(c.level, b, MagicSounds.impactOf(c.spell), 48, 0.7F);
				Magic.shake(c.level, b, 0.8F, 8, 20);
			}
		});
	}

	/** Огонь и лава гаснут: лава-исток → обсидиан, текущая → булыжник, огонь → воздух (в радиусе {@code r}). */
	private static void quench(Cast c, BlockPos at, int r) {
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-r, -r, -r), at.offset(r, r, r))) {
			BlockState s = c.level.getBlockState(p);
			if (s.is(BlockTags.FIRE)) {
				if (c.level.mayInteract(c.caster, p)) c.level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
			} else if (s.getFluidState().is(FluidTags.LAVA) && c.level.mayInteract(c.caster, p)) {
				c.level.setBlock(p, s.getFluidState().isSource() ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState(), 3);
				MagicFx.burst(c.level, Vec3.atCenterOf(p), ParticleTypes.LARGE_SMOKE, 4, 0.3, 0.02);
				MagicFx.sound(c.level, Vec3.atCenterOf(p), SoundEvents.LAVA_EXTINGUISH, 0.6F, 1.0F);
			}
		}
	}

	/** Лужа после потока: временный исток воды на земле под точкой (10 с). */
	private static void puddle(Cast c, Vec3 p) {
		int top = surface(c.level, Mth.floor(p.x), Mth.floor(p.y), Mth.floor(p.z), 0, 4);
		if (top == Integer.MIN_VALUE) return;
		BlockPos at = BlockPos.containing(p.x, top + 1, p.z);
		BlockState below = c.level.getBlockState(at.below());
		if (below.getFluidState().isEmpty() && c.level.getBlockState(at).isAir()) Terrain.temp(c, at, WATER, 200, false);
	}

	/** Огонь и вода не дружат: ифриты, магмовые кубы, лавомерки и эндермены получают двойной урон водой. */
	private static boolean fearsWater(LivingEntity e) {
		EntityType<?> t = e.getType();
		return t == EntityType.BLAZE || t == EntityType.MAGMA_CUBE || t == EntityType.STRIDER || t == EntityType.ENDERMAN;
	}

	// =====================================================================================================
	// Ранг 6
	// =====================================================================================================

	/** Сколько тиков формируется глыба в небе и сколько она падает. */
	static final int HAMMER_FORM = 50;
	static final double HAMMER_FALL = 1.5;

	/**
	 * Градовый молот ста цветов: из небесного круга (30 блоков над целью) 2,5 с идёт снег — враги в радиусе 10
	 * замедлены и промерзают, на них расцветают ледяные цветы (2 урона, +1 цветок каждые полсекунды); тем временем
	 * в небе кристаллизуется глыба и падает (1,5 блока за тик). Удар: 40 урона холодом в центре → 40% на краю
	 * радиуса 8, +2 за каждый цветок (до 10), отбрасывание; враги в 4 блоках вмерзают в ледяные темницы на 4 с;
	 * вода в 10 блоках замерзает. С разрушением — воронка радиуса 5 глубиной 4, полная ледяных шипов (15 с).
	 * Копии мультикаста — глыбы поменьше (60% урона, радиус 5) на следующих врагов.
	 */
	private static void hailHammer(Cast c) {
		Vec3 ground = c.aimGround();
		float sky = c.spell.skyHeight();
		Vec3 top = ground.add(0, sky - 3, 0);
		double snowR = c.copy == 0 ? 10.0 : 6.0;
		Map<UUID, Integer> flowers = new HashMap<>();
		RandomSource rnd = c.level.random;
		// Сто цветков-глифов по краю круга, нить света к земле, рождение глыбы.
		MagicFx.ring(c.level, ground.add(0, sky - 0.3, 0), c.spell.circleRadius() * (c.copy == 0 ? 1.0 : 0.75), MagicParticles.glyph(FROST, 0.35F, 70),
				50, 0);
		MagicFx.circle(c.level, c.spell, ground.add(0, sky - 1.5, 0), SpellKit.DOWN, 4.0F, 1, 0, 14, HAMMER_FORM, 16);
		MagicFx.beam(c.level, ground.add(0, sky, 0), ground, 0.25F, ICE, FROST, HAMMER_FORM);
		MagicFx.sphere(c.level, top, 0.4F, c.copy == 0 ? 4.5F : 3.0F, ICE, FROST, HAMMER_FORM);
		MagicFx.soundFar(c.level, top, MagicSounds.CIRCLE_BIG, 96, 0.8F);
		MagicFx.soundFar(c.level, ground, MagicSounds.LOOP_WIND, 64, 0.6F);
		Zones.add(c, ground, snowR, HAMMER_FORM, (z, age) -> {
			// Снег из круга и иней, сходящийся в глыбу.
			MagicFx.cloud(c.level, ground.add(0, sky * 0.55, 0), ParticleTypes.SNOWFLAKE, 14, snowR * 0.6, sky * 0.3, snowR * 0.6, 0.01);
			if (age % 2 == 0) {
				for (int i = 0; i < 4; i++) {
					Vec3 o = top.add((rnd.nextDouble() - 0.5) * 10, (rnd.nextDouble() - 0.5) * 6, (rnd.nextDouble() - 0.5) * 10);
					Vec3 v = top.subtract(o).scale(0.08);
					MagicFx.one(c.level, o, MagicParticles.spark(ICE, 0.4F, 14), v.x, v.y, v.z);
				}
				MagicFx.burst(c.level, top, BLUE_SHARDS, 6, 0.4 + age * 0.05, 0.02);
			}
			if (age % 10 == 0) {
				MagicFx.sound(c.level, top, SoundEvents.AMETHYST_BLOCK_CHIME, 2.0F, 0.5F + age * 0.01F);
				MagicFx.sound(c.level, top, MagicSounds.FREEZE, 1.5F, 0.6F + age * 0.008F);
			}
			if (age % 5 != 0) return;
			List<LivingEntity> inside = c.foesAround(ground.add(0, 2, 0), snowR);
			for (LivingEntity e : inside) {
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 1), c.caster);
				e.setTicksFrozen(Math.min(400, e.getTicksFrozen() + 5));
			}
			// Каждые полсекунды на случайном враге расцветает ледяной цветок.
			if (age % 10 == 0 && !inside.isEmpty()) {
				LivingEntity e = inside.get(rnd.nextInt(inside.size()));
				c.hurt(e, c.amount(2.0F), MagicDamage.FROST);
				flowers.merge(e.getUUID(), 1, Integer::sum);
				Vec3 at = e.getEyePosition().add(0, 0.4, 0);
				MagicFx.burst(c.level, at, MagicParticles.glyph(FROST, 0.4F, 30), 3, 0.15, 0.01);
				MagicFx.burst(c.level, at, ParticleTypes.SNOWFLAKE, 10, 0.3, 0.03);
				MagicFx.sound(c.level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.6F);
			}
		});
		// Глыба падает: визуально — огромный снаряд из синего льда, по-настоящему — точка, которую ведёт зона.
		int fall = Math.max(1, (int) Math.ceil((sky - 3) / HAMMER_FALL));
		Zones.later(c, HAMMER_FORM, () -> {
			SpellProjectile.spawn(c, top, new Vec3(0, -HAMMER_FALL, 0)).scale(c.copy == 0 ? 6.0F : 4.0F).pierce(99).life(fall - 1);
			MagicFx.comet(c.level, top, ground, c.copy == 0 ? 4.0F : 2.5F, ICE, FROST, fall + 4);
			MagicFx.soundFar(c.level, top, MagicSounds.METEOR_FALL, 128, 0.7F);
			Zones.add(c, top, 4, fall + 1, (z, age) -> {
				Vec3 at = top.add(0, -HAMMER_FALL * age, 0);
				MagicFx.burst(c.level, at, ParticleTypes.SNOWFLAKE, 12, 1.5, 0.05);
				MagicFx.burst(c.level, at.add(0, 2, 0), ParticleTypes.CLOUD, 4, 1.2, 0.02);
				if (at.y <= ground.y + 1.0) {
					z.stop();
					hammerImpact(c, ground, flowers);
				}
			});
		});
	}

	/** Удар глыбы. */
	private static void hammerImpact(Cast c, Vec3 at, Map<UUID, Integer> flowers) {
		boolean main = c.copy == 0;
		double r = c.radius(main ? 8.0 : 5.0);
		float dmg = c.amount(40.0F) * (main ? 1.0F : 0.6F);
		for (LivingEntity e : c.foesAround(at.add(0, 1, 0), r)) {
			double d = Cast.center(e).distanceTo(at);
			float k = (float) (1.0 - 0.6 * Math.min(1.0, d / r));
			int stacks = Math.min(10, flowers.getOrDefault(e.getUUID(), 0));
			c.hurt(e, dmg * k + c.amount(2.0F) * stacks, MagicDamage.FROST);
			e.setTicksFrozen(Math.max(e.getTicksFrozen(), 400));
			Cast.knock(e, at, 1.2 * k, 0.4 * k);
			if (d <= 4.0 && e.isAlive() && !boss(e) && !(e instanceof Player) && e.getBbHeight() <= 2.5F && e.getBbWidth() <= 1.5F) {
				List<BlockPos> shell = shell(c, e, 80, false);
				e.addEffect(new MobEffectInstance(MagicEffects.PARALYSIS, 80, 0), c.caster);
				Zones.later(c, 80, () -> {
					unshell(c, shell);
					if (e.isAlive()) iceBurst(c, Cast.center(e), 1.0F);
				});
			}
		}
		flowers.clear();
		Terrain.freezeWater(c, at, main ? 10 : 6, 300);
		double crater = main ? 5.0 : 3.0;
		Terrain.crater(c, at, crater, 0.8, Terrain.Rim.ICE);
		Zones.later(c, 6, () -> spikes(c, at, crater, main ? 16 : 8, 300));
		// Белая вспышка, осколки, морозное кольцо до 12 блоков, катящийся холодный туман.
		MagicFx.flash(c.level, at, main ? 96 : 48, main ? 0.8F : 0.4F, FROST, 16);
		MagicFx.shockwave(c.level, at.add(0, 0.3, 0), UP, 1.0F, main ? 12.0F : 7.0F, FROST, 18);
		MagicFx.shockwave(c.level, at.add(0, 1.0, 0), UP, 0.5F, main ? 9.0F : 5.0F, ICE, 14);
		MagicFx.sphere(c.level, at, 1.0F, (float) r, ICE, FROST, 14);
		MagicFx.burst(c.level, at.add(0, 1, 0), BLUE_SHARDS, 120, r * 0.3, 0.5);
		MagicFx.burst(c.level, at.add(0, 1, 0), SHARDS, 80, r * 0.3, 0.4);
		MagicFx.cloud(c.level, at.add(0, 0.6, 0), ParticleTypes.CLOUD, 60, r * 0.7, 0.4, r * 0.7, 0.04);
		MagicFx.burst(c.level, at.add(0, 2, 0), ParticleTypes.SNOWFLAKE, 80, r * 0.5, 0.15);
		MagicFx.soundFar(c.level, at, MagicSounds.EXPLOSION_HUGE, main ? 160 : 80, 1.1F);
		MagicFx.soundFar(c.level, at, MagicSounds.SHATTER, 96, 0.5F);
		MagicFx.sound(c.level, at, SoundEvents.GLASS_BREAK, 3.0F, 0.5F);
		Magic.shake(c.level, at, main ? 3.0F : 1.6F, 24, main ? 72 : 40);
	}

	/** Ледяные шипы из земли в круге радиуса {@code r} (временные, в центре выше). */
	private static void spikes(Cast c, Vec3 center, double r, int count, int ticks) {
		RandomSource rnd = c.level.random;
		for (int i = 0; i < count; i++) {
			Vec3 q = SpellKit.inDisc(rnd, center, r);
			int top = surface(c.level, Mth.floor(q.x), Mth.floor(center.y) + 1, Mth.floor(q.z), 3, 8);
			if (top == Integer.MIN_VALUE) continue;
			double k = 1.0 - q.distanceTo(new Vec3(center.x, q.y, center.z)) / (r + 1);
			int h = 1 + rnd.nextInt(2) + (int) Math.round(2.5 * k);
			for (int dy = 1; dy <= h; dy++) {
				Terrain.temp(c, BlockPos.containing(q.x, top + dy, q.z), dy == h ? PACKED : BLUE, ticks + rnd.nextInt(40), false);
			}
		}
	}

	// =====================================================================================================
	// Ранг 7
	// =====================================================================================================

	/** Потоп Левиафана: длина пути волны, ширина, высота, скорость. */
	static final double TIDE_START = 12, TIDE_LENGTH = 80, TIDE_HALF = 16, TIDE_HEIGHT = 10, TIDE_SPEED = 2;
	/** Этапы: волна поднимается, бежит, обрушивается, всё замерзает, оттаивает. */
	static final int TIDE_RISE = 10, TIDE_RUN = 40, TIDE_FREEZE = 10, TIDE_THAW = 100;

	/**
	 * Потоп Левиафана. Из пяти кругов поднимается вода; в 12 блоках впереди встаёт цунами шириной 32 и высотой
	 * 10 и катится по горизонтали 2 блока за тик на 80 блоков (2 с). Кого накрыла — 60 урона водой (боссы — 40),
	 * уносит волной, гасит огонь, Замедление III на 5 с. В конце волна обрушивается: 40 урона водой в радиусе 12
	 * и сильный отброс. Через полсекунды «дыхание Левиафана» мгновенно замораживает затопленную полосу: враги
	 * в ней вмерзают в лёд на 5 с и при оттаивании получают ещё 25 урона холодом. Лава на пути — в обсидиан;
	 * вода — временная. С разрушением волна сносит хрупкое (листву, траву, стекло, снег, песок) до 3 блоков высотой.
	 */
	private static void leviathan(Cast c) {
		Vec3 feet = c.caster.position();
		Vec3 dir = flat(c.look());
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		int baseY = Mth.floor(feet.y);
		Vec3 start = feet.add(dir.scale(TIDE_START));
		Vec3 end = feet.add(dir.scale(TIDE_START + TIDE_LENGTH));
		List<BlockPos> flood = new ArrayList<>();
		Set<Integer> struck = new HashSet<>();
		RandomSource rnd = c.level.random;

		// Пролог: вода спиралью поднимается в пять кругов «ствола», морской змей проходит сквозь них.
		Vec3 hand = c.hand();
		Vec3 point = c.aimPoint();
		Vec3 last = hand;
		for (int k = 0; k < 5; k++) {
			Vec3 o = CircleLayout.lineStep(hand, point, k, 5);
			int top = surface(c.level, Mth.floor(o.x), baseY, Mth.floor(o.z), 1, 8);
			Vec3 g = new Vec3(o.x, top == Integer.MIN_VALUE ? feet.y : top + 1, o.z);
			MagicFx.helix(c.level, g, o, 0.9F, DEEP, c.lightColor(), 20);
			last = o;
		}
		MagicFx.helix(c.level, hand, last, 1.4F, FROST, 0xFFFFFF, 18);
		MagicFx.soundFar(c.level, feet, MagicSounds.RELEASE_ULTIMATE, 192, 1.0F);
		MagicFx.soundFar(c.level, start, SoundEvents.ENDER_DRAGON_GROWL, 128, 0.5F);
		MagicFx.flash(c.level, feet, 64, 0.5F, c.lightColor(), 12);
		Magic.shake(c.level, feet, 2.0F, 30, 96);
		// Врата волны: огромный вертикальный круг впереди.
		MagicFx.circle(c.level, School.WATER, 7, c.color(), c.lightColor(), start.add(0, TIDE_HEIGHT * 0.5, 0), dir, 9.0F, 3, 0.8F, 10, TIDE_RISE + 10,
				20, 0.25F);

		// 1. Волна поднимается.
		Zones.add(c, start, TIDE_HALF, TIDE_RISE, (z, age) -> {
			double h = TIDE_HEIGHT * age / TIDE_RISE;
			for (int l = -15; l <= 15; l += 3) {
				Vec3 at = start.add(side.scale(l)).add(0, h * 0.5, 0);
				MagicFx.cloud(c.level, at, ParticleTypes.SPLASH, 8, 0.8, h * 0.4, 0.8, 0.2);
				if (age % 2 == 0) MagicFx.cloud(c.level, at, ParticleTypes.FALLING_WATER, 4, 0.8, h * 0.4, 0.8, 0);
			}
			if (age % 3 == 0) MagicFx.soundFar(c.level, start, MagicSounds.LOOP_WATER, 96, 0.6F);
		});

		// 2. Волна бежит.
		Zones.later(c, TIDE_RISE, () -> {
			MagicFx.slash(c.level, start.add(0, TIDE_HEIGHT * 0.6, 0), dir, (float) TIDE_HALF, (float) TIDE_LENGTH, DEEP, FROST, TIDE_RUN + 6);
			MagicFx.soundFar(c.level, start, MagicSounds.BEAM_BLAST, 160, 0.6F);
			double[] front = {TIDE_START};
			Zones.add(c, start, TIDE_HALF, TIDE_RUN, (z, age) -> {
				double s0 = front[0], s1 = TIDE_START + TIDE_SPEED * age;
				front[0] = s1;
				// Кого накрыло (полоса у фронта волны).
				Vec3 f = feet.add(dir.scale(s1));
				AABB box = slab(feet, dir, side, s1 - 4, s1 + 1, TIDE_HALF, baseY);
				for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && c.isFoe(e))) {
					Vec3 rel = e.position().subtract(feet);
					double along = rel.dot(dir), lat = rel.dot(side);
					if (along < s1 - 4 || along > s1 + 1 || Math.abs(lat) > TIDE_HALF + 0.5) continue;
					if (struck.add(e.getId())) {
						c.hurt(e, c.amount(boss(e) ? 40.0F : 60.0F), MagicDamage.WATER);
						e.clearFire();
						e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, c.duration(100), 2), c.caster);
						MagicFx.burst(c.level, Cast.center(e), ParticleTypes.SPLASH, 30, 0.5, 0.3);
					}
					double k = 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
					if (k > 0) {
						e.setDeltaMovement(dir.scale(1.5 * k).add(0, 0.15, 0));
						e.hurtMarked = true;
					}
				}
				// Пройденная полоса: лава гаснет, земля затоплена, хрупкое сносит.
				for (double s = Math.floor(s0) + 1; s <= s1; s += 1.0) {
					Vec3 row = feet.add(dir.scale(s));
					for (int l = -16; l <= 16; l++) {
						Vec3 q = row.add(side.scale(l));
						int top = surface(c.level, Mth.floor(q.x), baseY, Mth.floor(q.z), 6, 8);
						if (top == Integer.MIN_VALUE) continue;
						BlockPos ground = BlockPos.containing(q.x, top, q.z);
						if (c.level.getFluidState(ground).is(FluidTags.LAVA)) quench(c, ground, 0);
						if (((int) s) % 2 == 0 && l % 3 == 0) {
							BlockPos above = ground.above();
							if (c.level.getBlockState(above).isAir() && c.level.getFluidState(ground).isEmpty()
									&& Terrain.temp(c, above, WATER, 150 + rnd.nextInt(20), false)) {
								flood.add(above);
							}
						}
						if (((int) s) % 2 == 0 && l % 2 == 0 && Terrain.griefing(c.level)) {
							Terrain.shatter(c, new Vec3(q.x, top + 2.5, q.z), 1.5);
						}
					}
				}
				// Стена воды: брызги по всей ширине, пенный гребень, змей Левиафана по верху.
				for (int l = -15; l <= 15; l += 3) {
					Vec3 at = f.add(side.scale(l)).add(0, TIDE_HEIGHT * 0.5, 0);
					MagicFx.cloud(c.level, at, ParticleTypes.SPLASH, 8, 0.8, TIDE_HEIGHT * 0.4, 0.8, 0.3);
					MagicFx.cloud(c.level, at.add(0, TIDE_HEIGHT * 0.5, 0), ParticleTypes.CLOUD, 2, 0.8, 0.3, 0.8, 0.05);
					if (age % 2 == 0) MagicFx.one(c.level, at.add(0, TIDE_HEIGHT * 0.45, 0), MagicParticles.spark(FROST, 0.8F, 10), 0, 0, 0);
				}
				MagicFx.cloud(c.level, f.add(0, 1, 0), ParticleTypes.BUBBLE_POP, 20, TIDE_HALF * 0.5, 1.0, 1.0, 0.1);
				if (age % 4 == 0) {
					Vec3 crest = f.add(0, TIDE_HEIGHT + 1, 0);
					MagicFx.helix(c.level, crest.subtract(dir.scale(8)), crest, 2.2F, DEEP, FROST, 14);
					MagicFx.shockwave(c.level, f.add(0, 0.3, 0), UP, 2.0F, 9.0F, c.color(), 10);
				}
				if (age % 5 == 0) MagicFx.soundFar(c.level, f, MagicSounds.LOOP_WATER, 128, 0.7F);
				Magic.shake(c.level, f, 1.0F, 4, 48);
			});
		});

		// 3. Обрушение в конце пути.
		Zones.later(c, TIDE_RISE + TIDE_RUN, () -> {
			for (LivingEntity e : c.foesAround(end.add(0, 2, 0), 12)) {
				c.hurt(e, c.amount(40.0F), MagicDamage.WATER);
				e.clearFire();
				Cast.knock(e, end, 2.0, 0.6);
			}
			MagicFx.dome(c.level, end, 12.0F, c.color(), FROST, 30);
			MagicFx.sphere(c.level, end, 2.0F, 14.0F, DEEP, FROST, 18);
			MagicFx.cloud(c.level, end.add(0, 3, 0), ParticleTypes.SPLASH, 200, 8, 3, 8, 0.6);
			MagicFx.cloud(c.level, end.add(0, 1, 0), ParticleTypes.CLOUD, 60, 8, 1, 8, 0.08);
			MagicFx.soundFar(c.level, end, MagicSounds.EXPLOSION_HUGE, 192, 0.7F);
			MagicFx.soundFar(c.level, end, SoundEvents.GENERIC_SPLASH, 96, 0.5F);
			MagicFx.flash(c.level, end, 96, 0.5F, FROST, 14);
			Magic.shake(c.level, end, 3.5F, 30, 128);
		});

		// 4. Дыхание Левиафана: вся затопленная полоса замерзает.
		Zones.later(c, TIDE_RISE + TIDE_RUN + TIDE_FREEZE, () -> {
			for (BlockPos p : flood) {
				if (c.level.getFluidState(p).is(FluidTags.WATER)) Terrain.temp(c, p, rnd.nextInt(3) == 0 ? BLUE : PACKED, TIDE_THAW, false);
			}
			List<LivingEntity> frozen = new ArrayList<>();
			Map<LivingEntity, List<BlockPos>> shells = new HashMap<>();
			AABB strip = slab(feet, dir, side, TIDE_START - 2, TIDE_START + TIDE_LENGTH + 4, TIDE_HALF + 2, baseY);
			for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, strip, e -> e.isAlive() && c.isFoe(e))) {
				Vec3 rel = e.position().subtract(feet);
				double along = rel.dot(dir);
				if (along < TIDE_START - 2 || along > TIDE_START + TIDE_LENGTH + 4 || Math.abs(rel.dot(side)) > TIDE_HALF + 2) continue;
				frozen.add(e);
				e.setTicksFrozen(Math.max(e.getTicksFrozen(), 300));
				if (boss(e)) {
					e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, TIDE_THAW, 3), c.caster);
				} else {
					e.addEffect(new MobEffectInstance(MagicEffects.PARALYSIS, TIDE_THAW, 0), c.caster);
					e.setDeltaMovement(Vec3.ZERO);
					e.hurtMarked = true;
					if (!(e instanceof Player) && e.getBbHeight() <= 2.5F && e.getBbWidth() <= 1.5F) shells.put(e, shell(c, e, TIDE_THAW + 10, false));
				}
				MagicFx.burst(c.level, Cast.center(e), BLUE_SHARDS, 16, 0.4, 0.1);
			}
			Vec3 mid = feet.add(dir.scale(TIDE_START + TIDE_LENGTH * 0.5));
			MagicFx.shockwave(c.level, start.add(0, 0.5, 0), UP, 1.0F, 46.0F, FROST, 24);
			MagicFx.shockwave(c.level, mid.add(0, 0.5, 0), UP, 1.0F, 46.0F, 0xFFFFFF, 20);
			for (double s = TIDE_START; s <= TIDE_START + TIDE_LENGTH; s += 8) {
				for (int l = -12; l <= 12; l += 12) {
					MagicFx.burst(c.level, feet.add(dir.scale(s)).add(side.scale(l)).add(0, 1.5, 0), ParticleTypes.SNOWFLAKE, 16, 3.0, 0.05);
				}
			}
			MagicFx.flash(c.level, mid, 128, 0.8F, 0xFFFFFF, 20);
			MagicFx.soundFar(c.level, mid, MagicSounds.FREEZE, 192, 0.6F);
			MagicFx.soundFar(c.level, mid, MagicSounds.SHATTER, 160, 0.5F);
			Magic.shake(c.level, mid, 2.0F, 16, 128);
			// 5. Оттепель: лёд трескается, вмёрзшие получают удар холода.
			Zones.later(c, TIDE_THAW, () -> {
				for (LivingEntity e : frozen) {
					List<BlockPos> s = shells.get(e);
					if (s != null) unshell(c, s);
					if (!e.isAlive()) continue;
					e.removeEffect(MagicEffects.PARALYSIS);
					c.hurt(e, c.amount(25.0F), MagicDamage.FROST);
					iceBurst(c, Cast.center(e), 1.2F);
				}
				MagicFx.soundFar(c.level, mid, MagicSounds.SHATTER, 128, 0.7F);
			});
		});
	}

	/** Прямоугольник вдоль пути (от {@code s0} до {@code s1} по направлению, ±{@code half} поперёк) как AABB. */
	private static AABB slab(Vec3 feet, Vec3 dir, Vec3 side, double s0, double s1, double half, int baseY) {
		Vec3 a = feet.add(dir.scale(s0)).add(side.scale(half)), b = feet.add(dir.scale(s0)).subtract(side.scale(half));
		Vec3 cc = feet.add(dir.scale(s1)).add(side.scale(half)), d = feet.add(dir.scale(s1)).subtract(side.scale(half));
		double x0 = Math.min(Math.min(a.x, b.x), Math.min(cc.x, d.x)), x1 = Math.max(Math.max(a.x, b.x), Math.max(cc.x, d.x));
		double z0 = Math.min(Math.min(a.z, b.z), Math.min(cc.z, d.z)), z1 = Math.max(Math.max(a.z, b.z), Math.max(cc.z, d.z));
		return new AABB(x0, baseY - 8, z0, x1, baseY + TIDE_HEIGHT + 6, z1);
	}

	// =====================================================================================================
	// Общее
	// =====================================================================================================

	/** Боссы не вмерзают (ни корней, ни темниц) — только урон и замедление. */
	static boolean boss(LivingEntity e) {
		EntityType<?> t = e.getType();
		return t == EntityType.ENDER_DRAGON || t == EntityType.WITHER || t == EntityType.WARDEN || t == EntityType.ELDER_GUARDIAN;
	}

	/** Промерзание: +{@code ticks} к холоду (до 400). */
	private static void chill(LivingEntity e, int ticks) {
		e.setTicksFrozen(Math.min(400, Math.max(0, e.getTicksFrozen()) + ticks));
	}

	/**
	 * Ноги вмерзают в лёд: стоит на месте {@code ticks} тиков (скорость в ноль), вокруг ступней — временный лёд.
	 * Боссов и игроков не держит.
	 */
	private static void root(Cast c, LivingEntity e, int ticks) {
		if (boss(e) || e instanceof Player) return;
		e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 6, false, true), c.caster);
		e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0);
		e.hurtMarked = true;
		BlockPos feet = e.blockPosition();
		AABB body = e.getBoundingBox().inflate(0.02);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos p = feet.relative(d);
			if (!new AABB(p).intersects(body)) Terrain.temp(c, p, PACKED, ticks, false);
		}
		MagicFx.burst(c.level, e.position().add(0, 0.3, 0), ParticleTypes.SNOWFLAKE, 8, 0.4, 0.02);
	}

	/**
	 * Полая ледяная темница вокруг существа: стенки 3×3 вокруг его столба и крыша над головой (временные блоки).
	 * Существо ставится в середину своего блока, чтобы стенки не прошли сквозь него. Возвращает поставленные блоки.
	 */
	private static List<BlockPos> shell(Cast c, LivingEntity e, int ticks, boolean crystal) {
		BlockPos base = e.blockPosition();
		e.teleportTo(base.getX() + 0.5, e.getY(), base.getZ() + 0.5);
		AABB body = e.getBoundingBox().inflate(0.02);
		int height = Math.max(2, Mth.ceil(e.getBbHeight()));
		List<BlockPos> placed = new ArrayList<>();
		for (int dy = 0; dy <= height; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					boolean roof = dy == height;
					if (!roof && dx == 0 && dz == 0) continue;
					BlockPos p = base.offset(dx, dy, dz);
					if (new AABB(p).intersects(body)) continue;
					BlockState s = crystal ? (((dx + dz + dy) & 1) == 0 ? BLUE : PACKED) : (roof || dy == 0 ? PACKED : BLUE);
					if (Terrain.temp(c, p, s, ticks, false)) placed.add(p);
				}
			}
		}
		MagicFx.sound(c.level, e.position(), MagicSounds.FREEZE, 0.9F, 1.1F);
		return placed;
	}

	/** Снять темницу раньше срока (только свой лёд, который ещё стоит). */
	private static void unshell(Cast c, List<BlockPos> blocks) {
		for (BlockPos p : blocks) {
			BlockState s = c.level.getBlockState(p);
			if (Terrain.isTemp(p) && (s.is(Blocks.PACKED_ICE) || s.is(Blocks.BLUE_ICE))) Terrain.temp(c, p, Blocks.AIR.defaultBlockState(), 1, false);
		}
	}

	/** Существо стоит в этом блоке (лёд туда не ставим). */
	private static boolean occupied(List<LivingEntity> near, BlockPos p) {
		AABB box = new AABB(p);
		for (LivingEntity e : near) if (e.getBoundingBox().intersects(box)) return true;
		return false;
	}

	/** Сплошная преграда для потока воды (не листва, не стекло, не трава). */
	private static boolean wall(ServerLevel level, BlockPos p, BlockState s) {
		if (s.isAir() || !s.getFluidState().isEmpty() || s.getCollisionShape(level, p).isEmpty()) return false;
		if (s.is(BlockTags.LEAVES) || s.is(BlockTags.ICE) || s.is(BlockTags.IMPERMEABLE) || s.is(Blocks.GLASS_PANE) || s.is(BlockTags.SNOW)) return false;
		return s.getBlock().getExplosionResistance() >= 0.6F;
	}

	/**
	 * Верх земли (или воды) в столбце x/z: y верхнего непустого блока, ищем от {@code y+up} вниз до {@code y-down}
	 * (трава, цветы и снежный покров не в счёт). {@link Integer#MIN_VALUE} — не нашли.
	 */
	static int surface(ServerLevel level, int x, int y, int z, int up, int down) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int yy = y + up; yy >= y - down; yy--) {
			p.set(x, yy, z);
			BlockState s = level.getBlockState(p);
			if (s.isAir() || s.canBeReplaced() && s.getFluidState().isEmpty()) continue;
			return yy;
		}
		return Integer.MIN_VALUE;
	}

	/** Брызги льда: снежинки, осколки, искры и треск. */
	private static void iceBurst(Cast c, Vec3 at, float size) {
		MagicFx.burst(c.level, at, ParticleTypes.SNOWFLAKE, Math.round(8 + 10 * size), 0.2 * size, 0.06 * size);
		MagicFx.burst(c.level, at, SHARDS, Math.round(8 + 12 * size), 0.2 * size, 0.1);
		MagicFx.burst(c.level, at, MagicParticles.spark(ICE, 0.25F + 0.15F * size, 12), Math.round(4 + 6 * size), 0.15 * size, 0.05 * size);
		if (size >= 1.0F) MagicFx.shockwave(c.level, at, UP, 0.2F, 1.4F * size, FROST, 10);
		MagicFx.sound(c.level, at, MagicSounds.impactOf(c.spell), Math.min(2.0F, 0.6F + 0.3F * size), 1.3F - 0.1F * size);
		MagicFx.sound(c.level, at, MagicSounds.SHATTER, 0.4F + 0.2F * size, 1.5F);
	}

	/** Взгляд по горизонтали. */
	private static Vec3 flat(Vec3 look) {
		Vec3 f = new Vec3(look.x, 0, look.z);
		return f.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : f.normalize();
	}

	/** Повернуть горизонтальный вектор на {@code deg} градусов вокруг вертикали. */
	private static Vec3 rotY(Vec3 v, double deg) {
		double a = Math.toRadians(deg), cs = Math.cos(a), sn = Math.sin(a);
		return new Vec3(v.x * cs - v.z * sn, 0, v.x * sn + v.z * cs);
	}
}
