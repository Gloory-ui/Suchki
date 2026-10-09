package com.suchki.client.magic;

import com.suchki.SuchkiConfig;
import com.suchki.magic.FxNet;
import com.suchki.magic.FxNet.WorldFx;
import com.suchki.magic.MagicParticles;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Эффекты в мире из пакетов {@link FxNet.WorldFx}: лучи, ветвистые молнии, взрывные сферы, купола, чёрные дыры,
 * ударные кольца, столпы света, смерчи, летящие серпы, спиральные лучи, барьеры, кометы и вспышки экрана.
 * Всё — слоёная светящаяся геометрия: широкое мягкое свечение, цветное тело с бегущей текстурой и белая
 * сердцевина; формы растут и гаснут по кривым, а не линейно.
 */
public final class WorldFxRenderer {
	private static final float TAU = (float) (Math.PI * 2);
	/** Дальше этого эффекты не рисуются. */
	public static final double MAX_DISTANCE = 256.0;
	private static final int MAX_FX = 256;
	private static final int KINDS = 13;

	/** Живой эффект. */
	public static final class Fx {
		public final int kind;
		public final Vec3 a, b;
		public final float r0, r1;
		public final int color, light, ticks, seed;
		public final float start;
		/** Для галереи: заморозить возраст (≥ 0). */
		public float fixedAge = -1;

		public Fx(WorldFx p, float start) {
			this.kind = p.kind();
			this.a = p.a();
			this.b = p.b();
			this.r0 = p.r0();
			this.r1 = p.r1();
			this.color = p.color();
			this.light = p.light();
			this.ticks = Math.max(1, p.ticks());
			this.seed = p.seed();
			this.start = start;
		}

		float age(float now) {
			return fixedAge >= 0 ? fixedAge : now - start;
		}
	}

	private static final List<Fx> ACTIVE = new ArrayList<>();
	private static final List<Fx> FLASHES = new ArrayList<>();

	private WorldFxRenderer() {}

	/** Сколько эффектов живо (включая вспышки экрана). */
	public static int activeCount() {
		return ACTIVE.size() + FLASHES.size();
	}

	/** Сколько эффектов вида {@code kind} живо. */
	public static int activeCount(int kind) {
		int n = 0;
		for (Fx f : kind == WorldFx.FLASH ? FLASHES : ACTIVE) if (f.kind == kind) n++;
		return n;
	}

	public static void clear() {
		ACTIVE.clear();
		FLASHES.clear();
	}

	/** Принять эффект (из пакета или из теста). */
	public static Fx add(WorldFx p) {
		Fx fx = new Fx(p, CircleRenderer.now(0));
		if (p.kind() == WorldFx.FLASH) {
			if (FLASHES.size() >= 16) FLASHES.remove(0);
			FLASHES.add(fx);
			return fx;
		}
		if (p.kind() < 0 || p.kind() >= KINDS) return fx;
		if (ACTIVE.size() >= MAX_FX) ACTIVE.remove(0);
		ACTIVE.add(fx);
		return fx;
	}

	// ---------------------------------------------------------------- тик (частицы) и кадр

	static void tick(ClientLevel level) {
		float now = CircleRenderer.now(0);
		Minecraft mc = Minecraft.getInstance();
		Vec3 cam = mc.gameRenderer.getMainCamera().position();
		var rnd = level.random;
		for (Iterator<Fx> it = FLASHES.iterator(); it.hasNext(); ) {
			Fx f = it.next();
			if (f.fixedAge < 0 && f.age(now) > f.ticks) it.remove();
		}
		for (Fx f : ACTIVE) {
			float age = f.age(now);
			if (age > f.ticks || f.a.distanceToSqr(cam) > 96 * 96) continue;
			float t = age / f.ticks;
			switch (f.kind) {
				case WorldFx.TORNADO -> {
					// Обломки и пыль кружат вверх по воронке.
					for (int i = 0; i < 3; i++) {
						float h = rnd.nextFloat() * f.r1;
						float r = funnel(f.r0, f.r1, h) * (0.8F + 0.4F * rnd.nextFloat());
						float ang = rnd.nextFloat() * TAU;
						double x = f.a.x + Math.cos(ang) * r, z = f.a.z + Math.sin(ang) * r;
						double vx = -Math.sin(ang) * 0.35, vz = Math.cos(ang) * 0.35;
						level.addParticle(MagicParticles.mote(rnd.nextBoolean() ? f.color : f.light, 0.08F + 0.06F * rnd.nextFloat(), 14), x,
								f.a.y + h, z, vx, 0.12, vz);
					}
				}
				case WorldFx.CORE -> {
					// Свет падает в чёрную дыру по спирали.
					for (int i = 0; i < 2; i++) {
						float ang = rnd.nextFloat() * TAU;
						float r = f.r1 * (1.1F + 0.6F * rnd.nextFloat());
						double x = f.a.x + Math.cos(ang) * r, z = f.a.z + Math.sin(ang) * r, y = f.a.y + (rnd.nextFloat() - 0.5F) * f.r0;
						double vx = (f.a.x - x) * 0.06 - Math.sin(ang) * 0.12, vz = (f.a.z - z) * 0.06 + Math.cos(ang) * 0.12;
						level.addParticle(MagicParticles.mote(f.light, 0.1F, 16), x, y, z, vx, (f.a.y - y) * 0.06, vz);
					}
				}
				case WorldFx.COMET -> {
					if (t < 1) {
						Vec3 head = f.a.lerp(f.b, Math.pow(t, 1.6));
						for (int i = 0; i < 2; i++) {
							level.addParticle(MagicParticles.spark(rnd.nextBoolean() ? f.color : f.light, 0.15F + 0.1F * f.r0, 12),
									head.x + (rnd.nextDouble() - 0.5) * f.r0, head.y + (rnd.nextDouble() - 0.5) * f.r0,
									head.z + (rnd.nextDouble() - 0.5) * f.r0, 0, 0.02, 0);
						}
					}
				}
				default -> {
				}
			}
		}
	}

	/** Нарисовать все эффекты и убрать догоревшие. */
	static void render(CircleBatch b) {
		float now = b.time;
		for (Iterator<Fx> it = ACTIVE.iterator(); it.hasNext(); ) {
			Fx f = it.next();
			float age = f.age(now);
			if (f.fixedAge < 0 && age > f.ticks) {
				it.remove();
				continue;
			}
			double d2 = Math.min(f.a.distanceToSqr(b.camX, b.camY, b.camZ), f.b.distanceToSqr(b.camX, b.camY, b.camZ));
			if (d2 > MAX_DISTANCE * MAX_DISTANCE && !segmentNear(f, b)) continue;
			float t = Math.max(0, Math.min(1, age / f.ticks));
			switch (f.kind) {
				case WorldFx.BEAM -> beam(b, f, age, t);
				case WorldFx.LIGHTNING -> lightning(b, f, age, t);
				case WorldFx.SPHERE -> sphere(b, f, age, t);
				case WorldFx.DOME -> dome(b, f, age, t);
				case WorldFx.CORE -> core(b, f, age, t);
				case WorldFx.RING -> ring(b, f, age, t);
				case WorldFx.PILLAR -> pillar(b, f, age, t);
				case WorldFx.TORNADO -> tornado(b, f, age, t);
				case WorldFx.SLASH -> slash(b, f, age, t);
				case WorldFx.HELIX -> helix(b, f, age, t);
				case WorldFx.BARRIER -> barrier(b, f, age, t);
				case WorldFx.COMET -> comet(b, f, age, t);
				default -> {
				}
			}
		}
	}

