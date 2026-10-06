package com.suchki.mixin.client;

import com.suchki.client.magic.CastAnim;
import com.suchki.client.magic.CastPoses;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Кладём позу каста игрока в состояние отрисовки — модель возьмёт её в setupAnim. */
@Mixin(AvatarRenderer.class)
public abstract class CastPoseMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("TAIL"))
	private void suchki$castPose(Avatar avatar, AvatarRenderState state, float pt, CallbackInfo ci) {
		state.setData(CastPoses.KEY, avatar instanceof AbstractClientPlayer p ? CastAnim.compute(p, pt) : null);
	}
}
