package com.suchki.magic.spells;

import static com.suchki.magic.CastStyle.BOLT;
import static com.suchki.magic.CastStyle.CHANNEL;
import static com.suchki.magic.CastStyle.RITUAL;
import static com.suchki.magic.CastStyle.SELF;
import static com.suchki.magic.School.RESTORATION;

import com.suchki.magic.Cast;
import com.suchki.magic.MagicDamage;
import com.suchki.magic.MagicEffects;
import com.suchki.magic.MagicFx;
import com.suchki.magic.MagicParticles;
import com.suchki.magic.MagicSounds;
import com.suchki.magic.Spell;
import com.suchki.magic.Zones;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Восстановление: лечение, обереги, очищение. */
public final class RestorationSpells {
	static final int GREEN = 0x52E07F;
	static final int PALE = 0xD8FFE0;

	private RestorationSpells() {}

	public static void register() {
		Spell.of("healing", RESTORATION, 1, CHANNEL).mana(9).icon("glistering_melon_slice")
				.channel((c, t) -> {
					if (t % 2 == 0) {
						double a = t * 0.45;
						Vec3 at = c.caster.position().add(Math.cos(a) * 0.6, 0.1 + (t % 20) * 0.09, Math.sin(a) * 0.6);
						MagicFx.one(c.level, at, MagicParticles.mote(GREEN, 0.14F, 18), 0, 0.03, 0);
					}
					if (t % 10 != 0) return;
					if (c.caster.getHealth() < c.caster.getMaxHealth()) {
						c.heal(c.caster, c.amount(1.0F));
						if (t % 20 == 0) MagicFx.burst(c.level, c.caster.getEyePosition().add(0, 0.3, 0), ParticleTypes.HEART, 1, 0.3, 0);
					}
				})
				.register();

		// Оберег: снижение урона спереди считает SkillCombatMixin (пока игрок колдует «ward»).
		Spell.of("ward", RESTORATION, 1, CHANNEL).mana(6).icon("shield").range(2)
				.channel((c, t) -> {
				})
				.register();

		Spell.of("heal_other", RESTORATION, 2, BOLT).mana(20).charge(12).cooldown(10).icon("golden_carrot").range(24)
				.action(c -> {
					LivingEntity t = c.aimLiving(24, e -> e instanceof LivingEntity l && l != c.caster);
					if (t == null) return false;
					MagicFx.line(c.level, c.hand(), Cast.center(t), MagicParticles.mote(GREEN, 0.12F, 12), 0.35);
					if (Cast.isUndead(t)) {
						c.hurt(t, c.amount(6.0F), MagicDamage.HOLY);
						MagicFx.burst(c.level, Cast.center(t), ParticleTypes.END_ROD, 12, 0.3, 0.05);
					} else {
						c.heal(t, c.amount(8.0F));
						MagicFx.burst(c.level, t.getEyePosition().add(0, 0.3, 0), ParticleTypes.HEART, 4, 0.4, 0);
						MagicFx.burst(c.level, Cast.center(t), MagicParticles.spark(GREEN, 0.3F, 14), 10, 0.3, 0.04);
					}
					MagicFx.sound(c.level, Cast.center(t), MagicSounds.CAST_HEAL, 0.9F, 1.3F);
					return true;
				})
				.register();

		Spell.of("cure", RESTORATION, 2, SELF).mana(20).charge(12).cooldown(60).icon("milk_bucket")
				.action(c -> {
					List<Holder<MobEffect>> bad = new ArrayList<>();
					for (MobEffectInstance e : c.caster.getActiveEffects()) {
						if (e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) bad.add(e.getEffect());
					}
					bad.forEach(c.caster::removeEffect);
					c.caster.clearFire();
					c.caster.setTicksFrozen(0);
					c.heal(c.caster, c.amount(2.0F));
					Vec3 feet = c.caster.position();
					MagicFx.one(c.level, feet.add(0, 0.1, 0), MagicParticles.ring(PALE, 1.8F, 12), 0, 1, 0);
					MagicFx.burst(c.level, feet.add(0, 1, 0), MagicParticles.mote(GREEN, 0.14F, 20), 16, 0.4, 0.05);
					MagicFx.sound(c.level, feet, MagicSounds.CAST_HEAL, 1.0F, 1.0F);
					return true;
				})
				.register();

		Spell.of("rejuvenation", RESTORATION, 3, SELF).mana(35).charge(16).cooldown(200).icon("golden_apple")
				.action(c -> {
					double r = c.radius(8.0);
					Vec3 feet = c.caster.position();
					for (LivingEntity e : c.alliesAround(feet.add(0, 1, 0), r)) {
						e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, c.duration(200), 1), c.caster);
						MagicFx.burst(c.level, Cast.center(e), MagicParticles.mote(GREEN, 0.15F, 24), 8, 0.4, 0.04);
					}
					MagicFx.one(c.level, feet.add(0, 0.1, 0), MagicParticles.ring(GREEN, (float) r, 18), 0, 1, 0);
					MagicFx.one(c.level, feet.add(0, 0.05, 0), MagicParticles.rune(GREEN, 2.0F, 30), 0, 1, 0);
					MagicFx.sound(c.level, feet, MagicSounds.CAST_HEAL, 1.0F, 0.8F);
					return true;
				})
				.register();

		Spell.of("healing_circle", RESTORATION, 4, RITUAL).mana(60).charge(30).cooldown(400).icon("glow_berries")
				.action(c -> {
					double r = c.radius(4.0);
					Vec3 center = c.caster.position();
					int life = c.duration(240);
					MagicFx.one(c.level, center.add(0, 0.06, 0), MagicParticles.rune(GREEN, (float) r, life), 0, 1, 0);
					MagicFx.sound(c.level, center, MagicSounds.CAST_HEAL, 1.0F, 0.7F);
					Zones.add(c, center, r, life, (z, age) -> {
						if (age % 3 == 0) {
							double a = c.level.random.nextDouble() * Math.PI * 2;
							double d = Math.sqrt(c.level.random.nextDouble()) * r;
							MagicFx.one(c.level, center.add(Math.cos(a) * d, 0.1, Math.sin(a) * d), MagicParticles.mote(GREEN, 0.13F, 30), 0, 0.05, 0);
						}
						if (age % 20 != 0) return;
						for (LivingEntity e : c.around(center.add(0, 1, 0), r, l -> true)) {
							if (Cast.isUndead(e) && !c.isAlly(e)) c.hurt(e, c.amount(1.0F), MagicDamage.HOLY);
							else if (c.isAlly(e)) c.heal(e, c.amount(1.0F));
						}
					});
					return true;
				})
				.register();

		Spell.of("undying", RESTORATION, 5, SELF).mana(90).charge(40).cooldown(2400).icon("totem_of_undying")
				.action(c -> {
					c.caster.addEffect(new MobEffectInstance(MagicEffects.UNDYING, c.duration(1200), 0), c.caster);
					Vec3 feet = c.caster.position();
					MagicFx.one(c.level, feet.add(0, 0.06, 0), MagicParticles.rune(0xFFD24A, 1.6F, 40), 0, 1, 0);
					MagicFx.burst(c.level, feet.add(0, 1, 0), MagicParticles.spark(0xFFD24A, 0.35F, 24), 24, 0.5, 0.05);
					MagicFx.burst(c.level, feet.add(0, 1, 0), ParticleTypes.TOTEM_OF_UNDYING, 30, 0.4, 0.3);
					MagicFx.sound(c.level, feet, MagicSounds.CAST_HOLY, 1.0F, 0.9F);
					return true;
				})
				.register();
	}
}
