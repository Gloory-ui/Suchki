package com.suchki.client.magic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/**
 * Сборщик геометрии магии за кадр. Круги, лучи и сферы пишут вершины сюда (в координатах относительно камеры),
 * а в конце кадра каждый тип отрисовки сдаётся одним пакетом: иначе чередование текстур рвёт пакеты на сотни
 * мелких. Порядок сдачи фиксирован — тёмные подложки первыми, потом свечение.
 */
public final class CircleBatch {
	/** Вершины одного типа отрисовки: x, y, z, u, v и цвет ARGB. */
	public static final class Buf {
		final RenderType type;
		float[] pos = new float[5 * 1024];
		int[] col = new int[1024];
		int n;

		Buf(RenderType type) {
			this.type = type;
		}

		public void v(float x, float y, float z, float u, float v, int argb) {
			if (n == col.length) {
				col = java.util.Arrays.copyOf(col, n * 2);
				pos = java.util.Arrays.copyOf(pos, n * 10);
			}
			int i = n * 5;
			pos[i] = x;
			pos[i + 1] = y;
			pos[i + 2] = z;
			pos[i + 3] = u;
			pos[i + 4] = v;
			col[n++] = argb;
		}
	}

	public final Buf shade = new Buf(MagicRender.shade(MagicRender.DOT));
	public final Buf disc = new Buf(MagicRender.additive(MagicRender.DISC, false));
	public final Buf glow = new Buf(MagicRender.additive(MagicRender.LINE_GLOW, false));
	public final Buf noise = new Buf(MagicRender.additive(MagicRender.NOISE, true));
	public final Buf streak = new Buf(MagicRender.additive(MagicRender.STREAK, true));
	public final Buf hex = new Buf(MagicRender.additive(MagicRender.HEX, true));
	public final Buf wave = new Buf(MagicRender.additive(MagicRender.WAVE, false));
	public final Buf line = new Buf(MagicRender.additive(MagicRender.LINE, false));
	public final Buf dot = new Buf(MagicRender.additive(MagicRender.DOT, false));
	public final Buf spark = new Buf(MagicRender.additive(MagicRender.SPARK, false));
	public final Buf flare = new Buf(MagicRender.additive(MagicRender.FLARE, false));
	/** Слой поверх свечения: тёмное тело (чёрная дыра закрывает дальнюю половину диска) и то, что перед ним. */
	public final Buf shadeTop = new Buf(MagicRender.shade(MagicRender.DOT));
	public final Buf noiseTop = new Buf(MagicRender.additive(MagicRender.NOISE, true));
	public final Buf waveTop = new Buf(MagicRender.additive(MagicRender.WAVE, false));
	public final Buf dotTop = new Buf(MagicRender.additive(MagicRender.DOT, false));
	private final Buf[] order = {shade, disc, glow, noise, streak, hex, wave, line, dot, spark, flare, shadeTop, noiseTop, waveTop, dotTop};

	/** Камера: положение в мире и оси экрана (влево, вверх, вперёд). */
	public double camX, camY, camZ;
	public float lx, ly, lz, ux, uy, uz, fx, fy, fz;
	/** Пикселей на блок на расстоянии 1 (для LOD и минимальной толщины линий). */
	public float pxPerBlock = 800;
	/** До какого расстояния туман почти не виден (дальше круги «подтягиваются» к камере). */
	public float fogSafe = 96;
	/** Время клиента в тиках (с долей кадра). */
	public float time;
	public float pt;
	/** Вершин сдано в прошлом кадре. */
	int lastVertices;

	public void begin(Camera camera, float time, float pt) {
		Vec3 c = camera.position();
		camX = c.x;
		camY = c.y;
		camZ = c.z;
		Vector3fc l = camera.leftVector(), u = camera.upVector(), f = camera.forwardVector();
		lx = l.x();
		ly = l.y();
		lz = l.z();
		ux = u.x();
		uy = u.y();
		uz = u.z();
		fx = f.x();
		fy = f.y();
		fz = f.z();
		Minecraft mc = Minecraft.getInstance();
		double fov = Math.toRadians(Math.max(30, mc.options.fov().get()));
		pxPerBlock = (float) (mc.getWindow().getHeight() / (2 * Math.tan(fov / 2)));
		fogSafe = Math.max(24, mc.options.getEffectiveRenderDistance() * 16 * 0.55F);
		this.time = time;
		this.pt = pt;
	}

	public float rx(double x) {
		return (float) (x - camX);
	}

	public float ry(double y) {
		return (float) (y - camY);
	}

	public float rz(double z) {
		return (float) (z - camZ);
	}

