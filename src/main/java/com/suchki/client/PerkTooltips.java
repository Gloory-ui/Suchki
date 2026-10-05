package com.suchki.client;

import com.suchki.SuchkiConfig;
import com.suchki.enchant.ProtectionCurve;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Тексты перков для подсказок. Числа берутся из config/suchki.json клиента. */
final class PerkTooltips {
	private PerkTooltips() {}

	/** Доля → проценты: целое число или один знак после точки (без хвостов вроде 94.69999999). */
	private static String pct(double fraction) {
		return num(fraction * 100.0);
	}

	static List<Component> lines(String id, int level, int vanillaMax, boolean book) {
		SuchkiConfig cfg = SuchkiConfig.INSTANCE;
		List<Component> out = new ArrayList<>();

		// Реальный бонус: в строке «Урон при атаке» игра его не показывает.
		switch (id) {
			case "minecraft:sharpness" -> stat(out, "suchki.tooltip.damage", num(boosted(1.0, 0.5, level)));
			case "minecraft:power" -> stat(out, "suchki.tooltip.damage_power", num(boosted(1.0, 0.5, level)));
			case "minecraft:impaling" -> stat(out, "suchki.tooltip.damage_impaling", num(boosted(2.5, 2.5, level)));
			case "minecraft:smite" -> stat(out, "suchki.tooltip.damage_smite", num(2.5 * level));
			case "minecraft:bane_of_arthropods" -> stat(out, "suchki.tooltip.damage_bane", num(2.5 * level));
			case "minecraft:protection" -> {
				double perk = level >= cfg.protectionPerkLevel ? 1.0 - cfg.protectionPerkPerPiece * 4 : 1.0;
				double all = 1.0 - (1.0 - ProtectionCurve.reduction(4.0 * level)) * perk;
				double typed = 1.0 - (1.0 - ProtectionCurve.reduction(8.0 * level)) * perk;
				stat(out, "suchki.tooltip.protection_set", pct(round(all)), pct(round(typed)));
			}
			case "minecraft:feather_falling" ->
					stat(out, "suchki.tooltip.feather_set", pct(round(ProtectionCurve.reduction(3.0 * level))));
			default -> {}
		}

		// Постоянные улучшения — действуют на любом уровне.
		switch (id) {
			case "minecraft:protection" -> passive(out, "suchki.perk.protection.all");
			case "minecraft:thorns" -> passive(out, "suchki.perk.thorns");
			case "minecraft:infinity" -> passive(out, "suchki.perk.infinity");
			case "minecraft:mending" -> passive(out, "suchki.perk.mending");
			case "suchki:drill" -> passive(out, "suchki.perk.drill");
			case "suchki:smelting" -> passive(out, "suchki.perk.smelting");
			case "suchki:timber" -> passive(out, "suchki.perk.timber");
			case "suchki:magnet" -> passive(out, "suchki.perk.magnet");
			case "suchki:spring" -> passive(out, "suchki.perk.spring", level);
			case "suchki:deflect" -> passive(out, "suchki.perk.deflect",
					Math.round(cfg.deflectChancePerLevel * 100 * level));
			case "suchki:stun" -> passive(out, "suchki.perk.stun", 8 * level);
			default -> {}
		}

		// Перк высшего уровня: золотой, если уже работает, серый — если до него ещё качать.
		switch (id) {
			case "minecraft:sharpness" -> perk(out, level, 10, "suchki.perk.sharpness");
			case "minecraft:smite" -> perk(out, level, 10, "suchki.perk.smite");
			case "minecraft:bane_of_arthropods" -> perk(out, level, 10, "suchki.perk.bane_of_arthropods");
			case "minecraft:efficiency" -> perk(out, level, 10, "suchki.perk.efficiency");
			case "minecraft:fortune" -> perk(out, level, cfg.fortuneGemLevel, "suchki.perk.fortune", pct(cfg.fortuneGemChance));
			case "minecraft:looting" -> perk(out, level, cfg.lootingHeadLevel, "suchki.perk.looting", pct(cfg.lootingHeadChance));
			case "minecraft:unbreaking" -> perk(out, level, cfg.unbreakingSaveLevel, "suchki.perk.unbreaking");
			case "minecraft:feather_falling" -> perk(out, level, cfg.featherFallingPerkLevel, "suchki.perk.feather_falling",
					String.valueOf((int) cfg.featherFallingSafeHeight));
			case "minecraft:protection" -> perk(out, level, cfg.protectionPerkLevel, "suchki.perk.protection",
					pct(cfg.protectionPerkPerPiece), pct(cfg.protectionPerkPerPiece * 4));
			case "minecraft:fire_aspect" -> perk(out, level, cfg.maxLevels.getOrDefault(id, 5), "suchki.perk.fire_aspect");
			case "minecraft:silk_touch" -> {
				if (cfg.silkTouchSpawners) passive(out, "suchki.perk.silk_touch");
			}
			default -> {}
		}

		if (book) {
			int max = cfg.maxLevels.getOrDefault(id, vanillaMax);
			if (max > vanillaMax) {
				out.add(Component.literal("  ")
						.append(Component.translatable("suchki.tooltip.anvil_max", Component.translatable("enchantment.level." + max)))
						.withStyle(ChatFormatting.DARK_AQUA));
			}
		}
		return out;
	}

	/**
	 * Та же формула, что в data/minecraft/enchantment (sharpness, power, impaling): ванильный рост
	 * до V уровня, дальше — двойной шаг за уровень.
	 */
	private static double boosted(double base, double step, int level) {
		if (level <= 5) return base + step * (level - 1);
		return base + step * 4 + 2.0 * step * (level - 5);
	}

	private static double round(double fraction) {
		return Math.round(fraction * 1000.0) / 1000.0;
	}

	private static String num(double v) {
		double r = Math.round(v * 10.0) / 10.0;
		return r == Math.rint(r) ? String.valueOf((long) r) : String.format(java.util.Locale.ROOT, "%.1f", r);
	}

	private static void stat(List<Component> out, String key, Object... args) {
		out.add(Component.literal("  ").append(Component.translatable(key, args)).withStyle(ChatFormatting.BLUE));
	}

	private static void passive(List<Component> out, String key, Object... args) {
		out.add(Component.literal("  ✦ ").append(Component.translatable(key, args)).withStyle(ChatFormatting.AQUA));
	}

	private static void perk(List<Component> out, int level, int perkLevel, String key, Object... args) {
		Component text = Component.translatable(key, args);
		if (level >= perkLevel) {
			out.add(Component.literal("  ★ ").append(text).withStyle(ChatFormatting.GOLD));
		} else {
			out.add(Component.literal("  ☆ ")
					.append(Component.translatable("suchki.tooltip.locked", Component.translatable("enchantment.level." + perkLevel), text))
					.withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
