package com.suchki.magic.spells;

import static com.suchki.magic.CastStyle.BOLT;
import static com.suchki.magic.CastStyle.CHANNEL;
import static com.suchki.magic.CastStyle.RITUAL;
import static com.suchki.magic.CastStyle.SELF;
import static com.suchki.magic.School.DESTRUCTION;

import com.suchki.magic.Cast;
import com.suchki.magic.Magic;
import com.suchki.magic.MagicDamage;
import com.suchki.magic.MagicFx;
import com.suchki.magic.MagicParticles;
import com.suchki.magic.MagicSounds;
import com.suchki.magic.Spell;
import com.suchki.magic.SpellProjectile;
import com.suchki.magic.Zones;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Разрушение: огонь, лёд, молния. */
public final class DestructionSpells {
	static final int FIRE = 0xFF7A2E;
	static final int FROST = 0x8FD8FF;
	static final int SHOCK = 0xB9A8FF;

	private DestructionSpells() {}

	public static void register() {
		Spell.of("firebolt", DESTRUCTION, 1, BOLT).mana(12).charge(10).cooldown(6).icon("blaze_powder").size(0.3F)
				.action(c -> {
					SpellProjectile.shoot(c, 1.7F);
					MagicFx.sound(c.level, c.hand(), MagicSounds.CAST_FIRE, 0.8F, 1.25F);
					return true;
				})
				.hit((c, p, r) -> {
					if (r instanceof EntityHitResult e && e.getEntity() instanceof LivingEntity t && c.canHit(t)) {
						c.hurt(t, c.amount(6.0F), MagicDamage.FIRE, p);
						t.igniteForTicks(c.duration(60));
					}
					fireImpact(c, r.getLocation(), 0.6F);
				})
				.register();

		Spell.of("flames", DESTRUCTION, 1, CHANNEL).mana(10).icon("fire_charge").range(5.5)
				.channel((c, t) -> {
					Vec3 hand = c.hand();
					Vec3 look = c.look();
					for (int i = 0; i < 3; i++) {
						Vec3 v = look.scale(0.45 + c.level.random.nextDouble() * 0.15).add(spread(c, 0.09));
						MagicFx.one(c.level, hand, ParticleTypes.FLAME, v.x, v.y, v.z);
					}
					if (t % 3 == 0) {
						Vec3 v = look.scale(0.35).add(spread(c, 0.06));
						MagicFx.one(c.level, hand, MagicParticles.spark(FIRE, 0.3F, 12), v.x, v.y, v.z);
					}
					if (t % 10 != 0) return;
					for (LivingEntity e : c.around(hand, 5.5, e -> c.canHit(e) && !c.isAlly(e) && Cast.inCone(e, hand, look, 5.5, 30))) {
						c.hurt(e, c.amount(3.0F), MagicDamage.FIRE);
						e.igniteForTicks(40);
					}
				})
				.register();

		Spell.of("ice_shard", DESTRUCTION, 2, BOLT).mana(18).charge(12).cooldown(8).icon("packed_ice").model("packed_ice").size(0.28F)
				.action(c -> {
					SpellProjectile.shoot(c, 2.1F);
					MagicFx.sound(c.level, c.hand(), MagicSounds.CAST_FROST, 0.8F, 1.3F);
					return true;
				})
				.hit((c, p, r) -> {
					if (r instanceof EntityHitResult e && e.getEntity() instanceof LivingEntity t && c.canHit(t)) {
						c.hurt(t, c.amount(7.0F), MagicDamage.FROST, p);
						t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, c.duration(60), 1), c.caster);
						t.setTicksFrozen(Math.max(t.getTicksFrozen(), 220));
					}
					frostImpact(c, r.getLocation(), 0.6F);
				})
				.register();

		Spell.of("chain_lightning", DESTRUCTION, 3, BOLT).mana(30).charge(16).cooldown(20).icon("lightning_rod").range(24)
				.action(c -> {
					LivingEntity first = c.aimLiving(24, e -> e instanceof LivingEntity l && c.canHit(l) && !c.isAlly(l));
					if (first == null) return false;
					List<LivingEntity> hit = new ArrayList<>();
					hit.add(first);
					float dmg = c.amount(8.0F);
					MagicFx.lightning(c.level, c.hand(), Cast.center(first), SHOCK);
					c.hurt(first, dmg, MagicDamage.SHOCK);
					shockSpark(c, Cast.center(first));
					int extra = 3 + (int) ((c.power - 1.0F) / 0.5F);
					LivingEntity cur = first;
					for (int i = 0; i < extra; i++) {
						LivingEntity next = null;
						double best = 49;
						for (LivingEntity e : c.enemiesAround(Cast.center(cur), 7)) {
							double d = e.distanceToSqr(cur);
							if (!hit.contains(e) && d < best) {
								best = d;
								next = e;
							}
						}
						if (next == null) break;
						dmg *= 0.8F;
						MagicFx.lightning(c.level, Cast.center(cur), Cast.center(next), SHOCK);
						c.hurt(next, dmg, MagicDamage.SHOCK);
						shockSpark(c, Cast.center(next));
						hit.add(next);
						cur = next;
					}
					MagicFx.sound(c.level, c.hand(), MagicSounds.CAST_SHOCK, 0.7F, 1.4F);
					return true;
				})
				.register();

		Spell.of("frost_nova", DESTRUCTION, 3, SELF).mana(35).charge(16).cooldown(80).icon("powder_snow_bucket")
				.action(c -> {
					double r = c.radius(6.0);
					Vec3 feet = c.caster.position();
					for (LivingEntity e : c.enemiesAround(feet.add(0, 1, 0), r)) {
						c.hurt(e, c.amount(6.0F), MagicDamage.FROST);
						e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, c.duration(80), 2), c.caster);
						e.setTicksFrozen(Math.max(e.getTicksFrozen(), 240));
					}
					MagicFx.one(c.level, feet.add(0, 0.1, 0), MagicParticles.ring(FROST, (float) r, 14), 0, 1, 0);
					MagicFx.ring(c.level, feet.add(0, 0.3, 0), 0.8, ParticleTypes.SNOWFLAKE, 36, r / 10.0);
					MagicFx.ring(c.level, feet.add(0, 0.6, 0), 0.8, MagicParticles.spark(FROST, 0.3F, 14), 24, r / 12.0);
					MagicFx.burst(c.level, feet.add(0, 1, 0), ParticleTypes.ITEM_SNOWBALL, 30, 0.6, 0.2);
					MagicFx.sound(c.level, feet, MagicSounds.CAST_FROST, 1.0F, 0.8F);
					MagicFx.sound(c.level, feet, MagicSounds.IMPACT_FROST, 0.8F, 0.7F);
					Magic.shake(c.level, feet, 0.6F, 8, 12);
					return true;
				})
				.register();

		Spell.of("fireball", DESTRUCTION, 4, BOLT).mana(50).charge(24).cooldown(30).icon("fire_charge").model("fire_charge").size(0.5F)
				.action(c -> {
					SpellProjectile.shoot(c, 1.3F);
					MagicFx.sound(c.level, c.hand(), MagicSounds.CAST_FIRE, 1.0F, 0.8F);
					return true;
				})
				.hit((c, p, r) -> explode(c, r, c.radius(3.5), c.amount(14.0F), 1.6F))
				.register();

		Spell.of("meteor_shower", DESTRUCTION, 5, RITUAL).mana(90).charge(40).cooldown(300).icon("magma_block").model("magma_block")
				.size(0.7F).range(40)
				.action(c -> {
					Vec3 target = c.aimGround(40);
					double r = c.radius(6.0);
					int count = Math.round(8 * c.power);
					MagicFx.one(c.level, target.add(0, 0.1, 0), MagicParticles.rune(FIRE, (float) r, 100), 0, 1, 0);
					MagicFx.sound(c.level, c.hand(), MagicSounds.CAST_FIRE, 1.0F, 0.6F);
					for (int i = 0; i < count; i++) {
						int delay = 10 + i * 70 / count + c.level.random.nextInt(6);
						Zones.later(c, delay, () -> {
							double a = c.level.random.nextDouble() * Math.PI * 2;
							double d = Math.sqrt(c.level.random.nextDouble()) * r;
							Vec3 ground = target.add(Math.cos(a) * d, 0, Math.sin(a) * d);
							Vec3 sky = ground.add(-6 + c.level.random.nextDouble() * 3, 26, 3 - c.level.random.nextDouble() * 6);
							Vec3 vel = ground.subtract(sky).normalize().scale(1.5);
							SpellProjectile.spawn(c, sky, vel);
							MagicFx.sound(c.level, sky, MagicSounds.METEOR_FALL, 2.0F, 0.6F + c.level.random.nextFloat() * 0.2F);
						});
					}
					return true;
				})
				.hit((c, p, r) -> explode(c, r, 2.5, c.amount(9.0F) / Math.max(1.0F, c.power) * (0.5F + 0.5F * c.power), 1.2F))
				.register();
	}

	private static Vec3 spread(Cast c, double s) {
		return new Vec3((c.level.random.nextDouble() - 0.5) * 2 * s, (c.level.random.nextDouble() - 0.5) * 2 * s,
				(c.level.random.nextDouble() - 0.5) * 2 * s);
	}

	/** Огненный взрыв без разрушения блоков: урон спадает к краю, поджог, отбрасывание, тряска. */
	static void explode(Cast c, HitResult r, double radius, float damage, float shake) {
		Vec3 at = r instanceof EntityHitResult e ? Cast.center(e.getEntity()) : r.getLocation();
		for (LivingEntity e : c.around(at, radius, e -> c.canHit(e) && !c.isAlly(e))) {
			double dist = Cast.center(e).distanceTo(at);
			float k = (float) (1.0 - 0.5 * Math.min(1.0, dist / radius));
			c.hurt(e, damage * k, MagicDamage.FIRE);
			e.igniteForTicks(c.duration(80));
			Cast.knock(e, at, 0.7 * k, 0.35 * k);
		}
		MagicFx.burst(c.level, at, ParticleTypes.EXPLOSION_EMITTER, 1, 0, 0);
		MagicFx.burst(c.level, at, ParticleTypes.FLAME, 40, radius * 0.3, 0.15);
		MagicFx.burst(c.level, at, ParticleTypes.LARGE_SMOKE, 16, radius * 0.3, 0.05);
		MagicFx.burst(c.level, at, MagicParticles.spark(FIRE, 0.5F, 18), 24, radius * 0.25, 0.12);
		MagicFx.one(c.level, at.add(0, 0.2, 0), MagicParticles.ring(0xFFB04A, (float) radius * 1.2F, 12), 0, 1, 0);
		MagicFx.sound(c.level, at, MagicSounds.EXPLOSION, 1.4F, 0.9F + c.level.random.nextFloat() * 0.2F);
		Magic.shake(c.level, at, shake, 14, radius * 8);
	}

	static void fireImpact(Cast c, Vec3 at, float size) {
		MagicFx.burst(c.level, at, ParticleTypes.FLAME, 12, 0.2, 0.08);
		MagicFx.burst(c.level, at, ParticleTypes.SMOKE, 6, 0.2, 0.02);
		MagicFx.burst(c.level, at, MagicParticles.spark(FIRE, size, 12), 6, 0.15, 0.06);
		MagicFx.sound(c.level, at, MagicSounds.IMPACT_FIRE, 0.7F, 1.2F);
	}

	static void frostImpact(Cast c, Vec3 at, float size) {
		MagicFx.burst(c.level, at, ParticleTypes.SNOWFLAKE, 14, 0.2, 0.06);
		MagicFx.burst(c.level, at, ParticleTypes.ITEM_SNOWBALL, 8, 0.15, 0.1);
		MagicFx.burst(c.level, at, MagicParticles.spark(FROST, size, 12), 6, 0.15, 0.05);
		MagicFx.sound(c.level, at, MagicSounds.IMPACT_FROST, 0.8F, 1.3F);
	}

	static void shockSpark(Cast c, Vec3 at) {
		MagicFx.burst(c.level, at, ParticleTypes.ELECTRIC_SPARK, 14, 0.3, 0.2);
		MagicFx.burst(c.level, at, MagicParticles.spark(SHOCK, 0.6F, 6), 3, 0.1, 0.0);
		MagicFx.sound(c.level, at, MagicSounds.IMPACT_SHOCK, 0.6F, 1.5F + c.level.random.nextFloat() * 0.3F);
	}
}