	/** Сдать всё накопленное и очистить. */
	public void flush(MultiBufferSource buffers, PoseStack.Pose pose) {
		int total = 0;
		for (Buf b : order) {
			if (b.n == 0) continue;
			VertexConsumer vc = buffers.getBuffer(b.type);
			float[] p = b.pos;
			int[] c = b.col;
			for (int i = 0; i < b.n; i++) {
				int k = i * 5;
				MagicRender.vertex(vc, pose, p[k], p[k + 1], p[k + 2], p[k + 3], p[k + 4], c[i]);
			}
			total += b.n;
			b.n = 0;
		}
		lastVertices = total;
	}

	// ---------------------------------------------------------------- примитивы

	/** Квадрат, повёрнутый к камере, с центром (x, y, z) относительно камеры. */
	public void billboard(Buf b, float x, float y, float z, float size, float rot, int argb) {
		float c = (float) Math.cos(rot) * size, s = (float) Math.sin(rot) * size;
		// Оси: «вправо» = −left, «вверх» = up, повёрнутые на rot.
		float ax = -lx * c + ux * s, ay = -ly * c + uy * s, az = -lz * c + uz * s;
		float bx = lx * s + ux * c, by = ly * s + uy * c, bz = lz * s + uz * c;
		b.v(x - ax - bx, y - ay - by, z - az - bz, 0, 1, argb);
		b.v(x + ax - bx, y + ay - by, z + az - bz, 1, 1, argb);
		b.v(x + ax + bx, y + ay + by, z + az + bz, 1, 0, argb);
		b.v(x - ax + bx, y - ay + by, z - az + bz, 0, 0, argb);
	}

	/** Квадрат в плоскости с осями a и b (полуразмеры уже в векторах). */
	public void plane(Buf buf, float x, float y, float z, float ax, float ay, float az, float bx, float by, float bz, int argb) {
		buf.v(x - ax - bx, y - ay - by, z - az - bz, 0, 1, argb);
		buf.v(x + ax - bx, y + ay - by, z + az - bz, 1, 1, argb);
		buf.v(x + ax + bx, y + ay + by, z + az + bz, 1, 0, argb);
		buf.v(x - ax + bx, y - ay + by, z - az + bz, 0, 0, argb);
	}

