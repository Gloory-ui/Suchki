package com.suchki.client.magic;

import com.suchki.SuchkiConfig;
import com.suchki.client.magic.CircleDesigns.Design;
import com.suchki.client.magic.CircleDesigns.Elem;
import com.suchki.client.magic.CircleDesigns.Stroke;
import com.suchki.magic.FxNet;
import com.suchki.magic.School;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * Рисует магические круги штрих за штрихом. Круг задаётся {@link Params} (центр, нормаль, радиус, узор, прогресс
 * прорисовки p ∈ [0, 1], накопление силы, дрожь); сам узор берётся из {@link CircleDesigns}.
 *
 * <p>Каждый штрих — полоса в плоскости круга: тонкая яркая линия (сердцевина светлее края) поверх широкого мягкого
 * свечения. У недорисованного штриха горит «остриё пера»; дорисованный элемент вспыхивает; при p = 1 — большая
 * вспышка «замка». Слои узора крутятся навстречу друг другу. Кольца силы снаружи растут с накоплением, на пределе
 * круг дрожит и двоится (красно-голубой сдвиг). Вдали круг упрощается (меньше точек, без мелких рун).
 *
 * <p>Здесь же живут круги в мире ({@link Inst}): из пакетов {@link FxNet.CircleFx} и «призраки» кругов после
 * выпуска заклинания.
 */
public final class CircleRenderer {
	private static final float TAU = (float) (Math.PI * 2);
	/** Дальше этого круги не рисуются (блоки). */
	public static final double MAX_DISTANCE = 160.0;
	/** Не больше стольких кругов в мире одновременно (старые гаснут первыми). */
	private static final int MAX_WORLD = 96;

	/** Параметры круга в одном кадре. Объект переиспользуется — сбрасывайте {@link #reset()}. */
	public static final class Params {
		public double x, y, z;
		public float nx, ny = 1, nz;
		/** Подсказка «вправо» в плоскости круга (иначе — от мирового верха). */
		public boolean hasRight;
		public float rx, ry, rz;
		public float radius = 1;
		public School school = School.FIRE;
		public int tier = 1;
		public int variant;
		/** Стихии слияния: по кругу на каждую, сходятся к концу прорисовки. */
		public List<School> combo = List.of();
		public int color = 0xFFFFFF, light = 0xFFFFFF;
		/** Прорисовка 0…1. */
		public float progress = 1;
		/** За сколько тиков рисуется круг (для длительности вспышек элементов). */
		public float drawTicks = 20;
		/** Сколько тиков прошло с полной прорисовки (−1 — ещё рисуется). */
		public float sinceDone = -1;
		/** Угол вращения (радианы), слои крутятся с разными множителями. */
		public float spin;
		/** Накопление силы 0…1 — кольца силы снаружи. */
		public float power;
		/** Дрожь на пределе 0…1. */
		public float tremble;
		public float alpha = 1;
		/** Вспышка выпуска 0…1+ (ярче и толще). */
		public float flare;
		public float scale = 1;
		/** Тёмная подложка и пятно света под кругом (круги на земле). */
		public float underlay;
		public int layers = 1;
		public float layerGap = 0.4F;

		public Params reset() {
			x = y = z = 0;
			nx = nz = 0;
			ny = 1;
			hasRight = false;
			radius = 1;
			school = School.FIRE;
			tier = 1;
			variant = 0;
			combo = List.of();
			color = light = 0xFFFFFF;
			progress = 1;
			drawTicks = 20;
			sinceDone = -1;
			spin = power = tremble = flare = underlay = 0;
			alpha = scale = 1;
			layers = 1;
			layerGap = 0.4F;
			return this;
		}

		public Params at(Vec3 c, Vec3 n) {
			x = c.x;
			y = c.y;
			z = c.z;
			double l = n.length();
			if (l < 1.0E-6) {
				nx = 0;
				ny = 1;
				nz = 0;
			} else {
				nx = (float) (n.x / l);
				ny = (float) (n.y / l);
				nz = (float) (n.z / l);
			}
			return this;
		}

		public Params right(Vec3 r) {
			hasRight = r != null;
			if (r != null) {
				rx = (float) r.x;
				ry = (float) r.y;
				rz = (float) r.z;
			}
			return this;
		}

		public Params style(School school, int tier, int color, int light) {
			this.school = school;
			this.tier = tier;
			this.color = color;
			this.light = light;
			return this;
		}
	}

	private CircleRenderer() {}

	// ---------------------------------------------------------------- время и статистика

