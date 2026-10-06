package com.suchki.magic;

import net.minecraft.network.chat.Component;

/** Школы магии. Цвета — основной (свечение, частицы) и светлый (сердцевина, вспышки). */
public enum School {
	DESTRUCTION("destruction", 0xFF7A2E, 0xFFE0A0),
	RESTORATION("restoration", 0x52E07F, 0xD8FFE0),
	LIGHT("light", 0xFFD84A, 0xFFFFF0),
	DARK("dark", 0x9A4DFF, 0xE6CCFF),
	ALTERATION("alteration", 0x4DA6FF, 0xD6ECFF),
	ILLUSION("illusion", 0xE76BFF, 0xFFDDFF),
	CONJURATION("conjuration", 0x3FE0D0, 0xD8FFFA);

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
		return DESTRUCTION;
	}
}