	/**
	 * Лента лицом к камере вдоль ломаной {@code p} (x, y, z относительно камеры, {@code count} точек).
	 * Толщина {@code half[i]} (или одна {@code halfConst}), цвет {@code argb[i]} (или один), V — по длине с масштабом.
	 */
	public void ribbon(Buf b, float[] p, int count, float[] half, float halfConst, int[] argb, int argbConst, float v0, float vPerBlock) {
		if (count < 2) return;
		float prevX = 0, prevY = 0, prevZ = 0, prevV = v0;
		float pax = 0, pay = 0, paz = 0, pbx = 0, pby = 0, pbz = 0;
		int prevC = 0;
		float v = v0;
		float lastSx = 0, lastSy = 0, lastSz = 0;
		for (int i = 0; i < count; i++) {
			int k = i * 3;
			float x = p[k], y = p[k + 1], z = p[k + 2];
			int i0 = Math.max(0, i - 1), i1 = Math.min(count - 1, i + 1);
			float tx = p[i1 * 3] - p[i0 * 3], ty = p[i1 * 3 + 1] - p[i0 * 3 + 1], tz = p[i1 * 3 + 2] - p[i0 * 3 + 2];
			// Сторона ленты: перпендикуляр к касательной и к лучу зрения.
			float sx = ty * z - tz * y, sy = tz * x - tx * z, sz = tx * y - ty * x;
			float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
			float h = half != null ? half[i] : halfConst;
			float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz), pl = (float) Math.sqrt(x * x + y * y + z * z);
			if (sl < 1.0E-6F || (i > 0 && sl < 0.2F * tl * pl)) {
				// Лента идёт почти на камеру — сторона не определена; берём прежнюю, иначе на изгибах лезут «шипы».
				if (i > 0) {
					sx = lastSx;
					sy = lastSy;
					sz = lastSz;
				} else {
					sx = -lx;
					sy = -ly;
					sz = -lz;
				}
				sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
				if (sl < 1.0E-6F) {
					sx = -lx;
					sy = -ly;
					sz = -lz;
					sl = 1;
				}
			}
			sx = sx / sl * h;
			sy = sy / sl * h;
			sz = sz / sl * h;
			// Сторона не должна переворачиваться между точками (иначе лента перекручивается «бантиком»).
			if (i > 0 && (sx * lastSx + sy * lastSy + sz * lastSz) < 0) {
				sx = -sx;
				sy = -sy;
				sz = -sz;
			}
			lastSx = sx;
			lastSy = sy;
			lastSz = sz;
			int c = argb != null ? argb[i] : argbConst;
			if (i > 0) {
				float seg = (float) Math.sqrt((x - prevX) * (x - prevX) + (y - prevY) * (y - prevY) + (z - prevZ) * (z - prevZ));
				v += seg * vPerBlock;
				b.v(pax, pay, paz, 0, prevV, prevC);
				b.v(pbx, pby, pbz, 1, prevV, prevC);
				b.v(x + sx, y + sy, z + sz, 1, v, c);
				b.v(x - sx, y - sy, z - sz, 0, v, c);
			}
			pax = x - sx;
			pay = y - sy;
			paz = z - sz;
			pbx = x + sx;
			pby = y + sy;
			pbz = z + sz;
			prevX = x;
			prevY = y;
			prevZ = z;
			prevV = v;
			prevC = c;
		}
	}

	/**
	 * Ломаная из отдельных отрезков лицом к камере (без общих вершин): у кривых, которые идут на камеру (спирали,
	 * зигзаги), сплошная лента перекручивается, а отдельные отрезки — нет. Концы чуть заходят друг на друга.
	 */
	public void segments(Buf b, float[] p, int count, float[] half, float halfConst, int[] argb, int argbConst) {
		for (int i = 0; i + 1 < count; i++) {
			int k = i * 3, m = k + 3;
			float ax = p[k], ay = p[k + 1], az = p[k + 2], bx = p[m], by = p[m + 1], bz = p[m + 2];
			float dx = bx - ax, dy = by - ay, dz = bz - az;
			float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (len < 1.0E-5F) continue;
			float h0 = half != null ? half[i] : halfConst, h1 = half != null ? half[i + 1] : halfConst;
			// Середина отрезка → камера (камера в начале координат).
			float cx = -(ax + bx) * 0.5F, cy = -(ay + by) * 0.5F, cz = -(az + bz) * 0.5F;
			float sx = dy * cz - dz * cy, sy = dz * cx - dx * cz, sz = dx * cy - dy * cx;
			float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
			if (sl < 1.0E-6F) continue;
			sx /= sl;
			sy /= sl;
			sz /= sl;
			// Продлить отрезок на долю толщины, чтобы стыки не рвались.
			float ex = dx / len, ey = dy / len, ez = dz / len;
			float e0 = Math.min(h0 * 0.6F, len * 0.5F), e1 = Math.min(h1 * 0.6F, len * 0.5F);
			ax -= ex * e0;
			ay -= ey * e0;
			az -= ez * e0;
			bx += ex * e1;
			by += ey * e1;
			bz += ez * e1;
			int c0 = argb != null ? argb[i] : argbConst, c1 = argb != null ? argb[i + 1] : argbConst;
			b.v(ax - sx * h0, ay - sy * h0, az - sz * h0, 0, 0, c0);
			b.v(ax + sx * h0, ay + sy * h0, az + sz * h0, 1, 0, c0);
			b.v(bx + sx * h1, by + sy * h1, bz + sz * h1, 1, 1, c1);
			b.v(bx - sx * h1, by - sy * h1, bz - sz * h1, 0, 1, c1);
		}
	}

	/**
	 * Мягкое свечение вдоль ломаной — цепочкой круглых бликов (у широкого свечения на крутых изгибах прямоугольные
	 * полосы торчат углами, а круглые блики сливаются гладко). Альфа делится по перекрытию.
	 */
	public void beads(Buf b, float[] p, int count, float[] size, float sizeConst, int argb) {
		int a0 = argb >>> 24;
		int rgb = argb & 0xFFFFFF;
		for (int i = 0; i + 1 < count; i++) {
			int k = i * 3, m = k + 3;
			float dx = p[m] - p[k], dy = p[m + 1] - p[k + 1], dz = p[m + 2] - p[k + 2];
			float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
			float s0 = size != null ? size[i] : sizeConst, s1 = size != null ? size[i + 1] : sizeConst;
			float sMid = Math.max(1.0E-3F, (s0 + s1) * 0.5F);
			int steps = Math.max(1, Math.min(24, (int) Math.ceil(len / (sMid * 0.5F))));
			float spacing = len / steps;
			int alpha = Math.round(a0 * Math.min(1, spacing / (sMid * 0.7F)));
			if (alpha <= 0) continue;
			int col = alpha << 24 | rgb;
			for (int j = 0; j < steps; j++) {
				float t = j / (float) steps;
				billboard(b, p[k] + dx * t, p[k + 1] + dy * t, p[k + 2] + dz * t, s0 + (s1 - s0) * t, 0, col);
			}
		}
	}

	/** Сколько вершин было в прошлом кадре (для проверок). */
	public int lastVertices() {
		return lastVertices;
	}
}