	private static long ticks;
	private static int drawnCircles, drawnLast, drawnStrokes, strokesLast;

	/** Тик клиента (не идёт на паузе). */
	static void tick() {
		ticks++;
	}

	/** Время клиента в тиках с долей кадра. */
	public static float now(float pt) {
		return ticks + pt;
	}

	/** Сколько кругов в мире живо сейчас (пакеты и призраки). */
	public static int activeCount() {
		return WORLD.size();
	}

	/** Сколько кругов нарисовано в прошлом кадре (всех: у игроков и в мире). */
	public static int drawnLastFrame() {
		return drawnLast;
	}

	/** Сколько штрихов нарисовано в прошлом кадре. */
	public static int strokesLastFrame() {
		return strokesLast;
	}

	static void endFrame() {
		drawnLast = drawnCircles;
		strokesLast = drawnStrokes;
		drawnCircles = 0;
		drawnStrokes = 0;
	}

	// ---------------------------------------------------------------- рисование одного круга

	private static float smooth(float e0, float e1, float x) {
		float t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
		return t * t * (3 - 2 * t);
	}

	static int mix(int a, int b, float t) {
		t = Math.max(0, Math.min(1, t));
		int r = (int) (((a >> 16) & 255) + ((((b >> 16) & 255) - ((a >> 16) & 255)) * t));
		int g = (int) (((a >> 8) & 255) + ((((b >> 8) & 255) - ((a >> 8) & 255)) * t));
		int bl = (int) ((a & 255) + (((b & 255) - (a & 255)) * t));
		return r << 16 | g << 8 | bl;
	}

	static int argb(int rgb, float alpha) {
		int a = (int) (Math.max(0, Math.min(1, alpha)) * 255 + 0.5F);
		return a << 24 | (rgb & 0xFFFFFF);
	}

	/** Базис плоскости круга: ex, ey (ex × ey = n). Без подсказки «вверх» — проекция мирового верха. */
	static void basis(Params c, float[] ex, float[] ey) {
		float nx = c.nx, ny = c.ny, nz = c.nz;
		float ax, ay, az;
		if (c.hasRight) {
			float d = c.rx * nx + c.ry * ny + c.rz * nz;
			ax = c.rx - nx * d;
			ay = c.ry - ny * d;
			az = c.rz - nz * d;
		} else {
			ax = ay = az = 0;
		}
		float al = (float) Math.sqrt(ax * ax + ay * ay + az * az);
		if (al < 0.1F) {
			if (Math.abs(ny) < 0.9F) {
				// ey — мировой верх в плоскости, ex = ey × n.
				float ux = -nx * ny, uy = 1 - ny * ny, uz = -nz * ny;
				float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
				ux /= ul;
				uy /= ul;
				uz /= ul;
				ax = uy * nz - uz * ny;
				ay = uz * nx - ux * nz;
				az = ux * ny - uy * nx;
			} else {
				ax = 1 - nx * nx;
				ay = -ny * nx;
				az = -nz * nx;
			}
			al = (float) Math.sqrt(ax * ax + ay * ay + az * az);
		}
		ex[0] = ax / al;
		ex[1] = ay / al;
		ex[2] = az / al;
		// ey = n × ex
		ey[0] = ny * ex[2] - nz * ex[1];
		ey[1] = nz * ex[0] - nx * ex[2];
		ey[2] = nx * ex[1] - ny * ex[0];
	}

	private static final float[] EX = new float[3], EY = new float[3];

