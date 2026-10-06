package com.suchki.magic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Частицы магии. Все светятся (рисуются сложением цвета), цвет и размер задаются при запуске.
 * Скорость частицы (dx, dy, dz) у RUNE/CIRCLE — это направление, куда смотрит круг.
 */
public final class MagicParticles {
	/** Цвет (RGB), размер (у кругов — радиус в блоках), время жизни в тиках. */
	public record Glow(ParticleType<Glow> type, int color, float size, int life) implements ParticleOptions {
		static MapCodec<Glow> codec(ParticleType<Glow> type) {
			return RecordCodecBuilder.mapCodec(i -> i.group(
					Codec.INT.fieldOf("color").forGetter(Glow::color),
					Codec.FLOAT.fieldOf("size").forGetter(Glow::size),
					Codec.INT.fieldOf("life").forGetter(Glow::life)
			).apply(i, (c, s, l) -> new Glow(type, c, s, l)));
		}

		static StreamCodec<RegistryFriendlyByteBuf, Glow> streamCodec(ParticleType<Glow> type) {
			return StreamCodec.composite(
					ByteBufCodecs.INT, Glow::color,
					ByteBufCodecs.FLOAT, Glow::size,
					ByteBufCodecs.VAR_INT, Glow::life,
					(c, s, l) -> new Glow(type, c, s, l));
		}

		@Override
		public ParticleType<Glow> getType() {
			return type;
		}
	}

	/** Мягкая светящаяся искра. */
	public static final ParticleType<Glow> SPARK = register("spark");
	/** Мерцающая звёздочка. */
	public static final ParticleType<Glow> MOTE = register("mote");
	/** Магический круг, лежащий на земле (или повёрнутый по направлению из скорости), вращается. */
	public static final ParticleType<Glow> RUNE = register("rune");
	/** Расходящееся кольцо — ударная волна. Размер — конечный радиус. */
	public static final ParticleType<Glow> RING = register("ring");
	/** Руна-глиф, медленно всплывающая и тающая. */
	public static final ParticleType<Glow> GLYPH = register("glyph");
	/** Столп света от земли вверх. Размер — высота в блоках. */
	public static final ParticleType<Glow> PILLAR = register("pillar");

	private MagicParticles() {}

	private static ParticleType<Glow> register(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, Magic.id(name),
				FabricParticleTypes.complex(true, Glow::codec, Glow::streamCodec));
	}

	public static Glow spark(int color, float size, int life) {
		return new Glow(SPARK, color, size, life);
	}

	public static Glow mote(int color, float size, int life) {
		return new Glow(MOTE, color, size, life);
	}

	public static Glow rune(int color, float radius, int life) {
		return new Glow(RUNE, color, radius, life);
	}

	public static Glow ring(int color, float radius, int life) {
		return new Glow(RING, color, radius, life);
	}

	public static Glow glyph(int color, float size, int life) {
		return new Glow(GLYPH, color, size, life);
	}

	public static Glow pillar(int color, float height, int life) {
		return new Glow(PILLAR, color, height, life);
	}

	static void init() {
		// Регистрация — в статических полях.
	}
}
