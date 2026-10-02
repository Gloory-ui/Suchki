package com.suchki.enchant;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Разделение чар на точильном камне: зачарованный предмет + обычная книга → книга со всеми его чарами
 * (кроме проклятий), предмет остаётся без них. Стоит опыта: 1 уровень за уровень чар, не больше 30.
 */
public final class GrindstoneSplit {
	public static final int MAX_COST = 30;

	private GrindstoneSplit() {}

	/** Индекс ячейки (0 или 1) с зачарованным предметом, если пара подходит для разделения, иначе -1. */
	public static int sourceSlot(ItemStack a, ItemStack b) {
		if (isSource(a) && b.is(Items.BOOK)) return 0;
		if (isSource(b) && a.is(Items.BOOK)) return 1;
		return -1;
	}

	private static boolean isSource(ItemStack stack) {
		return stack.getCount() == 1 && !stack.is(Items.ENCHANTED_BOOK) && !transferable(stack).isEmpty();
	}

	/** Чары, которые перейдут на книгу (все, кроме проклятий). */
	public static ItemEnchantments transferable(ItemStack stack) {
		ItemEnchantments.Mutable out = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		for (var e : stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet()) {
			if (!e.getKey().is(EnchantmentTags.CURSE)) out.set(e.getKey(), e.getIntValue());
		}
		return out.toImmutable();
	}

	public static int cost(ItemStack source) {
		int sum = 0;
		for (var e : transferable(source).entrySet()) sum += e.getIntValue();
		return Math.max(1, Math.min(MAX_COST, sum));
	}

	public static ItemStack result(ItemStack source) {
		ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
		book.set(DataComponents.STORED_ENCHANTMENTS, transferable(source));
		return book;
	}

	/** Предмет после разделения: проклятия остаются, штраф наковальни сбрасывается. */
	public static ItemStack stripped(ItemStack source) {
		ItemStack copy = source.copy();
		EnchantmentHelper.updateEnchantments(copy, m -> m.removeIf(h -> !h.is(EnchantmentTags.CURSE)));
		copy.set(DataComponents.REPAIR_COST, 0);
		return copy;
	}
}