	/** Нарисовать круг (со слоями, слиянием, кольцами силы, подложкой и вспышками). */
	public static void draw(CircleBatch b, Params c) {
		if (c.alpha <= 0.01F || c.radius <= 0.01F) return;
		float R = c.radius * c.scale;
		float cx = b.rx(c.x), cy = b.ry(c.y), cz = b.rz(c.z);
		float dist = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
		if (dist - R * 1.4F > MAX_DISTANCE) return;
		// Дальний круг тонет в тумане. Сжимаем его к камере: на экране он тот же, а туман считается ближе.
		float gap = c.layerGap;
		if (dist > b.fogSafe && dist > R * 1.5F) {
			float k = Math.max(0.2F, b.fogSafe / dist);
			cx *= k;
			cy *= k;
			cz *= k;
			R *= k;
			gap *= k;
		}
		basis(c, EX, EY);
		float[] ex = {EX[0], EX[1], EX[2]}, ey = {EY[0], EY[1], EY[2]};
		int detail = Math.max(0, Math.min(2, SuchkiConfig.INSTANCE.circleDetail));
		float p = Math.max(0, Math.min(1, c.progress));
		float lock = c.sinceDone >= 0 && c.sinceDone < 16 ? (float) Math.exp(-c.sinceDone / 3.5F) : 0;
		float time = b.time;

		// Дрожь на пределе: центр и яркость скачут, круг двоится.
		float tr = c.tremble;
		if (tr > 0.01F) {
			float j = R * 0.03F * tr * tr;
			float h1 = hash(time * 1.37F), h2 = hash(time * 2.11F + 5), h3 = hash(time * 0.93F + 11);
			cx += (EX[0] * h1 + EY[0] * h2 + c.nx * h3 * 0.3F) * j;
			cy += (EX[1] * h1 + EY[1] * h2 + c.ny * h3 * 0.3F) * j;
			cz += (EX[2] * h1 + EY[2] * h2 + c.nz * h3 * 0.3F) * j;
		}

		// Подложка: тёмный диск (читаемость днём) и пятно света от круга.
		if (c.underlay > 0) {
			float k = Math.min(1, p * 3) * c.alpha * c.underlay;
			planeQuad(b, b.shade, cx, cy, cz, ex, ey, R * 1.1F, 0, argb(0x000000, 0.3F * k));
			planeQuad(b, b.disc, cx, cy, cz, ex, ey, R * 1.06F, 0, argb(c.color, (0.13F + 0.25F * lock + 0.1F * c.flare) * k));
		}

		int layers = Math.max(1, Math.min(6, c.layers));
		for (int i = 0; i < layers; i++) {
			float off = gap * i;
			float lx = cx + c.nx * off, ly = cy + c.ny * off, lz = cz + c.nz * off;
			float Ri = R * (1 - 0.16F * i);
			float pi = i == 0 ? p : Math.max(0, Math.min(1, (p - 0.12F * i) / (1 - 0.12F * i)));
			float spin = c.spin * (i % 2 == 0 ? 1 : -1.25F) + i * 0.4F;
			float a = c.alpha * (i == 0 ? 1 : 0.7F);
			if (c.combo.size() >= 2) {
				drawFusion(b, c, lx, ly, lz, ex, ey, Ri, pi, spin, a, detail, i == 0 ? c.sinceDone : -1, i);
			} else {
				Design d = CircleDesigns.get(c.school, c.tier, c.variant + i, detail);
				drawDesign(b, d, lx, ly, lz, ex, ey, Ri, pi, c.drawTicks, spin, c.color, c.light, a, tr, c.flare, i == 0 ? c.sinceDone : -1, time);
			}
		}

		// Кольца силы (накопление).
		if (c.power > 0.01F && p >= 1) {
			Design pw = CircleDesigns.power(c.school, c.tier, c.variant, detail);
			drawDesign(b, pw, cx, cy, cz, ex, ey, R, c.power, 30, c.spin * 1.7F, c.color, c.light, c.alpha * (0.55F + 0.45F * c.power), tr,
					c.flare, -1, time);
		}

		// Вспышка «замка» при полной прорисовке: блик в центре и волна от края.
		if (lock > 0.01F) {
			float a = lock * c.alpha;
			b.billboard(b.flare, cx, cy, cz, R * (0.45F + 0.9F * (1 - lock)), time * 0.05F, argb(c.light, a));
			b.billboard(b.spark, cx, cy, cz, R * 0.7F, 0, argb(c.color, 0.6F * a));
			annulus(b, b.wave, cx, cy, cz, ex, ey, R * (1.0F + 0.5F * (1 - lock)), R * 0.16F, 48, argb(c.light, 0.85F * a));
		}
		if (c.flare > 0.01F) {
			float a = Math.min(1, c.flare) * c.alpha;
			b.billboard(b.flare, cx, cy, cz, R * (0.7F + 0.5F * c.flare), 0.3F, argb(c.light, a));
			b.billboard(b.spark, cx, cy, cz, R * 1.1F, 0, argb(c.color, 0.45F * a));
		}
		drawnCircles++;
	}

