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
		// Под стрелкой и ячейкой результата: свободное место между ними и надписью «Инвентарь».
		Component text = Component.translatable("suchki.grindstone.cost", cost);
		int w = Minecraft.getInstance().font.width(text);
		int x = 137 - w / 2;
		int y = 56;
		g.fill(x - 3, y - 2, x + w + 3, y + 10, 0x60000000);
		g.drawString(Minecraft.getInstance().font, text, x, y, enough ? 0xFF80FF20 : 0xFFFF6060);
	}
}
