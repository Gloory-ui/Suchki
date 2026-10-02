package com.suchki.potion;

import com.suchki.Suchki;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

public final class SuchkiPotions {
	public static final RecipeSerializer<PotionMixRecipe> MIX_SERIALIZER = Registry.register(
			BuiltInRegistries.RECIPE_SERIALIZER,
			Identifier.fromNamespaceAndPath(Suchki.MOD_ID, "potion_mix"),
			new CustomRecipe.Serializer<>(PotionMixRecipe::new));

	private SuchkiPotions() {}

	/** Вызывается из Suchki.onInitialize, чтобы класс загрузился и сериализатор зарегистрировался. */
	public static void init() {}
}