	/** Слияние: по кругу на стихию — они кружат вокруг центра, сходятся и сливаются в один; снаружи — кольцо слияния. */
	private static void drawFusion(CircleBatch b, Params c, float cx, float cy, float cz, float[] ex, float[] ey, float R, float p, float spin,
			float alpha, int detail, float since, int layer) {
		int n = c.combo.size();
		float conv = smooth(0.55F, 1.0F, p);
		float orbit = spin * 0.6F + b.time * 0.03F * (1 - conv);
		for (int j = 0; j < n; j++) {
			School s = c.combo.get(j);
			float ang = TAU * j / n + orbit;
			float off = R * 0.5F * (1 - conv);
			float ox = (ex[0] * (float) Math.cos(ang) + ey[0] * (float) Math.sin(ang)) * off;
			float oy = (ex[1] * (float) Math.cos(ang) + ey[1] * (float) Math.sin(ang)) * off;
			float oz = (ex[2] * (float) Math.cos(ang) + ey[2] * (float) Math.sin(ang)) * off;
			float rj = R * (0.58F + 0.42F * conv) * (1 - 0.06F * j);
			float pj = Math.max(0, Math.min(1, p / 0.78F - 0.05F * j));
			float sj = spin * (j % 2 == 0 ? 1 : -1.2F) * (1 + 0.25F * j);
			Design d = CircleDesigns.get(s, c.tier, c.variant + j + layer, detail);
			// Сошедшиеся круги складываются по свету — чуть приглушаем, чтобы середина не выгорала в белое.
			drawDesign(b, d, cx + ox, cy + oy, cz + oz, ex, ey, rj, pj, c.drawTicks, sj, s.color, s.light, alpha * (1 - 0.3F * conv),
					c.tremble, c.flare, since, b.time);
		}
		float pr = Math.max(0, Math.min(1, (p - 0.66F) / 0.34F));
		if (pr > 0) {
			Design ring = CircleDesigns.fusionRing(c.tier, c.variant, detail);
			drawDesign(b, ring, cx, cy, cz, ex, ey, R * 0.93F, pr, c.drawTicks, -spin * 0.8F, c.color, c.light, alpha, c.tremble, c.flare, since,
					b.time);
		}
	}

	// Временные массивы для одной ломаной (рисование идёт в одном потоке).
	private static float[] px = new float[512], py = new float[512];
	private static float[] wx = new float[512 * 3];
	private static float[] o0 = new float[512 * 3], o1 = new float[512 * 3];
	private static int[] tones = new int[512];

	private static void ensure(int n) {
		if (px.length >= n) return;
		int m = Math.max(n, px.length * 2);
		px = new float[m];
		py = new float[m];
		wx = new float[m * 3];
		o0 = new float[m * 3];
		o1 = new float[m * 3];
		tones = new int[m];
	}

