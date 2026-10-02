package com.suchki.inventory;

import com.suchki.SuchkiConfig;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Автопополнение руки: если стак в выбранной ячейке хотбара закончился (поставил последний блок,
 * съел последнюю еду, сломался инструмент), в ячейку переезжает такой же предмет из инвентаря.
 * Работает на сервере, поэтому одинаково у всех игроков.
 */
public final class AutoRefill {
	/** Что лежало в руке на прошлом тике: ячейка, копия стака и сколько таких предметов было всего. */
	private record Held(int slot, ItemStack stack, int total) {}

	private static final Map<UUID, Held> LAST = new WeakHashMap<>();

	private AutoRefill() {}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!SuchkiConfig.INSTANCE.autoRefill) return;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) tick(player);
		});
	}

	private static void tick(ServerPlayer player) {
		Inventory inv = player.getInventory();
		int slot = inv.getSelectedSlot();
		ItemStack now = inv.getSelectedItem();
		Held before = LAST.get(player.getUUID());

		// Пополняем, только если ячейка не менялась, стак кончился сам и игрок не в другом окне
		// (иначе он мог просто переложить предмет руками).
		if (before != null && before.slot == slot && now.isEmpty() && !before.stack.isEmpty()
				&& !player.isSpectator() && player.containerMenu == player.inventoryMenu
				// Предметов стало меньше — значит, израсходованы, а не переложены мышкой.
				&& total(player, before.stack) < before.total) {
			int from = findReplacement(inv, slot, before.stack);
			if (from >= 0) {
				inv.setItem(slot, inv.getItem(from));
				inv.setItem(from, ItemStack.EMPTY);
				now = inv.getSelectedItem();
			}
		}
		LAST.put(player.getUUID(), new Held(slot, now.copy(), now.isEmpty() ? 0 : total(player, now)));
	}

	/** Сколько таких же предметов у игрока: в инвентаре и на курсоре. */
	private static int total(ServerPlayer player, ItemStack like) {
		int sum = 0;
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (ItemStack.isSameItemSameComponents(s, like)) sum += s.getCount();
		}
		ItemStack carried = player.containerMenu.getCarried();
		if (ItemStack.isSameItemSameComponents(carried, like)) sum += carried.getCount();
		return sum;
	}

	/** Сначала точно такой же предмет, для инструментов — того же типа с любой прочностью. */
	private static int findReplacement(Inventory inv, int selected, ItemStack wanted) {
		int sameType = -1;
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			if (i == selected) continue;
			ItemStack candidate = inv.getItem(i);
			if (candidate.isEmpty()) continue;
			if (ItemStack.isSameItemSameComponents(candidate, wanted)) return i;
			if (sameType < 0 && wanted.isDamageableItem() && candidate.is(wanted.getItem())) sameType = i;
		}
		return sameType;
	}
}
