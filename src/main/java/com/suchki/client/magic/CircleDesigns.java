package com.suchki.client.magic;

import com.suchki.magic.School;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Узоры магических кругов: из каких штрихов состоит круг стихии и ранга и в каком порядке они рисуются.
 * Всё в плоскости круга, радиус круга = 1, угол — против часовой от +X, «верх» — +Y.
 *
 * <p>У каждого штриха своё окно [t0, t1] на шкале прорисовки p ∈ [0, 1]: сначала внешнее кольцо, потом руны,
 * звезда, знак стихии, внутренние кольца и сердцевина. Штрихи объединены в «элементы» — у элемента своё
 * вращение (слои крутятся навстречу друг другу) и своя вспышка, когда он дорисован.
 * Ранг 1…7 задаёт сложность: число колец, поясов рун, вершин звезды, вложенных звёзд и деталей знака.
 */
public final class CircleDesigns {
	/** Обычный штрих (ломаная). */
	public static final byte LINE = 0;
	/** Узел: светящаяся точка радиусом {@code width}. */
	public static final byte DOT = 1;

	/** Штрих: ломаная в плоскости круга. {@code len} — накопленная длина до каждой точки. */
	public static final class Stroke {
		public final float[] xy;
		public final float[] len;
		public final float total;
		public final float t0, t1;
		/** Полутолщина (в долях радиуса), у узла — его радиус. */
		public final float width;
		/** 0 — основной цвет, 1 — светлый. */
		public final float tone;
		public final float alpha;
		public final int elem;
		public final boolean closed;
		public final byte kind;
		/** Показывать яркое «остриё пера», пока штрих рисуется. */
		public final boolean pen;

		Stroke(float[] xy, boolean closed, float t0, float t1, float width, float tone, float alpha, int elem, byte kind, boolean pen) {
			this.xy = xy;
			this.closed = closed;
			this.t0 = t0;
			this.t1 = Math.max(t0 + 1.0E-4F, t1);
			this.width = width;
			this.tone = tone;
			this.alpha = alpha;
			this.elem = elem;
			this.kind = kind;
			this.pen = pen;
			int n = xy.length / 2;
			len = new float[n];
			float acc = 0;
			for (int i = 1; i < n; i++) {
				float dx = xy[2 * i] - xy[2 * i - 2], dy = xy[2 * i + 1] - xy[2 * i - 1];
				acc += (float) Math.sqrt(dx * dx + dy * dy);
				len[i] = acc;
			}
			total = acc;
		}
	}

	/** Элемент узора: вращение (множитель общей скорости) и окно, когда он рисуется. */
	public record Elem(float spin, float t0, float t1) {}

	/** Готовый узор: штрихи по порядку и элементы. {@code extent} — самый дальний радиус. */
	public static final class Design {
		public final Stroke[] strokes;
		public final Elem[] elems;
		public final float extent;
		public final int segments;

		Design(List<Stroke> strokes, List<Elem> elems) {
			this.strokes = strokes.toArray(new Stroke[0]);
			this.elems = elems.toArray(new Elem[0]);
			float e = 0;
			int segs = 0;
			for (Stroke s : this.strokes) {
				for (int i = 0; i < s.xy.length; i += 2) e = Math.max(e, (float) Math.hypot(s.xy[i], s.xy[i + 1]) + s.width);
				segs += Math.max(1, s.xy.length / 2 - 1);
			}
			this.extent = e;
			this.segments = segs;
		}
	}