	/**
	 * Нарисовать узор: (cx, cy, cz) — центр относительно камеры, ex/ey — оси плоскости, R — радиус в блоках.
	 * Возвращает число нарисованных штрихов.
	 */
	static int drawDesign(CircleBatch b, Design d, float cx, float cy, float cz, float[] ex, float[] ey, float R, float p, float drawTicks,
			float spin, int color, int light, float alpha, float tremble, float flare, float since, float time) {
		if (p <= 0 || alpha <= 0.01F) return 0;
		float lock = since >= 0 && since < 16 ? (float) Math.exp(-since / 3.5F) : 0;
		float dist = Math.max(0.05F, (float) Math.sqrt(cx * cx + cy * cy + cz * cz));
		float k = b.pxPerBlock / dist;
		float pxR = R * k;
		if (pxR < 2.5F) return 0;
		int stride = pxR < 30 ? 4 : pxR < 90 ? 2 : 1;
		float minHalf = 0.85F / k;
		float ws = (0.35F * R + 0.65F * (float) Math.sqrt(R)) * 1.3F;
		ws *= 1 + 0.5F * Math.min(1, flare);
		Elem[] elems = d.elems;
		int ne = elems.length;
		float[] ec = new float[ne], es = new float[ne], fl = new float[ne];
		for (int e = 0; e < ne; e++) {
			float ang = elems[e].spin() * spin;
			if (tremble > 0.2F) ang += hash(time * 0.7F + e * 3.1F) * 0.035F * tremble;
			ec[e] = (float) Math.cos(ang);
			es[e] = (float) Math.sin(ang);
			float t1 = elems[e].t1();
			// Сколько тиков назад элемент дорисован: по прогрессу, а после полной прорисовки — ещё и по времени.
			float ago = (p - t1) * drawTicks + Math.max(0, since);
			fl[e] = drawTicks > 0 && p >= t1 && !(p >= 1 && t1 >= 0.999F) ? Math.max(0, 1 - ago / 6.0F) : 0;
		}
		float flick = tremble > 0.3F ? 0.8F + 0.2F * hash(time * 3.3F) : 1;
		float hx = (float) Math.cos(time * 0.07F), hy = (float) Math.sin(time * 0.07F);
		float split = tremble > 0.25F ? R * 0.022F * tremble : 0;
		int pens = 0;
		int drawn = 0;
		Stroke[] strokes = d.strokes;
		for (Stroke s : strokes) {
			if (p <= s.t0) continue;
			float f = Math.min(1, (p - s.t0) / (s.t1 - s.t0));
			int e = s.elem;
			float a = alpha * s.alpha * flick * (1 + 0.9F * fl[e] + 1.3F * lock + 0.6F * flare);
			float tone = Math.min(1, s.tone * 0.8F + 0.35F * fl[e] + 0.45F * lock + 0.3F * flare);
			if (f < 1) a *= 1.25F;
			float c0 = ec[e], s0 = es[e];
			if (s.kind == CircleDesigns.DOT) {
				float x = s.xy[0] * c0 - s.xy[1] * s0, y = s.xy[0] * s0 + s.xy[1] * c0;
				float px3 = cx + (ex[0] * x + ey[0] * y) * R, py3 = cy + (ex[1] * x + ey[1] * y) * R, pz3 = cz + (ex[2] * x + ey[2] * y) * R;
				float pop = f < 1 ? 1 + 1.2F * (1 - f) : 1;
				float r = Math.max(s.width * ws * 0.8F * pop, minHalf * 2.5F);
				planeQuad(b, b.dot, px3, py3, pz3, ex, ey, r, 0, argb(mix(color, light, tone + 0.3F), Math.min(1, a)));
				b.billboard(b.spark, px3, py3, pz3, r * 2.6F, 0, argb(color, 0.35F * Math.min(1, a)));
				drawn++;
				continue;
			}
			if (s.total * R * k < 2.5F) continue;
			// Точки (с прореживанием вдали) до места, где сейчас перо.
			float[] xy = s.xy;
			int n = xy.length / 2;
			int st = n > 12 ? stride : 1;
			float L = f * s.total;
			ensure(n + 2);
			int m = 0;
			int prevIdx = -1;
			boolean cut = false;
			for (int i = 0; ; ) {
				if (prevIdx >= 0 && s.len[i] > L) {
					float span = s.len[i] - s.len[prevIdx];
					float t = span > 1.0E-6F ? (L - s.len[prevIdx]) / span : 1;
					px[m] = xy[2 * prevIdx] + (xy[2 * i] - xy[2 * prevIdx]) * t;
					py[m] = xy[2 * prevIdx + 1] + (xy[2 * i + 1] - xy[2 * prevIdx + 1]) * t;
					m++;
					cut = true;
					break;
				}
				px[m] = xy[2 * i];
				py[m] = xy[2 * i + 1];
				m++;
				prevIdx = i;
				if (i == n - 1) break;
				i = Math.min(n - 1, i + st);
			}
			if (m < 2) continue;
			boolean full = !cut && f >= 1 && s.closed;
			// Поворот элемента и перевод в мир; оттенок «перелива» по углу.
			for (int i = 0; i < m; i++) {
				float x = px[i] * c0 - py[i] * s0, y = px[i] * s0 + py[i] * c0;
				px[i] = x;
				py[i] = y;
				wx[3 * i] = cx + (ex[0] * x + ey[0] * y) * R;
				wx[3 * i + 1] = cy + (ex[1] * x + ey[1] * y) * R;
				wx[3 * i + 2] = cz + (ex[2] * x + ey[2] * y) * R;
				float rr = (float) Math.sqrt(x * x + y * y);
				float h = rr > 1.0E-4F ? (x * hx + y * hy) / rr : 0;
				h = h > 0 ? h * h * h * h * h * h : 0;
				tones[i] = argb(mix(color, light, tone + 0.4F * h), Math.min(1, a * (1 + 0.7F * h)));
			}
			// Смещения краёв полосы: на плавных изгибах — общий «митр», на острых углах — свой у каждого отрезка.
			for (int i = 0; i < m - 1; i++) {
				float dx = px[i + 1] - px[i], dy = py[i + 1] - py[i];
				float dl = (float) Math.sqrt(dx * dx + dy * dy);
				if (dl < 1.0E-7F) {
					dx = 1;
					dy = 0;
				} else {
					dx /= dl;
					dy /= dl;
				}
				float qx = -dy, qy = dx;
				float sx = qx, sy = qy, exx = qx, eyy = qy;
				boolean hasPrev = i > 0 || full;
				if (hasPrev) {
					int pi0 = i > 0 ? i - 1 : m - 2;
					float jx = joinX(pi0, i, qx, qy), jy = JOIN_Y;
					if (!Float.isNaN(jx)) {
						sx = jx;
						sy = jy;
					}
				}
				boolean hasNext = i + 1 < m - 1 || full;
				if (hasNext) {
					int ni = i + 1 < m - 1 ? i + 1 : 0;
					float ndx = px[ni + 1] - px[ni], ndy = py[ni + 1] - py[ni];
					float nl = (float) Math.sqrt(ndx * ndx + ndy * ndy);
					if (nl > 1.0E-7F) {
						ndx /= nl;
						ndy /= nl;
						float dot = dx * ndx + dy * ndy;
						if (dot > 0.5F) {
							float mx = qx - ndy, my = qy + ndx;
							float ml = (float) Math.sqrt(mx * mx + my * my);
							mx /= ml;
							my /= ml;
							float sc = 1 / Math.max(0.6F, mx * qx + my * qy);
							exx = mx * sc;
							eyy = my * sc;
						}
					}
				}
				o0[3 * i] = ex[0] * sx + ey[0] * sy;
				o0[3 * i + 1] = ex[1] * sx + ey[1] * sy;
				o0[3 * i + 2] = ex[2] * sx + ey[2] * sy;
				o1[3 * i] = ex[0] * exx + ey[0] * eyy;
				o1[3 * i + 1] = ex[1] * exx + ey[1] * eyy;
				o1[3 * i + 2] = ex[2] * exx + ey[2] * eyy;
			}
			// Мелкие штрихи (руны, насечки) тоньше и не раздуваются вдали — иначе руны слипаются в кляксы.
			float half = s.pen ? Math.max(s.width * ws, minHalf) : Math.max(s.width * ws * 0.8F, minHalf * 0.6F);
			float glowHalf = half * 5.0F + minHalf * 2.0F;
			int glowCol = argb(color, Math.min(1, a * 0.42F));
			emit(b.glow, m, glowHalf, null, glowCol, 0, 0, 0);
			emit(b.line, m, half, tones, 0, 0, 0, 0);
			if (split > 0) {
				emit(b.line, m, half, null, argb(0xFF2050, 0.45F * tremble * Math.min(1, a)), ex[0] * split, ex[1] * split, ex[2] * split);
				emit(b.line, m, half, null, argb(0x20E8FF, 0.45F * tremble * Math.min(1, a)), -ex[0] * split, -ex[1] * split, -ex[2] * split);
			}
			if (f < 1 && s.pen && pens < 14) {
				int h = 3 * (m - 1);
				float size = Math.max(half * 10, R * 0.03F);
				b.billboard(b.flare, wx[h], wx[h + 1], wx[h + 2], size, time * 0.3F, argb(light, Math.min(1, alpha * 0.95F)));
				b.billboard(b.spark, wx[h], wx[h + 1], wx[h + 2], size * 0.45F, 0, argb(0xFFFFFF, Math.min(1, alpha * 0.9F)));
				pens++;
			}
			drawn++;
		}
		drawnStrokes += drawn;
		return drawn;
	}

