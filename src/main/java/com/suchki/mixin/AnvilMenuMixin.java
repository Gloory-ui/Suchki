package com.suchki.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.suchki.SuchkiConfig;
import com.suchki.enchant.Ench;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Наковальня: новые максимальные уровни, потолок цены вместо «Слишком дорого!», линейный штраф. */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin extends ItemCombinerMenu {
	private AnvilMenuMixin(MenuType<?> type, int id, Inventory inv, ContainerLevelAccess access,
			ItemCombinerMenuSlotDefinition slots) {
		super(type, id, inv, access, slots);
	}

	@WrapOperation(method = "createResult",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/Enchantment;getMaxLevel()I"))
	private int suchki$extendedMaxLevel(Enchantment enchantment, Operation<Integer> original) {
		return Ench.maxLevel(enchantment, this.player.registryAccess());
	}

	@WrapOperation(method = "createResult",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(JJJ)J"))
	private long suchki$capCost(long value, long min, long max, Operation<Long> original) {
		long cost = original.call(value, min, max);
		// Стопку предметов зачаровать нельзя (ваниль ставит цену 40) — это не трогаем.
		if (this.inputSlots.getItem(0).getCount() > 1) return cost;
		return Math.min(cost, SuchkiConfig.INSTANCE.anvilMaxCost);
	}

	@Inject(method = "calculateIncreasedRepairCost", at = @At("HEAD"), cancellable = true)
	private static void suchki$linearPenalty(int cost, CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue((int) Math.min((long) cost + SuchkiConfig.INSTANCE.anvilPenaltyStep, Integer.MAX_VALUE));
	}
}
