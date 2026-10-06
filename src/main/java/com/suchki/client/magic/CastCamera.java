package com.suchki.client.magic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.suchki.SuchkiConfig;
import com.suchki.client.magic.CastAnim.Pose;
import com.suchki.magic.CastStyle;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Камера мага: лёгкая дрожь на пике силы (в «пределе» — сильнее), отдача при выпуске, тряска от взрывов
 * и землетрясений рядом, сужение поля зрения во время зарядки. Сила — cameraShakeScale в config/suchki.json.
 */
public final class CastCamera {
	private record Shake(Vec3 at, float strength, int duration, long start) {}

	private static final List<Shake> SHAKES = new ArrayList<>();
	private static long releaseTick = -1000;
	private static float releasePower;
	private static float releaseSide;
	private static CastStyle releaseStyle = CastStyle.BOLT;

	private CastCamera() {}

	static void shake(Vec3 at, float strength, int duration) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		SHAKES.add(new Shake(at, strength, duration, mc.level.getGameTime()));
		if (SHAKES.size() > 16) SHAKES.remove(0);
	}

	/** Свой выпуск заклинания: отдача камеры. */
	static void localRelease(float power, float side, CastStyle style) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		releaseTick = mc.level.getGameTime();
		releasePower = power;
		releaseSide = side;
		releaseStyle = style;
	}

	private static float noise(float x, float seed) {
		return 0.5F * Mth.sin(x * 12.9F + seed) + 0.3F * Mth.sin(x * 7.3F + 1.3F + seed) + 0.2F * Mth.sin(x * 23.1F + 2.1F + seed);
	}

	private static float scale() {
		return (float) Math.max(0.0, Math.min(2.0, SuchkiConfig.INSTANCE.cameraShakeScale));
	}

	public static void apply(PoseStack pose, float pt) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.getCameraEntity() != mc.player) return;
		float scale = scale();
		if (scale <= 0) return;
		float now = mc.level.getGameTime() + pt;
		float pitch = 0, yaw = 0, roll = 0;

		Pose cp = CastAnim.compute(mc.player, pt);
		if (cp != null && cp.casting()) {
			float amp = cp.style == CastStyle.CHANNEL ? 0.12F * cp.channel : 0.3F * cp.over + 1.0F * cp.hold;
			if (cp.style == CastStyle.RITUAL) amp += 0.18F * cp.form;
			float t = now * 0.5F;
			pitch += amp * noise(t, 0);
			yaw += amp * noise(t, 5);
			roll += amp * 0.6F * noise(t, 9);
		}

		float age = now - releaseTick;
		if (age >= 0 && age < 9) {
			float k = (1 - age / 9) * (1 - age / 9);
			float kick = releaseStyle == CastStyle.RITUAL ? 3.2F : releaseStyle == CastStyle.BOLT ? 2.2F : 1.4F;
			pitch -= kick * Math.min(2.25F, releasePower) * k;
			roll += releaseSide * 0.9F * k;
		}

		if (!SHAKES.isEmpty()) {
			Vec3 me = mc.player.getEyePosition(pt);
			SHAKES.removeIf(s -> now - s.start > s.duration);
			for (Shake s : SHAKES) {
				float a = (now - s.start) / s.duration;
				float fall = (float) (1.0 / (1.0 + me.distanceTo(s.at) / 10.0));
				float k = (1 - a) * (1 - a) * s.strength * fall;
				float t = now * 0.6F;
				pitch += k * 1.6F * noise(t, 13);
				yaw += k * 1.3F * noise(t, 21);
				roll += k * 1.1F * noise(t, 34);
			}
		}

		if (pitch != 0) pose.mulPose(Axis.XP.rotationDegrees(pitch * scale));
		if (yaw != 0) pose.mulPose(Axis.YP.rotationDegrees(yaw * scale));
		if (roll != 0) pose.mulPose(Axis.ZP.rotationDegrees(roll * scale));
	}

	/** Множитель поля зрения (1 — без изменений). */
	public static float fovFactor(AbstractClientPlayer p, float effectScale) {
		Minecraft mc = Minecraft.getInstance();
		if (p != mc.player || mc.level == null) return 1.0F;
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float factor = 1.0F;
		Pose cp = CastAnim.compute(p, pt);
		if (cp != null && cp.casting()) {
			factor -= cp.style == CastStyle.CHANNEL ? 0.04F * cp.channel : 0.03F * cp.form + 0.05F * cp.over;
		}
		float age = mc.level.getGameTime() + pt - releaseTick;
		if (age >= 0 && age < 7) factor += 0.06F * Math.min(1.0F, releasePower / 2.0F + 0.3F) * (1 - age / 7) * (1 - age / 7);
		return Mth.lerp(effectScale, 1.0F, factor);
	}
}