	private static float JOIN_Y;

	/** Общая нормаль стыка отрезков a→i и i→i+1 (митр); NaN, если угол острый. Y — в {@link #JOIN_Y}. */
	private static float joinX(int a, int i, float qx, float qy) {
		float pdx = px[a + 1] - px[a], pdy = py[a + 1] - py[a];
		float pl = (float) Math.sqrt(pdx * pdx + pdy * pdy);
		if (pl < 1.0E-7F) return Float.NaN;
		pdx /= pl;
		pdy /= pl;
		float dot = pdx * qy - pdy * qx; // dir(prev) · dir(cur), где dir(cur) = (qy, −qx)
		if (dot <= 0.5F) return Float.NaN;
		float mx = -pdy + qx, my = pdx + qy;
		float ml = (float) Math.sqrt(mx * mx + my * my);
		mx /= ml;
		my /= ml;
		float sc = 1 / Math.max(0.6F, mx * qx + my * qy);
		JOIN_Y = my * sc;
		return mx * sc;
	}

	/** Выдать полосы ломаной (точки в {@link #wx}, смещения в {@link #o0}/{@link #o1}). */
	private static void emit(CircleBatch.Buf buf, int m, float half, int[] cols, int col, float sx, float sy, float sz) {
		for (int i = 0; i < m - 1; i++) {
			int a = 3 * i, c = 3 * (i + 1);
			float ax = wx[a] + sx, ay = wx[a + 1] + sy, az = wx[a + 2] + sz;
			float bx = wx[c] + sx, by = wx[c + 1] + sy, bz = wx[c + 2] + sz;
			float h0x = o0[a] * half, h0y = o0[a + 1] * half, h0z = o0[a + 2] * half;
			float h1x = o1[a] * half, h1y = o1[a + 1] * half, h1z = o1[a + 2] * half;
			int ca = cols != null ? cols[i] : col, cb = cols != null ? cols[i + 1] : col;
			buf.v(ax - h0x, ay - h0y, az - h0z, 0, 0, ca);
			buf.v(ax + h0x, ay + h0y, az + h0z, 1, 0, ca);
			buf.v(bx + h1x, by + h1y, bz + h1z, 1, 1, cb);
			buf.v(bx - h1x, by - h1y, bz - h1z, 0, 1, cb);
		}
	}