	private static boolean segmentNear(Fx f, CircleBatch b) {
		if (f.kind != WorldFx.BEAM && f.kind != WorldFx.LIGHTNING && f.kind != WorldFx.HELIX && f.kind != WorldFx.COMET) return false;
		Vec3 c = new Vec3(b.camX, b.camY, b.camZ);
		Vec3 d = f.b.subtract(f.a);
		double l2 = d.lengthSqr();
		double k = l2 < 1.0E-6 ? 0 : Mth.clamp(c.subtract(f.a).dot(d) / l2, 0, 1);
		return f.a.add(d.scale(k)).distanceToSqr(c) < MAX_DISTANCE * MAX_DISTANCE;
	}

	// ---------------------------------------------------------------- огибающие

	private static float easeOut(float x) {
		float k = 1 - Math.max(0, Math.min(1, x));
		return 1 - k * k * k;
	}

	private static float easeOutBack(float x) {
		x = Math.max(0, Math.min(1, x));
		float c1 = 1.70158F, c3 = c1 + 1;
		return 1 + c3 * (float) Math.pow(x - 1, 3) + c1 * (float) Math.pow(x - 1, 2);
	}

	/** Вход за {@code in}, держится, гаснет за последние {@code out} доли жизни. */
	private static float env(float t, float in, float out) {
		float a = in <= 0 ? 1 : Math.min(1, t / in);
		float b = out <= 0 ? 1 : Math.min(1, (1 - t) / out);
		return Math.max(0, Math.min(a, b));
	}

	private static int argb(int rgb, float a) {
		return CircleRenderer.argb(rgb, a);
	}

	private static int mix(int a, int b, float t) {
		return CircleRenderer.mix(a, b, t);
	}

	private static float[] tmp = new float[64 * 3];
	private static float[] tmpHalf = new float[64];
	private static int[] tmpCol = new int[64];

	private static void ensure(int n) {
		if (tmpHalf.length >= n) return;
		tmp = new float[n * 3];
		tmpHalf = new float[n];
		tmpCol = new int[n];
	}

	/** Точки отрезка a→b (относительно камеры) в {@link #tmp}, n штук. */
	private static void line(CircleBatch b, Vec3 a, Vec3 e, int n) {
		ensure(n);
		for (int i = 0; i < n; i++) {
			double s = i / (double) (n - 1);
			tmp[3 * i] = b.rx(a.x + (e.x - a.x) * s);
			tmp[3 * i + 1] = b.ry(a.y + (e.y - a.y) * s);
			tmp[3 * i + 2] = b.rz(a.z + (e.z - a.z) * s);
		}
	}

	/** Оси плоскости, перпендикулярной направлению d. */
	private static void perp(Vec3 d, float[] ex, float[] ey) {
		CircleRenderer.Params p = new CircleRenderer.Params().at(Vec3.ZERO, d);
		CircleRenderer.basis(p, ex, ey);
	}

	// ---------------------------------------------------------------- луч

	private static void beam(CircleBatch b, Fx f, float age, float t) {
		Vec3 d = f.b.subtract(f.a);
		if (d.lengthSqr() < 0.0025) return;
		float grow = easeOut(age / 3.0F);
		Vec3 end = f.a.add(d.scale(grow));
		float w = f.r0 * (age < 3 ? 0.4F + 0.9F * age / 3.0F : 1.0F + 0.25F * (float) Math.exp(-(age - 3) / 2.0F));
		float fade = t > 0.7F ? 1 - (t - 0.7F) / 0.3F : 1;
		w *= 0.25F + 0.75F * fade;
		beamShape(b, f.a, end, w, Math.max(0, Math.min(1, f.r1)), f.color, f.light, age, Math.min(1, fade * 1.4F), grow);
	}

	/**
	 * Объёмный луч из {@code from} в {@code to}: мягкое свечение, цветное тело с бегущими полосами, белая сердцевина,
	 * кольца-импульсы и вспышки на концах. {@code taper} 0…1 — сужение к концу. Нужен и для лучей потоков у мага.
	 */
	public static void beamShape(CircleBatch b, Vec3 from, Vec3 to, float w, float taper, int color, int light, float age, float a, float grow) {
		Vec3 d = to.subtract(from);
		float len = (float) d.length();
		if (len < 0.05F || a <= 0.01F) return;
		w *= 1 + 0.08F * (float) Math.sin(age * 1.9F);
		int n = Math.max(2, Math.min(64, (int) (len / 1.2F) + 2));
		line(b, from, to, n);
		float[] p = tmp.clone();
		float[] hs = new float[n];
		for (int i = 0; i < n; i++) hs[i] = (1 - 0.85F * taper * i / (float) (n - 1)) * w * 3.6F;
		b.ribbon(b.glow, p, n, hs, 0, null, argb(color, 0.42F * a), 0, 0);
		for (int i = 0; i < n; i++) hs[i] = (1 - 0.85F * taper * i / (float) (n - 1)) * w * 1.35F;
		b.ribbon(b.streak, p, n, hs, 0, null, argb(color, 0.9F * a), -age * 0.18F, 0.22F);
		b.ribbon(b.streak, p, n, hs, 0, null, argb(mix(color, light, 0.5F), 0.5F * a), age * 0.11F, 0.13F);
		for (int i = 0; i < n; i++) hs[i] = (1 - 0.85F * taper * i / (float) (n - 1)) * w * 0.42F;
		b.ribbon(b.line, p, n, hs, 0, null, argb(mix(light, 0xFFFFFF, 0.6F), a), 0, 0);
		// Кольца-импульсы, бегущие по лучу.
		float[] ex = new float[3], ey = new float[3];
		perp(d, ex, ey);
		for (int k = 0; k < 3; k++) {
			float s = (age * 0.09F + k / 3.0F) % 1.0F;
			Vec3 at = from.add(d.scale(s));
			float rr = w * (2.0F + 1.2F * (float) Math.sin(s * Math.PI)) * (1 - 0.85F * taper * s);
			CircleRenderer.annulus(b, b.wave, b.rx(at.x), b.ry(at.y), b.rz(at.z), ex, ey, rr, rr * 0.35F, 24,
					argb(light, 0.55F * a * (float) Math.sin(s * Math.PI)));
		}
		// Вспышки у начала и в точке удара.
		b.billboard(b.flare, b.rx(from.x), b.ry(from.y), b.rz(from.z), w * 5, age * 0.2F, argb(light, 0.8F * a));
		b.billboard(b.flare, b.rx(to.x), b.ry(to.y), b.rz(to.z), w * 7 * (1 - 0.85F * taper), -age * 0.15F, argb(light, a));
		b.billboard(b.spark, b.rx(to.x), b.ry(to.y), b.rz(to.z), w * 4.5F, 0, argb(color, 0.7F * a));
	}

	// ---------------------------------------------------------------- молния

