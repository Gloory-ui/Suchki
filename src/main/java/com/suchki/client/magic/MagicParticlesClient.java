package com.suchki.client.magic;

import com.suchki.magic.MagicParticles;
import com.suchki.magic.MagicParticles.Glow;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Частицы магии на клиенте: все светятся (сложение цвета), не зависят от освещения. */
public final class MagicParticlesClient {
	private MagicParticlesClient() {}

	public static void register() {
		ParticleFactoryRegistry reg = ParticleFactoryRegistry.getInstance();
		reg.register(MagicParticles.SPARK, sprites -> (o, level, x, y, z, dx, dy, dz, rnd) -> new Spark(level, x, y, z, dx, dy, dz, o, sprites, 0));
		reg.register(MagicParticles.MOTE, sprites -> (o, level, x, y, z, dx, dy, dz, rnd) -> new Spark(level, x, y, z, dx, dy, dz, o, sprites, 1));
		reg.register(MagicParticles.GLYPH, sprites -> (o, level, x, y, z, dx, dy, dz, rnd) -> new Spark(level, x, y, z, dx, dy, dz, o, sprites, 2));
		reg.register(MagicParticles.RUNE, sprites -> (o, level, x, y, z, dx, dy, dz, rnd) -> new Circle(level, x, y, z, dx, dy, dz, o, sprites, false));
		reg.register(MagicParticles.RING, sprites -> (o, level, x, y, z, dx, dy, dz, rnd) -> new Circle(level, x, y, z, dx, dy, dz, o, sprites, true));
		reg.register(MagicParticles.PILLAR, sprites -> (o, level, x, y, z, dx, dy, dz, rnd) -> new Pillar(level, x, y, z, o, sprites));
	}

	static float r(int rgb) {
		return (rgb >> 16 & 255) / 255.0F;
	}

	static float g(int rgb) {
		return (rgb >> 8 & 255) / 255.0F;
	}

	static float b(int rgb) {
		return (rgb & 255) / 255.0F;
	}

	/** Направление (нормаль круга) из скорости частицы; без скорости — лёжа на земле. */
	static Vec3 normal(double dx, double dy, double dz) {
		Vec3 n = new Vec3(dx, dy, dz);
		return n.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : n.normalize();
	}

	/**
	 * Искра (0), мерцающая звёздочка (1) или всплывающая руна (2): мягко гаснет и чуть уменьшается.
	 * Скорость задаётся точно (без ванильного разброса), чтобы спирали и потоки шли куда задумано.
	 */
	static final class Spark extends SingleQuadParticle {
		private final int kind;
		private final float baseSize;
		private final float phase;

		Spark(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, Glow o, SpriteSet sprites, int kind) {
			super(level, x, y, z, kind == 2 ? sprites.get(level.random) : sprites.first());
			this.kind = kind;
			this.xd = dx;
			this.yd = dy;
			this.zd = dz;
			this.hasPhysics = false;
			this.gravity = 0.0F;
			this.friction = kind == 2 ? 0.96F : 0.9F;
			this.lifetime = Math.max(2, o.life() + (kind == 0 ? random.nextInt(3) : 0));
			this.baseSize = o.size();
			this.quadSize = o.size();
			this.phase = random.nextFloat() * 6.28F;
			setColor(r(o.color()), g(o.color()), b(o.color()));
			if (kind == 2) this.yd += 0.01;
		}

		@Override
		protected Layer getLayer() {
			return MagicRender.GLOW_LAYER;
		}

		@Override
		public int getLightColor(float pt) {
			return 0xF000F0;
		}

		@Override
		public float getQuadSize(float pt) {
			float t = Mth.clamp((age + pt) / lifetime, 0.0F, 1.0F);
			float grow = kind == 2 ? Math.min(1.0F, (age + pt) / 4.0F) : 1.0F;
			return baseSize * (1.0F - 0.4F * t) * grow;
		}

		@Override
		public void tick() {
			super.tick();
			float t = (float) age / lifetime;
			float a = kind == 2 ? Math.min(1.0F, age / 4.0F) * (1.0F - t * t) : 1.0F - t * t;
			if (kind == 1) a *= 0.55F + 0.45F * Mth.sin(age * 0.9F + phase);
			this.alpha = Math.max(0.0F, a);
		}
	}

	/**
	 * Магический круг (руна) или ударная волна (кольцо). Повёрнут по нормали; руна вращается и рисуется двумя
	 * слоями навстречу друг другу, кольцо расходится от центра.
	 */
	static final class Circle extends SingleQuadParticle {
		private final boolean ring;
		private final SpriteSet sprites;
		private final Quaternionf base;
		private final float radius;
		private final int color;
		private final float spin;

		Circle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, Glow o, SpriteSet sprites, boolean ring) {
			super(level, x, y, z, sprites.first());
			this.ring = ring;
			this.sprites = sprites;
			Vec3 n = normal(dx, dy, dz);
			this.base = new Quaternionf().rotationTo(0, 0, 1, (float) n.x, (float) n.y, (float) n.z);
			this.radius = o.size();
			this.color = o.color();
			this.lifetime = Math.max(4, o.life());
			this.hasPhysics = false;
			this.gravity = 0.0F;
			this.spin = (random.nextBoolean() ? 1 : -1) * (ring ? 0.0F : 0.6F / Math.max(1.0F, radius));
			setSize(radius * 2.2F, radius * 2.2F);
		}

