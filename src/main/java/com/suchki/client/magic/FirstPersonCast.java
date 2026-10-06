package com.suchki.client.magic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.suchki.client.magic.CastAnim.Pose;
import com.suchki.magic.FocusItem;
import com.suchki.mixin.client.ItemInHandRendererInvoker;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Каст от первого лица. Посох/книга двигаются по фазам (замах, накопление с дрожью, толчок; подъём и удар у
 * ритуала) и по стойке (на цель, к небу, выпад, в сторону, по кругу). С гримуаром свободная рука поднимается
 * в кадр и колдует сама.
 */
public final class FirstPersonCast {
	private FirstPersonCast() {}

	/** true — нарисовали сами (ванильную отрисовку этой руки пропускаем). */
	public static boolean render(ItemInHandRenderer renderer, AbstractClientPlayer player, float pt, InteractionHand hand, float swing,
			ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector out, int light) {
		Pose p = CastAnim.compute(player, pt);
		if (p == null || p.weight <= 0.001F || player.isScoping()) return false;
		HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		float side = arm == HumanoidArm.RIGHT ? 1 : -1;
		if (stack.getItem() instanceof FocusItem focus) {
			pose.pushPose();
			pose.translate(side * 0.56F, -0.52F + equip * -0.6F, -0.72F);
			if (focus.staff) staff(pose, p, side);
			else book(pose, p, side);
			renderer.renderItem(player, stack, side > 0 ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
					pose, out, light);
			pose.popPose();
			return true;
		}
		if (stack.isEmpty() && !p.staff && arm == p.castArm && !player.isInvisible()) {
			pose.pushPose();
			freeHand(pose, p, side);
			// Рука выезжает снизу по мере того, как поза мага набирает вес.
			((ItemInHandRendererInvoker) renderer).suchki$renderPlayerArm(pose, out, light, 1.0F - p.weight, 0.0F, arm);
			pose.popPose();
			return true;
		}
		return false;
	}

	private static float tremble(Pose p, float seed) {
		return CastAnim.tremble(p.time * 1.7F, seed) * (0.002F + 0.01F * p.over + 0.022F * p.hold);
	}

	private static void staff(PoseStack pose, Pose p, float side) {
		float w = p.weight;
		float x = 0, y = 0, z = 0, rx = 0, rz = 0;
		switch (p.stance()) {
			case BOLT -> {
				if (p.casting()) {
					float f = CastAnim.easeOut(p.form);
					x = -0.16F * f;
					y = 0.06F * f + 0.02F * p.over;
					z = -0.08F * f + 0.06F * p.over;
					rx = -16.0F * f;
					rz = 12.0F * f;
				} else {
					float thrust = CastAnim.bump(Mth.clamp(p.release * 1.6F, 0, 1));
					x = -0.16F;
					y = 0.06F;
					z = -0.08F - 0.34F * thrust;
					rx = -16.0F - 22.0F * thrust;
					rz = 12.0F;
				}
			}
			case CHANNEL -> {
				float ramp = p.casting() ? CastAnim.easeOut(Math.min(1.0F, p.time / 6.0F)) : 1.0F;
				x = -0.2F * ramp;
				y = 0.08F * ramp + 0.012F * Mth.sin(p.time * 0.35F);
				z = -0.12F * ramp;
				rx = -28.0F * ramp;
				rz = 8.0F * ramp;
			}
			case SELF -> {
				float f = p.casting() ? CastAnim.easeOut(p.form) : 1.0F;
				float rise = p.casting() ? 0 : CastAnim.easeOut(Mth.clamp(p.release * 2.0F, 0, 1));
				x = -0.22F * f;
				y = 0.12F * f + 0.12F * rise;
				z = -0.05F * f;
				rx = 15.0F * f;
				rz = 8.0F * f;
			}
			case RITUAL, SUMMON, SKY -> {
				// У небесного круга посох поднимается выше и чуть раскачивается — маг тянется к кругу над целью.
				float high = p.stance() == CastAnim.Stance.SKY ? 1.25F : 1.0F;
				if (p.casting()) {
					float f = CastAnim.easeOut(p.form);
					x = -0.15F * f;
					y = 0.38F * f * high + (high > 1 ? 0.015F * Mth.sin(p.time * 0.2F) : 0);
					z = 0.05F * f;
					rx = 25.0F * f * high;
					rz = 6.0F * f;
				} else {
					float slam = CastAnim.easeOut(Mth.clamp(p.release / (p.style == com.suchki.magic.CastStyle.SUMMON ? 0.3F : 0.25F), 0, 1));
					x = -0.15F;
					y = Mth.lerp(slam, 0.38F * high, -0.12F);
					z = Mth.lerp(slam, 0.05F, -0.22F);
					rx = Mth.lerp(slam, 25.0F * high, -38.0F);
					rz = 6.0F;
				}
			}
			case POINT -> {
				// Посох ровно на цель, без замаха; на выпуске — короткая отдача вверх.
				float f = p.casting() ? CastAnim.easeOut(p.form) : 1.0F;
				float kick = p.casting() || p.fizzle ? 0 : CastAnim.bump(Mth.clamp(p.release * 2.2F, 0, 1));
				x = -0.2F * f;
				y = 0.05F * f + 0.05F * kick;
				z = -0.14F * f + 0.06F * kick;
				rx = -10.0F * f + 14.0F * kick;
				rz = 4.0F * f;
			}
			case THRUST -> {
				if (p.casting()) {
					// Посох к центру кадра, на накоплении отводится к себе.
					float f = CastAnim.easeOut(p.form);
					x = -0.26F * f;
					y = 0.02F * f;
					z = -0.06F * f + 0.12F * p.over;
					rx = -30.0F * f;
					rz = 4.0F * f;
				} else {
					float th = p.fizzle ? 0 : CastAnim.bump(Mth.clamp(p.release * 1.4F, 0, 1));
					x = -0.26F;
					y = 0.02F;
					z = -0.06F + 0.12F * (1 - Math.min(1, p.release * 4)) - 0.42F * th;
					rx = -30.0F - 10.0F * th;
					rz = 4.0F;
				}
			}
			case SPREAD -> {
				if (p.casting()) {
					// Посох уходит в сторону — руки раскинуты.
					float f = CastAnim.easeOut(p.form);
					x = 0.18F * f;
					y = 0.08F * f + 0.03F * p.over;
					z = 0.02F * f;
					rx = 6.0F * f;
					rz = -24.0F * f;
				} else {
					// Взмах из стороны к центру — «огонь».
					float sw = p.fizzle ? 0 : CastAnim.easeOut(Mth.clamp(p.release / 0.3F, 0, 1));
					x = Mth.lerp(sw, 0.18F, -0.18F);
					y = Mth.lerp(sw, 0.08F, 0.04F);
					z = Mth.lerp(sw, 0.02F, -0.2F);
					rx = Mth.lerp(sw, 6.0F, -20.0F);
					rz = Mth.lerp(sw, -24.0F, 10.0F);
				}
			}
			case ORBIT -> {
				float f = p.casting() ? CastAnim.easeOut(p.form) : 1.0F - CastAnim.easeOut(Mth.clamp(p.release * 2.0F, 0, 1));
				float a = p.time * 0.3F;
				x = (-0.18F + 0.05F * Mth.cos(a)) * f;
				y = (0.1F + 0.05F * Mth.sin(a)) * f;
				z = -0.04F * f;
				rx = 8.0F * f;
				rz = 10.0F * f * Mth.cos(a);
			}
		}
		float rank = 1.0F + 0.1F * (p.tier() - 1);
		pose.translate(side * x * w + tremble(p, 0) * rank, y * w + tremble(p, 1.3F) * rank, z * w);
		pose.mulPose(Axis.XP.rotationDegrees(rx * w));
		pose.mulPose(Axis.ZP.rotationDegrees(side * rz * w));
	}

