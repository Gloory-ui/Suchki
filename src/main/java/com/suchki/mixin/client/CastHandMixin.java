package com.suchki.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.suchki.client.magic.FirstPersonCast;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Каст от первого лица: посох, гримуар и свободная рука двигаются по фазам заклинания. */
@Mixin(ItemInHandRenderer.class)
public abstract class CastHandMixin {
	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
	private void suchki$castArm(AbstractClientPlayer player, float pt, float pitch, InteractionHand hand, float swing, ItemStack stack,
			float equip, PoseStack pose, SubmitNodeCollector out, int light, CallbackInfo ci) {
		if (FirstPersonCast.render((ItemInHandRenderer) (Object) this, player, pt, hand, swing, stack, equip, pose, out, light)) ci.cancel();
	}
}
