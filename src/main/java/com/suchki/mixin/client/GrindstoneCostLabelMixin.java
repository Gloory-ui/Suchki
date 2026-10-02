package com.suchki.mixin.client;

import com.suchki.enchant.GrindstoneSplit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** В окне точильного камня при разделении чар показывает цену в уровнях, как наковальня. */
@Mixin(AbstractContainerScreen.class)
public abstract class GrindstoneCostLabelMixin {
	@Shadow protected int imageWidth;
	@Shadow protected AbstractContainerMenu menu;

	@Inject(method = "renderLabels", at = @At("TAIL"))
	private void suchki$cost(GuiGraphics g, int mouseX, int mouseY, CallbackInfo ci) {
		if (!((Object) this instanceof GrindstoneScreen)) return;
		ItemStack a = this.menu.getSlot(0).getItem();
		ItemStack b = this.menu.getSlot(1).getItem();
		int src = GrindstoneSplit.sourceSlot(a, b);
		if (src < 0) return;
		int cost = GrindstoneSplit.cost(src == 0 ? a : b);
		Player player = Minecraft.getInstance().player;
		boolean enough = player != null && (player.hasInfiniteMaterials() || player.experienceLevel >= cost);
		Component text = Component.translatable("container.repair.cost", cost);
		int x = this.imageWidth - 8 - Minecraft.getInstance().font.width(text) - 2;
		g.fill(x - 2, 67, this.imageWidth - 8, 79, 0x4F000000);
		g.drawString(Minecraft.getInstance().font, text, x, 69, enough ? 0xFF80FF20 : 0xFFFF6060);
	}
}
