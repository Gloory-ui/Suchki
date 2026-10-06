package com.suchki.magic;

import net.minecraft.network.chat.Component;

/**
 * Стихии магии (по ним заклинания разложены в колесе, у каждой — свой рисунок круга, цвета и звуки).
 * Цвета — основной (свечение, линии круга, частицы) и светлый (сердцевина, вспышки).
 * {@link #FUSION} — слияние стихий: у таких заклинаний есть список стихий-составляющих ({@link Spell#combo}).
 */
public enum School {
	FIRE("fire", 0xFF6A1F, 0xFFE0A0),
	WATER("water", 0x2F8CFF, 0xD2F0FF),
	WIND("wind", 0x8CFFC8, 0xF2FFF8),
	EARTH("earth", 0xD08B3A, 0xF6E2BC),
	LIGHTNING("lightning", 0x6FE3FF, 0xF2FCFF),
	LIGHT("light", 0xFFD84A, 0xFFFFF0),
	DARK("dark", 0x8A2BE2, 0xE2C8FF),
	SPACE("space", 0x4B55FF, 0xDCDFFF),
	LIFE("life", 0x52E07F, 0xD8FFE0),
	MIND("mind", 0xFF5FD2, 0xFFDDF6),
	SUMMON("summon", 0x3FE0D0, 0xD8FFFA),
	FUSION("fusion", 0xFF4FA3, 0xFFE0F0);

	public final String id;
	public final int color;
	public final int light;

	School(String id, int color, int light) {
		this.id = id;
		this.color = color;
		this.light = light;
	}

	public Component displayName() {
		return Component.translatable("suchki.school." + id);
	}

	public static School byId(String id) {
		for (School s : values()) if (s.id.equals(id)) return s;
		return FIRE;
	}
}
