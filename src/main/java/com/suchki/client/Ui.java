package com.suchki.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/** Мелкие помощники рисования и анимации для окон Suchki. */
final class Ui {
	private Ui() {}

	// --- Цвета ---

	/** Цвет ARGB из RGB и альфы 0–255. */
	static int argb(int alpha, int rgb) {
		return (Math.max(0, Math.min(255, alpha)) & 0xFF) << 24 | rgb & 0xFFFFFF;
	}

	/** Умножить альфу цвета ARGB на {@code factor} (0..1) — для плавного появления. */
	static int fade(int argb, double factor) {
		int a = (int) (((argb >>> 24) & 0xFF) * Math.max(0, Math.min(1, factor)));
		return a << 24 | argb & 0xFFFFFF;
	}

	/** Затемнить/осветлить RGB: factor < 1 темнее, > 1 светлее. */
	static int shade(int rgb, double factor) {
		int r = (int) Math.min(255, ((rgb >> 16) & 0xFF) * factor);
		int g = (int) Math.min(255, ((rgb >> 8) & 0xFF) * factor);
		int b = (int) Math.min(255, (rgb & 0xFF) * factor);
		return r << 16 | g << 8 | b;
	}

	/** Смешать два RGB: t = 0 — первый, 1 — второй. */
	static int mix(int a, int b, double t) {
		t = Math.max(0, Math.min(1, t));
		int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return r << 16 | g << 8 | bl;
	}

	// --- Время и плавность ---

	static long now() {
		return System.currentTimeMillis();
	}

	/** Мягкое «дыхание» 0..1 (синус) — для мерцания доступных элементов; длинный период не режет глаз. */
	static double pulse(long periodMs) {
		return 0.5 + 0.5 * Math.sin(now() % periodMs / (double) periodMs * Math.PI * 2);
	}

	/** Прогресс 0..1 анимации длиной {@code durationMs}, начавшейся в {@code startMs}. */
	static double progress(long startMs, long durationMs) {
		return Math.max(0, Math.min(1, (now() - startMs) / (double) durationMs));
	}

	/** Плавное замедление к концу. */
	static double easeOut(double t) {
		double inv = 1 - t;
		return 1 - inv * inv * inv;
	}

	/** Плавное приближение значения к цели: не зависит от FPS. */
	static double approach(double current, double target, double perSecond, double dtSeconds) {
		double k = 1 - Math.exp(-perSecond * dtSeconds);
		return current + (target - current) * k;
	}

	// --- Фигуры ---

	/** Панель со срезанными углами: фон и рамка в 1 px. */
	static void panel(GuiGraphics g, int x, int y, int w, int h, int bg, int border) {
		g.fill(x + 1, y, x + w - 1, y + h, bg);
		g.fill(x, y + 1, x + 1, y + h - 1, bg);
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, bg);
		g.hLine(x + 1, x + w - 2, y, border);
		g.hLine(x + 1, x + w - 2, y + h - 1, border);
		g.vLine(x, y, y + h - 1, border);
		g.vLine(x + w - 1, y, y + h - 1, border);
	}

	/** Только рамка (без фона). */
	static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
		g.hLine(x + 1, x + w - 2, y, color);
		g.hLine(x + 1, x + w - 2, y + h - 1, color);
		g.vLine(x, y, y + h - 1, color);
		g.vLine(x + w - 1, y, y + h - 1, color);
	}

	/** Толстая пиксельная линия из (x1,y1) в (x2,y2). */
	static void line(GuiGraphics g, double x1, double y1, double x2, double y2, int thickness, int color) {
		double dx = x2 - x1, dy = y2 - y1;
		int steps = (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)));
		int half = thickness / 2;
		int lastX = Integer.MIN_VALUE, lastY = Integer.MIN_VALUE;
		for (int i = 0; i <= Math.max(steps, 1); i++) {
			double t = steps == 0 ? 0 : i / (double) steps;
			int x = (int) Math.round(x1 + dx * t);
			int y = (int) Math.round(y1 + dy * t);
			if (x == lastX && y == lastY) continue;
			g.fill(x - half, y - half, x - half + thickness, y - half + thickness, color);
			lastX = x;
			lastY = y;
		}
	}

	/** Квадратичная кривая Безье толщиной {@code thickness}: от (x1,y1) через контрольную точку к (x2,y2). */
	static void curve(GuiGraphics g, double x1, double y1, double cx, double cy, double x2, double y2,
			int thickness, int color) {
		int segments = 12;
		double px = x1, py = y1;
		for (int i = 1; i <= segments; i++) {
			double t = i / (double) segments, u = 1 - t;
			double x = u * u * x1 + 2 * u * t * cx + t * t * x2;
			double y = u * u * y1 + 2 * u * t * cy + t * t * y2;
			line(g, px, py, x, y, thickness, color);
			px = x;
			py = y;
		}
	}

	/** Иконка предмета в масштабе {@code scale}, левый верхний угол в (x, y). */
	static void item(GuiGraphics g, ItemStack stack, float x, float y, float scale) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.renderItem(stack, 0, 0);
		g.pose().popMatrix();
	}
}
