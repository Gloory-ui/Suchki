package com.suchki.magic;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/**
 * Где найти книги заклинаний: сундуки (I–III круг — в данжах, шахтах и храмах; III–V — в сокровищницах)
 * и священники (книга по уровню священника; мастер продаёт и книги V круга).
 */
public final class MagicLoot {
	/** Таблица → (низший круг, высший круг, шанс выпадения книги на сундук). */
	private record Where(int minTier, int maxTier, float chance) {}

	private static final Map<ResourceKey<LootTable>, Where> CHESTS = Map.ofEntries(
			Map.entry(BuiltInLootTables.SIMPLE_DUNGEON, new Where(1, 2, 0.30F)),
			Map.entry(BuiltInLootTables.ABANDONED_MINESHAFT, new Where(1, 2, 0.20F)),
			Map.entry(BuiltInLootTables.DESERT_PYRAMID, new Where(1, 3, 0.30F)),
			Map.entry(BuiltInLootTables.JUNGLE_TEMPLE, new Where(1, 3, 0.35F)),
			Map.entry(BuiltInLootTables.IGLOO_CHEST, new Where(1, 3, 0.40F)),
			Map.entry(BuiltInLootTables.PILLAGER_OUTPOST, new Where(2, 3, 0.25F)),
			Map.entry(BuiltInLootTables.RUINED_PORTAL, new Where(1, 3, 0.20F)),
			Map.entry(BuiltInLootTables.STRONGHOLD_LIBRARY, new Where(3, 5, 0.60F)),
			Map.entry(BuiltInLootTables.WOODLAND_MANSION, new Where(3, 5, 0.45F)),
			Map.entry(BuiltInLootTables.ANCIENT_CITY, new Where(4, 5, 0.45F)),
			Map.entry(BuiltInLootTables.END_CITY_TREASURE, new Where(4, 5, 0.40F)),
			Map.entry(BuiltInLootTables.BASTION_TREASURE, new Where(4, 5, 0.40F)),
			Map.entry(BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS, new Where(4, 5, 0.25F)));

	private MagicLoot() {}

	static void init() {
		LootTableEvents.MODIFY.register((key, table, source, registries) -> {
			Where where = CHESTS.get(key);
			if (where == null || !source.isBuiltin()) return;
			LootPool.Builder pool = LootPool.lootPool().setRolls(ConstantValue.exactly(1));
			int total = 0;
			for (Spell spell : Spells.all()) {
				if (spell.tier < where.minTier || spell.tier > where.maxTier) continue;
				// Низкие круги попадаются чаще.
				int weight = 6 - spell.tier;
				total += weight;
				pool.add(LootItem.lootTableItem(MagicItems.TOMES.get(spell.school)).setWeight(weight)
						.apply(SetComponentsFunction.setComponent(MagicItems.SPELL, spell.id)));
			}
			if (total == 0) return;
			int empty = Math.round(total * (1.0F - where.chance) / where.chance);
			pool.add(EmptyLootItem.emptyItem().setWeight(empty));
			table.withPool(pool);
		});

		// Священники: на каждом уровне — книга своего круга (на 5-м — IV или V), цена растёт с кругом.
		for (int villagerLevel = 2; villagerLevel <= 5; villagerLevel++) {
			int lo = villagerLevel - 1;
			int hi = villagerLevel == 5 ? 5 : villagerLevel - 1;
			TradeOfferHelper.registerVillagerOffers(VillagerProfession.CLERIC, villagerLevel, offers -> offers.add((level, trader, random) -> {
				List<Spell> pool = Spells.all().stream().filter(s -> s.tier >= lo && s.tier <= hi).toList();
				if (pool.isEmpty()) return null;
				Spell spell = pool.get(random.nextInt(pool.size()));
				int price = switch (spell.tier) {
					case 1 -> 8;
					case 2 -> 14;
					case 3 -> 22;
					case 4 -> 34;
					default -> 56;
				};
				return new MerchantOffer(new ItemCost(Items.EMERALD, price), Optional.of(new ItemCost(Items.BOOK, 1)),
						MagicItems.tome(spell), 1, 10 + spell.tier * 5, 0.05F);
			}));
		}
	}
}
