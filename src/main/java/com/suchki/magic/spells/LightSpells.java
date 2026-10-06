package com.suchki.magic.spells;

import static com.suchki.magic.CastStyle.BOLT;
import static com.suchki.magic.CastStyle.CHANNEL;
import static com.suchki.magic.CastStyle.RITUAL;
import static com.suchki.magic.CastStyle.SELF;
import static com.suchki.magic.School.LIGHT;

import com.suchki.magic.Cast;
import com.suchki.magic.Magic;
import com.suchki.magic.MagicBlocks;
import com.suchki.magic.MagicDamage;
import com.suchki.magic.MagicFx;
import com.suchki.magic.MagicParticles;
import com.suchki.magic.MagicSounds;
import com.suchki.magic.Minds;
import com.suchki.magic.Spell;
import com.suchki.magic.SpellProjectile;
import com.suchki.magic.Zones;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Свет: святой урон (особенно по нежити), свет, благословения. */
public final class LightSpells {
	static final int GOLD = 0xFFD84A;
	static final int WHITE = 0xFFF6D8;

	private LightSpells() {}

	public static void register() {
		Spell.of("candlelight", LIGHT, 1, SELF).mana(10).charge(8).cooldown(20).icon("lantern")
				.action(c -> {
					MagicBlocks.candle(c.caster, c.duration(3600));
					MagicFx.burst(c.level, c.caster.getEyePosition().add(0, 0.6, 0), MagicParticles.spark(WHITE, 0.4F, 16), 8, 0.2, 0.02);
					MagicFx.sound(c.level, c.caster.position(), MagicSounds.CAST_HOLY, 0.6F, 1.6F);
					return true;
				})
				.register();

		Spell.of("smite", LIGHT, 1, BOLT).mana(12).charge(10).cooldown(6).icon("glowstone_dust").size(0.3F)
				.action(c -> {
					SpellProjectile.shoot(c, 1.9F);
					MagicFx.sound(c.level, c.hand(), MagicSounds.CAST_HOLY, 0.7F, 1.4F);
					return true;
				})
				.hit((c, p, r) -> {
					if (r instanceof EntityHitResult e && e.getEntity() instanceof LivingEntity t && c.canHit(t)) {
						holyHit(c, t, c.amount(5.0F), p);
					}
					holyImpact(c, r.getLocation(), 0.5F);
				})
				.register();

		Spell.of("flash", LIGHT, 2, SELF).mana(22).charge(12).cooldown(100).icon("glow_ink_sac")
				.action(c -> {
					double r = c.radius(7.0);
					Vec3 eye = c.caster.getEyePosition();
					for (LivingEntity e : c.enemiesAround(eye, r)) {
						e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, c.duration(100), 0), c.caster);
						if (e instanceof Mob m) Minds.apply(m, Minds.Mode.CALM, 60, c.caster.position(), null);
					}
					MagicFx.burst(c.level, eye.add(c.look().scale(0.6)), MagicParticles.spark(0xFFFFFF, 3.0F, 6), 1, 0, 0);
					MagicFx.burst(c.level, eye, MagicParticles.spark(WHITE, 0.4F, 10), 30, 1.2, 0.2);
					MagicFx.one(c.level, c.caster.position().add(0, 0.1, 0), MagicParticles.ring(WHITE, (float) r, 10), 0, 1, 0);
					MagicFx.sound(c.level, eye, MagicSounds.CAST_HOLY, 1.0F, 1.8F);
					return true;
				})
				.register();

		Spell.of("sunbeam", LIGHT, 3, CHANNEL).mana(14).icon("sunflower").range(24).beam()
				.channel((c, t) -> {
					HitResult hit = c.aim(24, e -> e instanceof LivingEntity l && c.canHit(l) && !c.isAlly(l));
					Vec3 end = hit.getLocation();
					if (t % 2 == 0) MagicFx.burst(c.level, end, MagicParticles.spark(GOLD, 0.25F, 8), 2, 0.1, 0.05);
					if (t % 10 != 0) return;
					if (hit instanceof EntityHitResult e && e.getEntity() instanceof LivingEntity target) {
						holyHit(c, target, c.amount(3.0F), c.caster);
						MagicFx.burst(c.level, end, ParticleTypes.END_ROD, 4, 0.2, 0.05);
					}
				})
				.register();

		Spell.of("blessing", LIGHT, 3, SELF).mana(40).charge(20).cooldown(600).icon("bell")
				.action(c -> {
					double r = c.radius(8.0);
					Vec3 feet = c.caster.position();
					for (LivingEntity e : c.alliesAround(feet.add(0, 1, 0), r)) {
						e.addEffect(new MobEffectInstance(MobEffects.STRENGTH, c.duration(900), 0), c.caster);
						e.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, c.duration(900), 0), c.caster);
						MagicFx.burst(c.level, Cast.center(e), MagicParticles.mote(GOLD, 0.15F, 24), 10, 0.4, 0.05);
					}
					MagicFx.one(c.level, feet.add(0, 0.06, 0), MagicParticles.rune(GOLD, 2.2F, 40), 0, 1, 0);
					MagicFx.one(c.level, feet.add(0, 0.1, 0), MagicParticles.ring(GOLD, (float) r, 20), 0, 1, 0);
					MagicFx.sound(c.level, feet, MagicSounds.CAST_HOLY, 1.0F, 1.0F);
					return true;
				})
				.register();

		Spell.of("sanctuary", LIGHT, 4, RITUAL).mana(60).charge(30).cooldown(600).icon("beacon")
				.action(c -> {
					double r = c.radius(6.0);
					Vec3 center = c.caster.position();
					int life = c.duration(300);
					MagicFx.one(c.level, center.add(0, 0.06, 0), MagicParticles.rune(GOLD, (float) r, life), 0, 1, 0);
					MagicFx.sound(c.level, center, MagicSounds.PILLAR, 1.0F, 1.2F);
					Zones.add(c, center, r, life, (z, age) -> {
						if (age % 2 == 0) {
							double a = c.level.random.nextDouble() * Math.PI * 2;
							double d = Math.sqrt(c.level.random.nextDouble()) * r;
							MagicFx.one(c.level, center.add(Math.cos(a) * d, 0.1, Math.sin(a) * d), ParticleTypes.END_ROD, 0, 0.12, 0);
						}
						if (age % 10 != 0) return;
						for (LivingEntity e : c.around(center.add(0, 1, 0), r, l -> !(l instanceof net.minecraft.world.entity.decoration.ArmorStand))) {
							if (c.isAlly(e)) {
								e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0), c.caster);
							} else if (Cast.isUndead(e)) {
								c.hurt(e, c.amount(1.0F), MagicDamage.HOLY);
								e.igniteForTicks(40);
							} else if (c.isEnemy(e)) {
								Cast.knock(e, center, 0.5, 0.1);
							}
						}
					});
					return true;
				})
				.register();

		Spell.of("judgement", LIGHT, 5, RITUAL).mana(100).charge(40).cooldown(400).icon("nether_star").range(40)
				.action(c -> {
					Vec3 at = c.aimGround(40);
					double r = c.radius(3.0);
					MagicFx.one(c.level, at.add(0, 0.08, 0), MagicParticles.rune(GOLD, (float) r, 26), 0, 1, 0);
					MagicFx.one(c.level, at, MagicParticles.pillar(WHITE, 30.0F, 26), 0, 0, 0);
					MagicFx.sound(c.level, at, MagicSounds.CAST_HOLY, 1.5F, 0.6F);
					Zones.later(c, 15, () -> {
						for (LivingEntity e : c.around(at.add(0, 1, 0), r + 0.5, l -> c.canHit(l) && !c.isAlly(l))) {
							float dmg = c.amount(18.0F) * (Cast.isUndead(e) ? 1.5F : 1.0F);
							c.hurt(e, dmg, MagicDamage.HOLY);
							e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0), c.caster);
							Cast.knock(e, at, 0.4, 0.6);
						}
						MagicFx.one(c.level, at, MagicParticles.pillar(0xFFFFFF, 40.0F, 18), 0, 0, 0);
						MagicFx.one(c.level, at.add(0, 0.2, 0), MagicParticles.ring(GOLD, (float) r * 2.2F, 14), 0, 1, 0);
						MagicFx.burst(c.level, at.add(0, 1, 0), ParticleTypes.END_ROD, 60, r * 0.4, 0.3);
						MagicFx.burst(c.level, at.add(0, 1, 0), MagicParticles.spark(GOLD, 0.6F, 20), 30, r * 0.4, 0.15);
						MagicFx.burst(c.level, at.add(0, 0.5, 0), MagicParticles.spark(0xFFFFFF, 4.0F, 5), 1, 0, 0);
						MagicFx.sound(c.level, at, MagicSounds.PILLAR, 2.0F, 0.8F);
						MagicFx.sound(c.level, at, MagicSounds.EXPLOSION, 1.2F, 1.3F);
						Magic.shake(c.level, at, 2.0F, 18, 40);
					});
					return true;
				})
				.register();
	}

	/** Святой удар: нежить получает ×2,5 и загорается. */
	static void holyHit(Cast c, LivingEntity t, float base, net.minecraft.world.entity.Entity direct) {
		boolean undead = Cast.isUndead(t);
		c.hurt(t, undead ? base * 2.5F : base, MagicDamage.HOLY, direct);
		if (undead) t.igniteForTicks(c.duration(60));
	}

	static void holyImpact(Cast c, Vec3 at, float size) {
		MagicFx.burst(c.level, at, ParticleTypes.END_ROD, 10, 0.15, 0.08);
		MagicFx.burst(c.level, at, MagicParticles.spark(GOLD, size, 12), 6, 0.15, 0.05);
		MagicFx.sound(c.level, at, MagicSounds.IMPACT_HOLY, 0.7F, 1.5F);
	}
}
