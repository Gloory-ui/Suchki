package com.suchki.appleskin;

import com.suchki.SuchkiConfig;

/**
 * Настройки AppleSkin. Хранятся в config/suchki.json (раздел "appleskin"), а не в отдельном файле.
 * Раньше читались через Jankson/Cloth Config — в Suchki не нужны.
 */
public class ModConfig {
	/** Текущие настройки — всегда из общего конфига Suchki. */
	public static ModConfig INSTANCE = new ModConfig();

	public static void init() {
		if (SuchkiConfig.INSTANCE.appleskin == null) SuchkiConfig.INSTANCE.appleskin = new ModConfig();
		INSTANCE = SuchkiConfig.INSTANCE.appleskin;
	}

	/** Показывать сытость и насыщение еды в подсказке при зажатом SHIFT. */
	public boolean showFoodValuesInTooltip = true;
	/** Показывать их в подсказке всегда, без SHIFT. */
	public boolean showFoodValuesInTooltipAlways = true;
	/** Текущее насыщение поверх полоски голода. */
	public boolean showSaturationHudOverlay = true;
	/** Сколько голода (и насыщения) восстановит еда в руке. */
	public boolean showFoodValuesHudOverlay = true;
	/** То же для еды во второй руке. */
	public boolean showFoodValuesHudOverlayWhenOffhand = true;
	/** Истощение — полоской под полоской голода. */
	public boolean showFoodExhaustionHudUnderlay = true;
	/** Сколько здоровья примерно восстановит еда — на полоске здоровья. */
	public boolean showFoodHealthHudOverlay = true;
	/** Иконки подрагивают вместе с ванильными. */
	public boolean showVanillaAnimationsOverlay = true;
	/** Насколько видны мигающие иконки в самой яркой точке (1.0 — полностью). */
	public float maxHudOverlayFlashAlpha = 0.65f;
	/** Suchki: эффекты еды (золотое яблоко, хорус и т. п.) в подсказке. */
	public boolean showFoodEffectsInTooltip = true;
	/** Suchki: сколько штук нужно съесть до полной сытости. */
	public boolean showFoodToFullInTooltip = true;
}
