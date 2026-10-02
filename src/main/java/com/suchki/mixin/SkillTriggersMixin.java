package com.suchki.mixin;

import com.suchki.skill.Skills;
import java.util.Collection;
import net.minecraft.advancements.criterion.FishingRodHookedTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Рыбак: опыт за улов (срабатывает там же, где ванильное достижение «Рыбалка»). */
@Mixin(FishingRodHookedTrigger.class)
public abstract class SkillTriggersMixin {
	@Inject(method = "trigger", at = @At("HEAD"))
	private void suchki$fished(ServerPlayer player, ItemStack rod, FishingHook hook, Collection<ItemStack> items,
			CallbackInfo ci) {
		if (!items.isEmpty()) Skills.addXp(player, "fisher", 8);
	}
}
