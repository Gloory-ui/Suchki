package com.suchki.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.suchki.client.magic.CastCamera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Тряска и отдача камеры от магии — туда же, где ванильный наклон камеры при уроне. */
@Mixin(GameRenderer.class)
public abstract class CastCameraMixin {
	@Inject(method = "bobHurt", at = @At("TAIL"))
	private void suchki$magicShake(PoseStack pose, float pt, CallbackInfo ci) {
		CastCamera.apply(pose, pt);
	}
}
