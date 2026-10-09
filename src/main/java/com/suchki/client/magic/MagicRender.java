package com.suchki.client.magic;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.suchki.Suchki;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;

/**
 * Светящаяся отрисовка магии: цвет складывается с фоном (как у молнии и маяка), глубина не пишется —
 * наложенные круги и лучи не режут друг друга, а сияют вместе.
 */
public final class MagicRender {
	/** Частицы со свечением. */
	public static final RenderPipeline GLOW_PARTICLE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(Suchki.MOD_ID, "pipeline/glow_particle"))
			.withBlend(BlendFunction.LIGHTNING)
			.withDepthWrite(false)
			.withCull(false)
			.build());

	public static final SingleQuadParticle.Layer GLOW_LAYER = new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, GLOW_PARTICLE);

	/** Объёмная геометрия со свечением (круги, лучи, столпы, ядра снарядов). */
	public static final RenderPipeline GLOW_GEOMETRY = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(Suchki.MOD_ID, "pipeline/glow_geometry"))
			.withVertexShader("core/entity")
			.withFragmentShader("core/entity")
			.withShaderDefine("EMISSIVE")
			.withShaderDefine("NO_OVERLAY")
			.withShaderDefine("NO_CARDINAL_LIGHTING")
			.withSampler("Sampler0")
			.withBlend(BlendFunction.LIGHTNING)
			.withDepthWrite(false)
			.withCull(false)
			.withVertexFormat(DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS)
			.build());

	/** Полупрозрачная тёмная геометрия (подложка кругов, чёрное ядро): обычное смешивание, глубина не пишется. */
	public static final RenderPipeline SHADE_GEOMETRY = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(Suchki.MOD_ID, "pipeline/shade_geometry"))
			.withVertexShader("core/entity")
			.withFragmentShader("core/entity")
			.withShaderDefine("EMISSIVE")
			.withShaderDefine("NO_OVERLAY")
			.withShaderDefine("NO_CARDINAL_LIGHTING")
			.withSampler("Sampler0")
			.withBlend(BlendFunction.TRANSLUCENT)
			.withDepthWrite(false)
			.withCull(false)
			.withVertexFormat(DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS)
			.build());

	private static final Map<Identifier, RenderType> GLOW_TYPES = new HashMap<>();
	private static final Map<Identifier, RenderType> ADD_TYPES = new HashMap<>();
	private static final Map<Identifier, RenderType> SHADE_TYPES = new HashMap<>();

	public static final Identifier SPARK = tex("spark");
	public static final Identifier RUNE = tex("rune_circle");
	public static final Identifier RUNE_INNER = tex("rune_circle_inner");
	public static final Identifier RING = tex("ring");
	public static final Identifier BEAM = tex("beam");
	/** Профиль линии круга (U поперёк штриха). */
	public static final Identifier LINE = tex("circle_line");
	/** Широкое мягкое свечение под линией. */
	public static final Identifier LINE_GLOW = tex("circle_glow");
	/** Блик: горячая точка с лучами (остриё пера, вспышки). */
	public static final Identifier FLARE = tex("circle_flare");
	/** Мягкое пятно света (подсветка под кругом). */
	public static final Identifier DISC = tex("circle_disc");
	/** Круглый узел без лучей. */
	public static final Identifier DOT = tex("circle_dot");
	/** Профиль ударной волны: яркая кромка снаружи (U=1). */
	public static final Identifier WAVE = tex("fx_wave");
	/** Бесшовный шум (оболочки, смерч). */
	public static final Identifier NOISE = tex("fx_noise");
	/** Бесшовные полосы вдоль V (лучи, столпы). */
	public static final Identifier STREAK = tex("fx_streak");
	/** Бесшовные соты (барьер). */
	public static final Identifier HEX = tex("fx_hex");

	private MagicRender() {}

	private static Identifier tex(String name) {
		return Identifier.fromNamespaceAndPath(Suchki.MOD_ID, "textures/particle/" + name + ".png");
	}

	public static RenderType glow(Identifier texture) {
		return GLOW_TYPES.computeIfAbsent(texture, t -> RenderType.create("suchki_glow",
				RenderSetup.builder(GLOW_GEOMETRY).withTexture("Sampler0", t).sortOnUpload().createRenderSetup()));
	}

	/**
	 * Свечение без сортировки и со сглаженной текстурой (линейная выборка; {@code repeat} — текстура повторяется).
	 * Для сложения порядок не важен, а тысячи штрихов сортировать незачем.
	 */
	public static RenderType additive(Identifier texture, boolean repeat) {
		return ADD_TYPES.computeIfAbsent(texture, t -> RenderType.create("suchki_additive",
				RenderSetup.builder(GLOW_GEOMETRY).withTexture("Sampler0", t, () -> repeat
						? RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR)
						: RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)).createRenderSetup()));
	}

	/** Полупрозрачная тёмная подложка/тело (обычное смешивание). */
	public static RenderType shade(Identifier texture) {
		return SHADE_TYPES.computeIfAbsent(texture, t -> RenderType.create("suchki_shade",
				RenderSetup.builder(SHADE_GEOMETRY).withTexture("Sampler0", t, () -> RenderSystem.getSamplerCache()
						.getClampToEdge(FilterMode.LINEAR)).createRenderSetup()));
	}

	/** Вершина для {@link #GLOW_GEOMETRY}. Цвет ARGB. */
	public static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, int argb) {
		vc.addVertex(pose, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0)
				.setNormal(pose, 0.0F, 1.0F, 0.0F);
	}

	/** Квадрат в плоскости XY с центром в 0 и полуразмером {@code r}. */
	public static void quad(VertexConsumer vc, PoseStack.Pose pose, float r, int argb) {
		vertex(vc, pose, -r, -r, 0, 0, 1, argb);
		vertex(vc, pose, r, -r, 0, 1, 1, argb);
		vertex(vc, pose, r, r, 0, 1, 0, argb);
		vertex(vc, pose, -r, r, 0, 0, 0, argb);
	}

	public static int argb(int rgb, float alpha) {
		int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
		return a << 24 | rgb & 0xFFFFFF;
	}

	/** Смешать цвет с белым (0 — исходный, 1 — белый). */
	public static int lighten(int rgb, float k) {
		int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
		r = Math.round(r + (255 - r) * k);
		g = Math.round(g + (255 - g) * k);
		b = Math.round(b + (255 - b) * k);
		return r << 16 | g << 8 | b;
	}
}
