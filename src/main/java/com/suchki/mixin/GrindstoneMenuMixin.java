package com.suchki.mixin;

import com.suchki.enchant.GrindstoneSplit;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Точильный камень: предмет с чарами + обычная книга → книга с этими чарами. */
@Mixin(GrindstoneMenu.class)
public abstract class GrindstoneMenuMixin {
	@Inject(method = "computeResult", at = @At("HEAD"), cancellable = true)
	private void suchki$split(ItemStack a, ItemStack b, CallbackInfoReturnable<ItemStack> cir) {
		int src = GrindstoneSplit.sourceSlot(a, b);
		if (src >= 0) cir.setReturnValue(GrindstoneSplit.result(src == 0 ? a : b));
	}
}