	/** Квадрат в плоскости круга с полуразмером r, повёрнутый на rot. */
	static void planeQuad(CircleBatch b, CircleBatch.Buf buf, float x, float y, float z, float[] ex, float[] ey, float r, float rot, int argb) {
		float c = (float) Math.cos(rot) * r, s = (float) Math.sin(rot) * r;
		b.plane(buf, x, y, z, ex[0] * c + ey[0] * s, ex[1] * c + ey[1] * s, ex[2] * c + ey[2] * s, -ex[0] * s + ey[0] * c,
				-ex[1] * s + ey[1] * c, -ex[2] * s + ey[2] * c, argb);
	}

	/** Кольцо-полоса в плоскости: от r − width (U = 0) до r (U = 1). */
	static void annulus(CircleBatch b, CircleBatch.Buf buf, float x, float y, float z, float[] ex, float[] ey, float r, float width, int segs,
			int argb) {
		float r0 = Math.max(0, r - width);
		float pc = 1, ps = 0;
		for (int i = 1; i <= segs; i++) {
			float a = TAU * i / segs;
			float c = (float) Math.cos(a), s = (float) Math.sin(a);
			float ax = ex[0] * pc + ey[0] * ps, ay = ex[1] * pc + ey[1] * ps, az = ex[2] * pc + ey[2] * ps;
			float bx = ex[0] * c + ey[0] * s, by = ex[1] * c + ey[1] * s, bz = ex[2] * c + ey[2] * s;
			buf.v(x + ax * r0, y + ay * r0, z + az * r0, 0, 0, argb);
			buf.v(x + ax * r, y + ay * r, z + az * r, 1, 0, argb);
			buf.v(x + bx * r, y + by * r, z + bz * r, 1, 1, argb);
			buf.v(x + bx * r0, y + by * r0, z + bz * r0, 0, 1, argb);
			pc = c;
			ps = s;
		}
	}

	/** Плавный псевдошум −1…1 по времени (для дрожи без рывков на каждом кадре). */
	static float hash(float t) {
		return 0.55F * (float) Math.sin(t * 12.9898F) * (float) Math.cos(t * 4.1414F) + 0.45F * (float) Math.sin(t * 7.233F + 1.3F);
	}

	// ---------------------------------------------------------------- круги в мире

	/** Круг в мире: рисуется {@code draw} тиков, держится {@code hold}, гаснет {@code fade}. */
	public static final class Inst {
		public Vec3 pos;
		public Vec3 normal = new Vec3(0, 1, 0);
		public Vec3 right;
		public float radius = 1;
		public School school = School.FIRE;
		public List<School> combo = List.of();
		public int tier = 1;
		public int color = 0xFFFFFF, light = 0xFFFFFF;
		public int layers = 1;
		public float gap = 0.4F;
		public int draw = 20, hold = 20, fade = 10;
		/** Оборотов в секунду. */
		public float spinRev = 0.15F;
		public int seed;
		public float start;
		public float underlay;
		/** Призрак выпуска: сразу дорисован, вспыхивает и гаснет. */
		public boolean ghost;
		/** Срыв: мерцает, двоится и рассыпается. */
		public boolean fizzle;
		/** Сила вспышки призрака (0 — просто погаснуть). */
		public float flareAmount = 1.4F;
		/** Для галереи и тестов: заморозить прорисовку (≥ 0), вращение, силу и дрожь. */
		public float fixedProgress = -1;
		public float fixedSince = -1;
		public float fixedSpin = Float.NaN;
		public float power;
		public float tremble;
		/** Живёт вечно (пока не удалят) — для галереи. */
		public boolean forever;
		public float alpha = 1;

		public float age(float now) {
			return now - start;
		}