	private static final Map<Long, Design> CACHE = new LinkedHashMap<>(64, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, Design> eldest) {
			return size() > 96;
		}
	};

	/** Сколько вариантов узора у одной стихии и ранга (разные руны, повороты). */
	public static final int VARIANTS = 4;

	private CircleDesigns() {}

	/** Узор круга стихии {@code school} ранга {@code tier}; {@code detail} — 0…2 (настройка «подробность кругов»). */
	public static Design get(School school, int tier, int variant, int detail) {
		return cached(0, school, tier, variant, detail);
	}

	/** Кольца силы вокруг круга (накопление): рисуются по мере накопления. */
	public static Design power(School school, int tier, int variant, int detail) {
		return cached(1, school, tier, variant, detail);
	}

	/** Внешнее кольцо слияния, которое появляется, когда круги стихий сошлись. */
	public static Design fusionRing(int tier, int variant, int detail) {
		return cached(2, School.FUSION, tier, variant, detail);
	}

	private static Design cached(int kind, School school, int tier, int variant, int detail) {
		int t = Math.max(1, Math.min(7, tier));
		int v = Math.floorMod(variant, VARIANTS);
		int d = Math.max(0, Math.min(2, detail));
		long key = ((long) kind << 24) | ((long) school.ordinal() << 16) | (t << 8) | (v << 4) | d;
		Design design = CACHE.get(key);
		if (design == null) {
			B b = new B(school, t, v, d);
			switch (kind) {
				case 0 -> b.main();
				case 1 -> b.power();
				default -> b.fusion();
			}
			design = new Design(b.strokes, b.elems);
			CACHE.put(key, design);
		}
		return design;
	}

	// ---------------------------------------------------------------- руны

	/** Руна: штрихи в квадрате 0…1 (Y вверх). */
	record Glyph(float[][] strokes, boolean[] closed) {}

	/**
	 * Наборы рун (свои значки). Запись: штрихи через «/», точки — две цифры «xy» на сетке 0…8 (Y вверх),
	 * «cX,Y:r» — окружность, «aX,Y:r:от:до» — дуга (градусы против часовой от +X).
	 */
	private static final String[] ANGULAR = {
			"20 28/25 68/23 66", "20 28 66 60", "20 28/26 54 22", "20 28/28 55/25 62", "20 28 64 24 60", "68 24 60",
			"18 70/10 78", "20 28 65 22", "10 18/70 78/15 73", "40 48/26 63", "40 48/25 65/22 62", "36 15 33/52 74 55",
			"40 48/48 66/40 22", "40 48/17 44 77", "18 54 34 70", "40 48/15 48 75", "20 28 66 24 62 20", "10 18 44 78 70",
			"10 18 74/70 78 14", "40 74 48 14 40", "10 18 70 78 10", "10 64 48 24 70", "40 48/17 71/11 77", "28 64 20/68 24 60"};
	private static final String[] CURVED = {
			"28 20/a2,5:2:90:-90", "28 20/a2,6:1.6:90:-90/a2,2.8:1.6:90:-90", "68 60/a6,5:2:90:270", "a4,6:3:180:360/47 40",
			"c4,4:3/c4,4:1", "a4,4:3:60:300/a5.3,4:2.4:66:294", "a4,6:2:0:270/a4,2:2:90:-180", "48 42/a6,2:2:180:360/82 86",
			"c4,6:1.8/44 40/22 62", "a4,5:3:20:160/a4,2:3:20:160/48 40", "a4,4:3:100:440", "18 a4,3:2.2:150:-150"};
	private static final String[] ALCHEMIC = {
			"c4,4:3/40 48", "c4,4:3/14 74", "c4,5:2/43 40/21 61", "c4,3:2/55 77/57 77 75", "10 48 70 10", "18 40 78 18",
			"10 48 70 10/14 74", "18 40 78 18/14 74", "a4,4:3:60:300/a5.4,4:2.6:64:296", "c4,4:3/c4,4:0.6",
			"c4,5:1.8/43 40/21 61/a4,8.2:1.6:200:340", "18 78/14 74/10 70", "18 38/58 78/14 74/10 30/50 70", "c4,4:3/17 71",
			"40 48/a4,8:2:200:340/a4,0:2:20:160", "c4,4:3/14 47 74"};
	private static final String[] CLASSIC = {
			"48 40/46 78/44 76", "48 40/17 44 77", "48 75 42 15 48/42 40", "18 10/78 70/16 72", "48 40/15 48 75",
			"18 78/18 70/78 10/10 70", "48 40/c4,4:1.6/21 61", "28 65 23 60/14 34"};

	private static final Glyph[][] SETS = {parseAll(ANGULAR), parseAll(CURVED), parseAll(ALCHEMIC), parseAll(CLASSIC)};
	private static final int SET_ANGULAR = 0, SET_CURVED = 1, SET_ALCHEMIC = 2, SET_CLASSIC = 3;

	private static Glyph[] parseAll(String[] src) {
		Glyph[] out = new Glyph[src.length];
		for (int i = 0; i < src.length; i++) out[i] = parse(src[i]);
		return out;
	}

	static Glyph parse(String src) {
		List<float[]> strokes = new ArrayList<>();
		List<Boolean> closed = new ArrayList<>();
		for (String part : src.split("/")) {
			List<Float> pts = new ArrayList<>();
			for (String tok : part.trim().split("\\s+")) {
				if (tok.isEmpty()) continue;
				char c = tok.charAt(0);
				if (c == 'c' || c == 'a') {
					if (pts.size() >= 4) {
						strokes.add(toArray(pts));
						closed.add(false);
					}
					pts.clear();
					String[] f = tok.substring(1).split(":");
					String[] xy = f[0].split(",");
					float cx = Float.parseFloat(xy[0]) / 8.0F, cy = Float.parseFloat(xy[1]) / 8.0F;
					float r = Float.parseFloat(f[1]) / 8.0F;
					float a0 = c == 'c' ? 0 : (float) Math.toRadians(Float.parseFloat(f[2]));
					float a1 = c == 'c' ? (float) (Math.PI * 2) : (float) Math.toRadians(Float.parseFloat(f[3]));
					int n = Math.max(6, Math.round(Math.abs(a1 - a0) / (float) (Math.PI * 2) * 18));
					float[] arc = new float[(n + 1) * 2];
					for (int k = 0; k <= n; k++) {
						float a = a0 + (a1 - a0) * k / n;
						arc[2 * k] = cx + r * (float) Math.cos(a);
						arc[2 * k + 1] = cy + r * (float) Math.sin(a);
					}
					strokes.add(arc);
					closed.add(c == 'c');
				} else {
					pts.add((tok.charAt(0) - '0') / 8.0F);
					pts.add((tok.charAt(1) - '0') / 8.0F);
				}
			}
			if (pts.size() >= 4) {
				strokes.add(toArray(pts));
				closed.add(false);
			}
		}
		boolean[] cl = new boolean[closed.size()];
		for (int i = 0; i < cl.length; i++) cl[i] = closed.get(i);
		return new Glyph(strokes.toArray(new float[0][]), cl);
	}

	private static float[] toArray(List<Float> pts) {
		float[] a = new float[pts.size()];
		for (int i = 0; i < a.length; i++) a[i] = pts.get(i);
		return a;
	}

	/** Какими рунами пишет стихия (основной набор и набор второго пояса). */
	private static int[] glyphSets(School s) {
		return switch (s) {
			case FIRE -> new int[] {SET_ANGULAR, SET_ALCHEMIC};
			case WATER, WIND, LIFE -> new int[] {SET_CURVED, SET_ALCHEMIC};
			case EARTH, LIGHTNING -> new int[] {SET_ANGULAR, SET_CLASSIC};
			case LIGHT -> new int[] {SET_ALCHEMIC, SET_CLASSIC};
			case DARK -> new int[] {SET_ANGULAR, SET_ALCHEMIC};
			case SPACE -> new int[] {SET_ALCHEMIC, SET_CURVED};
			case MIND -> new int[] {SET_CURVED, SET_CLASSIC};
			case SUMMON -> new int[] {SET_CLASSIC, SET_ANGULAR};
			case FUSION -> new int[] {SET_CLASSIC, SET_CURVED};
		};
	}

	// ---------------------------------------------------------------- построитель

	private static final float TAU = (float) (Math.PI * 2);
	private static final float W_MAIN = 0.0105F, W_RING = 0.0065F, W_THIN = 0.0045F, W_STAR = 0.0075F, W_MOTIF = 0.0062F;

	private static final class B {
		final List<Stroke> strokes = new ArrayList<>();
		final List<Elem> elems = new ArrayList<>();
		final School school;
		final int tier;
		final int detail;
		final Random rnd;
		int cur;
		/** Число отрезков на полный круг единичного радиуса. */
		final int segs;

		B(School school, int tier, int variant, int detail) {
			this.school = school;
			this.tier = tier;
			this.detail = detail;
			this.rnd = new Random(school.ordinal() * 7919L + tier * 104729L + variant * 1299709L + 17);
			this.segs = detail == 0 ? 56 : detail == 1 ? 88 : 120;
		}

		// --- элементы и штрихи

		void elem(float spin, float t0, float t1) {
			elems.add(new Elem(spin, t0, t1));
			cur = elems.size() - 1;
		}

		void add(float[] xy, boolean closed, float t0, float t1, float w, float tone, float alpha, boolean pen) {
			if (xy.length < 4) return;
			strokes.add(new Stroke(xy, closed, t0, t1, w, tone, alpha, cur, LINE, pen));
		}

		void dot(float x, float y, float size, float t, float tone) {
			strokes.add(new Stroke(new float[] {x, y}, false, t, t + 0.03F, size, tone, 1.0F, cur, DOT, false));
		}

		int arcPoints(float r, float sweep) {
			return Math.max(6, (int) Math.ceil(Math.abs(sweep) / TAU * segs * Math.max(0.35F, (float) Math.sqrt(r))));
		}

		float[] arcXY(float cx, float cy, float r, float a0, float a1) {
			int n = arcPoints(r, a1 - a0);
			float[] xy = new float[(n + 1) * 2];
			for (int k = 0; k <= n; k++) {
				float a = a0 + (a1 - a0) * k / n;
				xy[2 * k] = cx + r * (float) Math.cos(a);
				xy[2 * k + 1] = cy + r * (float) Math.sin(a);
			}
			return xy;
		}

		void arc(float cx, float cy, float r, float a0, float a1, float t0, float t1, float w, float tone) {
			add(arcXY(cx, cy, r, a0, a1), false, t0, t1, w, tone, 1.0F, true);
		}

		/** Кольцо одним росчерком из угла {@code start} в сторону {@code dir} (±1). */
		void ring(float r, float t0, float t1, float w, float tone, float start, int dir) {
			add(arcXY(0, 0, r, start, start + dir * TAU), true, t0, t1, w, tone, 1.0F, true);
		}

		/** Кольцо двумя перьями навстречу друг другу (от {@code start} в обе стороны). */
		void ring2(float r, float t0, float t1, float w, float tone, float start) {
			add(arcXY(0, 0, r, start, start + TAU / 2), false, t0, t1, w, tone, 1.0F, true);
			add(arcXY(0, 0, r, start, start - TAU / 2), false, t0, t1, w, tone, 1.0F, true);
		}

		void line(float x0, float y0, float x1, float y1, float t0, float t1, float w, float tone) {
			add(new float[] {x0, y0, x1, y1}, false, t0, t1, w, tone, 1.0F, true);
		}

		/** Звезда {n/k} радиуса r: при взаимно простых n и k — одним росчерком, иначе несколькими многоугольниками. */
		float[][] star(int n, int k, float r, float rot, float t0, float t1, float w, float tone) {
			int g = gcd(n, k);
			int m = n / g;
			float[][] verts = new float[n][2];
			for (int i = 0; i < n; i++) {
				float a = rot + TAU * i / n;
				verts[i][0] = r * (float) Math.cos(a);
				verts[i][1] = r * (float) Math.sin(a);
			}
			for (int s = 0; s < g; s++) {
				float[] xy = new float[(m + 1) * 2];
				for (int i = 0; i <= m; i++) {
					int idx = (s + i * k) % n;
					xy[2 * i] = verts[idx][0];
					xy[2 * i + 1] = verts[idx][1];
				}
				add(xy, true, t0, t1, w, tone, 1.0F, true);
			}
			return verts;
		}

		/** Насечки между радиусами r0 и r1, появляются по кругу. {@code skip} — пропускать каждую skip-ю. */
		void ticks(float r0, float r1, int count, int skip, float rot, float t0, float t1, float w, float tone, float longEvery) {
			for (int i = 0; i < count; i++) {
				if (skip > 0 && i % skip == 0) continue;
				float a = rot + TAU * i / count;
				float rr = longEvery > 0 && i % (int) longEvery == 0 ? r0 - (r1 - r0) * 0.8F : r0;
				float c = (float) Math.cos(a), s = (float) Math.sin(a);
				float ts = t0 + (t1 - t0) * 0.85F * i / count;
				add(new float[] {rr * c, rr * s, r1 * c, r1 * s}, false, ts, ts + (t1 - t0) * 0.15F, w, tone, 0.9F, false);
			}
		}

		/** Руна в точке (cx, cy), верх руны смотрит по углу {@code up}; высота h. */
		void glyph(Glyph gl, float cx, float cy, float up, float h, float t0, float t1, float w, float tone) {
			float gw = h * 0.7F;
			float ux = (float) Math.cos(up), uy = (float) Math.sin(up);
			float rx = uy, ry = -ux;
			int n = gl.strokes().length;
			for (int si = 0; si < n; si++) {
				float[] src = gl.strokes()[si];
				float[] xy = new float[src.length];
				for (int i = 0; i < src.length; i += 2) {
					float dx = (src[i] - 0.5F) * gw, dy = (src[i + 1] - 0.5F) * h;
					xy[i] = cx + rx * dx + ux * dy;
					xy[i + 1] = cy + ry * dx + uy * dy;
				}
				float a = t0 + (t1 - t0) * si / n, b = t0 + (t1 - t0) * (si + 1) / n;
				add(xy, gl.closed()[si], a, b, w, tone, 1.0F, false);
			}
		}

		/** Пояс рун по кругу радиуса r: руны появляются одна за другой по часовой от верха. */
		void band(float r, float h, int count, int set, float rot, float t0, float t1, float w, float tone) {
			Glyph[] glyphs = SETS[set];
			int start = rnd.nextInt(glyphs.length);
			float span = (t1 - t0) / (count * 0.6F + 0.4F);
			for (int i = 0; i < count; i++) {
				float a = rot - TAU * i / count;
				Glyph gl = glyphs[(start + i * 7 + (i * i) % 5) % glyphs.length];
				float ts = t0 + span * 0.6F * i;
				glyph(gl, r * (float) Math.cos(a), r * (float) Math.sin(a), a, h, ts, Math.min(t1, ts + span), w, tone);
			}
		}

		void spokes(float r0, float r1, int count, float rot, float t0, float t1, float w, float tone) {
			for (int i = 0; i < count; i++) {
				float a = rot + TAU * i / count;
				float c = (float) Math.cos(a), s = (float) Math.sin(a);
				add(new float[] {r0 * c, r0 * s, r1 * c, r1 * s}, false, t0, t1, w, tone, 0.85F, true);
			}
		}

		/** Ломаная по функции радиуса: r(a) на полном круге (волны, пила). */
		void radial(java.util.function.IntFunction<float[]> pt, int n, boolean closed, float t0, float t1, float w, float tone) {
			float[] xy = new float[(n + (closed ? 1 : 0)) * 2];
			for (int i = 0; i < n; i++) {
				float[] p = pt.apply(i);
				xy[2 * i] = p[0];
				xy[2 * i + 1] = p[1];
			}
			if (closed) {
				xy[2 * n] = xy[0];
				xy[2 * n + 1] = xy[1];
			}
			add(xy, closed, t0, t1, w, tone, 1.0F, true);
		}

		/** Линза (лепесток, лист, глаз) между двумя точками, выпуклость — доля длины. */
		void lens(float x0, float y0, float x1, float y1, float bulge, float t0, float t1, float w, float tone) {
			int n = detail == 0 ? 8 : 12;
			float dx = x1 - x0, dy = y1 - y0;
			float len = (float) Math.sqrt(dx * dx + dy * dy);
			float px = -dy / len, py = dx / len;
			float[] xy = new float[(2 * n + 1) * 2];
			for (int i = 0; i <= n; i++) {
				float s = i / (float) n;
				float b = bulge * len * (float) Math.sin(Math.PI * s);
				xy[2 * i] = x0 + dx * s + px * b;
				xy[2 * i + 1] = y0 + dy * s + py * b;
			}
			for (int i = 1; i <= n; i++) {
				float s = 1 - i / (float) n;
				float b = bulge * len * (float) Math.sin(Math.PI * s);
				xy[2 * (n + i)] = x0 + dx * s - px * b;
				xy[2 * (n + i) + 1] = y0 + dy * s - py * b;
			}
			add(xy, true, t0, t1, w, tone, 1.0F, true);
		}

		/** Капля/язык пламени: округлое основание в (x, y), острие в направлении {@code dir}, изгиб {@code curl}. */
		void tongue(float x, float y, float dir, float len, float width, float curl, float t0, float t1, float w, float tone) {
			int n = detail == 0 ? 7 : 11;
			float ux = (float) Math.cos(dir), uy = (float) Math.sin(dir);
			float vx = -uy, vy = ux;
			float[] xy = new float[(2 * n + 1) * 2];
			int k = 0;
			for (int side = 0; side < 2; side++) {
				for (int i = 0; i <= n; i++) {
					if (side == 1 && i == 0) continue;
					float s = side == 0 ? 1 - i / (float) n : i / (float) n;
					float hw = width * (float) Math.sqrt(Math.max(0, s)) * (float) Math.pow(1 - s, 1.1) * 1.9F;
					float bend = curl * len * s * s;
					float sign = side == 0 ? 1 : -1;
					float u = len * s - width * 0.35F * (1 - s) * (1 - s);
					float v = sign * hw + bend;
					xy[k++] = x + ux * u + vx * v;
					xy[k++] = y + uy * u + vy * v;
				}
			}
			add(java.util.Arrays.copyOf(xy, k), false, t0, t1, w, tone, 1.0F, true);
		}

		/** Язык пламени: круглое основание в (x, y), волнистые края, острие с наклоном {@code lean}. */
		void flame(float x, float y, float dir, float len, float width, float lean, float t0, float t1, float w, float tone) {
			int n = detail == 0 ? 8 : 13;
			float ux = (float) Math.cos(dir), uy = (float) Math.sin(dir);
			float vx = -uy, vy = ux;
			float[] xy = new float[(2 * n + 1) * 2];
			int k = 0;
			for (int side = 0; side < 2; side++) {
				for (int i = 0; i <= n; i++) {
					if (side == 1 && i == 0) continue;
					float s = side == 0 ? 1 - i / (float) n : i / (float) n;
					float sign = side == 0 ? 1 : -1;
					float wave = 1 + 0.35F * (float) Math.sin(s * 9.0F + (side == 0 ? 0 : 1.9F));
					float hw = width * (float) Math.sqrt(Math.max(0, s * 1.2F)) * (float) Math.pow(1 - s, 1.5) * 1.6F * wave;
					float u = len * s - width * 0.45F * (1 - s) * (1 - s) * (1 - s);
					float v = sign * hw + lean * len * s * s;
					xy[k++] = x + ux * u + vx * v;
					xy[k++] = y + uy * u + vy * v;
				}
			}
			add(java.util.Arrays.copyOf(xy, k), false, t0, t1, w, tone, 1.0F, true);
		}

		void zigzag(float x0, float y0, float x1, float y1, int kinks, float amp, float t0, float t1, float w, float tone) {
			float dx = x1 - x0, dy = y1 - y0;
			float len = (float) Math.sqrt(dx * dx + dy * dy);
			float px = -dy / len, py = dx / len;
			float[] xy = new float[(kinks + 2) * 2];
			for (int i = 0; i <= kinks + 1; i++) {
				float s = i / (float) (kinks + 1);
				float off = i == 0 || i == kinks + 1 ? 0 : (i % 2 == 0 ? 1 : -1) * amp * (0.6F + 0.4F * rnd.nextFloat());
				xy[2 * i] = x0 + dx * s + px * off;
				xy[2 * i + 1] = y0 + dy * s + py * off;
			}
			add(xy, false, t0, t1, w, tone, 1.0F, true);
		}

		void polygon(int n, float cx, float cy, float r, float rot, float t0, float t1, float w, float tone) {
			float[] xy = new float[(n + 1) * 2];
			for (int i = 0; i <= n; i++) {
				float a = rot + TAU * i / n;
				xy[2 * i] = cx + r * (float) Math.cos(a);
				xy[2 * i + 1] = cy + r * (float) Math.sin(a);
			}
			add(xy, true, t0, t1, w, tone, 1.0F, true);
		}

		void circleAt(float cx, float cy, float r, float t0, float t1, float w, float tone) {
			add(arcXY(cx, cy, r, (float) Math.PI / 2, (float) Math.PI / 2 + TAU), true, t0, t1, w, tone, 1.0F, true);
		}

		// --- общий скелет круга

		/** Звезда стихии: {n, k, повернуть вершиной вниз}. */
		int[][] stars() {
			int t = tier;
			return switch (school) {
				case FIRE -> t <= 1 ? new int[][] {{3, 1, 0}} : t >= 6 ? new int[][] {{6, 2, 0}, {12, 5, 0}} : new int[][] {{6, 2, 0}};
				case WATER -> t <= 1 ? new int[][] {{6, 1, 0}} : t >= 5 ? new int[][] {{6, 2, 0}, {12, 5, 0}} : new int[][] {{6, 2, 0}};
				case EARTH -> t <= 1 ? new int[][] {{4, 1, 0}} : t <= 3 ? new int[][] {{8, 2, 0}} : t <= 5 ? new int[][] {{8, 3, 0}}
						: new int[][] {{8, 3, 0}, {8, 2, 0}};
				case LIGHTNING -> t <= 3 ? new int[][] {{5, 2, 0}} : t <= 5 ? new int[][] {{7, 3, 0}} : new int[][] {{9, 4, 0}, {9, 2, 0}};
				case LIGHT -> t <= 1 ? new int[][] {{4, 1, 0}} : t <= 2 ? new int[][] {{5, 2, 0}} : t <= 5 ? new int[][] {{8, 3, 0}}
						: new int[][] {{8, 3, 0}, {16, 7, 0}};
				case DARK -> t <= 4 ? new int[][] {{5, 2, 1}} : t <= 6 ? new int[][] {{7, 3, 1}} : new int[][] {{7, 3, 1}, {7, 2, 1}};
				case SPACE -> t <= 1 ? new int[][] {{3, 1, 0}} : t <= 3 ? new int[][] {{7, 2, 0}} : t <= 5 ? new int[][] {{8, 3, 0}}
						: new int[][] {{12, 5, 0}, {12, 1, 0}};
				case LIFE -> t <= 2 ? new int[][] {{6, 1, 0}} : t <= 5 ? new int[][] {{6, 2, 0}} : new int[][] {{6, 2, 0}, {12, 5, 0}};
				case MIND -> t <= 1 ? new int[][] {{3, 1, 0}} : t <= 4 ? new int[][] {{7, 2, 0}} : new int[][] {{7, 3, 0}, {7, 2, 0}};
				case SUMMON -> t >= 5 ? new int[][] {{5, 2, 0}, {10, 3, 0}} : new int[][] {{5, 2, 0}};
				case FUSION -> t <= 3 ? new int[][] {{6, 2, 0}} : new int[][] {{6, 2, 0}, {12, 5, 0}};
				case WIND -> t <= 1 ? new int[][] {{3, 1, 0}} : t <= 3 ? new int[][] {{5, 2, 0}} : t <= 5 ? new int[][] {{8, 3, 0}}
						: new int[][] {{8, 3, 0}, {8, 1, 0}};
			};
		}

		void main() {
			int t = tier;
			float dir = rnd.nextBoolean() ? 1 : -1;
			float top = (float) Math.PI / 2;
			int[] sets = glyphSets(school);

			// 1. Внешние кольца.
			elem(1.0F, 0.0F, 0.22F);
			ring(1.0F, 0.0F, t == 1 ? 0.3F : 0.18F, W_MAIN, 0.35F, top, (int) dir);
			if (t >= 2) ring(0.968F, 0.05F, 0.22F, W_RING, 0.2F, top + (float) Math.PI, (int) -dir);
			if (t >= 6) ring2(1.035F, 0.08F, 0.22F, W_THIN, 0.5F, -top);
			if (t >= 3 && detail > 0) ticks(0.935F, 0.962F, 72 + 12 * (t - 3), 3, top, 0.1F, 0.32F, W_THIN, 0.4F, t >= 5 ? 6 : 0);

			// 2. Внешний пояс рун.
			float starR = t == 1 ? 0.97F : 0.8F;
			if (t >= 2) {
				float bandOut = t >= 3 && detail > 0 ? 0.93F : 0.962F;
				float h = (bandOut - starR) * 0.72F;
				int count = Math.max(8, Math.round((8 + 4 * t) * (detail == 0 ? 0.6F : detail == 2 ? 1.25F : 1.0F)));
				elem(-0.65F, 0.12F, 0.5F);
				band((bandOut + starR) / 2, h, count, sets[0], top, 0.14F, 0.48F, W_THIN * (0.8F + h * 6), 0.55F);
				ring(starR, 0.12F, 0.3F, W_RING, 0.25F, top, (int) -dir);
			}

			// 3. Звезда (одним росчерком) и узлы на вершинах.
			elem(0.4F, 0.36F, 0.72F);
			int[][] stars = stars();
			float[][] verts = null;
			for (int si = 0; si < stars.length; si++) {
				int[] st = stars[si];
				float r = starR * (si == 0 ? 1.0F : 0.97F);
				float srot = (st[2] == 1 ? -top : top) + (si == 0 ? 0 : (float) Math.PI / st[0]);
				float a = 0.38F + 0.08F * si, b = 0.66F + 0.04F * si;
				float[][] v = star(st[0], st[1], r, srot, a, b, si == 0 ? W_STAR : W_RING, si == 0 ? 0.35F : 0.2F);
				if (si == 0) verts = v;
			}
			if (t >= 3 && verts != null) {
				for (int i = 0; i < verts.length; i++) dot(verts[i][0], verts[i][1], 0.03F + 0.004F * t, 0.62F + 0.06F * i / verts.length, 0.8F);
			}
			if (t >= 5 && verts != null && detail > 0) {
				// Малые круги-спутники с руной на вершинах звезды.
				elem(0.4F, 0.62F, 0.82F);
				for (int i = 0; i < verts.length; i++) {
					float vr = 0.075F;
					float ts = 0.62F + 0.16F * i / verts.length;
					circleAt(verts[i][0], verts[i][1], vr, ts, ts + 0.06F, W_THIN, 0.4F);
					float a = (float) Math.atan2(verts[i][1], verts[i][0]);
					Glyph gl = SETS[sets[1]][(i * 5 + 3) % SETS[sets[1]].length];
					glyph(gl, verts[i][0], verts[i][1], a, vr * 1.1F, ts + 0.03F, ts + 0.09F, W_THIN * 0.8F, 0.6F);
				}
			}

			// 4. Знак стихии.
			motif(starR);

			// 5. Внутренние кольца и второй пояс рун.
			elem(1.25F, 0.74F, 0.95F);
			boolean eye = school == School.MIND;
			float inner = eye ? 0.48F : t >= 4 ? 0.34F : 0.27F;
			if (t >= 2) ring(inner, 0.74F, 0.86F, W_RING, 0.3F, top, (int) dir);
			if (t >= 4 && !eye) {
				float in2 = inner - 0.075F;
				ring(in2, 0.78F, 0.88F, W_THIN, 0.3F, -top, (int) -dir);
				band((inner + in2) / 2, (inner - in2) * 0.7F, 8 + 2 * t, sets[1], top, 0.8F, 0.94F, W_THIN * 0.9F, 0.6F);
			}
			if (t >= 3 && verts != null) spokes(inner, starR * 0.98F, Math.min(verts.length, 12), stars[0][2] == 1 ? -top : top, 0.7F, 0.8F,
					W_THIN, 0.15F);

			// 6. Сердцевина.
			elem(-1.8F, 0.86F, 1.0F);
			center(t >= 4 ? 0.19F : 0.2F);
		}

		/** Кольца силы (вне круга): радиусы 1.07…1.3, насечки и руны, крутятся быстро. */
		void power() {
			int[] sets = glyphSets(school);
			float top = (float) Math.PI / 2;
			elem(1.8F, 0.0F, 0.34F);
			ring2(1.075F, 0.0F, 0.2F, W_RING, 0.5F, top);
			ticks(1.09F, 1.12F, 48 + 6 * tier, 0, top, 0.05F, 0.32F, W_THIN, 0.5F, 4);
			elem(-1.3F, 0.33F, 0.67F);
			ring(1.16F, 0.33F, 0.5F, W_THIN, 0.4F, -top, 1);
			if (detail > 0) band(1.205F, 0.06F, 16 + 2 * tier, sets[0], top, 0.4F, 0.66F, W_THIN * 0.9F, 0.6F);
			ring(1.25F, 0.45F, 0.66F, W_RING, 0.4F, top, -1);
			elem(0.9F, 0.66F, 1.0F);
			int n = 8 + (tier >= 5 ? 4 : 0);
			for (int i = 0; i < n; i++) {
				float a = TAU * i / n;
				float ts = 0.66F + 0.3F * i / n;
				float c = (float) Math.cos(a), s = (float) Math.sin(a);
				// Шипы наружу — «корона» предела силы.
				add(new float[] {1.27F * c - 0.035F * s, 1.27F * s + 0.035F * c, 1.36F * c, 1.36F * s, 1.27F * c + 0.035F * s,
						1.27F * s - 0.035F * c}, false, ts, ts + 0.05F, W_THIN, 0.6F, 1.0F, true);
			}
			ring2(1.30F, 0.7F, 1.0F, W_THIN, 0.3F, -top);
		}

		/** Кольцо слияния: двойное кольцо, насечки и руны всех наборов. */
		void fusion() {
			float top = (float) Math.PI / 2;
			elem(0.6F, 0.0F, 0.5F);
			ring2(1.04F, 0.0F, 0.35F, W_MAIN, 0.45F, top);
			ring2(1.0F, 0.1F, 0.45F, W_RING, 0.25F, -top);
			elem(-0.8F, 0.3F, 1.0F);
			ticks(1.055F, 1.08F, 96, 4, top, 0.3F, 0.6F, W_THIN, 0.5F, 8);
			if (detail > 0) band(1.13F, 0.06F, 20 + 2 * tier, SET_CLASSIC, top, 0.45F, 0.9F, W_THIN, 0.6F);
			ring(1.175F, 0.6F, 1.0F, W_RING, 0.35F, top, 1);
		}

		// --- знаки стихий

		void motif(float starR) {
			int t = tier;
			float top = (float) Math.PI / 2;
			elem(school == School.MIND ? 0.0F : -0.3F, 0.52F, 0.9F);
			switch (school) {
				case FIRE -> {
					int n = 6 + 2 * Math.min(3, t / 2);
					float base = t >= 4 ? 0.42F : 0.36F;
					for (int i = 0; i < n; i++) {
						float a = top + TAU * i / n;
						float ts = 0.54F + 0.28F * i / n;
						float lean = i % 2 == 0 ? 0.18F : -0.18F;
						float x = base * (float) Math.cos(a), y = base * (float) Math.sin(a);
						flame(x, y, a, 0.22F + 0.02F * t, 0.075F, lean, ts, ts + 0.08F, W_MOTIF, 0.5F);
						if (t >= 3) flame(x, y, a, 0.12F + 0.01F * t, 0.04F, -lean, ts + 0.04F, ts + 0.1F, W_THIN, 0.75F);
					}
					if (t >= 6) {
						for (int i = 0; i < n; i++) {
							float a = top + TAU * (i + 0.5F) / n;
							float ts = 0.6F + 0.25F * i / n;
							flame(0.62F * (float) Math.cos(a), 0.62F * (float) Math.sin(a), a + (float) Math.PI, 0.12F, 0.04F, 0.2F, ts, ts + 0.06F,
									W_THIN, 0.4F);
						}
					}
				}
				case WATER -> {
					float wr = t >= 4 ? 0.6F : 0.55F;
					int k = 8 + 2 * t;
					radial(i -> {
						float a = TAU * i / 144;
						float r = wr + 0.028F * (float) Math.sin(a * k);
						return new float[] {r * (float) Math.cos(a), r * (float) Math.sin(a)};
					}, 144, true, 0.54F, 0.72F, W_MOTIF, 0.45F);
					if (t >= 3) {
						radial(i -> {
							float a = TAU * i / 144;
							float r = wr - 0.04F + 0.028F * (float) Math.sin(a * k + Math.PI);
							return new float[] {r * (float) Math.cos(a), r * (float) Math.sin(a)};
						}, 144, true, 0.58F, 0.76F, W_THIN, 0.3F);
					}
					int drops = t >= 4 ? 8 : 6;
					for (int i = 0; i < drops; i++) {
						float a = top + TAU * (i + 0.5F) / drops;
						float ts = 0.62F + 0.2F * i / drops;
						float r = 0.46F;
						tongue(r * (float) Math.cos(a), r * (float) Math.sin(a), a + (float) Math.PI, 0.12F, 0.045F, 0, ts, ts + 0.06F, W_MOTIF, 0.5F);
					}
					if (t >= 5) snowflake(0.27F, 0.72F, 0.88F);
				}
				case WIND -> {
					int arms = 3 + Math.min(3, t / 2);
					float turn = 1.1F + 0.1F * t;
					for (int i = 0; i < arms; i++) {
						float ph = top + TAU * i / arms;
						float ts = 0.54F + 0.2F * i / arms;
						radial(j -> {
							float s = j / 47.0F;
							float r = 0.1F + 0.62F * s;
							float a = ph + turn * (float) Math.PI * (float) Math.pow(s, 0.8);
							return new float[] {r * (float) Math.cos(a), r * (float) Math.sin(a)};
						}, 48, false, ts, ts + 0.16F, W_MOTIF, 0.45F);
					}
					if (t >= 3) {
						int n = 6 + t;
						for (int i = 0; i < n; i++) {
							float a = TAU * i / n;
							float ts = 0.6F + 0.25F * i / n;
							float r = 0.66F;
							// Завиток: дуга по кругу, загнутая внутрь.
							float[] xy = new float[16 * 2];
							for (int j = 0; j < 16; j++) {
								float s = j / 15.0F;
								float rr = r - 0.09F * s * s;
								float aa = a + 0.42F * s;
								xy[2 * j] = rr * (float) Math.cos(aa);
								xy[2 * j + 1] = rr * (float) Math.sin(aa);
							}
							add(xy, false, ts, ts + 0.06F, W_THIN, 0.5F, 1.0F, true);
						}
					}
				}
				case EARTH -> {
					float sq = t >= 4 ? 0.5F : 0.55F;
					polygon(4, 0, 0, sq, top / 2, 0.54F, 0.64F, W_MOTIF, 0.35F);
					if (t >= 2) polygon(4, 0, 0, sq, 0, 0.58F, 0.68F, W_MOTIF, 0.35F);
					int n = 12 + 4 * Math.min(3, t / 2);
					float r0 = starR - 0.13F, r1 = starR - 0.05F;
					if (t >= 2) {
						radial(i -> {
							float a = TAU * i / (2 * n);
							float r = i % 2 == 0 ? r0 : r1;
							return new float[] {r * (float) Math.cos(a), r * (float) Math.sin(a)};
						}, 2 * n, true, 0.6F, 0.76F, W_THIN, 0.45F);
					}
					if (t >= 4) {
						// Решётка внутри квадрата.
						float h = sq * 0.7071F * 0.9F;
						int g = 3 + Math.min(2, (t - 4));
						for (int i = 1; i < g; i++) {
							float x = -h + 2 * h * i / g;
							float ts = 0.66F + 0.12F * i / g;
							line(x, -h, x, h, ts, ts + 0.06F, W_THIN, 0.3F);
							line(-h, x, h, x, ts + 0.02F, ts + 0.08F, W_THIN, 0.3F);
						}
					}
					if (t >= 6) {
						for (int i = 0; i < 4; i++) {
							float a = top / 2 + TAU * i / 4;
							polygon(4, sq * (float) Math.cos(a), sq * (float) Math.sin(a), 0.06F, top / 2, 0.8F, 0.86F, W_THIN, 0.5F);
						}
					}
				}
				case LIGHTNING -> {
					int bolts = 4 + Math.min(4, t);
					for (int i = 0; i < bolts; i++) {
						float a = top + TAU * i / bolts;
						float ts = 0.54F + 0.22F * i / bolts;
						float c = (float) Math.cos(a), s = (float) Math.sin(a);
						zigzag(0.22F * c, 0.22F * s, (starR - 0.04F) * c, (starR - 0.04F) * s, 3 + t / 2, 0.05F, ts, ts + 0.06F, W_MOTIF, 0.55F);
					}
					if (t >= 3) {
						int n = 20 + 4 * t;
						radial(i -> {
							float a = TAU * i / (2 * n);
							float r = i % 2 == 0 ? 0.48F : 0.53F;
							return new float[] {r * (float) Math.cos(a), r * (float) Math.sin(a)};
						}, 2 * n, true, 0.64F, 0.8F, W_THIN, 0.5F);
					}
				}
				case LIGHT -> {
					int rays = 12 + 4 * Math.min(3, t / 2);
					for (int i = 0; i < rays; i++) {
						float a = top + TAU * i / rays;
						float ts = 0.54F + 0.26F * i / rays;
						float r0 = 0.32F, r1 = i % 2 == 0 ? starR - 0.06F : 0.58F;
						float half = 0.6F * (float) Math.PI / rays;
						add(new float[] {r0 * (float) Math.cos(a - half), r0 * (float) Math.sin(a - half), r1 * (float) Math.cos(a),
								r1 * (float) Math.sin(a), r0 * (float) Math.cos(a + half), r0 * (float) Math.sin(a + half)}, false, ts, ts + 0.06F,
								W_MOTIF, 0.6F, 1.0F, true);
					}
					if (t >= 3) {
						for (int i = 0; i < 24; i++) {
							float a = TAU * i / 24;
							dot(0.3F * (float) Math.cos(a), 0.3F * (float) Math.sin(a), 0.012F, 0.7F + 0.15F * i / 24, 0.9F);
						}
					}
				}
				case DARK -> {
					int n = 12 + 2 * t;
					float r = 0.66F;
					for (int i = 0; i < n; i++) {
						float a = TAU * i / n;
						float ts = 0.54F + 0.26F * i / n;
						// Шип: узкий изогнутый треугольник от кольца внутрь.
						float len = 0.13F + 0.012F * t, half = 0.045F;
						float[] xy = new float[17 * 2];
						for (int j = 0; j <= 16; j++) {
							float u = j <= 8 ? j / 8.0F : (16 - j) / 8.0F;
							float side = j <= 8 ? -1 : 1;
							float rr = r - len * u;
							float aa = a + side * half * (1 - u) + 0.18F * u * u;
							xy[2 * j] = rr * (float) Math.cos(aa);
							xy[2 * j + 1] = rr * (float) Math.sin(aa);
						}
						add(xy, false, ts, ts + 0.05F, W_THIN, 0.4F, 1.0F, false);
					}
					ring(r, 0.54F, 0.66F, W_MOTIF, 0.3F, -top, 1);
					if (t >= 3) {
						int m = t >= 5 ? 4 : 3;
						for (int i = 0; i < m; i++) {
							float a = -top + TAU * i / m;
							float cx = 0.43F * (float) Math.cos(a), cy = 0.43F * (float) Math.sin(a);
							float ts = 0.7F + 0.12F * i / m;
							// Полумесяц, рогами к центру.
							arc(cx, cy, 0.07F, a + 0.75F, a + TAU - 0.75F, ts, ts + 0.05F, W_THIN * 1.2F, 0.5F);
							arc(cx + 0.03F * (float) Math.cos(a), cy + 0.03F * (float) Math.sin(a), 0.056F, a + 1.0F, a + TAU - 1.0F, ts + 0.02F,
									ts + 0.07F, W_THIN, 0.5F);
						}
					}
				}
				case SPACE -> {
					int orbits = 2 + Math.min(3, t / 2);
					for (int i = 0; i < orbits; i++) {
						float rot = (float) Math.PI * i / orbits + 0.3F;
						float ts = 0.54F + 0.18F * i / orbits;
						float ea = starR - 0.1F, eb = 0.2F + 0.02F * i;
						float c = (float) Math.cos(rot), s = (float) Math.sin(rot);
						radial(j -> {
							float a = TAU * j / 72;
							float x = ea * (float) Math.cos(a), y = eb * (float) Math.sin(a);
							return new float[] {x * c - y * s, x * s + y * c};
						}, 72, true, ts, ts + 0.12F, W_THIN * 1.2F, 0.4F);
						float pa = 0.9F + 2.1F * i;
						float px = ea * (float) Math.cos(pa), py = eb * (float) Math.sin(pa);
						dot(px * c - py * s, px * s + py * c, 0.028F, ts + 0.12F, 0.9F);
					}
					if (t >= 3) {
						for (int i = 0; i < 4; i++) circleAt(0, 0, 0.08F + 0.055F * i * (1 + 0.25F * i), 0.68F + 0.04F * i, 0.74F + 0.04F * i, W_THIN,
								0.5F - 0.08F * i);
					}
					int stars = 6 + 2 * t;
					for (int i = 0; i < stars; i++) {
						float a = rnd.nextFloat() * TAU;
						float r = 0.32F + rnd.nextFloat() * (starR - 0.42F);
						float x = r * (float) Math.cos(a), y = r * (float) Math.sin(a);
						float sz = 0.02F + rnd.nextFloat() * 0.02F;
						float ts = 0.6F + 0.25F * i / stars;
						line(x - sz, y, x + sz, y, ts, ts + 0.03F, W_THIN * 0.8F, 0.8F);
						line(x, y - sz, x, y + sz, ts + 0.015F, ts + 0.045F, W_THIN * 0.8F, 0.8F);
					}
				}
				case LIFE -> {
					int petals = t <= 2 ? 6 : t <= 4 ? 8 : 12;
					for (int i = 0; i < petals; i++) {
						float a = top + TAU * i / petals;
						float ts = 0.54F + 0.2F * i / petals;
						lens(0.08F * (float) Math.cos(a), 0.08F * (float) Math.sin(a), 0.44F * (float) Math.cos(a), 0.44F * (float) Math.sin(a), 0.22F,
								ts, ts + 0.07F, W_MOTIF, 0.5F);
					}
					if (t >= 3) {
						int leaves = 10 + 2 * t;
						for (int i = 0; i < leaves; i++) {
							float a = TAU * i / leaves;
							float b = a + 0.22F;
							float ts = 0.64F + 0.2F * i / leaves;
							float r0 = 0.6F, r1 = 0.66F;
							lens(r0 * (float) Math.cos(a), r0 * (float) Math.sin(a), r1 * (float) Math.cos(b), r1 * (float) Math.sin(b), 0.3F, ts,
									ts + 0.04F, W_THIN, 0.55F);
						}
					}
					if (t >= 5) {
						// «Цветок жизни»: шесть кругов вокруг центрального.
						for (int i = 0; i < 6; i++) {
							float a = top + TAU * i / 6;
							float ts = 0.7F + 0.12F * i / 6;
							circleAt(0.22F * (float) Math.cos(a), 0.22F * (float) Math.sin(a), 0.22F, ts, ts + 0.07F, W_THIN, 0.35F);
						}
					}
				}
				case MIND -> {
					int ripples = 2 + Math.min(4, t / 2 + 1);
					for (int i = 0; i < ripples; i++) {
						float r = 0.4F + 0.055F * i;
						float ts = 0.54F + 0.06F * i;
						ring(r, ts, ts + 0.1F, W_THIN, 0.3F + 0.1F * i, top + i, i % 2 == 0 ? 1 : -1);
					}
					if (t >= 2) {
						float w = t >= 4 ? 0.44F : 0.32F;
						lens(-w, 0, w, 0, 0.3F, 0.68F, 0.8F, W_MOTIF * 1.2F, 0.7F);
						circleAt(0, 0, 0.1F, 0.78F, 0.84F, W_MOTIF, 0.7F);
						dot(0, 0, 0.045F, 0.85F, 1.0F);
						if (t >= 4) {
							for (int i = 0; i < 7; i++) {
								float a = top + (i - 3) * 0.32F;
								float ts = 0.8F + 0.01F * i;
								line(0.18F * (float) Math.cos(a), 0.12F + 0.1F * (float) Math.sin(a), 0.26F * (float) Math.cos(a),
										0.15F + 0.14F * (float) Math.sin(a), ts, ts + 0.04F, W_THIN, 0.5F);
							}
						}
					}
				}
				case SUMMON -> {
					float h = t >= 4 ? 0.3F : 0.34F;
					polygon(4, 0, 0, h * 1.4142F, top / 2, 0.54F, 0.64F, W_MOTIF, 0.35F);
					if (t >= 2) {
						int bars = 4 + Math.min(3, t / 2);
						for (int i = 0; i < bars; i++) {
							float x = -h + 2 * h * (i + 0.5F) / bars;
							float ts = 0.62F + 0.16F * i / bars;
							line(x, -h, x, h * 0.85F, ts, ts + 0.05F, W_THIN * 1.2F, 0.45F);
						}
						// Арка над вратами.
						arc(0, h * 0.85F - 0.02F, h, 0, (float) Math.PI, 0.78F, 0.86F, W_THIN, 0.5F);
					}
					if (t >= 3) {
						for (int i = 0; i < 4; i++) {
							float a = top / 2 + TAU * i / 4;
							float r = h * 1.4142F;
							circleAt(r * (float) Math.cos(a), r * (float) Math.sin(a), 0.045F, 0.66F + 0.04F * i, 0.7F + 0.04F * i, W_THIN, 0.5F);
						}
					}
				}
				case FUSION -> {
					int n = t >= 4 ? 3 : 2;
					for (int i = 0; i < n; i++) {
						float a = top + TAU * i / n;
						float ts = 0.54F + 0.12F * i;
						circleAt(0.2F * (float) Math.cos(a), 0.2F * (float) Math.sin(a), 0.32F, ts, ts + 0.14F, W_MOTIF, 0.4F);
					}
				}
			}
		}

		/** Снежинка: шесть лучей с веточками. */
		void snowflake(float r, float t0, float t1) {
			float top = (float) Math.PI / 2;
			for (int i = 0; i < 6; i++) {
				float a = top + TAU * i / 6;
				float c = (float) Math.cos(a), s = (float) Math.sin(a);
				float ts = t0 + (t1 - t0) * 0.6F * i / 6;
				line(0, 0, r * c, r * s, ts, ts + (t1 - t0) * 0.3F, W_THIN * 1.2F, 0.7F);
				for (int k = 1; k <= 2; k++) {
					float d = r * (0.4F + 0.28F * k);
					float bl = r * (0.32F - 0.08F * k);
					float x = d * c, y = d * s;
					for (int side = -1; side <= 1; side += 2) {
						float ba = a + side * 0.75F;
						line(x, y, x + bl * (float) Math.cos(ba), y + bl * (float) Math.sin(ba), ts + 0.03F * k, ts + 0.03F * k + 0.05F, W_THIN,
								0.7F);
					}
				}
			}
			polygon(6, 0, 0, r * 0.35F, top, t0 + (t1 - t0) * 0.7F, t1, W_THIN, 0.6F);
		}

		/** Сердцевина: малый знак стихии. */
		void center(float r) {
			float top = (float) Math.PI / 2;
			float w = W_MOTIF;
			switch (school) {
				case FIRE -> {
					star(3, 1, r * 0.8F, top, 0.88F, 0.95F, w, 0.6F);
					if (tier >= 4) star(3, 1, r * 0.8F, -top, 0.9F, 0.97F, W_THIN, 0.5F);
				}
				case WATER -> {
					star(3, 1, r * 0.8F, -top, 0.88F, 0.95F, w, 0.6F);
					if (tier >= 3) circleAt(0, 0, r * 0.3F, 0.93F, 0.98F, W_THIN, 0.6F);
				}
				case WIND -> {
					star(3, 1, r * 0.8F, top, 0.88F, 0.95F, w, 0.6F);
					line(-r * 0.6F, 0, r * 0.6F, 0, 0.94F, 0.98F, W_THIN, 0.6F);
				}
				case EARTH -> {
					star(3, 1, r * 0.8F, -top, 0.88F, 0.95F, w, 0.6F);
					line(-r * 0.6F, 0, r * 0.6F, 0, 0.94F, 0.98F, W_THIN, 0.6F);
				}
				case LIGHTNING -> zigzag(0, r * 0.9F, 0, -r * 0.9F, 3, r * 0.4F, 0.88F, 0.97F, w, 0.8F);
				case LIGHT -> {
					circleAt(0, 0, r * 0.55F, 0.88F, 0.95F, w, 0.7F);
					dot(0, 0, r * 0.25F, 0.95F, 1.0F);
				}
				case DARK -> {
					arc(0, 0, r * 0.7F, 0.6F, TAU - 0.6F, 0.88F, 0.95F, w, 0.5F);
					arc(r * 0.3F, 0, r * 0.55F, 0.9F, TAU - 0.9F, 0.9F, 0.97F, W_THIN, 0.5F);
				}
				case SPACE -> {
					circleAt(0, 0, r * 0.35F, 0.88F, 0.93F, w, 0.6F);
					radial(j -> {
						float a = TAU * j / 40;
						float x = r * 0.85F * (float) Math.cos(a), y = r * 0.3F * (float) Math.sin(a);
						return new float[] {x * 0.866F - y * 0.5F, x * 0.5F + y * 0.866F};
					}, 40, true, 0.92F, 0.98F, W_THIN, 0.6F);
				}
				case LIFE -> {
					for (int i = 0; i < 4; i++) {
						float a = top + TAU * i / 4;
						lens(0, 0, r * 0.8F * (float) Math.cos(a), r * 0.8F * (float) Math.sin(a), 0.3F, 0.88F + 0.02F * i, 0.92F + 0.02F * i, W_THIN,
								0.6F);
					}
				}
				case MIND -> {
					if (tier < 2) {
						circleAt(0, 0, r * 0.5F, 0.88F, 0.95F, w, 0.7F);
						dot(0, 0, r * 0.2F, 0.95F, 1.0F);
					}
				}
				case SUMMON -> {
					polygon(5, 0, 0, r * 0.5F, top, 0.88F, 0.95F, w, 0.6F);
					dot(0, 0, r * 0.15F, 0.95F, 1.0F);
				}
				case FUSION -> {
					star(6, 2, r * 0.7F, top, 0.88F, 0.96F, w, 0.6F);
				}
			}
			if (school != School.MIND || tier < 2) circleAt(0, 0, r, 0.86F, 0.94F, W_RING, 0.3F);
		}
	}

	private static int gcd(int a, int b) {
		return b == 0 ? a : gcd(b, a % b);
	}
}
