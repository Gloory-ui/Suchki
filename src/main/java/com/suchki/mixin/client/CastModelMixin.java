package com.suchki.mixin.client;

import com.suchki.client.magic.CastPoses;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Руки мага от третьего лица: поверх обычной позы плавно накладывается поза каста. */
@Mixin(HumanoidModel.class)
public abstract class CastModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void suchki$castPose(HumanoidRenderState state, CallbackInfo ci) {
		CastPoses.apply((HumanoidModel<?>) (Object) this, state.getData(CastPoses.KEY));
	}
}
