package com.suchki.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.suchki.client.magic.CastCamera;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Поле зрения при касте: сужается по мере накопления, «толкается» на выпуске. */
@Mixin(AbstractClientPlayer.class)
public abstract class CastFovMixin {
	@ModifyReturnValue(method = "getFieldOfViewModifier", at = @At("RETURN"))
	private float suchki$castFov(float fov, boolean firstPerson, float effectScale) {
		return fov * CastCamera.fovFactor((AbstractClientPlayer) (Object) this, effectScale);
	}
}