	/** Ломаная молнии: середина смещается случайно на каждом уровне (половина размаха). */
	private static int bolt(Random r, Vec3 a, Vec3 e, int depth, float amp, Vec3[] out) {
		int n = (1 << depth) + 1;
		out[0] = a;
		out[n - 1] = e;
		Vec3 d = e.subtract(a);
		Vec3 p1 = d.cross(new Vec3(0, 1, 0));
		if (p1.lengthSqr() < 1.0E-6) p1 = d.cross(new Vec3(1, 0, 0));
		p1 = p1.normalize();
		Vec3 p2 = d.cross(p1).normalize();
		float span = amp;
		for (int step = n - 1; step > 1; step /= 2) {
			for (int i = step / 2; i < n; i += step) {
				Vec3 m = out[i - step / 2].add(out[i + step / 2]).scale(0.5);
				double o1 = (r.nextFloat() - 0.5F) * 2 * span, o2 = (r.nextFloat() - 0.5F) * 2 * span;
				out[i] = m.add(p1.scale(o1)).add(p2.scale(o2));
			}
			span *= 0.55F;
		}
		return n;
	}

	private static void lightning(CircleBatch b, Fx f, float age, float t) {
		Vec3 d = f.b.subtract(f.a);
		float len = (float) d.length();
		if (len < 0.05F) return;
		// Форма перестраивается каждые 2 тика, яркость мерцает (повторные удары).
		int frame = (int) (age / 2);
		Random r = new Random(f.seed * 31L + frame * 7919L);
		float flick = 0.55F + 0.45F * (float) Math.abs(Math.sin(age * 2.7F + f.seed));
		float a = (float) Math.pow(1 - t, 0.6) * flick;
		if (a <= 0.01F) return;
		float w = f.r0;
		Vec3[] pts = new Vec3[33];
		int n = bolt(r, f.a, f.b, 5, len * 0.12F, pts);
		strand(b, pts, n, w, f, a);
		int branches = 2 + r.nextInt(3) + (int) Math.min(4, len / 12);
		for (int k = 0; k < branches; k++) {
			int from = 4 + r.nextInt(n - 10);
			Vec3 start = pts[from];
			Vec3 dir = pts[from + 2].subtract(start).normalize();
			Vec3 side = new Vec3(r.nextFloat() - 0.5, r.nextFloat() - 0.5, r.nextFloat() - 0.5).normalize();
			Vec3 bd = dir.add(side.scale(0.9)).normalize();
			float bl = len * (0.12F + 0.22F * r.nextFloat());
			Vec3[] bp = new Vec3[17];
			int bn = bolt(r, start, start.add(bd.scale(bl)), 4, bl * 0.18F, bp);
			strand(b, bp, bn, w * 0.55F, f, a * 0.85F);
		}
		b.billboard(b.flare, b.rx(f.b.x), b.ry(f.b.y), b.rz(f.b.z), w * 22, frame * 0.7F, argb(f.light, a));
		b.billboard(b.spark, b.rx(f.b.x), b.ry(f.b.y), b.rz(f.b.z), w * 14, 0, argb(f.color, 0.6F * a));
		b.billboard(b.flare, b.rx(f.a.x), b.ry(f.a.y), b.rz(f.a.z), w * 10, frame * 0.5F, argb(f.light, 0.6F * a));
	}

	private static void strand(CircleBatch b, Vec3[] pts, int n, float w, Fx f, float a) {
		ensure(n);
		for (int i = 0; i < n; i++) {
			tmp[3 * i] = b.rx(pts[i].x);
			tmp[3 * i + 1] = b.ry(pts[i].y);
			tmp[3 * i + 2] = b.rz(pts[i].z);
		}
		float[] t2 = tmp.clone();
		b.beads(b.spark, t2, n, null, w * 6.0F, argb(f.color, 0.55F * a));
		b.segments(b.line, t2, n, null, w * 1.6F, null, argb(mix(f.color, f.light, 0.4F), 0.95F * a));
		b.segments(b.line, t2, n, null, w * 0.55F, null, argb(0xFFFFFF, a));
	}

	// ---------------------------------------------------------------- оболочки

	/**
	 * Сфера (или верхняя полусфера) с «френелем»: края ярче середины, текстура бежит по u/v.
	 * {@code pulse} ≥ 0 — яркая полоса на этой широте (0 — низ, 1 — верх).
	 */
	private static void shell(CircleBatch b, CircleBatch.Buf buf, float cx, float cy, float cz, float r, int lat, int lon, boolean hemi, int color,
			float aCenter, float aEdge, float uRep, float vRep, float uOff, float vOff, float pulse, float squash) {
		int rows = lat + 1, cols = lon + 1;
		float[] vx = new float[rows * cols * 3];
		int[] vc = new int[rows * cols];
		float[] uu = new float[rows * cols], vv = new float[rows * cols];
		for (int i = 0; i < rows; i++) {
			float s = i / (float) lat;
			float th = hemi ? s * (float) Math.PI / 2 : s * (float) Math.PI - (float) Math.PI / 2;
			float ny = (float) Math.sin(th), nr = (float) Math.cos(th);
			for (int j = 0; j < cols; j++) {
				float ph = TAU * j / lon;
				float nx = nr * (float) Math.cos(ph), nz = nr * (float) Math.sin(ph);
				int k = i * cols + j;
				float x = cx + nx * r, y = cy + ny * r * squash, z = cz + nz * r;
				vx[3 * k] = x;
				vx[3 * k + 1] = y;
				vx[3 * k + 2] = z;
				float dl = (float) Math.sqrt(x * x + y * y + z * z);
				float fres = dl < 1.0E-4F ? 1 : 1 - Math.abs((nx * x + ny * y + nz * z) / dl);
				float al = aCenter + (aEdge - aCenter) * fres * fres;
				if (pulse >= 0) {
					float h = hemi ? s : (ny + 1) / 2;
					float dp = (h - pulse) / 0.07F;
					al *= 1 + 1.6F * (float) Math.exp(-dp * dp);
				}
				vc[k] = argb(color, al);
				uu[k] = uRep * j / lon + uOff;
				vv[k] = vRep * s + vOff;
			}
		}
		for (int i = 0; i < lat; i++) {
			for (int j = 0; j < lon; j++) {
				int k0 = i * cols + j, k1 = k0 + 1, k2 = k0 + cols + 1, k3 = k0 + cols;
				buf.v(vx[3 * k0], vx[3 * k0 + 1], vx[3 * k0 + 2], uu[k0], vv[k0], vc[k0]);
				buf.v(vx[3 * k1], vx[3 * k1 + 1], vx[3 * k1 + 2], uu[k1], vv[k1], vc[k1]);
				buf.v(vx[3 * k2], vx[3 * k2 + 1], vx[3 * k2 + 2], uu[k2], vv[k2], vc[k2]);
				buf.v(vx[3 * k3], vx[3 * k3 + 1], vx[3 * k3 + 2], uu[k3], vv[k3], vc[k3]);
			}
		}
	}

	/** Сегменты по дистанции: крупная сфера рядом — подробнее. */
	private static int segs(CircleBatch b, float x, float y, float z, float r, int lo, int hi) {
		float d = Math.max(1, (float) Math.sqrt(x * x + y * y + z * z));
		float px = r * b.pxPerBlock / d;
		return Math.max(lo, Math.min(hi, (int) (px / 7)));
	}