		@Override
		protected Layer getLayer() {
			return MagicRender.GLOW_LAYER;
		}

		@Override
		public int getLightColor(float pt) {
			return 0xF000F0;
		}

		@Override
		public void tick() {
			xo = x;
			yo = y;
			zo = z;
			if (age++ >= lifetime) remove();
		}

		@Override
		public void extract(QuadParticleRenderState state, Camera camera, float pt) {
			float age = this.age + pt;
			float t = Mth.clamp(age / lifetime, 0.0F, 1.0F);
			Vec3 cam = camera.position();
			float px = (float) (Mth.lerp(pt, xo, x) - cam.x);
			float py = (float) (Mth.lerp(pt, yo, y) - cam.y);
			float pz = (float) (Mth.lerp(pt, zo, z) - cam.z);
			if (ring) {
				float k = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
				float size = Math.max(0.1F, radius * (0.08F + 0.92F * k));
				float a = (1.0F - t) * 0.95F;
				add(state, sprites.first(), base, px, py, pz, size, color, a);
				add(state, sprites.first(), base, px, py, pz, size * 0.96F, MagicRender.lighten(color, 0.6F), a * 0.5F);
				return;
			}
			// Появление 6 тиков (раскрывается из центра), исчезновение — последние 10 тиков.
			float appear = Math.min(1.0F, age / 6.0F);
			appear = 1.0F - (1.0F - appear) * (1.0F - appear);
			float fade = Math.min(1.0F, (lifetime - age) / 10.0F);
			float a = Math.max(0.0F, Math.min(appear, fade)) * (0.85F + 0.15F * Mth.sin(age * 0.25F));
			float size = radius * (0.6F + 0.4F * appear);
			Quaternionf outer = new Quaternionf(base).rotateZ(age * spin * 0.05F);
			Quaternionf inner = new Quaternionf(base).rotateZ(-age * spin * 0.09F);
			add(state, sprites.get(0, 1), outer, px, py, pz, size, color, a);
			add(state, sprites.get(1, 1), inner, px, py, pz, size, MagicRender.lighten(color, 0.35F), a * 0.9F);
		}

		private static void add(QuadParticleRenderState state, TextureAtlasSprite s, Quaternionf q, float x, float y, float z, float size,
				int rgb, float alpha) {
			state.add(MagicRender.GLOW_LAYER, x, y, z, q.x, q.y, q.z, q.w, size, s.getU0(), s.getU1(), s.getV0(), s.getV1(),
					ARGB.colorFromFloat(alpha, r(rgb), g(rgb), b(rgb)), 0xF000F0);
		}
	}

	/** Столп света от земли вверх: стопка полос, повёрнутых к камере, со «стекающим» узором. */
	static final class Pillar extends SingleQuadParticle {
		private final float height;
		private final int color;
		private final TextureAtlasSprite beam;

		Pillar(ClientLevel level, double x, double y, double z, Glow o, SpriteSet sprites) {
			super(level, x, y, z, sprites.first());
			this.beam = sprites.first();
			this.height = o.size();
			this.color = o.color();
			this.lifetime = Math.max(4, o.life());
			this.hasPhysics = false;
			this.gravity = 0.0F;
			setSize(2.0F, height);
		}

		@Override
		protected Layer getLayer() {
			return MagicRender.GLOW_LAYER;
		}

		@Override
		public void tick() {
			xo = x;
			yo = y;
			zo = z;
			if (age++ >= lifetime) remove();
		}

		@Override
		public void extract(QuadParticleRenderState state, Camera camera, float pt) {
			float age = this.age + pt;
			float t = Mth.clamp(age / lifetime, 0.0F, 1.0F);
			float a = Math.min(1.0F, age / 3.0F) * (1.0F - t * t);
			Vec3 cam = camera.position();
			float px = (float) (Mth.lerp(pt, xo, x) - cam.x);
			float py = (float) (Mth.lerp(pt, yo, y) - cam.y);
			float pz = (float) (Mth.lerp(pt, zo, z) - cam.z);
			Quaternionf q = new Quaternionf(0, camera.rotation().y, 0, camera.rotation().w).normalize();
			drawColumn(state, q, px, py, pz, 0.45F * (1.0F + 0.6F * t), age, color, a);
			drawColumn(state, q, px, py, pz, 0.16F, age, 0xFFFFFF, a);
			drawColumn(state, q, px, py, pz, 1.1F, age, color, a * 0.3F);
		}

		private void drawColumn(QuadParticleRenderState state, Quaternionf q, float x, float y, float z, float half, float age, int rgb, float a) {
			int n = Math.max(1, (int) Math.ceil(height / (half * 2)));
			float vSpan = (beam.getV1() - beam.getV0()) / 4.0F;
			int shift = (int) (age * 0.8F);
			for (int i = 0; i < n; i++) {
				int band = Math.floorMod(shift - i, 4);
				float v0 = beam.getV0() + vSpan * band;
				float fadeTop = 1.0F - (float) i / n;
				state.add(MagicRender.GLOW_LAYER, x, y + half + i * half * 2, z, q.x, q.y, q.z, q.w, half, beam.getU0(), beam.getU1(), v0,
						v0 + vSpan, ARGB.colorFromFloat(a * (0.35F + 0.65F * fadeTop), r(rgb), g(rgb), b(rgb)), 0xF000F0);
			}
		}
	}
}