		public boolean done(float now) {
			return !forever && age(now) > draw + hold + fade;
		}
	}

	private static final List<Inst> WORLD = new ArrayList<>();
	private static final Params P = new Params();

	/** Добавить круг в мир (обрезает список, если кругов слишком много). */
	public static Inst add(Inst inst) {
		if (WORLD.size() >= MAX_WORLD) WORLD.remove(0);
		WORLD.add(inst);
		return inst;
	}

	/** Убрать все круги (смена мира, тесты). */
	public static void clear() {
		WORLD.clear();
	}

	/** Круг из пакета сервера. */
	static void receive(FxNet.CircleFx fx, float now) {
		Inst in = new Inst();
		in.pos = fx.pos();
		in.normal = fx.normal();
		in.radius = fx.radius();
		in.color = fx.color();
		in.light = fx.light();
		School[] all = School.values();
		in.school = all[Math.floorMod(fx.design(), all.length)];
		in.tier = Math.max(1, Math.min(7, fx.tier()));
		in.layers = Math.max(1, fx.layers());
		in.gap = fx.layerGap() > 0 ? fx.layerGap() : Math.max(0.3F, fx.radius() * 0.3F);
		in.draw = Math.max(1, fx.draw());
		in.hold = Math.max(0, fx.hold());
		in.fade = Math.max(1, fx.fade());
		in.spinRev = fx.spin();
		in.seed = fx.seed();
		in.start = now;
		in.underlay = Math.abs(fx.normal().y) > 0.7 && fx.normal().y > 0 ? 1 : 0;
		add(in);
	}

	/** Нарисовать живые круги мира и убрать догоревшие. */
	static void renderWorld(CircleBatch b) {
		float now = b.time;
		Iterator<Inst> it = WORLD.iterator();
		while (it.hasNext()) {
			Inst in = it.next();
			if (in.done(now)) {
				it.remove();
				continue;
			}
			if (in.pos.distanceToSqr(b.camX, b.camY, b.camZ) > (MAX_DISTANCE + in.radius) * (MAX_DISTANCE + in.radius)) continue;
			draw(b, params(in, now));
		}
	}

	/** Параметры кадра для круга мира. */
	static Params params(Inst in, float now) {
		float age = Math.max(0, in.age(now));
		Params c = P.reset().at(in.pos, in.normal).right(in.right).style(in.school, in.tier, in.color, in.light);
		c.combo = in.combo;
		c.radius = in.radius;
		c.variant = Math.floorMod(in.seed, CircleDesigns.VARIANTS);
		c.layers = in.layers;
		c.layerGap = in.gap;
		c.underlay = in.underlay;
		c.drawTicks = in.draw;
		c.power = in.power;
		c.tremble = in.tremble;
		float spin = in.spinRev * TAU * age / 20.0F + (in.seed & 255) * 0.05F;
		if (in.ghost) {
			// Призрак выпуска: вспышка, лёгкое расширение, затухание.
			c.progress = in.fixedProgress >= 0 ? in.fixedProgress : 1;
			c.sinceDone = 99;
			float k = age / Math.max(1, in.hold + in.fade);
			c.flare = in.fizzle ? 0 : in.flareAmount * (float) Math.exp(-age / 3.0F);
			c.scale = 1 + (in.fizzle ? -0.15F : 0.1F) * CastAnim.easeOut(Math.min(1, age / 8.0F));
			float fade = age < in.hold ? 1 : 1 - (age - in.hold) / in.fade;
			c.alpha = in.alpha * Math.max(0, fade) * (in.fizzle ? 0.55F + 0.45F * hash(age * 4.0F) : 1);
			if (in.fizzle) c.tremble = Math.max(c.tremble, 0.8F * (1 - k));
			c.spin = spin + (in.fizzle ? 0 : 0.6F * (float) Math.sqrt(age));
		} else {
			c.progress = in.fixedProgress >= 0 ? in.fixedProgress : Math.min(1, age / in.draw);
			float since = age - in.draw;
			c.sinceDone = in.fixedProgress >= 0 ? in.fixedSince : since >= 0 ? since : -1;
			float fadeAge = age - in.draw - in.hold;
			c.alpha = in.alpha * (in.forever || fadeAge <= 0 ? 1 : Math.max(0, 1 - fadeAge / in.fade));
			if (!in.forever && fadeAge > 0) c.scale = 1 + 0.06F * fadeAge / in.fade;
			c.spin = Float.isNaN(in.fixedSpin) ? spin : in.fixedSpin;
		}
		return c;
	}
}