	private static final float[] UP_X = {1, 0, 0}, UP_Y = {0, 0, -1};

	private static void sphere(CircleBatch b, Fx f, float age, float t) {
		float r = f.r0 + (f.r1 - f.r0) * easeOut(t * 1.2F);
		float a = (float) Math.pow(1 - t, 1.2);
		float x = b.rx(f.a.x), y = b.ry(f.a.y), z = b.rz(f.a.z);
		int lon = segs(b, x, y, z, r, 20, 48);
		// Оболочка: бегущий шум, края (силуэт) ярче середины; поверх — ровное свечение кромки.
		// Горячая середина: чем раньше, тем ярче и белее.
		float hot = Math.max(0, 1 - t * 1.6F);
		shell(b, b.noise, x, y, z, r, lon / 2, lon, false, mix(f.color, f.light, 0.4F + 0.4F * hot), 0.25F * a, 1.0F * a, 2, 1, age * 0.02F,
				-age * 0.03F, -1, 1);
		shell(b, b.dot, x, y, z, r * 1.02F, lon / 2, lon, false, f.light, 0.05F * a, 0.7F * a, 0, 0, 0.5F, 0.5F, -1, 1);
		shell(b, b.dot, x, y, z, r * 0.8F, lon / 3, lon, false, mix(f.light, 0xFFFFFF, hot), 0.35F * hot, 0.05F * hot, 0, 0, 0.5F, 0.5F, -1, 1);
		if (t < 0.35F) {
			float k = 1 - t / 0.35F;
			b.billboard(b.flare, x, y, z, r * 1.6F, 0.4F, argb(f.light, k));
			b.billboard(b.spark, x, y, z, r * 1.1F, 0, argb(0xFFFFFF, k * 0.9F));
		}
		// Лучи взрыва: острые клинья света из центра, быстро гаснут.
		if (t < 0.5F) {
			float k = 1 - t / 0.5F;
			Random rnd = new Random(f.seed);
			float[] p = new float[9];
			float[] hs = new float[3];
			int[] cs = new int[3];
			for (int i = 0; i < 14; i++) {
				double th = rnd.nextDouble() * Math.PI * 2, ph = Math.acos(rnd.nextDouble() * 1.6 - 0.6);
				float dx = (float) (Math.sin(ph) * Math.cos(th)), dy = (float) Math.cos(ph), dz = (float) (Math.sin(ph) * Math.sin(th));
				float len = r * (1.1F + 0.7F * rnd.nextFloat());
				for (int j = 0; j < 3; j++) {
					float s0 = j / 2.0F;
					p[3 * j] = x + dx * len * s0;
					p[3 * j + 1] = y + dy * len * s0;
					p[3 * j + 2] = z + dz * len * s0;
					hs[j] = r * 0.07F * (1 - s0) + 0.02F;
					cs[j] = argb(mix(f.light, 0xFFFFFF, 0.4F), k * (1 - 0.7F * s0));
				}
				b.ribbon(b.glow, p, 3, hs, 0, cs, 0, 0, 0);
			}
		}
		// Волна по земле под взрывом.
		CircleRenderer.annulus(b, b.wave, x, y - r * 0.02F, z, UP_X, UP_Y, r * 1.3F, r * 0.35F, lon, argb(f.light, 0.8F * a));
	}

	private static void dome(CircleBatch b, Fx f, float age, float t) {
		float r = f.r0 * easeOutBack(t / 0.12F);
		float e = env(t, 0.05F, 0.25F);
		if (r <= 0.01F || e <= 0) return;
		float x = b.rx(f.a.x), y = b.ry(f.a.y), z = b.rz(f.a.z);
		int lon = segs(b, x, y, z, r, 14, 32);
		float pulse = (age * 0.04F) % 1.0F;
		shell(b, b.noise, x, y, z, r, lon / 3, lon, true, f.color, 0.15F * e, 1.0F * e, 3, 1.5F, age * 0.01F, -age * 0.04F, pulse, 1);
		shell(b, b.streak, x, y, z, r * 0.98F, lon / 3, lon, true, f.light, 0.06F * e, 0.5F * e, 4, 1, -age * 0.006F, -age * 0.06F, -1, 1);
		shell(b, b.dot, x, y, z, r * 1.01F, lon / 3, lon, true, f.light, 0.0F, 0.45F * e, 0, 0, 0.5F, 0.5F, -1, 1);
		CircleRenderer.annulus(b, b.wave, x, y + 0.05F, z, UP_X, UP_Y, r * 1.06F, r * 0.22F, lon, argb(f.light, e));
		CircleRenderer.planeQuad(b, b.disc, x, y + 0.04F, z, UP_X, UP_Y, r * 1.1F, 0, argb(f.color, 0.35F * e));
		if (t < 0.15F) b.billboard(b.flare, x, y + r * 0.3F, z, r * 1.4F, 0, argb(f.light, 1 - t / 0.15F));
	}

	private static void core(CircleBatch b, Fx f, float age, float t) {
		float grow = easeOutBack(t / 0.1F);
		float shrink = t > 0.85F ? 1 - (t - 0.85F) / 0.15F : 1;
		float r = f.r0 * grow * shrink;
		float ring = f.r1 * Math.min(1, t / 0.15F) * (0.6F + 0.4F * shrink);
		if (r <= 0.01F) return;
		float x = b.rx(f.a.x), y = b.ry(f.a.y), z = b.rz(f.a.z);
		// Наклонный аккреционный диск: дальняя половина — до чёрного шара, ближняя — поверх.
		Random rnd = new Random(f.seed);
		float tilt = 0.35F + 0.25F * rnd.nextFloat();
		float yaw = rnd.nextFloat() * TAU;
		float[] ex = {(float) Math.cos(yaw), 0, (float) Math.sin(yaw)};
		float[] ey = {-(float) Math.sin(yaw) * (float) Math.cos(tilt), (float) Math.sin(tilt), (float) Math.cos(yaw) * (float) Math.cos(tilt)};
		float a = Math.min(1, t / 0.1F) * (0.4F + 0.6F * shrink);
		disk(b, x, y, z, ex, ey, r * 1.25F, ring, age, f, a, false);
		shell(b, b.shadeTop, x, y, z, r, 10, 18, false, 0x000000, 0.97F, 0.97F, 0, 0, 0.5F, 0.5F, -1, 1);
		disk(b, x, y, z, ex, ey, r * 1.25F, ring, age, f, a, true);
		// Кольцо фотонов и ореол вокруг силуэта (лицом к камере).
		float[] cl = {-b.lx, -b.ly, -b.lz}, cu = {b.ux, b.uy, b.uz};
		CircleRenderer.annulus(b, b.waveTop, x, y, z, cl, cu, r * 1.12F, r * 0.2F, 40, argb(f.light, 0.95F * a));
		halo(b, b.dotTop, x, y, z, cl, cu, r * 1.05F, r * 2.6F, 40, argb(f.color, 0.45F * a), argb(f.color, 0));
		if (t > 0.85F) b.billboard(b.flare, x, y, z, f.r1 * 1.5F * (1 - shrink), age * 0.1F, argb(f.light, 1 - shrink));
	}

