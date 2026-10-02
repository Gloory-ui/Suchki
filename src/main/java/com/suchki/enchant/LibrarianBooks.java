package com.suchki.enchant;

import com.suchki.SuchkiConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * Мастера-библиотекари иногда продают книгу на уровень выше ванильного максимума (Острота VI, Защита V…).
 * Дорого и только одна штука: 64 изумруда + книга.
 */
public final class LibrarianBooks {
	private LibrarianBooks() {}

	public static void register() {
		TradeOfferHelper.registerVillagerOffers(VillagerProfession.LIBRARIAN, 5, offers -> offers.add((level, trader, random) -> {
			if (random.nextDouble() >= SuchkiConfig.INSTANCE.librarianHighBookChance) return null;
			var lookup = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
			List<Holder<Enchantment>> candidates = new ArrayList<>();
			for (Map.Entry<String, Integer> e : SuchkiConfig.INSTANCE.maxLevels.entrySet()) {
				Identifier id = Identifier.tryParse(e.getKey());
				if (id == null) continue;
				Optional<Holder.Reference<Enchantment>> h = lookup.get(ResourceKey.create(Registries.ENCHANTMENT, id));
				h.filter(ref -> e.getValue() > ref.value().getMaxLevel()).ifPresent(candidates::add);
			}
			if (candidates.isEmpty()) return null;
			Holder<Enchantment> pick = candidates.get(random.nextInt(candidates.size()));
			int bookLevel = pick.value().getMaxLevel() + 1;
			return new MerchantOffer(new ItemCost(Items.EMERALD, 64), Optional.of(new ItemCost(Items.BOOK, 1)),
					EnchantmentHelper.createBook(new EnchantmentInstance(pick, bookLevel)), 1, 30, 0.05F);
		}));
	}
}
