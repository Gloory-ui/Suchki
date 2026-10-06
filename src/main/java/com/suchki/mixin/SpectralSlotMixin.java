package com.suchki.mixin;

import com.suchki.magic.SpectralItem;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Призрачные предметы живут только в инвентаре игрока: в сундуки, печи и прочие контейнеры их не положить. */
@Mixin(Slot.class)
public abstract class SpectralSlotMixin {
	@Shadow @Final public Container container;

	@Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
	private void suchki$noSpectralInContainers(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (SpectralItem.is(stack) && !(container instanceof Inventory)) cir.setReturnValue(false);
	}
}