	/** Диск в полярных координатах (шум кружится), только ближняя или дальняя к камере половина. */
	private static void disk(CircleBatch b, float x, float y, float z, float[] ex, float[] ey, float rIn, float rOut, float age, Fx f, float a,
			boolean near) {
		int segs = 48;
		float spin = age * 0.03F;
		for (int i = 0; i < segs; i++) {
			float a0 = TAU * i / segs, a1 = TAU * (i + 1) / segs;
			float mc = (float) Math.cos((a0 + a1) / 2), ms = (float) Math.sin((a0 + a1) / 2);
			float mx = ex[0] * mc + ey[0] * ms, my = ex[1] * mc + ey[1] * ms, mz = ex[2] * mc + ey[2] * ms;
			// Ближняя половина: направление от центра смотрит на камеру (камера в начале координат).
			boolean isNear = -(mx * x + my * y + mz * z) > 0;
			if (isNear != near) continue;
			CircleBatch.Buf buf = near ? b.noiseTop : b.noise;
			float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
			float d0x = ex[0] * c0 + ey[0] * s0, d0y = ex[1] * c0 + ey[1] * s0, d0z = ex[2] * c0 + ey[2] * s0;
			float d1x = ex[0] * c1 + ey[0] * s1, d1y = ex[1] * c1 + ey[1] * s1, d1z = ex[2] * c1 + ey[2] * s1;
			float side = 0.7F + 0.3F * mc;
			int cin = argb(f.light, Math.min(1, 1.2F * a * side)), cout = argb(f.color, 0);
			float u0 = 3.0F * i / segs + spin, u1 = 3.0F * (i + 1) / segs + spin;
			buf.v(x + d0x * rIn, y + d0y * rIn, z + d0z * rIn, u0, 0, cin);
			buf.v(x + d0x * rOut, y + d0y * rOut, z + d0z * rOut, u0, 1, cout);
			buf.v(x + d1x * rOut, y + d1y * rOut, z + d1z * rOut, u1, 1, cout);
			buf.v(x + d1x * rIn, y + d1y * rIn, z + d1z * rIn, u1, 0, cin);
			// Второй слой — ярче у внутреннего края, крутится быстрее.
			int cin2 = argb(0xFFFFFF, 0.8F * a * side), cmid = argb(f.light, 0);
			float rm = rIn + (rOut - rIn) * 0.35F;
			float w0 = u0 * 2 + spin * 3, w1 = u1 * 2 + spin * 3;
			buf.v(x + d0x * rIn, y + d0y * rIn, z + d0z * rIn, w0, 0.3F, cin2);
			buf.v(x + d0x * rm, y + d0y * rm, z + d0z * rm, w0, 0.7F, cmid);
			buf.v(x + d1x * rm, y + d1y * rm, z + d1z * rm, w1, 0.7F, cmid);
			buf.v(x + d1x * rIn, y + d1y * rIn, z + d1z * rIn, w1, 0.3F, cin2);
		}
	}

	/** Плавный ореол-кольцо: цвет от {@code cIn} у радиуса rIn до {@code cOut} у rOut (текстура однотонная). */
	private static void halo(CircleBatch b, CircleBatch.Buf buf, float x, float y, float z, float[] ex, float[] ey, float rIn, float rOut, int segs,
			int cIn, int cOut) {
		float pc = 1, ps = 0;
		for (int i = 1; i <= segs; i++) {
			float ang = TAU * i / segs;
			float c = (float) Math.cos(ang), s = (float) Math.sin(ang);
			float ax = ex[0] * pc + ey[0] * ps, ay = ex[1] * pc + ey[1] * ps, az = ex[2] * pc + ey[2] * ps;
			float bx = ex[0] * c + ey[0] * s, by = ex[1] * c + ey[1] * s, bz = ex[2] * c + ey[2] * s;
			buf.v(x + ax * rIn, y + ay * rIn, z + az * rIn, 0.5F, 0.5F, cIn);
			buf.v(x + ax * rOut, y + ay * rOut, z + az * rOut, 0.5F, 0.5F, cOut);
			buf.v(x + bx * rOut, y + by * rOut, z + bz * rOut, 0.5F, 0.5F, cOut);
			buf.v(x + bx * rIn, y + by * rIn, z + bz * rIn, 0.5F, 0.5F, cIn);
			pc = c;
			ps = s;
		}
	}

	// ---------------------------------------------------------------- кольцо, столп

	private static void ring(CircleBatch b, Fx f, float age, float t) {
		float r = f.r0 + (f.r1 - f.r0) * easeOut(t);
		float a = (float) Math.pow(1 - t, 1.3);
		float[] ex = new float[3], ey = new float[3];
		perp(f.b.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : f.b, ex, ey);
		float x = b.rx(f.a.x), y = b.ry(f.a.y), z = b.rz(f.a.z);
		int segs = segs(b, x, y, z, r, 24, 72);
		float width = Math.min(r * (0.1F + 0.2F * (1 - t)), 3.0F);
		CircleRenderer.annulus(b, b.wave, x, y, z, ex, ey, r, width, segs, argb(f.color, a));
		CircleRenderer.annulus(b, b.wave, x, y, z, ex, ey, r * 0.99F, Math.min(r * 0.05F, 0.8F), segs, argb(mix(f.color, f.light, 0.6F), a));
		CircleRenderer.annulus(b, b.wave, x, y, z, ex, ey, r * 0.8F, Math.min(r * 0.06F, 1.0F), segs, argb(mix(f.color, f.light, 0.3F), 0.4F * a));
		// Невысокая стена света у наземной волны.
		if (Math.abs(f.b.normalize().y) > 0.8) {
			float h = Math.min(r * 0.1F, 1.5F) * (1 - t);
			wall(b, b.glow, x, y, z, r, h, segs, argb(f.color, 0.3F * a), argb(f.color, 0));
		}
		if (t < 0.2F) CircleRenderer.planeQuad(b, b.disc, x, y, z, ex, ey, r * 1.1F, 0, argb(f.light, (1 - t / 0.2F) * 0.6F));
	}

	/** Вертикальная полоса-стена по кругу радиуса r высотой h. */
	private static void wall(CircleBatch b, CircleBatch.Buf buf, float x, float y, float z, float r, float h, int segs, int cBottom, int cTop) {
		float pc = 1, ps = 0;
		for (int i = 1; i <= segs; i++) {
			float ang = TAU * i / segs;
			float c = (float) Math.cos(ang), s = (float) Math.sin(ang);
			buf.v(x + pc * r, y, z + ps * r, 0.5F, 0.5F, cBottom);
			buf.v(x + c * r, y, z + s * r, 0.5F, 0.5F, cBottom);
			buf.v(x + c * r, y + h, z + s * r, 0.5F, 0, cTop);
			buf.v(x + pc * r, y + h, z + ps * r, 0.5F, 0, cTop);
			pc = c;
			ps = s;
		}
	}

