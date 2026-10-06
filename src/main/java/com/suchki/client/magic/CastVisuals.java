package com.suchki.client.magic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.suchki.client.magic.CastAnim.Pose;
import com.suchki.magic.CastStyle;
import com.suchki.magic.MagicParticles;
import com.suchki.magic.Spell;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Эффекты каста на клиенте для всех колдующих игроков: спирали искр к руке, вращающиеся магические круги
 * (перед рукой у снарядов, на земле у ритуалов и призыва), сияние руки, лучи потоков, щит Оберега.
 */
public final class CastVisuals {
	private CastVisuals() {}

	static boolean firstPerson(AbstractClientPlayer p) {
		Minecraft mc = Minecraft.getInstance();
		return p == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON && mc.getCameraEntity() == p;
	}

	// --- Частицы (каждый тик) ---

	/** Регистрация приёма пакетов эффектов и прочего, что нужно визуалам. */
	public static void register() {
	}

	static void tick(ClientLevel level) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		RandomSource rnd = level.random;
		for (AbstractClientPlayer p : level.players()) {
			if (p.distanceToSqr(mc.player) > 48 * 48) continue;
			Pose pose = CastAnim.compute(p, 0);
			if (pose == null || !pose.casting()) continue;
			Spell spell = pose.spell;
			int color = spell.school.color;
			int light = spell.school.light;
			Vec3 hand = CastAnim.handPos(p, pose, 0, firstPerson(p));
			// Спираль искр, стягивающихся к руке: чем больше накоплено, тем гуще.
			boolean fp = firstPerson(p);
			int n = pose.style == CastStyle.CHANNEL ? 1 : 1 + (int) (2 * pose.form + 3 * pose.over + 2 * pose.hold);
			if (fp) n = (n + 1) / 2;
			float radius = fp ? 0.35F : 0.75F;
			for (int i = 0; i < n; i++) {
				double a = rnd.nextDouble() * Math.PI * 2;
				double b = (rnd.nextDouble() - 0.5) * Math.PI;
				Vec3 off = new Vec3(Math.cos(a) * Math.cos(b), Math.sin(b), Math.sin(a) * Math.cos(b)).scale(radius);
				Vec3 at = hand.add(off);
				Vec3 v = off.scale(-0.11).add(off.cross(new Vec3(0, 1, 0)).scale(0.04));
				float size = (0.05F + 0.03F * pose.over) * (fp ? 0.4F : 1.0F);
				level.addParticle(MagicParticles.mote(rnd.nextBoolean() ? color : light, size, 9), at.x, at.y, at.z, v.x, v.y, v.z);
			}
			if (pose.style == CastStyle.RITUAL && p.tickCount % 3 == 0) {
				double a = rnd.nextDouble() * Math.PI * 2;
				double r = 1.0 + 1.4 * pose.form;
				Vec3 at = p.position().add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
				level.addParticle(MagicParticles.glyph(color, 0.12F, 30), at.x, at.y, at.z, 0, 0.03, 0);
			}
			if (pose.style == CastStyle.SUMMON && p.tickCount % 2 == 0) {
				Vec3 c = summonSpot(p, 0);
				double a = rnd.nextDouble() * Math.PI * 2;
				Vec3 at = c.add(Math.cos(a) * 1.1, 0.05, Math.sin(a) * 1.1);
				level.addParticle(MagicParticles.spark(color, 0.12F, 16), at.x, at.y, at.z, 0, 0.04 + 0.05 * pose.form, 0);
			}
			if (pose.hold > 0.3F && rnd.nextFloat() < pose.hold) {
				level.addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, hand.x, hand.y, hand.z,
						(rnd.nextDouble() - 0.5) * 0.3, (rnd.nextDouble() - 0.5) * 0.3, (rnd.nextDouble() - 0.5) * 0.3);
			}
		}
	}

	/** Где появится призыв (круг на земле перед магом). */
	static Vec3 summonSpot(AbstractClientPlayer p, float pt) {
		float yaw = p.getViewYRot(pt) * Mth.DEG_TO_RAD;
		return p.getPosition(pt).add(-Mth.sin(yaw) * 2.2, 0.06, Mth.cos(yaw) * 2.2);
	}

	/** Вспышка в руке при выпуске (видят все). */
	static void releaseBurst(AbstractClientPlayer p, Spell spell, float power) {
		ClientLevel level = (ClientLevel) p.level();
		Pose pose = new Pose();
		pose.spell = spell;
		pose.style = spell.style;
		pose.form = 1;
		pose.castArm = p.getMainArm();
		pose.staff = true;
		Vec3 hand = CastAnim.handPos(p, pose, 0, firstPerson(p));
		Vec3 look = p.getViewVector(1.0F);
		level.addParticle(MagicParticles.spark(spell.school.light, 0.5F + 0.25F * power, 5), hand.x, hand.y, hand.z, 0, 0, 0);
		if (spell.style == CastStyle.BOLT || spell.style == CastStyle.SUMMON) {
			level.addParticle(MagicParticles.ring(spell.school.color, 0.9F + 0.4F * power, 9), hand.x, hand.y, hand.z, look.x, look.y, look.z);
		} else if (spell.style == CastStyle.SELF || spell.style == CastStyle.RITUAL) {
			Vec3 feet = p.position().add(0, 0.08, 0);
			level.addParticle(MagicParticles.ring(spell.school.color, 2.0F + 1.2F * power, 12), feet.x, feet.y, feet.z, 0, 1, 0);
		}
		for (int i = 0; i < 10; i++) {
			Vec3 v = look.scale(0.15).add((level.random.nextDouble() - 0.5) * 0.2, (level.random.nextDouble() - 0.5) * 0.2,
					(level.random.nextDouble() - 0.5) * 0.2);
			level.addParticle(MagicParticles.spark(spell.school.color, 0.12F, 10), hand.x, hand.y, hand.z, v.x, v.y, v.z);
		}
	}

	// --- Геометрия (каждый кадр) ---

	static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 cam = mc.gameRenderer.getMainCamera().position();
		PoseStack ps = ctx.matrices();
		MultiBufferSource buffers = ctx.consumers();
		for (AbstractClientPlayer p : mc.level.players()) {
			if (p.distanceToSqr(mc.player) > 64 * 64) continue;
			Pose pose = CastAnim.compute(p, pt);
			if (pose == null) continue;
			boolean fp = firstPerson(p);
			drawPlayer(ps, buffers, cam, p, pose, pt, fp);
		}
	}

	private static void drawPlayer(PoseStack ps, MultiBufferSource buffers, Vec3 cam, AbstractClientPlayer p, Pose pose, float pt, boolean fp) {
		Spell spell = pose.spell;
		int color = spell.school.color;
		int light = spell.school.light;
		float time = p.tickCount + pt;
		Vec3 hand = CastAnim.handPos(p, pose, pt, fp);
		Vec3 look = p.getViewVector(pt);
		float fade = pose.casting() ? pose.weight : Math.max(0, 1 - pose.release * 1.6F);
		if (fade <= 0.01F) return;

		// Сияние руки/навершия.
		float g = pose.glow();
		if (fp) {
			billboard(ps, buffers, cam, hand, MagicRender.SPARK, 0.035F + 0.05F * g, light, 0.55F * fade);
			billboard(ps, buffers, cam, hand, MagicRender.SPARK, 0.08F + 0.08F * g, color, 0.3F * fade);
		} else {
			billboard(ps, buffers, cam, hand, MagicRender.SPARK, 0.1F + 0.14F * g, light, 0.75F * fade);
			billboard(ps, buffers, cam, hand, MagicRender.SPARK, 0.22F + 0.24F * g, color, 0.35F * fade);
		}

		if (!pose.casting()) return;
		float charge = pose.style == CastStyle.CHANNEL ? pose.channel : pose.form;
		float pulse = pose.over >= 1 ? 0.85F + 0.15F * Mth.sin(time * 0.6F) : 1.0F;
		switch (pose.style) {
			case BOLT -> {
				// Круг у навершия: растёт с силой, от первого лица — маленький и прозрачный, чтобы не слепил.
				float r = (0.14F + 0.18F * CastAnim.easeOut(pose.form) + 0.14F * pose.over) * (fp ? 0.45F : 1.0F);
				Vec3 at = hand.add(look.scale(fp ? 0.12 : 0.3));
				float a = (0.35F + 0.3F * pose.over) * (fp ? 0.55F : 1.0F);
				circle(ps, buffers, cam, at, look, r, time * (2.0F + 6.0F * pose.over), color, a * fade * pulse);
			}
			case SUMMON -> {
				Vec3 spot = summonSpot(p, pt);
				circle(ps, buffers, cam, spot, new Vec3(0, 1, 0), 0.4F + 0.9F * CastAnim.easeOut(pose.form) + 0.3F * pose.over,
						time * (1.5F + 3.0F * pose.over), color, (0.4F + 0.35F * pose.over) * fade * pulse);
			}
			case SELF -> {
				Vec3 feet = p.getPosition(pt).add(0, 0.06, 0);
				circle(ps, buffers, cam, feet, new Vec3(0, 1, 0), 0.5F + 0.6F * CastAnim.easeOut(pose.form) + 0.25F * pose.over,
						time * (1.5F + 4.0F * pose.over), color, (0.45F + 0.5F * pose.over) * fade * pulse);
			}
			case RITUAL -> {
				Vec3 feet = p.getPosition(pt).add(0, 0.06, 0);
				float r = 0.8F + 1.6F * CastAnim.easeOut(pose.form) + 0.6F * pose.over;
				circle(ps, buffers, cam, feet, new Vec3(0, 1, 0), r, time * (1.0F + 3.0F * pose.over), color, (0.45F + 0.3F * pose.over) * fade * pulse);
				circle(ps, buffers, cam, feet.add(0, 0.02, 0), new Vec3(0, 1, 0), r * 0.55F, -time * 3.0F, light, 0.35F * fade * pose.form);
			}
			case CHANNEL -> {
				if (spell.beam) {
					Vec3 end = beamEnd(p, pt, spell.range);
					beam(ps, buffers, cam, hand, end, (0.07F + 0.1F * charge) * (fp ? 0.5F : 1.0F), time, color, light, fade);
					billboard(ps, buffers, cam, end, MagicRender.SPARK, 0.35F + 0.3F * charge, color, 0.8F * fade);
					billboard(ps, buffers, cam, end, MagicRender.SPARK, 0.15F + 0.1F * charge, 0xFFFFFF, 0.9F * fade);
				} else if ("ward".equals(spell.id)) {
					Vec3 at = p.getEyePosition(pt).add(look.scale(fp ? 1.1 : 1.0)).add(0, -0.1, 0);
					float r = (0.75F + 0.15F * charge) * (fp ? 0.85F : 1.0F);
					circle(ps, buffers, cam, at, look, r, time * 1.2F, light, (fp ? 0.16F : 0.3F) * fade);
					disc(ps, buffers, cam, at, look, r * 1.02F, MagicRender.RING, color, (fp ? 0.35F : 0.6F) * fade);
				} else if ("healing".equals(spell.id)) {
					Vec3 feet = p.getPosition(pt).add(0, 0.06, 0);
					circle(ps, buffers, cam, feet, new Vec3(0, 1, 0), 0.9F, time * 1.5F, color, 0.5F * fade);
				} else {
					Vec3 at = hand.add(look.scale(fp ? 0.12 : 0.3));
					circle(ps, buffers, cam, at, look, (0.22F + 0.12F * charge) * (fp ? 0.45F : 1.0F), time * 4.0F, color, (fp ? 0.3F : 0.5F) * fade);
				}
			}
		}
	}

	/** Куда бьёт луч: первое существо по прицелу или блок (как на сервере). */
	static Vec3 beamEnd(AbstractClientPlayer p, float pt, double range) {
		Vec3 eye = p.getEyePosition(pt);
		Vec3 end = eye.add(p.getViewVector(pt).scale(range));
		BlockHitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 limit = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
		Vec3 best = limit;
		double bestDist = Double.MAX_VALUE;
		for (Entity e : p.level().getEntities(p, new AABB(eye, limit).inflate(1.5), e -> e instanceof LivingEntity l && l.isAlive() && !e.isSpectator())) {
			var hit = e.getBoundingBox().inflate(0.35).clip(eye, limit);
			if (hit.isEmpty()) continue;
			double d = eye.distanceToSqr(hit.get());
			if (d < bestDist) {
				bestDist = d;
				best = e.getBoundingBox().getCenter();
			}
		}
		return best;
	}

	// --- Примитивы ---

	private static void billboard(PoseStack ps, MultiBufferSource buffers, Vec3 cam, Vec3 at, net.minecraft.resources.Identifier tex,
			float size, int rgb, float alpha) {
		Minecraft mc = Minecraft.getInstance();
		ps.pushPose();
		ps.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
		ps.mulPose(mc.gameRenderer.getMainCamera().rotation());
		MagicRender.quad(buffers.getBuffer(MagicRender.glow(tex)), ps.last(), size, MagicRender.argb(rgb, alpha));
		ps.popPose();
	}

	/** Магический круг: два слоя навстречу друг другу, плоскость перпендикулярна {@code normal}. */
	private static void circle(PoseStack ps, MultiBufferSource buffers, Vec3 cam, Vec3 at, Vec3 normal, float r, float spinDeg, int rgb,
			float alpha) {
		if (alpha <= 0.01F) return;
		Vec3 n = normal.normalize();
		ps.pushPose();
		ps.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
		ps.mulPose(new Quaternionf().rotationTo(0, 0, 1, (float) n.x, (float) n.y, (float) n.z));
		ps.pushPose();
		ps.mulPose(Axis.ZP.rotationDegrees(spinDeg));
		MagicRender.quad(buffers.getBuffer(MagicRender.glow(MagicRender.RUNE)), ps.last(), r, MagicRender.argb(rgb, alpha));
		ps.popPose();
		ps.mulPose(Axis.ZP.rotationDegrees(-spinDeg * 1.6F));
		MagicRender.quad(buffers.getBuffer(MagicRender.glow(MagicRender.RUNE_INNER)), ps.last(), r,
				MagicRender.argb(MagicRender.lighten(rgb, 0.25F), alpha * 0.6F));
		ps.popPose();
	}

	private static void disc(PoseStack ps, MultiBufferSource buffers, Vec3 cam, Vec3 at, Vec3 normal, float r,
			net.minecraft.resources.Identifier tex, int rgb, float alpha) {
		Vec3 n = normal.normalize();
		ps.pushPose();
		ps.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
		ps.mulPose(new Quaternionf().rotationTo(0, 0, 1, (float) n.x, (float) n.y, (float) n.z));
		MagicRender.quad(buffers.getBuffer(MagicRender.glow(tex)), ps.last(), r, MagicRender.argb(rgb, alpha));
		ps.popPose();
	}

	/**
	 * Объёмный луч: три полосы под 60° вокруг оси (видно с любой стороны), узор бежит от руки к цели;
	 * внутри — тонкая белая сердцевина.
	 */
	private static void beam(PoseStack ps, MultiBufferSource buffers, Vec3 cam, Vec3 from, Vec3 to, float width, float time, int rgb,
			int light, float alpha) {
		Vec3 d = to.subtract(from);
		float len = (float) d.length();
		if (len < 0.05F) return;
		Vec3 dir = d.scale(1.0 / len);
		ps.pushPose();
		ps.translate(from.x - cam.x, from.y - cam.y, from.z - cam.z);
		ps.mulPose(new Quaternionf().rotationTo(0, 1, 0, (float) dir.x, (float) dir.y, (float) dir.z));
		VertexConsumer vc = buffers.getBuffer(MagicRender.glow(MagicRender.BEAM));
		float scroll = (time * 0.12F) % 1.0F;
		for (int plane = 0; plane < 3; plane++) {
			ps.pushPose();
			ps.mulPose(Axis.YP.rotationDegrees(plane * 60.0F + time * 4.0F));
			strip(vc, ps.last(), len, width * (1.0F + 0.12F * Mth.sin(time * 0.7F + plane)), scroll, MagicRender.argb(rgb, 0.75F * alpha));
			strip(vc, ps.last(), len, width * 0.35F, scroll * 1.7F, MagicRender.argb(MagicRender.lighten(light, 0.5F), 0.9F * alpha));
			ps.popPose();
		}
		ps.popPose();
	}

	/** Полоса вдоль оси Y длиной {@code len}, нарезанная на куски по 1,5 блока (узор бесшовный, бежит со сдвигом). */
	private static void strip(VertexConsumer vc, PoseStack.Pose pose, float len, float half, float scroll, int argb) {
		float seg = 1.5F;
		float y = -scroll * seg;
		while (y < len) {
			float y0 = Math.max(0, y);
			float y1 = Math.min(len, y + seg);
			float v0 = (y0 - y) / seg;
			float v1 = (y1 - y) / seg;
			MagicRender.vertex(vc, pose, -half, y0, 0, 0, v0, argb);
			MagicRender.vertex(vc, pose, half, y0, 0, 1, v0, argb);
			MagicRender.vertex(vc, pose, half, y1, 0, 1, v1, argb);
			MagicRender.vertex(vc, pose, -half, y1, 0, 0, v1, argb);
			y += seg;
		}
	}
}
