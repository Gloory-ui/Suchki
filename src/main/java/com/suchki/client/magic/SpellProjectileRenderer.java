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

/**
 * Снаряд заклинания: светящееся ядро (белая сердцевина и цветной ореол, пульсирует), а у части заклинаний
 * внутри вращается «материя» — ледышка, огненный заряд, магма.
 */
public class SpellProjectileRenderer extends EntityRenderer<SpellProjectile, SpellProjectileRenderer.State> {
	public static class State extends EntityRenderState {
		int color = 0xFFFFFF;
		int light = 0xFFFFFF;
		float size = 0.3F;
		float power = 1.0F;
		float age;
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
		if (spell == null) {
			state.hasItem = false;
			return;
		}
		state.color = spell.school.color;
		state.light = spell.school.light;
		state.size = spell.projectileSize * (0.85F + 0.15F * state.power);
		state.hasItem = spell.projectileModel != null;
		if (state.hasItem) {
			itemModels.updateForNonLiving(state.item, new ItemStack(BuiltInRegistries.ITEM.getValue(spell.projectileModel)),
					ItemDisplayContext.GROUND, entity);
		}
	}

	@Override
	public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
		float pulse = 1.0F + 0.08F * (float) Math.sin(s.age * 0.9F);
		// Ореол и сердцевина — всегда лицом к камере.
		pose.pushPose();
		pose.translate(0.0F, 0.2F, 0.0F);
		pose.mulPose(camera.orientation);
		float halo = s.size * 2.4F * pulse;
		out.submitCustomGeometry(pose, MagicRender.glow(MagicRender.SPARK), (p, vc) -> {
			MagicRender.quad(vc, p, halo, MagicRender.argb(s.color, 0.75F));
			MagicRender.quad(vc, p, s.size * 1.2F * pulse, MagicRender.argb(s.light, 0.95F));
			MagicRender.quad(vc, p, s.size * 0.55F, MagicRender.argb(0xFFFFFF, 1.0F));
		});
		pose.pushPose();
		pose.mulPose(Axis.ZP.rotation(s.age * 0.15F));
		out.submitCustomGeometry(pose, MagicRender.glow(MagicRender.RING), (p, vc) ->
				MagicRender.quad(vc, p, s.size * 1.7F, MagicRender.argb(s.color, 0.45F)));
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
}
