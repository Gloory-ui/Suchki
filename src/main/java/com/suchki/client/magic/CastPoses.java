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
 * Поза выбирается по стойке ({@link CastAnim.Stance}): круг у цели — рука указывает на неё, круг в небе — руки
 * воздеты, ряд кругов — выпад посохом, веер за спиной — руки раскинуты, орбита — руки ведут круги.
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
		// Дрожь растёт с накоплением, на удержании и у высоких рангов — сильнее.
		float rank = 1.0F + 0.08F * (pose.tier() - 1);
		float tr = CastAnim.tremble(t, rightCasts ? 0 : 2) * (0.012F + 0.035F * pose.over + 0.07F * pose.hold) * rank;
		Arm c = new Arm();
		Arm o = new Arm();
		float bodyY = 0;
		float bodyX = 0;

		switch (pose.stance()) {
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
			case POINT -> {
				if (pose.casting()) {
					float f = CastAnim.easeOut(pose.form);
					// Рука вытянута точно на цель, вторая придерживает её у локтя — прицел не «гуляет».
					c.set(Mth.lerp(f, 0.0F, aimX) + tr * 0.6F, aimY + inC * 0.04F, outC * 0.02F + tr * 0.3F);
					o.set(Mth.lerp(f, 0.0F, aimX * 0.75F - 0.25F), aimY + inO * 0.78F, 0.0F);
					bodyY = inC * 0.12F * f;
				} else if (pose.fizzle) {
					c.set(aimX * (1 - pose.release), aimY, outC * 0.15F);
					o.set(0.0F, 0.0F, 0.0F);
				} else {
					// Короткая отдача вверх, как от выстрела, и возврат на цель.
					float kick = CastAnim.bump(Mth.clamp(pose.release * 2.2F, 0, 1));
					c.set(aimX - 0.42F * kick, aimY + inC * 0.04F, outC * 0.02F);
					o.set(aimX * 0.75F - 0.25F - 0.2F * kick, aimY + inO * 0.78F, 0.0F);
					bodyY = inC * 0.12F;
				}
			}
			case SKY -> {
				if (pose.casting()) {
					float f = CastAnim.easeOut(pose.form);
					float sway = 0.07F * Mth.sin(t * 0.2F) * (0.3F + pose.over);
					// Руки воздеты буквой V к кругу над целью; на накоплении — ещё выше и шире.
					c.set(Mth.lerp(f, 0.0F, -2.95F) + tr + sway, inC * 0.08F, outC * (0.42F + 0.12F * pose.over) * f);
					o.set(Mth.lerp(f, 0.0F, -2.95F) - tr - sway, inO * 0.08F, outO * (0.42F + 0.12F * pose.over) * f);
					bodyX = -0.06F * f;
				} else if (pose.fizzle) {
					float d = CastAnim.easeOut(pose.release);
					c.set(Mth.lerp(d, -2.95F, 0.0F), 0.0F, outC * 0.42F * (1 - d));
					o.set(Mth.lerp(d, -2.95F, 0.0F), 0.0F, outO * 0.42F * (1 - d));
				} else {
					// «Обрушить»: обе руки резко опускаются и указывают на цель.
					float slam = CastAnim.easeOut(Mth.clamp(pose.release / 0.25F, 0, 1));
					c.set(Mth.lerp(slam, -2.95F, aimX), aimY * slam + inC * 0.08F, outC * 0.42F * (1 - slam));
					o.set(Mth.lerp(slam, -2.95F, aimX + 0.1F), aimY * slam + inO * 0.2F, outO * 0.42F * (1 - slam));
					bodyX = 0.18F * CastAnim.bump(Math.min(1, pose.release * 1.4F));
				}
			}
			case THRUST -> {
				if (pose.casting()) {
					float f = CastAnim.easeOut(pose.form);
					// Посох двумя руками вперёд по прицелу; на накоплении отводится к плечу, корпус закручивается.
					float pull = 0.22F * pose.over;
					c.set(Mth.lerp(f, 0.0F, aimX + pull) + tr, aimY + inC * (0.22F + 0.12F * pose.over), outC * 0.04F);
					o.set(Mth.lerp(f, 0.0F, aimX + pull * 0.6F) - tr * 0.5F, aimY + inO * 0.5F, 0.0F);
					bodyY = inO * 0.22F * pose.over;
				} else if (pose.fizzle) {
					c.set(aimX * (1 - pose.release), aimY, 0.0F);
					o.set(aimX * (1 - pose.release), aimY, 0.0F);
				} else {
					// Выпад: посох выбрасывается вперёд, корпус доворачивает и наклоняется.
					float th = CastAnim.easeOut(Mth.clamp(pose.release / 0.2F, 0, 1));
					c.set(Mth.lerp(th, aimX + 0.22F, aimX - 0.05F), aimY + inC * Mth.lerp(th, 0.34F, 0.05F), outC * 0.04F);
					o.set(Mth.lerp(th, aimX + 0.13F, aimX - 0.02F), aimY + inO * Mth.lerp(th, 0.5F, 0.3F), 0.0F);
					bodyY = inC * 0.3F * CastAnim.bump(Math.min(1, pose.release * 1.5F));
					bodyX = 0.12F * CastAnim.bump(Math.min(1, pose.release * 1.5F));
				}
			}
			case SPREAD -> {
				if (pose.casting()) {
					float f = CastAnim.easeOut(pose.form);
					// Руки раскинуты в стороны — за спиной раскрывается веер кругов; на накоплении поднимаются.
					float lift = 0.2F * pose.over;
					c.set(Mth.lerp(f, 0.0F, -0.3F - lift) + tr, 0.0F, outC * Mth.lerp(f, 0.0F, 1.3F + lift));
					o.set(Mth.lerp(f, 0.0F, -0.3F - lift) - tr, 0.0F, outO * Mth.lerp(f, 0.0F, 1.3F + lift));
					bodyX = -0.05F * f;
				} else if (pose.fizzle) {
					float d = CastAnim.easeOut(pose.release);
					c.set(-0.3F * (1 - d), 0.0F, outC * 1.3F * (1 - d));
					o.set(-0.3F * (1 - d), 0.0F, outO * 1.3F * (1 - d));
				} else {
					// Рука с посохом взмахивает вперёд на цель («огонь!»), вторая остаётся раскрытой.
					float sw = CastAnim.easeOut(Mth.clamp(pose.release / 0.25F, 0, 1));
					c.set(Mth.lerp(sw, -0.5F, aimX), aimY * sw, outC * Mth.lerp(sw, 1.5F, 0.05F));
					o.set(-0.5F, 0.0F, outO * 1.5F);
					bodyY = inC * 0.25F * CastAnim.bump(Math.min(1, pose.release * 1.6F));
				}
			}
			case ORBIT -> {
				float a = t * 0.3F;
				if (pose.casting()) {
					float f = CastAnim.easeOut(pose.form);
					float r = 0.18F + 0.1F * pose.over;
					// Ладони ведут круги, которые летают вокруг мага.
					c.set(Mth.lerp(f, 0.0F, -1.0F + r * Mth.sin(a)) + tr, inC * 0.1F, outC * Mth.lerp(f, 0.0F, 0.55F + r * Mth.cos(a)));
					o.set(Mth.lerp(f, 0.0F, -1.0F + r * Mth.sin(a + Mth.PI)) - tr, inO * 0.1F, outO * Mth.lerp(f, 0.0F, 0.55F + r * Mth.cos(a + Mth.PI)));
				} else {
					// Руки резко раскрываются наружу — круги срываются с орбиты.
					float e = CastAnim.easeOut(Mth.clamp(pose.release * 2.4F, 0, 1));
					c.set(Mth.lerp(e, -1.0F, -0.6F), inC * 0.1F * (1 - e), outC * Mth.lerp(e, 0.55F, 1.4F));
					o.set(Mth.lerp(e, -1.0F, -0.6F), inO * 0.1F * (1 - e), outO * Mth.lerp(e, 0.55F, 1.4F));
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
