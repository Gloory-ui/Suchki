package com.suchki.magic.spells;

import static com.suchki.magic.CastStyle.BOLT;
import static com.suchki.magic.CastStyle.CHANNEL;
import static com.suchki.magic.CastStyle.RITUAL;
import static com.suchki.magic.CastStyle.SELF;
import static com.suchki.magic.CastStyle.SUMMON;
import static com.suchki.magic.School.DARK;

import com.suchki.magic.Cast;
import com.suchki.magic.Magic;
import com.suchki.magic.MagicDamage;
import com.suchki.magic.MagicEffects;
import com.suchki.magic.MagicFx;
import com.suchki.magic.MagicParticles;
import com.suchki.magic.MagicSounds;
import com.suchki.magic.Minions;
import com.suchki.magic.Spell;
import com.suchki.magic.SpellProjectile;
import com.suchki.magic.Zones;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Тьма: тени, проклятия, похищение жизни, мертвецы. */
public final class DarkSpells {
	static final int VIOLET = 0x9A4DFF;
	static final int BLOOD = 0xC0304A;

	private DarkSpells() {}

	public static void register() {
		Spell.of("shadow_bolt", DARK, 1, BOLT).mana(12).charge(10).cooldown(6).icon("ink_sac").size(0.32F)
				.action(c -> {
					SpellProjectile.shoot(c, 1.7F);
					MagicFx.sound(c.level, c.hand(), MagicSounds.CAST_DARK, 0.6F, 1.5F);
					return true;
				})
				.hit((c, p, r) -> {
					if (r instanceof EntityHitResult e && e.getEntity() instanceof LivingEntity t && c.canHit(t)) {
						c.hurt(t, c.amount(5.0F), MagicDamage.DARK, p);
						t.addEffect(new MobEffectInstance(MobEffects.WITHER, c.duration(80), 0), c.caster);
					}
					darkImpact(c, r.getLocation(), 0.5F);
				})
				.register();

		Spell.of("drain_life", DARK, 2, CHANNEL).mana(12).icon("fermented_spider_eye").range(16).beam()
				.channel((c, t) -> {
					HitResult hit = c.aim(16, e -> e instanceof LivingEntity l && c.canHit(l) && !c.isAlly(l));
					if (!(hit instanceof EntityHitResult eh) || !(eh.getEntity() instanceof LivingEntity target)) return;
					// Капли жизни текут от цели к магу.
					if (t % 2 == 0) {
						Vec3 from = Cast.center(target);
						Vec3 v = c.hand().subtract(from).scale(0.08);
						MagicFx.one(c.level, from, MagicParticles.spark(BLOOD, 0.22F, 13), v.x, v.y, v.z);
					}
					if (t % 10 != 0) return;
					float dmg = c.amount(2.0F);
					float before = target.getHealth();
					c.hurt(target, dmg, MagicDamage.DARK);
					float dealt = Math.max(0.0F, before - target.getHealth());
					if (dealt > 0) c.heal(c.caster, dealt * 0.6F);
				})
				.register();

		Spell.of("curse", DARK, 2, BOLT).mana(22).charge(12).cooldown(60).icon("spider_eye").range(24)
				.action(c -> {
					LivingEntity t = c.aimLiving(24, e -> e instanceof LivingEntity l && c.canHit(l) && !c.isAlly(l));
					if (t == null) return false;
					int dur = c.duration(300);
					t.addEffect(new MobEffectInstance(MagicEffects.HEX, dur, c.strong() ? 1 : 0), c.caster);
					t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, dur, 0), c.caster);
					t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, dur, 0), c.caster);
					MagicFx.line(c.level, c.hand(), Cast.center(t), MagicParticles.spark(VIOLET, 0.12F, 10), 0.4);
					MagicFx.one(c.level, t.getEyePosition().add(0, 0.7, 0), MagicParticles.rune(VIOLET, 0.6F, 40), 0, 1, 0);
					MagicFx.burst(c.level, Cast.center(t), ParticleTypes.SMOKE, 16, 0.3, 0.02);
					MagicFx.sound(c.level, Cast.center(t), MagicSounds.IMPACT_DARK, 1.0F, 0.7F);
					return true;
				})
				.register();

		Spell.of("shadow_step", DARK, 3, SELF).mana(30).charge(12).cooldown(60).icon("ender_pearl").range(20)
				.action(c -> {
					HitResult hit = c.aim(20, e -> e instanceof LivingEntity l && c.canHit(l) && !c.isAlly(l));
					Vec3 dest = null;
					if (hit instanceof EntityHitResult eh) {
						// За спину цели; если там стена — сбоку, иначе перед ней.
						Vec3 at = eh.getEntity().position();
						Vec3 face = eh.getEntity().getLookAngle().multiply(1, 0, 1);
						if (face.lengthSqr() < 1.0E-4) face = c.look().multiply(-1, 0, -1);
						face = face.normalize();
						Vec3 side = new Vec3(-face.z, 0, face.x);
						for (Vec3 off : new Vec3[] {face.scale(-1.4), side.scale(1.4), side.scale(-1.4), face.scale(1.4)}) {
							dest = c.safeSpot(at.add(off));
							if (dest != null) break;
						}
					}
					if (dest == null && hit instanceof BlockHitResult bh && hit.getType() != HitResult.Type.MISS) {
						dest = c.safeSpot(Vec3.atBottomCenterOf(bh.getBlockPos().relative(bh.getDirection())));
					}
					// В пустоту (в небо) шаг не делается — только к блоку или существу.
					if (dest == null) return false;
					c.teleport(dest, VIOLET);
					c.caster.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, c.duration(40), 0, false, false, true), c.caster);
					MagicFx.burst(c.level, dest.add(0, 1, 0), ParticleTypes.LARGE_SMOKE, 12, 0.3, 0.02);
					return true;
				})
				.register();

		Spell.of("raise_dead", DARK, 3, SUMMON).mana(45).charge(20).cooldown(600).icon("rotten_flesh")
				.action(c -> {
					int count = c.strong() ? 3 : 2;
					int life = Math.round(1200 * c.power);
					Vec3 front = c.caster.position().add(c.look().multiply(1, 0, 1).normalize().scale(2.5));
					int made = 0;
					for (int i = 0; i < count; i++) {
						Husk husk = EntityType.HUSK.create(c.level, EntitySpawnReason.MOB_SUMMONED);
						if (husk == null) continue;
						double side = (i - (count - 1) / 2.0) * 1.6;
						Vec3 at = c.safeSpot(front.add(c.right().scale(side)));
						if (at == null) at = c.caster.position();
						husk.snapTo(at.x, at.y, at.z, c.caster.getYRot(), 0);
						Minions.summon(c, husk, life);
						MagicFx.burst(c.level, at.add(0, 0.2, 0), ParticleTypes.SOUL, 10, 0.4, 0.05);
						MagicFx.burst(c.level, at.add(0, 0.2, 0), ParticleTypes.LARGE_SMOKE, 8, 0.4, 0.02);
						made++;
					}
					MagicFx.sound(c.level, front, MagicSounds.CAST_SUMMON, 1.0F, 0.6F);
					MagicFx.sound(c.level, front, MagicSounds.IMPACT_DARK, 1.0F, 0.5F);
					return made > 0;
				})
				.register();

		Spell.of("darkness", DARK, 4, BOLT).mana(55).charge(24).cooldown(300).icon("sculk").range(24)
				.action(c -> {
					Vec3 at = c.aimGround(24);
					double r = c.radius(4.0);
					int life = c.duration(160);
					MagicFx.one(c.level, at.add(0, 0.08, 0), MagicParticles.rune(VIOLET, (float) r, life), 0, 1, 0);
					MagicFx.line(c.level, c.hand(), at.add(0, 1, 0), MagicParticles.spark(VIOLET, 0.15F, 10), 0.5);
					MagicFx.sound(c.level, at, MagicSounds.CAST_DARK, 1.0F, 0.6F);
					Zones.add(c, at, r, life, (z, age) -> {
						if (age % 2 == 0) {
							double a = c.level.random.nextDouble() * Math.PI * 2;
							double d = Math.sqrt(c.level.random.nextDouble()) * r;
							Vec3 p = at.add(Math.cos(a) * d, 0.3 + c.level.random.nextDouble() * 1.8, Math.sin(a) * d);
							MagicFx.one(c.level, p, ParticleTypes.SQUID_INK, 0, 0.01, 0);
							MagicFx.one(c.level, p, MagicParticles.spark(0x2A1040, 0.5F, 20), 0, 0.01, 0);
						}
						if (age % 10 != 0) return;
						for (LivingEntity e : c.enemiesAround(at.add(0, 1, 0), r)) {
							e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0), c.caster);
							e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0), c.caster);
							e.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0), c.caster);
							e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0), c.caster);
						}
					});
					return true;
				})
				.register();

		Spell.of("soul_harvest", DARK, 5, RITUAL).mana(100).charge(40).cooldown(900).icon("wither_skeleton_skull")
				.action(c -> {
					double r = c.radius(12.0);
					Vec3 eye = c.caster.getEyePosition();
					List<LivingEntity> victims = c.enemiesAround(eye, r);
					int healed = 0;
					for (LivingEntity e : victims) {
						c.hurt(e, c.amount(14.0F), MagicDamage.DARK);
						e.addEffect(new MobEffectInstance(MobEffects.WITHER, c.duration(120), 1), c.caster);
						Vec3 from = Cast.center(e);
						Vec3 v = eye.subtract(from).scale(0.06);
						for (int i = 0; i < 4; i++) {
							MagicFx.one(c.level, from.add(0, i * 0.2, 0), ParticleTypes.SCULK_SOUL, v.x, v.y, v.z);
							MagicFx.one(c.level, from.add(0, i * 0.2, 0), MagicParticles.spark(VIOLET, 0.3F, 18), v.x, v.y, v.z);
						}
						if (healed < 8) {
							c.heal(c.caster, 2.0F);
							healed++;
						}
					}
					if (healed > 0) c.caster.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 400, Math.min(3, healed / 2)), c.caster);
					Vec3 feet = c.caster.position();
					MagicFx.one(c.level, feet.add(0, 0.06, 0), MagicParticles.rune(VIOLET, 3.0F, 40), 0, 1, 0);
					MagicFx.one(c.level, feet.add(0, 0.2, 0), MagicParticles.ring(VIOLET, (float) r, 20), 0, 1, 0);
					MagicFx.sound(c.level, feet, MagicSounds.CAST_DARK, 1.5F, 0.5F);
					MagicFx.sound(c.level, feet, MagicSounds.IMPACT_DARK, 1.5F, 0.6F);
					Magic.shake(c.level, feet, 1.0F, 14, 24);
					return true;
				})
				.register();
	}

	static void darkImpact(Cast c, Vec3 at, float size) {
		MagicFx.burst(c.level, at, ParticleTypes.SMOKE, 12, 0.2, 0.03);
		MagicFx.burst(c.level, at, ParticleTypes.SOUL, 4, 0.2, 0.03);
		MagicFx.burst(c.level, at, MagicParticles.spark(VIOLET, size, 12), 6, 0.15, 0.05);
		MagicFx.sound(c.level, at, MagicSounds.IMPACT_DARK, 0.8F, 1.2F);
	}
}
