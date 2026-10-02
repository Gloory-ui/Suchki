package com.suchki.mixin;

import com.suchki.skill.Skills;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Строитель: опыт за каждый поставленный блок. */
@Mixin(ItemUsedOnLocationTrigger.class)
public abstract class SkillPlaceTriggerMixin {
	@Inject(method = "trigger", at = @At("HEAD"))
	private void suchki$placed(ServerPlayer player, BlockPos pos, ItemStack stack, CallbackInfo ci) {
		if ((Object) this == CriteriaTriggers.PLACED_BLOCK && !player.isCreative()) Skills.addXp(player, "builder", 1);
	}
}
