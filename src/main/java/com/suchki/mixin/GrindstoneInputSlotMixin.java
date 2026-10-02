package com.suchki.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Обе входные ячейки точильного камня принимают обычную книгу (для разделения чар). */
@Mixin(targets = {"net.minecraft.world.inventory.GrindstoneMenu$2", "net.minecraft.world.inventory.GrindstoneMenu$3"})
public abstract class GrindstoneInputSlotMixin {
	@ModifyReturnValue(method = "mayPlace", at = @At("RETURN"))
	private boolean suchki$acceptBook(boolean original, ItemStack stack) {
		return original || stack.is(Items.BOOK);
	}
}
