package com.suchki.mixin.client;

import com.suchki.client.HunterSenseClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Охотник: мобы, о которых сообщил сервер, светятся контуром только у самого охотника. */
@Mixin(Minecraft.class)
public abstract class HunterGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true)
	private void suchki$hunterGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && HunterSenseClient.isSensed(entity)) cir.setReturnValue(true);
	}
}