	private static void pillar(CircleBatch b, Fx f, float age, float t) {
		float H = f.r1;
		float grow = easeOut(age / 4.0F);
		float w = f.r0 * (t < 0.1F ? 0.2F + 1.1F * easeOutBack(t / 0.1F) : 1.0F) * (t > 0.65F ? 1 - (t - 0.65F) / 0.35F : 1);
		w *= 1 + 0.07F * (float) Math.sin(age * 2.3F);
		if (w <= 0.01F) return;
		float a = env(t, 0.03F, 0.3F);
		int n = Math.max(2, Math.min(48, (int) (H / 2) + 2));
		Vec3 top = f.a.add(0, H * grow, 0);
		line(b, f.a, top, n);
		float[] p = tmp.clone();
		float[] hs = new float[n];
		int[] cs = new int[n];
		for (int i = 0; i < n; i++) {
			float s = i / (float) (n - 1);
			hs[i] = w * 3.0F * (1 + 0.3F * (1 - s));
			cs[i] = argb(f.color, 0.4F * a * Math.min(1, (1 - s) * 4));
		}
		b.ribbon(b.glow, p, n, hs, 0, cs, 0, 0, 0);
		for (int i = 0; i < n; i++) {
			float s = i / (float) (n - 1);
			hs[i] = w * 1.25F;
			cs[i] = argb(f.color, 0.95F * a * Math.min(1, (1 - s) * 5));
		}
		b.ribbon(b.streak, p, n, hs, 0, cs, 0, age * 0.25F, -0.12F);
		b.ribbon(b.streak, p, n, hs, 0, cs, 0, age * 0.17F + 0.5F, -0.07F);
		for (int i = 0; i < n; i++) {
			float s = i / (float) (n - 1);
			hs[i] = w * 0.4F;
			cs[i] = argb(mix(f.light, 0xFFFFFF, 0.6F), a * Math.min(1, (1 - s) * 5));
		}
		b.ribbon(b.line, p, n, hs, 0, cs, 0, 0, 0);
		float x = b.rx(f.a.x), y = b.ry(f.a.y), z = b.rz(f.a.z);
		// Пятно у основания, расходящееся кольцо и кольца, поднимающиеся по столпу.
		CircleRenderer.planeQuad(b, b.disc, x, y + 0.05F, z, UP_X, UP_Y, w * 3, 0, argb(f.color, 0.5F * a));
		float rr = w * (1.6F + 2.2F * easeOut(age / 10.0F));
		CircleRenderer.annulus(b, b.wave, x, y + 0.06F, z, UP_X, UP_Y, rr, rr * 0.18F, 40,
				argb(mix(f.color, f.light, 0.4F), 0.7F * a * Math.max(0, 1 - age / 14.0F)));
		for (int k = 0; k < 3; k++) {
			float s = (age * 0.05F + k / 3.0F) % 1.0F;
			if (s > grow) continue;
			float ry = y + H * s;
			float rw = w * (1.8F - 0.6F * s);
			CircleRenderer.annulus(b, b.wave, x, ry, z, UP_X, UP_Y, rw, rw * 0.35F, 28, argb(f.light, 0.6F * a * (1 - s)));
		}
		if (t < 0.12F) b.billboard(b.flare, x, y + 0.5F, z, w * 8, 0, argb(f.light, 1 - t / 0.12F));
	}

	// ---------------------------------------------------------------- смерч

	/** Радиус воронки на высоте h: узко внизу, широко вверху. */
	private static float funnel(float r0, float H, float h) {
		float s = Math.max(0, Math.min(1, h / Math.max(0.1F, H)));
		return r0 * (0.18F + 0.82F * (float) Math.pow(s, 0.85));
	}

	private static void tornado(CircleBatch b, Fx f, float age, float t) {
		float H = f.r1 * easeOut(t / 0.15F);
		float a = env(t, 0.08F, 0.2F);
		if (H <= 0.1F || a <= 0) return;
		float x = b.rx(f.a.x), y = b.ry(f.a.y), z = b.rz(f.a.z);
		int rows = 16, cols = segs(b, x, y, z, f.r0, 18, 36);
		// Слои воронки: полосы ветра крутятся с разной скоростью, края ярче, верх и низ гаснут.
		for (int layer = 0; layer < 3; layer++) {
			float scale = 1 - 0.2F * layer;
			float spin = age * (0.06F + 0.035F * layer) * (layer % 2 == 0 ? 1 : -1);
			CircleBatch.Buf buf = layer == 2 ? b.noise : b.streak;
			int col = layer == 1 ? f.light : f.color;
			float base = layer == 2 ? 0.35F : 0.8F;
			lathe(b, buf, x, y, z, f.r0 * scale, H, rows, cols, col, base * a, spin, -age * 0.05F, f.seed + layer);
		}
		// Яркие вихри: короткие отрезки спирали на разной высоте, бегут по кругу и вверх.
		Random rnd = new Random(f.seed);
		int strands = 18;
		int n = 18;
		float[] p = new float[n * 3];
		float[] hs = new float[n];
		int[] cs = new int[n];
		for (int k = 0; k < strands; k++) {
			float h0 = rnd.nextFloat() * 0.7F;
			float span = 0.25F + 0.3F * rnd.nextFloat();
			float ph = rnd.nextFloat() * TAU + age * (0.3F + 0.15F * rnd.nextFloat());
			float rise = (age * 0.012F + rnd.nextFloat()) % 1.0F * 0.3F;
			for (int i = 0; i < n; i++) {
				float s = i / (float) (n - 1);
				float hh = Math.min(1, h0 + rise + span * s);
				float h = H * hh;
				float r = funnel(f.r0, H, h) * 1.06F;
				float ang = ph + s * 2.4F;
				p[3 * i] = x + (float) Math.cos(ang) * r;
				p[3 * i + 1] = y + h;
				p[3 * i + 2] = z + (float) Math.sin(ang) * r;
				hs[i] = f.r0 * 0.035F * (0.5F + hh);
				cs[i] = argb(mix(f.color, f.light, 0.6F), a * 0.95F * (float) Math.sin(Math.PI * s));
			}
			b.segments(b.line, p, n, hs, 0, cs, 0);
			for (int i = 0; i < n; i++) hs[i] *= 4;
			b.beads(b.spark, p, n, hs, 0, argb(mix(f.color, f.light, 0.4F), 0.5F * a));
		}
		CircleRenderer.annulus(b, b.wave, x, y + 0.05F, z, UP_X, UP_Y, f.r0 * 0.6F, f.r0 * 0.4F, 32, argb(f.color, 0.7F * a));
		CircleRenderer.planeQuad(b, b.disc, x, y + 0.04F, z, UP_X, UP_Y, f.r0 * 0.7F, 0, argb(f.color, 0.4F * a));
	}

