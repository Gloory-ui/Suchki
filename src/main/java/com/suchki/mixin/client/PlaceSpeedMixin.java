package com.suchki.mixin.client;

import com.suchki.skill.Perk;
import com.suchki.skill.Skills;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.BlockItem;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Строитель: блоки ставятся быстрее (меньше пауза между установками при зажатой ПКМ). */
@Mixin(Minecraft.class)
public abstract class PlaceSpeedMixin {
	@Shadow private int rightClickDelay;
	@Shadow public @Nullable LocalPlayer player;

	@Inject(method = "startUseItem", at = @At("TAIL"))
	private void suchki$fasterPlacing(CallbackInfo ci) {
		if (this.player == null || !(this.player.getMainHandItem().getItem() instanceof BlockItem)) return;
		int bonus = (int) Skills.perk(this.player, Perk.PLACE_SPEED);
		if (bonus > 0) this.rightClickDelay = Math.max(1, this.rightClickDelay - bonus);
	}
}
