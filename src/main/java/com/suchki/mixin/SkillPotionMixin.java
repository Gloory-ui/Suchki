package com.suchki.mixin;

import com.suchki.skill.Perk;
import com.suchki.skill.Skills;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Алхимик: выпитые зелья действуют дольше. */
@Mixin(PotionContents.class)
public abstract class SkillPotionMixin {
	@ModifyVariable(method = "applyToLivingEntity", at = @At("HEAD"), argsOnly = true)
	private float suchki$longer(float durationScale, LivingEntity entity) {
		if (!(entity instanceof Player p)) return durationScale;
		double bonus = Skills.perk(p, Perk.POTION_DURATION);
		return bonus > 0 ? (float) (durationScale * (1.0 + bonus)) : durationScale;
	}
}
