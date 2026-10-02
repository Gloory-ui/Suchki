package com.suchki.client;

import com.suchki.SuchkiConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public class SuchkiClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		new com.suchki.appleskin.AppleSkin().onInitializeClient();
		FoodTooltips.register();
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS).register(entries -> {
			HolderLookup.RegistryLookup<Enchantment> lookup =
					entries.getContext().holders().lookupOrThrow(Registries.ENCHANTMENT);
			for (Map.Entry<String, Integer> e : SuchkiConfig.INSTANCE.maxLevels.entrySet()) {
				Identifier id = Identifier.tryParse(e.getKey());
				if (id == null) continue;
				lookup.get(ResourceKey.create(Registries.ENCHANTMENT, id)).ifPresent(holder -> {
					int vanillaMax = holder.value().getMaxLevel();
					List<ItemStack> books = new ArrayList<>();
					for (int level = vanillaMax + 1; level <= e.getValue(); level++) {
						books.add(EnchantmentHelper.createBook(new EnchantmentInstance(holder, level)));
					}
					if (books.isEmpty()) return;
					// Ставим новые книги сразу после ванильной книги максимального уровня.
					entries.addAfter(EnchantmentHelper.createBook(new EnchantmentInstance(holder, vanillaMax)), books);
				});
			}
		});

		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			addPerkLines(stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY), lines, false);
			addPerkLines(stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY), lines, true);
		});
	}

	/** Под строкой каждого зачарования — его перки; на книгах ещё и максимальный уровень на наковальне. */
	private static void addPerkLines(ItemEnchantments enchantments, List<Component> lines, boolean book) {
		for (var entry : enchantments.entrySet()) {
			Holder<Enchantment> holder = entry.getKey();
			int level = entry.getIntValue();
			String id = holder.unwrapKey().map(k -> k.identifier().toString()).orElse("");
			List<Component> extra = PerkTooltips.lines(id, level, holder.value().getMaxLevel(), book);
			if (extra.isEmpty()) continue;
			int at = lines.indexOf(Enchantment.getFullname(holder, level));
			if (at < 0) lines.addAll(extra);
			else lines.addAll(at + 1, extra);
		}
	}
}
