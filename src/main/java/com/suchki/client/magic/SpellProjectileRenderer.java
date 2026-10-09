package com.suchki.client.magic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.suchki.magic.Spell;
import com.suchki.magic.SpellProjectile;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Снаряд заклинания: светящееся ядро (белая сердцевина, светлый слой и цветной ореол, пульсирует), блик-звезда,
 * вращающееся кольцо и хвост по скорости. У крупных снарядов (метеоры, сферы) — ещё широкое свечение вокруг.
 * У части заклинаний внутри вращается «материя» — ледышка, огненный заряд, магма.
 * Цвета — заклинания (у слияния — смесь стихий), размер — по заклинанию, силе и масштабу снаряда.
 */
public class SpellProjectileRenderer extends EntityRenderer<SpellProjectile, SpellProjectileRenderer.State> {
	public static class State extends EntityRenderState {
		int color = 0xFFFFFF;
		int light = 0xFFFFFF;
		float size = 0.3F;
		float power = 1.0F;
		float age;
		float vx, vy, vz;
		final ItemStackRenderState item = new ItemStackRenderState();
		boolean hasItem;
	}

	private final ItemModelResolver itemModels;

	public SpellProjectileRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.itemModels = context.getItemModelResolver();
	}

	@Override
	protected int getBlockLightLevel(SpellProjectile entity, BlockPos pos) {
		return 15;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(SpellProjectile entity, State state, float pt) {
		super.extractRenderState(entity, state, pt);
		Spell spell = entity.spell();
		state.age = entity.tickCount + pt;
		state.power = entity.power();
		Vec3 v = entity.getDeltaMovement();
		state.vx = (float) v.x;
		state.vy = (float) v.y;
		state.vz = (float) v.z;
		if (spell == null) {
			state.hasItem = false;
			return;
		}
		state.color = spell.color();
		state.light = spell.lightColor();
		state.size = spell.projectileSize * entity.scale() * (0.85F + 0.15F * state.power);
		state.hasItem = spell.projectileModel != null;
		if (state.hasItem) {
			itemModels.updateForNonLiving(state.item, new ItemStack(BuiltInRegistries.ITEM.getValue(spell.projectileModel)),
					ItemDisplayContext.GROUND, entity);
		}
	}

	@Override
	public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
		float pulse = 1.0F + 0.08F * (float) Math.sin(s.age * 0.9F);
		float big = Math.max(0, Math.min(1, (s.size - 0.5F) / 1.5F));
		pose.pushPose();
		pose.translate(0.0F, 0.2F, 0.0F);
		tail(s, pose, out, camera);
		// Ореол и сердцевина — всегда лицом к камере.
		pose.pushPose();
		pose.mulPose(camera.orientation);
		float halo = s.size * (2.4F + 0.8F * big) * pulse;
		out.submitCustomGeometry(pose, MagicRender.additive(MagicRender.SPARK, false), (p, vc) -> {
			MagicRender.quad(vc, p, halo, MagicRender.argb(s.color, 0.75F));
			MagicRender.quad(vc, p, s.size * 1.2F * pulse, MagicRender.argb(s.light, 0.95F));
			MagicRender.quad(vc, p, s.size * 0.55F, MagicRender.argb(0xFFFFFF, 1.0F));
		});
		if (big > 0) {
			out.submitCustomGeometry(pose, MagicRender.additive(MagicRender.DISC, false), (p, vc) ->
					MagicRender.quad(vc, p, s.size * 4.5F * pulse, MagicRender.argb(s.color, 0.3F * big)));
		}
		pose.pushPose();
		pose.mulPose(Axis.ZP.rotation(s.age * 0.05F));
		float flare = s.size * (2.6F + 1.2F * big) * (0.9F + 0.2F * (float) Math.sin(s.age * 1.7F));
		out.submitCustomGeometry(pose, MagicRender.additive(MagicRender.FLARE, false), (p, vc) ->
				MagicRender.quad(vc, p, flare, MagicRender.argb(s.light, 0.55F)));
		pose.popPose();
		pose.pushPose();
		pose.mulPose(Axis.ZP.rotation(s.age * 0.15F));
		out.submitCustomGeometry(pose, MagicRender.glow(MagicRender.RING), (p, vc) ->
				MagicRender.quad(vc, p, s.size * 1.7F, MagicRender.argb(s.color, 0.45F)));
		pose.popPose();
		pose.popPose();
		pose.popPose();
		if (s.hasItem) {
			pose.pushPose();
			pose.translate(0.0F, 0.2F, 0.0F);
			float k = s.size * 1.9F;
			pose.scale(k, k, k);
			pose.mulPose(Axis.YP.rotation(s.age * 0.35F));
			pose.mulPose(Axis.XP.rotation(s.age * 0.22F));
			pose.translate(0.0F, -0.25F, 0.0F);
			s.item.submit(pose, out, 0xF000F0, OverlayTexture.NO_OVERLAY, s.outlineColor);
			pose.popPose();
		}
		super.submit(s, pose, out, camera);
	}

	/** Хвост по скорости: лента лицом к камере, сужается назад. */
	private static void tail(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
		float speed = (float) Math.sqrt(s.vx * s.vx + s.vy * s.vy + s.vz * s.vz);
		if (speed < 0.05F) return;
		float len = Math.min(4.0F + s.size * 4, speed * 2.5F);
		float dx = -s.vx / speed, dy = -s.vy / speed, dz = -s.vz / speed;
		// Направление на камеру от снаряда.
		float cx = (float) (camera.pos.x - s.x), cy = (float) (camera.pos.y - s.y - 0.2), cz = (float) (camera.pos.z - s.z);
		float qx = dy * cz - dz * cy, qy = dz * cx - dx * cz, qz = dx * cy - dy * cx;
		float sl = (float) Math.sqrt(qx * qx + qy * qy + qz * qz);
		if (sl < 1.0E-5F) return;
		float sx = qx / sl, sy = qy / sl, sz = qz / sl;
		float ex = dx * len, ey = dy * len, ez = dz * len;
		float w0 = s.size * 1.3F, w1 = s.size * 0.4F;
		out.submitCustomGeometry(pose, MagicRender.additive(MagicRender.LINE_GLOW, false), (p, vc) -> {
			int c0 = MagicRender.argb(s.color, 0.6F), c1 = MagicRender.argb(s.color, 0);
			MagicRender.vertex(vc, p, -sx * w0, -sy * w0, -sz * w0, 0, 0, c0);
			MagicRender.vertex(vc, p, sx * w0, sy * w0, sz * w0, 1, 0, c0);
			MagicRender.vertex(vc, p, ex + sx * w1, ey + sy * w1, ez + sz * w1, 1, 1, c1);
			MagicRender.vertex(vc, p, ex - sx * w1, ey - sy * w1, ez - sz * w1, 0, 1, c1);
		});
		float c = s.size * 0.4F;
		out.submitCustomGeometry(pose, MagicRender.additive(MagicRender.LINE, false), (p, vc) -> {
			int c0 = MagicRender.argb(s.light, 0.85F), c1 = MagicRender.argb(s.light, 0);
			MagicRender.vertex(vc, p, -sx * c, -sy * c, -sz * c, 0, 0, c0);
			MagicRender.vertex(vc, p, sx * c, sy * c, sz * c, 1, 0, c0);
			MagicRender.vertex(vc, p, ex * 0.7F, ey * 0.7F, ez * 0.7F, 1, 1, c1);
			MagicRender.vertex(vc, p, ex * 0.7F, ey * 0.7F, ez * 0.7F, 0, 1, c1);
		});
	}
}
