package com.suchki.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.suchki.SuchkiConfig;
import com.suchki.enchant.Ench;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Прочность X: предмет не ломается насовсем — останавливается на последней единице прочности. */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	@ModifyArg(
			method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/item/ItemStack;applyDamage(ILnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V"),
			index = 0)
	private int suchki$neverBreak(int newDamage, @Local(argsOnly = true) ServerLevel level) {
		ItemStack self = (ItemStack) (Object) this;
		int limit = self.getMaxDamage() - 1;
		if (newDamage <= limit) return newDamage;
		return Ench.level(level.registryAccess(), Enchantments.UNBREAKING, self)
				>= SuchkiConfig.INSTANCE.unbreakingSaveLevel ? limit : newDamage;
	}
}