	private static void book(PoseStack pose, Pose p, float side) {
		float w = p.weight;
		pose.translate(-side * 0.1F * w, 0.06F * w, -0.04F * w);
		pose.mulPose(Axis.YP.rotationDegrees(-side * 20.0F * w));
		pose.mulPose(Axis.XP.rotationDegrees(-10.0F * w));
	}

	private static void freeHand(PoseStack pose, Pose p, float side) {
		float x = 0, y = 0, z = 0, rx = 0;
		switch (p.stance()) {
			case BOLT, POINT, THRUST -> {
				if (p.casting()) {
					float f = CastAnim.easeOut(p.form);
					x = -0.1F * f;
					y = 0.05F * f;
					z = -0.08F * f + 0.05F * p.over;
				} else {
					float thrust = CastAnim.bump(Mth.clamp(p.release * 1.6F, 0, 1));
					x = -0.1F;
					y = 0.05F;
					z = -0.08F - 0.3F * thrust;
					rx = -10.0F * thrust;
				}
			}
			case CHANNEL -> {
				x = -0.15F;
				y = 0.05F + 0.01F * Mth.sin(p.time * 0.35F);
				z = -0.12F;
			}
			case SELF -> {
				x = -0.2F;
				y = p.casting() ? 0.0F : 0.1F * CastAnim.easeOut(Mth.clamp(p.release * 2.0F, 0, 1));
			}
			case RITUAL, SUMMON, SKY -> {
				float f = p.casting() ? CastAnim.easeOut(p.form) : 1.0F - CastAnim.easeOut(Mth.clamp(p.release / 0.3F, 0, 1));
				y = 0.3F * f;
				x = -0.05F;
				rx = 15.0F * f;
			}
			case SPREAD -> {
				float f = p.casting() ? CastAnim.easeOut(p.form) : 1.0F - CastAnim.easeOut(Mth.clamp(p.release / 0.3F, 0, 1));
				x = 0.12F * f;
				y = 0.06F * f;
			}
			case ORBIT -> {
				float a = p.time * 0.3F;
				x = -0.1F + 0.05F * Mth.cos(a);
				y = 0.05F + 0.05F * Mth.sin(a);
			}
		}
		// Свободная рука ближе к центру и выше обычной — ладонь в кадре.
		pose.translate(side * (x - 0.12F) + tremble(p, 2), y + 0.14F + tremble(p, 3), z - 0.05F);
		pose.mulPose(Axis.XP.rotationDegrees(rx));
	}
}
