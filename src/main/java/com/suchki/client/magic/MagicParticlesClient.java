package com.suchki.client.magic;

import com.suchki.magic.FxNet;
import com.suchki.magic.MagicParticles;
import com.suchki.magic.MagicParticles.Glow;
import com.suchki.magic.School;
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
		// Руна и кольцо — не плоские картинки, а настоящий круг, рисуемый штрихами, и ударная волна мира.
		reg.register(MagicParticles.RUNE, sprites -> (o, level, x, y, z, dx, dy, dz, rnd) -> rune(o, x, y, z, dx, dy, dz));
		reg.register(MagicParticles.RING, sprites -> (o, level, x, y, z, dx, dy, dz, rnd) -> ring(o, x, y, z, dx, dy, dz));
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
	 * Частица-руна: круг стихии, ближайшей по цвету, сложность — по размеру. Рисуется за треть жизни, держится
	 * и гаснет. Частицу не создаём — круг живёт в {@link CircleRenderer}.
	 */
	static SingleQuadParticle rune(Glow o, double x, double y, double z, double dx, double dy, double dz) {
		CircleRenderer.Inst in = new CircleRenderer.Inst();
		in.pos = new Vec3(x, y, z);
		in.normal = normal(dx, dy, dz);
		in.radius = o.size();
		in.school = closest(o.color());
		in.tier = Mth.clamp(1 + Math.round(o.size() * 1.1F), 1, 7);
		in.color = o.color();
		in.light = MagicRender.lighten(o.color(), 0.6F);
		int life = Math.max(6, o.life());
		in.draw = Math.max(2, life / 3);
		in.fade = Math.max(2, Math.min(10, life / 3));
		in.hold = Math.max(0, life - in.draw - in.fade);
		in.spinRev = 0.12F;
		in.seed = (int) (x * 31 + z * 17);
		in.underlay = in.normal.y > 0.7 ? 0.6F : 0;
		in.start = CircleRenderer.now(0);
		CircleRenderer.add(in);
		return null;
	}

	/** Частица-кольцо: ударная волна мира (яркая кромка, расходится от центра). */
	static SingleQuadParticle ring(Glow o, double x, double y, double z, double dx, double dy, double dz) {
		Vec3 at = new Vec3(x, y, z);
		WorldFxRenderer.add(new FxNet.WorldFx(FxNet.WorldFx.RING, at, normal(dx, dy, dz), o.size() * 0.08F, o.size(), o.color(),
				MagicRender.lighten(o.color(), 0.6F), Math.max(4, o.life()), 0));
		return null;
	}

	/** Стихия с самым похожим цветом (для узора руны). */
	static School closest(int rgb) {
		School best = School.FIRE;
		int bestD = Integer.MAX_VALUE;
		for (School s : School.values()) {
			if (s == School.FUSION) continue;
			int dr = (s.color >> 16 & 255) - (rgb >> 16 & 255), dg = (s.color >> 8 & 255) - (rgb >> 8 & 255), db = (s.color & 255) - (rgb & 255);
			int d = dr * dr + dg * dg + db * db;
			if (d < bestD) {
				bestD = d;
				best = s;
			}
		}
		return best;
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
