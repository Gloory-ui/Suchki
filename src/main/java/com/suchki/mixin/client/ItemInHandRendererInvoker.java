package com.suchki.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Рука игрока от первого лица — чтобы нарисовать свободную колдующую руку при гримуаре. */
@Mixin(ItemInHandRenderer.class)
public interface ItemInHandRendererInvoker {
	@Invoker("renderPlayerArm")
	void suchki$renderPlayerArm(PoseStack pose, SubmitNodeCollector out, int light, float equip, float swing, HumanoidArm arm);
}
