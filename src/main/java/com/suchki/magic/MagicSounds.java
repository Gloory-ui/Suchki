package com.suchki.magic;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * Звуки магии. Сами звуки — подобранные и пересведённые ванильные (assets/suchki/sounds.json),
 * так их можно заменить ресурспаком, не трогая код.
 */
public final class MagicSounds {
	/** Гул зарядки — свой у каждой школы (крутится, пока маг копит силу). */
	public static final Map<School, SoundEvent> CHARGE = new EnumMap<>(School.class);

	public static final SoundEvent READY = register("magic.ready");
	public static final SoundEvent STAGE = register("magic.stage");
	public static final SoundEvent PEAK = register("magic.peak");
	public static final SoundEvent FIZZLE = register("magic.fizzle");
	public static final SoundEvent LEARN = register("magic.learn");

	public static final SoundEvent CAST_FIRE = register("magic.cast.fire");
	public static final SoundEvent CAST_FROST = register("magic.cast.frost");
	public static final SoundEvent CAST_SHOCK = register("magic.cast.shock");
	public static final SoundEvent CAST_HOLY = register("magic.cast.holy");
	public static final SoundEvent CAST_DARK = register("magic.cast.dark");
	public static final SoundEvent CAST_ARCANE = register("magic.cast.arcane");
	public static final SoundEvent CAST_HEAL = register("magic.cast.heal");
	public static final SoundEvent CAST_ILLUSION = register("magic.cast.illusion");
	public static final SoundEvent CAST_SUMMON = register("magic.cast.summon");
	public static final SoundEvent CAST_TELEPORT = register("magic.cast.teleport");
	public static final SoundEvent CAST_EARTH = register("magic.cast.earth");

	public static final SoundEvent IMPACT_FIRE = register("magic.impact.fire");
	public static final SoundEvent IMPACT_FROST = register("magic.impact.frost");
	public static final SoundEvent IMPACT_SHOCK = register("magic.impact.shock");
	public static final SoundEvent IMPACT_HOLY = register("magic.impact.holy");
	public static final SoundEvent IMPACT_DARK = register("magic.impact.dark");
	public static final SoundEvent IMPACT_ARCANE = register("magic.impact.arcane");
	public static final SoundEvent EXPLOSION = register("magic.explosion");
	public static final SoundEvent METEOR_FALL = register("magic.meteor_fall");
	public static final SoundEvent PILLAR = register("magic.pillar");

	public static final SoundEvent LOOP_FLAMES = register("magic.loop.flames");
	public static final SoundEvent LOOP_BEAM = register("magic.loop.beam");
	public static final SoundEvent LOOP_DRAIN = register("magic.loop.drain");
	public static final SoundEvent LOOP_HEAL = register("magic.loop.heal");
	public static final SoundEvent LOOP_WARD = register("magic.loop.ward");
	public static final SoundEvent LOOP_WIND = register("magic.loop.wind");

	public static final SoundEvent VANISH = register("magic.vanish");
	public static final SoundEvent WARD_HIT = register("magic.ward_hit");

	static {
		for (School s : School.values()) CHARGE.put(s, register("magic.charge." + s.id));
	}

	private MagicSounds() {}

	private static SoundEvent register(String name) {
		Identifier id = Magic.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	static void init() {
		// Регистрация — в статических полях.
	}

	/** Звук выпуска по школе (если у заклинания нет своего). */
	public static SoundEvent castOf(School school) {
		return switch (school) {
			case DESTRUCTION -> CAST_FIRE;
			case RESTORATION -> CAST_HEAL;
			case LIGHT -> CAST_HOLY;
			case DARK -> CAST_DARK;
			case ALTERATION -> CAST_ARCANE;
			case ILLUSION -> CAST_ILLUSION;
			case CONJURATION -> CAST_SUMMON;
		};
	}
}