	/** Поверхность вращения по профилю воронки; альфа — у силуэта ярче, к низу и верху гаснет. */
	private static void lathe(CircleBatch b, CircleBatch.Buf buf, float x, float y, float z, float r0, float H, int rows, int cols, int color,
			float alpha, float uOff, float vOff, int seed) {
		int rr = rows + 1, cc = cols + 1;
		float[] v = new float[rr * cc * 3];
		int[] c = new int[rr * cc];
		for (int i = 0; i < rr; i++) {
			float s = i / (float) rows;
			float h = H * s;
			float r = funnel(r0, H, h);
			float vert = (float) Math.sin(Math.PI * Math.min(1, s * 1.15F)) * (s < 0.05F ? s / 0.05F : 1);
			for (int j = 0; j < cc; j++) {
				float ang = TAU * j / cols;
				float nx = (float) Math.cos(ang), nz = (float) Math.sin(ang);
				int k = i * cc + j;
				float px = x + nx * r, py = y + h, pz = z + nz * r;
				v[3 * k] = px;
				v[3 * k + 1] = py;
				v[3 * k + 2] = pz;
				float dl = (float) Math.sqrt(px * px + py * py + pz * pz);
				float fres = 1 - Math.abs((nx * px + nz * pz) / Math.max(1.0E-4F, dl));
				c[k] = argb(color, alpha * vert * (0.25F + 0.75F * fres * fres));
			}
		}
		for (int i = 0; i < rows; i++) {
			for (int j = 0; j < cols; j++) {
				int k0 = i * cc + j, k1 = k0 + 1, k2 = k0 + cc + 1, k3 = k0 + cc;
				float u0 = 2.0F * j / cols + uOff, u1 = 2.0F * (j + 1) / cols + uOff;
				float v0 = 1.5F * i / rows + vOff, v1 = 1.5F * (i + 1) / rows + vOff;
				buf.v(v[3 * k0], v[3 * k0 + 1], v[3 * k0 + 2], u0, v0, c[k0]);
				buf.v(v[3 * k1], v[3 * k1 + 1], v[3 * k1 + 2], u1, v0, c[k1]);
				buf.v(v[3 * k2], v[3 * k2 + 1], v[3 * k2 + 2], u1, v1, c[k2]);
				buf.v(v[3 * k3], v[3 * k3 + 1], v[3 * k3 + 2], u0, v1, c[k3]);
			}
		}
	}

	// ---------------------------------------------------------------- серп, спираль, барьер, комета

	private static void slash(CircleBatch b, Fx f, float age, float t) {
		Vec3 dir = f.b.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : f.b.normalize();
		Vec3 side = dir.cross(new Vec3(0, 1, 0));
		if (side.lengthSqr() < 1.0E-6) side = new Vec3(1, 0, 0);
		side = side.normalize();
		Vec3 up = side.cross(dir).normalize();
		// Наклон серпа зависит от seed (косой удар).
		float roll = ((Math.floorMod(f.seed, 61)) - 30) * Mth.DEG_TO_RAD;
		Vec3 s2 = side.scale(Math.cos(roll)).add(up.scale(Math.sin(roll)));
		float a = env(t, 0.06F, 0.35F);
		float R = f.r0;
		for (int k = 0; k < 3; k++) {
			float tk = Math.max(0, t - 0.05F * k);
			Vec3 pos = f.a.add(dir.scale(f.r1 * easeOut(tk)));
			float ak = a * (k == 0 ? 1 : 0.35F / k);
			crescent(b, pos, dir, s2, R * (1 + 0.1F * k), ak, f);
		}
	}

	/** Полумесяц: дуга ±70° с яркой передней кромкой, толще в середине. */
	private static void crescent(CircleBatch b, Vec3 front, Vec3 fwd, Vec3 side, float R, float a, Fx f) {
		Vec3 center = front.subtract(fwd.scale(R));
		float cx = b.rx(center.x), cy = b.ry(center.y), cz = b.rz(center.z);
		int segs = 24;
		float max = 70 * Mth.DEG_TO_RAD;
		for (int pass = 0; pass < 2; pass++) {
			CircleBatch.Buf buf = pass == 0 ? b.glow : b.wave;
			float thick = pass == 0 ? 0.55F : 0.32F;
			int col = pass == 0 ? argb(f.color, 0.5F * a) : argb(mix(f.color, f.light, 0.5F), a);
			for (int i = 0; i < segs; i++) {
				float p0 = -max + 2 * max * i / segs, p1 = -max + 2 * max * (i + 1) / segs;
				float t0 = R * thick * (float) Math.pow(Math.cos(p0 / max * Math.PI / 2), 0.8);
				float t1 = R * thick * (float) Math.pow(Math.cos(p1 / max * Math.PI / 2), 0.8);
				Vec3 d0 = fwd.scale(Math.cos(p0)).add(side.scale(Math.sin(p0)));
				Vec3 d1 = fwd.scale(Math.cos(p1)).add(side.scale(Math.sin(p1)));
				float o = pass == 0 ? R * 0.08F : 0;
				float rOut0 = R + o, rOut1 = R + o;
				float rIn0 = R - t0 - o, rIn1 = R - t1 - o;
				buf.v(cx + (float) d0.x * rIn0, cy + (float) d0.y * rIn0, cz + (float) d0.z * rIn0, pass == 0 ? 0 : 0.35F, 0, col);
				buf.v(cx + (float) d0.x * rOut0, cy + (float) d0.y * rOut0, cz + (float) d0.z * rOut0, pass == 0 ? 0.5F : 1, 0, col);
				buf.v(cx + (float) d1.x * rOut1, cy + (float) d1.y * rOut1, cz + (float) d1.z * rOut1, pass == 0 ? 0.5F : 1, 1, col);
				buf.v(cx + (float) d1.x * rIn1, cy + (float) d1.y * rIn1, cz + (float) d1.z * rIn1, pass == 0 ? 0 : 0.35F, 1, col);
			}
		}
		// Кромка — лентой лицом к камере: серп виден и с ребра (например, сзади, когда он улетает).
		int n = segs + 1;
		float[] p = new float[n * 3];
		float[] hs = new float[n];
		int[] cs = new int[n];
		for (int i = 0; i < n; i++) {
			float ph = -max + 2 * max * i / segs;
			Vec3 d = fwd.scale(Math.cos(ph)).add(side.scale(Math.sin(ph)));
			float r = R * 0.97F;
			p[3 * i] = cx + (float) d.x * r;
			p[3 * i + 1] = cy + (float) d.y * r;
			p[3 * i + 2] = cz + (float) d.z * r;
			float k = (float) Math.cos(ph / max * Math.PI / 2);
			hs[i] = R * 0.06F * (0.3F + 0.7F * k);
			cs[i] = argb(mix(f.light, 0xFFFFFF, 0.4F), a * k);
		}
		b.ribbon(b.line, p, n, hs, 0, cs, 0, 0, 0);
		for (int i = 0; i < n; i++) {
			hs[i] *= 4;
			cs[i] = argb(f.color, ((cs[i] >>> 24) / 255.0F) * 0.6F);
		}
		b.ribbon(b.glow, p, n, hs, 0, cs, 0, 0, 0);
	}

