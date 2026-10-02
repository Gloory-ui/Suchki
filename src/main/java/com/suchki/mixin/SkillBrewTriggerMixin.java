package com.suchki.mixin;

import com.suchki.skill.Skills;
import net.minecraft.advancements.criterion.BrewedPotionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.alchemy.Potion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Алхимик: опыт за каждое забранное из варочной стойки зелье. */
@Mixin(BrewedPotionTrigger.class)
public abstract class SkillBrewTriggerMixin {
	@Inject(method = "trigger", at = @At("HEAD"))
	private void suchki$brewed(ServerPlayer player, Holder<Potion> potion, CallbackInfo ci) {
		Skills.addXp(player, "alchemist", 4);
	}
}
