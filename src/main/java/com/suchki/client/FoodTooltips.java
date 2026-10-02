package com.suchki.client;

import com.suchki.appleskin.ModConfig;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;

/** Доработки AppleSkin: эффекты еды и «сколько штук до полной сытости» в подсказке. */
final class FoodTooltips {
	private static final int MAX_FOOD = 20;

	private FoodTooltips() {}

	static void register() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			FoodProperties food = stack.get(DataComponents.FOOD);
			Consumable consumable = stack.get(DataComponents.CONSUMABLE);
			if (food == null && consumable == null) return;
			// Зелья и т. п. уже сами показывают свои эффекты.
			if (stack.has(DataComponents.POTION_CONTENTS)) return;
			ModConfig cfg = ModConfig.INSTANCE;

			if (cfg.showFoodEffectsInTooltip && consumable != null) {
				for (ConsumeEffect effect : consumable.onConsumeEffects()) {
					if (!(effect instanceof ApplyStatusEffectsConsumeEffect apply) || apply.effects().isEmpty()) continue;
					if (apply.probability() < 1.0F) {
						lines.add(Component.translatable("suchki.food.chance", Math.round(apply.probability() * 100))
								.withStyle(ChatFormatting.GRAY));
					}
					List<Component> effectLines = new ArrayList<>();
					PotionContents.addPotionTooltip(apply.effects(), effectLines::add, 1.0F, 20.0F);
					lines.addAll(effectLines);
				}
			}

			Player player = Minecraft.getInstance().player;
			if (cfg.showFoodToFullInTooltip && food != null && food.nutrition() > 0 && player != null) {
				int missing = MAX_FOOD - player.getFoodData().getFoodLevel();
				if (missing <= 0) {
					lines.add(Component.translatable("suchki.food.full").withStyle(ChatFormatting.DARK_GRAY));
				} else {
					int pieces = (missing + food.nutrition() - 1) / food.nutrition();
					lines.add(Component.translatable("suchki.food.to_full", pieces).withStyle(ChatFormatting.DARK_GREEN));
				}
			}
		});
	}
}
