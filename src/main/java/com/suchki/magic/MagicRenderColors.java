package com.suchki.magic;

/** Работа с цветами 0xRRGGBB (общая для сервера и клиента). */
public final class MagicRenderColors {
	private MagicRenderColors() {}

	/** Осветлить: {@code k} = 0 — цвет как есть, 1 — белый. */
	public static int lighten(int rgb, float k) {
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		r = Math.round(r + (255 - r) * k);
		g = Math.round(g + (255 - g) * k);
		b = Math.round(b + (255 - b) * k);
		return r << 16 | g << 8 | b;
	}

	/** Затемнить: {@code k} = 0 — как есть, 1 — чёрный. */
	public static int darken(int rgb, float k) {
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		float m = 1 - k;
		return Math.round(r * m) << 16 | Math.round(g * m) << 8 | Math.round(b * m);
	}

	/** Смешать два цвета ({@code t} = 0 — первый, 1 — второй). */
	public static int mix(int a, int b, float t) {
		int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		return Math.round(ar + (br - ar) * t) << 16 | Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
	}
}
