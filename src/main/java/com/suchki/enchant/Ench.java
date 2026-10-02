package com.suchki.enchant;

import com.suchki.SuchkiConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** Мелкие помощники для чтения уровней зачарований. */
public final class Ench {
	private Ench() {}

	public static int level(Entity entity, ResourceKey<Enchantment> key, ItemStack stack) {
		return level(entity.registryAccess(), key, stack);
	}

	public static int level(RegistryAccess registries, ResourceKey<Enchantment> key, ItemStack stack) {
		if (stack.isEmpty()) return 0;
		Holder<Enchantment> holder = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
		return EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
	}

	/** Новый максимальный уровень из конфига или ванильный, если чары не улучшались. */
	public static int maxLevel(Enchantment enchantment, RegistryAccess registries) {
		int vanilla = enchantment.getMaxLevel();
		return registries.lookupOrThrow(Registries.ENCHANTMENT).getResourceKey(enchantment)
				.map(k -> SuchkiConfig.INSTANCE.maxLevels.get(k.identifier().toString()))
				.map(m -> Math.max(m, vanilla))
				.orElse(vanilla);
	}
}
