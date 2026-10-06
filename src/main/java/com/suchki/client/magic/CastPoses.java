package com.suchki.client.magic;

import com.suchki.client.magic.CastAnim.Pose;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Позы рук мага от третьего лица (их видят все). Цели считаются в радианах и плавно смешиваются с обычной
 * позой по {@link Pose#weight}. Обозначения: xRot &lt; 0 — рука вперёд/вверх; zRot «наружу» у правой руки &gt; 0.
 */
public final class CastPoses {
	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "suchki:cast_pose");

	private CastPoses() {}

	/** Цель для одной руки. */
	private static final class Arm {
		float x, y, z;

		Arm set(float x, float y, float z) {
			this.x = x;
			this.y = y;
			this.z = z;
			return this;
		}
	}

	public static void apply(HumanoidModel<?> model, Pose pose) {
		if (pose == null || pose.weight <= 0.001F) return;
		boolean rightCasts = pose.castArm == HumanoidArm.RIGHT;
		ModelPart cast = rightCasts ? model.rightArm : model.leftArm;
		ModelPart other = rightCasts ? model.leftArm : model.rightArm;
		// «Внутрь» (к середине тела) по yRot и «наружу» по zRot для каждой руки.
		float inC = rightCasts ? -1 : 1;
		float inO = -inC;
		float outC = -inC;
		float outO = -inO;
		float aimX = model.head.xRot - Mth.HALF_PI;
		float aimY = model.head.yRot;
		float t = pose.time;
		float tr = CastAnim.tremble(t, rightCasts ? 0 : 2) * (0.012F + 0.035F * pose.over + 0.07F * pose.hold);
		Arm c = new Arm();
		Arm o = new Arm();
		float bodyY = 0;
		float bodyX = 0;

		switch (pose.style) {
			case BOLT -> {
				if (pose.casting()) {
					float f = CastAnim.easeOut(pose.form);
					// Замах: рука поднимается к прицелу и чуть выше, на накоплении отводится назад-вбок.
					c.set(Mth.lerp(f, 0.0F, aimX - 0.25F - 0.12F * pose.over) + tr, aimY + inC * (0.12F - 0.28F * pose.over),
							outC * (0.08F + 0.1F * pose.over) + tr * 0.5F);
					o.set(Mth.lerp(f, 0.0F, aimX * 0.55F - 0.3F), aimY + inO * 0.55F, 0.0F);
					bodyY = inO * 0.18F * pose.over;
				} else if (pose.fizzle) {
					c.set(aimX * (1 - pose.release) + 0.2F * CastAnim.bump(pose.release), aimY, outC * 0.2F);
					o.set(0.0F, 0.0F, 0.0F);
				} else {
					// Толчок вперёд по прицелу с перехлёстом, корпус доворачивает за рукой.
					float r = pose.release;
					float thrust = CastAnim.easeOut(Mth.clamp(r / 0.22F, 0, 1));
					c.set(Mth.lerp(thrust, aimX - 0.37F, aimX + 0.12F), aimY + inC * Mth.lerp(thrust, -0.16F, 0.08F), outC * 0.04F);
					o.set(aimX * 0.4F - 0.2F, aimY + inO * 0.5F, 0.0F);
					bodyY = inC * 0.35F * CastAnim.bump(Math.min(1, r * 1.6F));
				}
			}
			case CHANNEL -> {
				float sway = 0.035F * Mth.sin(t * 0.35F) * (0.5F + pose.channel);
				float ramp = pose.casting() ? CastAnim.easeOut(Math.min(1.0F, t / 6.0F)) : 1.0F;
				c.set(Mth.lerp(ramp, 0.0F, aimX + sway) + tr * 0.4F, aimY + inC * 0.18F, outC * 0.03F);
				o.set(Mth.lerp(ramp, 0.0F, aimX - sway * 0.6F), aimY + inO * (pose.staff ? 0.38F : 0.25F), 0.0F);
			}
			case SELF -> {
				if (pose.casting()) {
					float f = CastAnim.easeOut(pose.form);
					// Руки к груди, ладони навстречу.
					c.set(Mth.lerp(f, 0.0F, -1.25F) + tr, inC * (0.62F + 0.1F * pose.over), 0.0F);
					o.set(Mth.lerp(f, 0.0F, -1.2F) - tr, inO * (0.58F + 0.1F * pose.over), 0.0F);
				} else {
					// Раскрываются в стороны и вверх.
					float e = CastAnim.easeOut(Mth.clamp(pose.release * 2.4F, 0, 1));
					c.set(Mth.lerp(e, -1.25F, -0.55F), inC * Mth.lerp(e, 0.62F, 0.0F), outC * 1.35F * e);
					o.set(Mth.lerp(e, -1.2F, -0.55F), inO * Mth.lerp(e, 0.58F, 0.0F), outO * 1.35F * e);
				}
			}
			case RITUAL -> {
				if (pose.casting()) {
					float f = CastAnim.easeOut(pose.form);
					float sway = 0.06F * Mth.sin(t * 0.18F) * pose.over;
					c.set(Mth.lerp(f, 0.0F, -2.85F) + tr + sway, 0.0F, outC * 0.32F * f);
					o.set(Mth.lerp(f, 0.0F, -2.85F) - tr + sway, 0.0F, outO * 0.32F * f);
				} else {
					// Удар вниз-вперёд, корпус наклоняется.
					float slam = CastAnim.easeOut(Mth.clamp(pose.release / 0.25F, 0, 1));
					c.set(Mth.lerp(slam, -2.85F, -0.75F), 0.0F, outC * 0.32F * (1 - slam));
					o.set(Mth.lerp(slam, -2.85F, -0.75F), 0.0F, outO * 0.32F * (1 - slam));
					bodyX = 0.16F * CastAnim.bump(Math.min(1, pose.release * 1.4F));
				}
			}
			case SUMMON -> {
				if (pose.casting()) {
					float f = CastAnim.easeOut(pose.form);
					c.set(Mth.lerp(f, 0.0F, -2.75F) + tr, inC * 0.1F, outC * 0.22F * f);
					o.set(Mth.lerp(f, 0.0F, -0.95F), inO * 0.35F, 0.0F);
				} else {
					// Взмах вниз-вперёд к месту призыва.
					float sweep = CastAnim.easeOut(Mth.clamp(pose.release / 0.3F, 0, 1));
					c.set(Mth.lerp(sweep, -2.75F, aimX + 0.35F), inC * 0.1F, outC * 0.22F * (1 - sweep));
					o.set(-0.95F, inO * 0.35F, 0.0F);
					bodyX = 0.1F * CastAnim.bump(Math.min(1, pose.release * 1.5F));
				}
			}
		}

		// С гримуаром книга остаётся у груди — колдует свободная рука.
		if (!pose.staff) o.set(-0.9F, inO * 0.42F, 0.0F);

		float w = pose.weight;
		blend(cast, c, w);
		blend(other, o, w);
		model.body.yRot = Mth.lerp(w, model.body.yRot, model.body.yRot + bodyY);
		model.body.xRot += bodyX * w;
	}

	private static void blend(ModelPart part, Arm target, float w) {
		part.xRot = Mth.lerp(w, part.xRot, target.x);
		part.yRot = Mth.lerp(w, part.yRot, target.y);
		part.zRot = Mth.lerp(w, part.zRot, target.z);
	}
}
