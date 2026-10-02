package com.suchki.enchant;

import com.suchki.Suchki;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

/** Ключи наших зачарований. Сами зачарования описаны в data/suchki/enchantment/*.json. */
public final class SuchkiEnchantments {
	public static final ResourceKey<Enchantment> DRILL = key("drill");
	public static final ResourceKey<Enchantment> SMELTING = key("smelting");
	public static final ResourceKey<Enchantment> TIMBER = key("timber");
	public static final ResourceKey<Enchantment> SPRING = key("spring");
	public static final ResourceKey<Enchantment> DEFLECT = key("deflect");
	public static final ResourceKey<Enchantment> STUN = key("stun");
	public static final ResourceKey<Enchantment> MAGNET = key("magnet");

	private SuchkiEnchantments() {}

	private static ResourceKey<Enchantment> key(String path) {
		return ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(Suchki.MOD_ID, path));
	}
}
