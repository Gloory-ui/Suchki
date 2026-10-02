package com.suchki.mixin;

import com.suchki.enchant.GrindstoneSplit;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ячейка результата точильного камня при разделении чар: берёт плату опытом, оставляет предмет
 * без чар на месте и тратит одну книгу (вместо ванильного «очистить обе ячейки и выдать опыт»).
 */
@Mixin(targets = "net.minecraft.world.inventory.GrindstoneMenu$4")
public abstract class GrindstoneResultSlotMixin extends Slot {
	private GrindstoneResultSlotMixin(Container container, int index, int x, int y) {
		super(container, index, x, y);
	}

	@Override
	public boolean mayPickup(Player player) {
		if (player.containerMenu instanceof GrindstoneMenu menu) {
			Container in = ((GrindstoneMenuAccessor) menu).suchki$repairSlots();
			int src = GrindstoneSplit.sourceSlot(in.getItem(0), in.getItem(1));
			if (src >= 0 && !player.hasInfiniteMaterials()) {
				return player.experienceLevel >= GrindstoneSplit.cost(in.getItem(src));
			}
		}
		return super.mayPickup(player);
	}

	@Inject(method = "onTake", at = @At("HEAD"), cancellable = true)
	private void suchki$onTake(Player player, ItemStack taken, CallbackInfo ci) {
		if (!(player.containerMenu instanceof GrindstoneMenu menu)) return;
		GrindstoneMenuAccessor acc = (GrindstoneMenuAccessor) menu;
		Container in = acc.suchki$repairSlots();
		int src = GrindstoneSplit.sourceSlot(in.getItem(0), in.getItem(1));
		if (src < 0) return;
		ItemStack source = in.getItem(src);
		if (!player.hasInfiniteMaterials()) player.giveExperienceLevels(-GrindstoneSplit.cost(source));
		ItemStack books = in.getItem(1 - src).copy();
		books.shrink(1);
		in.setItem(src, GrindstoneSplit.stripped(source));
		in.setItem(1 - src, books);
		acc.suchki$access().execute((level, pos) -> level.levelEvent(1042, pos, 0));
		ci.cancel();
	}
}
