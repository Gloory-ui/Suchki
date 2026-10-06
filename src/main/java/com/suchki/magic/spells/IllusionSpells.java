package com.suchki.magic.spells;

import static com.suchki.magic.CastStyle.BOLT;
import static com.suchki.magic.CastStyle.RITUAL;
import static com.suchki.magic.CastStyle.SELF;
import static com.suchki.magic.School.ILLUSION;

import com.suchki.magic.Cast;
import com.suchki.magic.Magic;
import com.suchki.magic.MagicFx;
import com.suchki.magic.MagicParticles;
import com.suchki.magic.MagicSounds;
import com.suchki.magic.Minds;
import com.suchki.magic.Minions;
import com.suchki.magic.Spell;
import com.suchki.mixin.MannequinInvoker;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Иллюзия: разум мобов и обман — страх, умиротворение, бешенство, невидимость, двойник. */
public final class IllusionSpells {
	static final int PINK = 0xE76BFF;
	static final int PALE = 0xFFDDFF;

	private IllusionSpells() {}

	public static void register() {
		Spell.of("night_eye", ILLUSION, 1, SELF).mana(10).charge(8).cooldown(20).icon("golden_carrot")
				.action(c -> {
					c.caster.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, c.duration(4800), 0), c.caster);
					MagicFx.burst(c.level, c.caster.getEyePosition(), MagicParticles.mote(PALE, 0.1F, 20), 10, 0.3, 0.02);
					MagicFx.sound(c.level, c.caster.position(), MagicSounds.CAST_ILLUSION, 0.6F, 1.6F);
					return true;
				})
				.register();

		Spell.of("fear", ILLUSION, 1, BOLT).mana(18).charge(12).cooldown(60).icon("goat_horn").range(24)
				.action(c -> {
					Vec3 at = c.aimPoint(24);
					double r = c.radius(4.0);
					int n = 0;
					for (LivingEntity e : c.enemiesAround(at, r)) {
						if (e instanceof Mob m) {
							Minds.apply(m, Minds.Mode.FEAR, c.duration(160), c.caster.position(), null);
							n++;
						}
					}
					mindWave(c, at, r, 0x6A2BA0);
					MagicFx.sound(c.level, at, MagicSounds.CAST_ILLUSION, 1.0F, 0.6F);
					return true;
				})
				.register();

		Spell.of("calm", ILLUSION, 2, BOLT).mana(25).charge(14).cooldown(120).icon("poppy").range(24)
				.action(c -> {
					Vec3 at = c.aimPoint(24);
					double r = c.radius(4.0);
					for (LivingEntity e : c.around(at, r, l -> l instanceof Mob && !c.isAlly(l))) {
						Minds.apply((Mob) e, Minds.Mode.CALM, c.duration(400), c.caster.position(), null);
						MagicFx.burst(c.level, e.getEyePosition().add(0, 0.4, 0), ParticleTypes.HEART, 2, 0.2, 0);
					}
					mindWave(c, at, r, PINK);
					MagicFx.sound(c.level, at, MagicSounds.CAST_ILLUSION, 1.0F, 1.3F);
					return true;
				})
				.register();

		Spell.of("invisibility", ILLUSION, 3, SELF).mana(35).charge(16).cooldown(200).icon("glass")
				.action(c -> {
					c.caster.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, c.duration(600), 0), c.caster);
					for (Mob m : c.level.getEntitiesOfClass(Mob.class, new AABB(c.caster.blockPosition()).inflate(32),
							m -> m.getTarget() == c.caster)) {
						Minds.apply(m, Minds.Mode.CALM, 60, c.caster.position(), null);
					}
					MagicFx.burst(c.level, Cast.center(c.caster), MagicParticles.spark(PALE, 0.3F, 16), 24, 0.4, 0.03);
					MagicFx.burst(c.level, Cast.center(c.caster), ParticleTypes.WITCH, 16, 0.4, 0.02);
					MagicFx.sound(c.level, c.caster.position(), MagicSounds.CAST_ILLUSION, 1.0F, 1.0F);
					return true;
				})
				.register();

		Spell.of("frenzy", ILLUSION, 3, BOLT).mana(40).charge(18).cooldown(300).icon("red_dye").range(24)
				.action(c -> {
					Vec3 at = c.aimPoint(24);
					double r = c.radius(5.0);
					for (LivingEntity e : c.around(at, r, l -> l instanceof Mob && !c.isAlly(l))) {
						Minds.apply((Mob) e, Minds.Mode.FRENZY, c.duration(300), c.caster.position(), null);
					}
					mindWave(c, at, r, 0xFF4A6A);
					MagicFx.sound(c.level, at, MagicSounds.CAST_ILLUSION, 1.0F, 0.8F);
					return true;
				})
				.register();

		Spell.of("mirror_image", ILLUSION, 4, SELF).mana(55).charge(20).cooldown(400).icon("armor_stand")
				.action(c -> {
					Mannequin decoy = EntityType.MANNEQUIN.create(c.level, EntitySpawnReason.MOB_SUMMONED);
					if (decoy == null) return false;
					Vec3 at = c.safeSpot(c.caster.position().add(c.right().scale(1.6)));
					if (at == null) at = c.caster.position();
					decoy.snapTo(at.x, at.y, at.z, c.caster.getYRot(), c.caster.getXRot());
					decoy.setYHeadRot(c.caster.getYHeadRot());
					decoy.setComponent(DataComponents.PROFILE, ResolvableProfile.createResolved(c.caster.getGameProfile()));
					((MannequinInvoker) decoy).suchki$setHideDescription(true);
					decoy.setCustomName(c.caster.getName());
					decoy.setCustomNameVisible(true);
					for (EquipmentSlot slot : EquipmentSlot.values()) {
						if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR || slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
							decoy.setItemSlot(slot, c.caster.getItemBySlot(slot).copy());
						}
					}
					Minions.summon(c, decoy, c.duration(240));
					for (Mob m : c.level.getEntitiesOfClass(Mob.class, new AABB(c.caster.blockPosition()).inflate(16), c::isEnemy)) {
						Minds.apply(m, Minds.Mode.DECOY, c.duration(240), at, decoy);
					}
					c.caster.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, false, false, true), c.caster);
					MagicFx.burst(c.level, at.add(0, 1, 0), MagicParticles.spark(PINK, 0.3F, 18), 20, 0.4, 0.04);
					MagicFx.sound(c.level, at, MagicSounds.CAST_ILLUSION, 1.0F, 1.2F);
					return true;
				})
				.register();

		Spell.of("mass_hysteria", ILLUSION, 5, RITUAL).mana(90).charge(40).cooldown(900).icon("echo_shard")
				.action(c -> {
					double r = c.radius(16.0);
					Vec3 feet = c.caster.position();
					for (LivingEntity e : c.enemiesAround(feet.add(0, 1, 0), r)) {
						if (e instanceof Mob m) Minds.apply(m, Minds.Mode.FEAR, c.duration(240), feet, null);
						e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, c.duration(240), 0), c.caster);
					}
					MagicFx.one(c.level, feet.add(0, 0.06, 0), MagicParticles.rune(PINK, 3.0F, 40), 0, 1, 0);
					MagicFx.one(c.level, feet.add(0, 0.2, 0), MagicParticles.ring(PINK, (float) r, 24), 0, 1, 0);
					MagicFx.sound(c.level, feet, MagicSounds.CAST_ILLUSION, 1.6F, 0.5F);
					Magic.shake(c.level, feet, 0.8F, 12, 24);
					return true;
				})
				.register();
	}

	/** Волна по разуму: кольцо и мерцание в точке. */
	private static void mindWave(Cast c, Vec3 at, double r, int color) {
		MagicFx.line(c.level, c.hand(), at, MagicParticles.spark(color, 0.12F, 10), 0.5);
		MagicFx.one(c.level, at.add(0, 0.1, 0), MagicParticles.ring(color, (float) r, 14), 0, 1, 0);
		MagicFx.burst(c.level, at, MagicParticles.mote(color, 0.12F, 22), 16, r * 0.4, 0.02);
		MagicFx.burst(c.level, at, ParticleTypes.WITCH, 10, r * 0.3, 0.02);
	}
}
