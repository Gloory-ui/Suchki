package com.suchki.mixin;

import com.suchki.skill.Perk;
import com.suchki.skill.Skills;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Кузнец: опыт за созданное снаряжение и бонус к его прочности. */
@Mixin(ItemStack.class)
public abstract class SkillCraftMixin {
	@Inject(method = "onCraftedBy", at = @At("HEAD"))
	private void suchki$crafted(Player player, int amount, CallbackInfo ci) {
		ItemStack self = (ItemStack) (Object) this;
		if (!(player instanceof ServerPlayer sp) || !self.isDamageableItem()) return;
		Skills.addXp(sp, "blacksmith", 10.0 * Math.max(1, amount));
		double bonus = Skills.perk(sp, Perk.CRAFT_DURABILITY);
		// От базовой прочности предмета (а не текущей): повторный крафт/улучшение не накапливает бонус,
		// а незерит после кузнечного стола получает бонус от незеритовой прочности, а не алмазной.
		Integer base = self.getItem().components().get(DataComponents.MAX_DAMAGE);
		if (bonus > 0 && base != null) self.set(DataComponents.MAX_DAMAGE, (int) Math.round(base * (1.0 + bonus)));
	}
}
