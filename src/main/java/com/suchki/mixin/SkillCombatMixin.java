package com.suchki.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.suchki.skill.Perk;
import com.suchki.skill.PlayerSkills;
import com.suchki.skill.Skills;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Навыки в бою: бонусы атакующему игроку, снижение урона игроку-цели, короче негативные эффекты. */
@Mixin(LivingEntity.class)
public abstract class SkillCombatMixin {
	@ModifyReturnValue(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"))
	private float suchki$skillDamage(float damage, DamageSource source) {
		if (damage <= 0.0F || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return damage;
		LivingEntity victim = (LivingEntity) (Object) this;
		double mult = 1.0;
		if (source.getEntity() instanceof Player attacker && attacker != victim) {
			PlayerSkills s = Skills.get(attacker);
			Entity direct = source.getDirectEntity();
			if (direct instanceof Projectile) {
				mult += s.perk(Perk.PROJECTILE_DAMAGE);
			} else if (direct == attacker) {
				mult += s.perk(Perk.MELEE_DAMAGE);
				if (attacker.getHealth() < attacker.getMaxHealth() / 2.0F) mult += s.perk(Perk.LOW_HP_DAMAGE);
				if (attacker.getMainHandItem().is(ItemTags.AXES)) mult += s.perk(Perk.AXE_DAMAGE);
			}
			if (victim instanceof Enemy) mult += s.perk(Perk.MONSTER_DAMAGE);
		}
		if (victim instanceof Player p) mult *= Math.max(0.0, 1.0 - Skills.perk(p, Perk.INCOMING_DAMAGE));
		return (float) (damage * mult);
	}

	@ModifyVariable(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
			at = @At("HEAD"), argsOnly = true)
	private MobEffectInstance suchki$shorterHarmful(MobEffectInstance effect) {
		if (!((Object) this instanceof Player p) || p.level().isClientSide() || effect.isInfiniteDuration()
				|| effect.getEffect().value().isBeneficial() || effect.getDuration() <= 20) {
			return effect;
		}
		double cut = Skills.perk(p, Perk.HARMFUL_REDUCTION);
		if (cut <= 0) return effect;
		int duration = Math.max(20, (int) Math.round(effect.getDuration() * (1.0 - Math.min(0.9, cut))));
		return new MobEffectInstance(effect.getEffect(), duration, effect.getAmplifier(), effect.isAmbient(),
				effect.isVisible(), effect.showIcon());
	}
}
