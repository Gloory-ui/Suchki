package com.suchki.clientsort.client.order;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/** Suchki: категория предмета для сортировки «По категориям». Меньше число — раньше в списке. */
public final class ItemCategory {
	public static final int BLOCKS = 0;
	public static final int ORES = 1;
	public static final int FOOD = 2;
	public static final int GEAR = 3;
	public static final int OTHER = 4;

	private ItemCategory() {}

	public static int of(ItemStack stack) {
		if (stack.is(ConventionalItemTags.ORES) || stack.is(ConventionalItemTags.RAW_MATERIALS)
				|| stack.is(ConventionalItemTags.INGOTS) || stack.is(ConventionalItemTags.GEMS)
				|| stack.is(ConventionalItemTags.NUGGETS) || stack.is(ConventionalItemTags.DUSTS)
				|| stack.is(ConventionalItemTags.STORAGE_BLOCKS)) {
			return ORES;
		}
		if (stack.has(DataComponents.FOOD)) return FOOD;
		if (stack.has(DataComponents.TOOL) || stack.has(DataComponents.WEAPON)
				|| stack.has(DataComponents.EQUIPPABLE) || stack.isDamageableItem()) {
			return GEAR;
		}
		if (stack.getItem() instanceof BlockItem) return BLOCKS;
		return OTHER;
	}
}