	private static void helix(CircleBatch b, Fx f, float age, float t) {
		Vec3 d = f.b.subtract(f.a);
		float len = (float) d.length();
		if (len < 0.05F) return;
		float grow = easeOut(age / 4.0F);
		float fade = t > 0.7F ? 1 - (t - 0.7F) / 0.3F : 1;
		float a = Math.min(1, fade * 1.3F);
		float w = f.r0 * (0.4F + 0.6F * fade);
		float[] ex = new float[3], ey = new float[3];
		perp(d, ex, ey);
		float turns = len / 2.6F;
		int n = Math.max(16, Math.min(480, (int) (turns * grow * 28)));
		float[] p = new float[n * 3];
		float[] hs = new float[n];
		for (int strand = 0; strand < 2; strand++) {
			float ph = strand * (float) Math.PI + age * 0.4F;
			for (int i = 0; i < n; i++) {
				float s = grow * i / (n - 1);
				float ang = ph + s * turns * TAU;
				float rad = w * 3.0F * (0.6F + 0.4F * (float) Math.sin(Math.PI * Math.min(1, s * 4)));
				float ox = (ex[0] * (float) Math.cos(ang) + ey[0] * (float) Math.sin(ang)) * rad;
				float oy = (ex[1] * (float) Math.cos(ang) + ey[1] * (float) Math.sin(ang)) * rad;
				float oz = (ex[2] * (float) Math.cos(ang) + ey[2] * (float) Math.sin(ang)) * rad;
				p[3 * i] = b.rx(f.a.x + d.x * s) + ox;
				p[3 * i + 1] = b.ry(f.a.y + d.y * s) + oy;
				p[3 * i + 2] = b.rz(f.a.z + d.z * s) + oz;
				hs[i] = w * 0.5F;
			}
			int col = strand == 0 ? f.color : f.light;
			b.segments(b.line, p, n, hs, 0, null, argb(mix(col, 0xFFFFFF, 0.3F), a));
			b.beads(b.spark, p, n, null, w * 1.8F, argb(col, 0.5F * a));
		}
		Vec3 end = f.a.add(d.scale(grow));
		line(b, f.a, end, Math.max(2, Math.min(64, (int) (len * grow) + 2)));
		int m = Math.max(2, Math.min(64, (int) (len * grow) + 2));
		b.ribbon(b.streak, tmp.clone(), m, null, w * 0.9F, null, argb(f.color, 0.8F * a), -age * 0.2F, 0.2F);
		b.ribbon(b.line, tmp.clone(), m, null, w * 0.3F, null, argb(0xFFFFFF, a), 0, 0);
		b.billboard(b.flare, b.rx(end.x), b.ry(end.y), b.rz(end.z), w * 7, age * 0.2F, argb(f.light, a));
		b.billboard(b.flare, b.rx(f.a.x), b.ry(f.a.y), b.rz(f.a.z), w * 5, -age * 0.2F, argb(f.light, 0.8F * a));
	}

	private static void barrier(CircleBatch b, Fx f, float age, float t) {
		float r = f.r0 * easeOutBack(t / 0.1F);
		float e = env(t, 0.04F, 0.15F);
		if (r <= 0.01F || e <= 0) return;
		float x = b.rx(f.a.x), y = b.ry(f.a.y), z = b.rz(f.a.z);
		int lon = segs(b, x, y, z, r, 16, 36);
		float pulse = 1 - (age * 0.025F) % 1.0F;
		shell(b, b.hex, x, y, z, r, lon / 2, lon, false, mix(f.color, f.light, 0.25F), 0.16F * e, 1.0F * e, 8, 4, age * 0.004F, 0, pulse, 1);
		shell(b, b.noise, x, y, z, r * 1.005F, lon / 2, lon, false, f.light, 0.03F * e, 0.35F * e, 2, 1, -age * 0.01F, age * 0.01F, -1, 1);
		shell(b, b.dot, x, y, z, r * 1.01F, lon / 2, lon, false, f.color, 0.0F, 0.5F * e, 0, 0, 0.5F, 0.5F, -1, 1);
		if (t < 0.12F) {
			float k = 1 - t / 0.12F;
			shell(b, b.dot, x, y, z, r, lon / 2, lon, false, f.light, 0.25F * k, 0.9F * k, 0, 0, 0.5F, 0.5F, -1, 1);
		}
	}

	private static void comet(CircleBatch b, Fx f, float age, float t) {
		Vec3 d = f.b.subtract(f.a);
		float len = (float) d.length();
		if (len < 0.05F || t >= 1) return;
		float s = (float) Math.pow(t, 1.6);
		Vec3 head = f.a.add(d.scale(s));
		Vec3 dir = d.scale(1 / len);
		float tailLen = Math.min(len * s, 6 + f.r0 * 8);
		int n = 20;
		float[] p = new float[n * 3];
		float[] hs = new float[n];
		int[] cs = new int[n];
		for (int i = 0; i < n; i++) {
			float k = i / (float) (n - 1);
			Vec3 at = head.subtract(dir.scale(tailLen * k));
			p[3 * i] = b.rx(at.x);
			p[3 * i + 1] = b.ry(at.y);
			p[3 * i + 2] = b.rz(at.z);
			hs[i] = f.r0 * 1.6F * (1 - k) * (1 - k * 0.3F);
			cs[i] = argb(f.color, 0.9F * (1 - k));
		}
		b.ribbon(b.streak, p, n, hs, 0, cs, 0, -age * 0.3F, 0.25F);
		for (int i = 0; i < n; i++) hs[i] *= 2.6F;
		b.ribbon(b.glow, p, n, hs, 0, cs, 0, 0, 0);
		for (int i = 0; i < n; i++) {
			float k = i / (float) (n - 1);
			hs[i] = f.r0 * 0.45F * (1 - k);
			cs[i] = argb(mix(f.light, 0xFFFFFF, 0.5F), 1 - k);
		}
		b.ribbon(b.line, p, n, hs, 0, cs, 0, 0, 0);
		float x = b.rx(head.x), y = b.ry(head.y), z = b.rz(head.z);
		b.billboard(b.flare, x, y, z, f.r0 * 4.5F, age * 0.25F, argb(f.light, 1));
		b.billboard(b.spark, x, y, z, f.r0 * 3.0F, 0, argb(f.color, 0.9F));
		b.billboard(b.spark, x, y, z, f.r0 * 1.2F, 0, argb(0xFFFFFF, 1));
	}

	// ---------------------------------------------------------------- вспышка экрана

	/** Вспышка экрана (элемент HUD): быстро вспыхивает и гаснет; дальше от центра — слабее. */
	static void renderFlash(GuiGraphics g, DeltaTracker delta) {
		if (FLASHES.isEmpty()) return;
		float scale = (float) Math.max(0, Math.min(2, SuchkiConfig.INSTANCE.screenFlashScale));
		if (scale <= 0) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		float now = CircleRenderer.now(delta.getGameTimeDeltaPartialTick(false));
		float r = 0, gg = 0, bb = 0, total = 0, white = 0;
		Vec3 eye = mc.player.getEyePosition();
		for (Fx f : FLASHES) {
			float age = f.age(now);
			float t = Math.max(0, age / f.ticks);
			if (t > 1) continue;
			float e = t < 0.08F ? t / 0.08F : (float) Math.pow(1 - (t - 0.08F) / 0.92F, 2);
			float dist = (float) eye.distanceTo(f.a);
			float fall = f.r0 <= 0 ? 1 : Math.max(0, 1 - dist / Math.max(1, f.r0)) * 0.7F + 0.3F;
			float k = Math.max(0, f.r1) * e * fall * scale;
			r += ((f.color >> 16) & 255) * k;
			gg += ((f.color >> 8) & 255) * k;
			bb += (f.color & 255) * k;
			total += k;
			if (t < 0.15F) white += k * (1 - t / 0.15F) * 0.5F;
		}
		if (total <= 0.001F) return;
		int col = (int) Math.min(255, r / total) << 16 | (int) Math.min(255, gg / total) << 8 | (int) Math.min(255, bb / total);
		int w = g.guiWidth(), h = g.guiHeight();
		g.fill(0, 0, w, h, argb(col, Math.min(0.85F, total * 0.75F)));
		if (white > 0.01F) g.fill(0, 0, w, h, argb(0xFFFFFF, Math.min(0.6F, white)));
	}
}
