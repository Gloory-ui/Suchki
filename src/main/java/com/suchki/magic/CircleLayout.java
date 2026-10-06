package com.suchki.magic;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Где стоят магические круги заклинания и его копий (мультикаст). Одна формула на сервере и клиенте: сервер
 * выпускает копии из этих точек, клиент рисует в них круги.
 */
public final class CircleLayout {
	/** Круг: центр, нормаль (куда «смотрит» плоскость) и радиус. */
	public record Placement(Vec3 center, Vec3 normal, float radius) {}

	private CircleLayout() {}

	/** Вектор «вправо» от взгляда (по горизонтали). */
	public static Vec3 right(Vec3 look) {
		Vec3 r = new Vec3(-look.z, 0, look.x);
		return r.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : r.normalize();
	}

	/** Вектор «вверх» в плоскости, перпендикулярной взгляду. */
	public static Vec3 up(Vec3 look) {
		return right(look).cross(look).normalize().scale(-1);
	}

	/**
	 * Главный круг. {@code hand} — точка у посоха/руки, {@code feet} — ноги мага, {@code ground} — земля под прицелом,
	 * {@code point} — сама точка прицела.
	 */
	public static Placement main(Spell spell, Vec3 hand, Vec3 look, Vec3 feet, Vec3 point, Vec3 ground) {
		float r = spell.circleRadius();
		return switch (spell.anchor) {
			case HAND -> new Placement(hand.add(look.scale(0.25)), look, r);
			case FEET -> new Placement(feet.add(0, 0.06, 0), new Vec3(0, 1, 0), r);
			case TARGET -> new Placement(ground.add(0, 0.08, 0), new Vec3(0, 1, 0), r);
			case SKY -> new Placement(ground.add(0, spell.skyHeight(), 0), new Vec3(0, -1, 0), r);
			case LINE -> new Placement(hand.add(look.scale(0.6)), look, r);
			case BACK -> new Placement(feet.add(0, 1.6, 0).subtract(flat(look).scale(0.9)), look, r);
			case ORBIT -> new Placement(feet.add(0, 1.2, 0).add(right(look).scale(1.4)), look, r);
		};
	}

	/**
	 * Круги вдоль линии от посоха к цели (для {@link Anchor#LINE}): {@code layers} кругов, растущих к цели.
	 * Возвращает центр круга номер {@code i}.
	 */
	public static Vec3 lineStep(Vec3 hand, Vec3 point, int i, int layers) {
		Vec3 d = point.subtract(hand);
		double len = Math.min(d.length(), 6.0 + layers * 2.0);
		Vec3 dir = d.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : d.normalize();
		return hand.add(dir.scale(0.6 + len * (i + 0.5) / Math.max(1, layers)));
	}

	/**
	 * Смещение копии {@code i} из {@code n} (мультикаст) относительно главного круга. Копии стоят по кольцу вокруг
	 * главной точки: у посоха — в плоскости круга, на земле/в небе — по горизонтали.
	 */
	public static Vec3 copyOffset(Spell spell, Vec3 look, int i, int n) {
		if (n <= 1) return Vec3.ZERO;
		double a = Math.PI * 2 * i / n + Math.PI / 2;
		return switch (spell.anchor) {
			case HAND, LINE -> {
				double ring = 0.55 + 0.18 * n;
				yield right(look).scale(Math.cos(a) * ring).add(up(look).scale(Math.sin(a) * ring));
			}
			case FEET -> new Vec3(Math.cos(a), 0, Math.sin(a)).scale(1.6 + spell.circleRadius() * 0.6);
			case TARGET, SKY -> new Vec3(Math.cos(a), 0, Math.sin(a)).scale(Math.max(3.0, spell.circleRadius() * 1.4));
			case BACK, ORBIT -> right(look).scale((i - (n - 1) * 0.5) * (spell.circleRadius() * 2.4 + 0.4));
		};
	}

	/** Копия {@code i} из {@code n}: главный круг, сдвинутый на {@link #copyOffset}. */
	public static Placement copy(Spell spell, Placement main, Vec3 look, int i, int n) {
		if (n <= 1) return main;
		float scale = spell.anchor == Anchor.HAND || spell.anchor == Anchor.LINE ? 0.8F : 0.75F;
		return new Placement(main.center().add(copyOffset(spell, look, i, n)), main.normal(), main.radius() * scale);
	}

	private static Vec3 flat(Vec3 look) {
		Vec3 f = new Vec3(look.x, 0, look.z);
		return f.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : f.normalize();
	}

	/** Позиция круга {@code k} из {@code count} в веере за спиной ({@link Anchor#BACK}). */
	public static Vec3 backSlot(Vec3 feet, Vec3 look, int k, int count, float spread) {
		Vec3 f = flat(look);
		Vec3 r = right(look);
		double t = count <= 1 ? 0 : (k / (double) (count - 1)) * 2 - 1;
		double height = 1.9 + 0.9 * (1 - t * t) + Mth.sin(k * 1.7F) * 0.25;
		return feet.add(0, height, 0).subtract(f.scale(0.8)).add(r.scale(t * spread));
	}
}
