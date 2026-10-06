package com.suchki.magic;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * Звуки магии. Все свои — синтезированы tools/gen_sounds.py (assets/suchki/sounds/magic/*.ogg), список событий —
 * assets/suchki/sounds.json (его пишет tools/gen_magic.py). Имя события «magic.cast.fire» ↔ файлы cast_fire_N.ogg.
 *
 * <p>Как звучит долгое заклинание: гул зарядки стихии (петля, тон растёт с силой) → щелчки рисования круга →
 * «круг замкнулся» → нарастание (у рангов 5+) → выпуск («шшинг» + удар) → звук стихии у цели → хвост.
 */
public final class MagicSounds {
	/** Гул зарядки — свой у каждой стихии (петля, крутится, пока маг копит силу). */
	public static final Map<School, SoundEvent> CHARGE = new EnumMap<>(School.class);
	/** Выпуск заклинания стихии. */
	public static final Map<School, SoundEvent> CAST = new EnumMap<>(School.class);
	/** Попадание/взрыв стихии у цели. */
	public static final Map<School, SoundEvent> IMPACT = new EnumMap<>(School.class);

	// --- Ход каста ---
	public static final SoundEvent READY = register("magic.ready");
	public static final SoundEvent STAGE = register("magic.stage");
	public static final SoundEvent PEAK = register("magic.peak");
	public static final SoundEvent FIZZLE = register("magic.fizzle");
	public static final SoundEvent LEARN = register("magic.learn");
	/** Выбор заклинания в колесе. */
	public static final SoundEvent SELECT = register("magic.select");
	/** Переключение мультикаста. */
	public static final SoundEvent MULTICAST = register("magic.multicast");
	/** Штрих рисования круга (короткий звон-росчерк). */
	public static final SoundEvent CIRCLE_DRAW = register("magic.circle.draw");
	/** Круг замкнулся (готово). */
	public static final SoundEvent CIRCLE_COMPLETE = register("magic.circle.complete");
	/** Появился огромный круг (небо, ярусы). */
	public static final SoundEvent CIRCLE_BIG = register("magic.circle.big");
	/** Нарастание силы ~2 с (ранги 4–5). */
	public static final SoundEvent RISER_SHORT = register("magic.riser.short");
	/** Долгое нарастание ~5 с (ранги 6–7): тоны ползут вверх, гул растёт. */
	public static final SoundEvent RISER_LONG = register("magic.riser.long");
	/** Выпуск тяжёлого заклинания: «шшинг» вверх и удар. */
	public static final SoundEvent RELEASE_HEAVY = register("magic.release.heavy");
	/** Выпуск высшего заклинания: «шшинг», удар, стена звука, нисходящий хвост (~6 с). */
	public static final SoundEvent RELEASE_ULTIMATE = register("magic.release.ultimate");
	/** Глитч надписи заклинания. */
	public static final SoundEvent GLITCH = register("magic.glitch");

	// --- Большие эффекты ---
	public static final SoundEvent EXPLOSION = register("magic.explosion");
	/** Огромный взрыв с долгим хвостом (~8 с). */
	public static final SoundEvent EXPLOSION_HUGE = register("magic.explosion.huge");
	public static final SoundEvent METEOR_FALL = register("magic.meteor_fall");
	public static final SoundEvent PILLAR = register("magic.pillar");
	public static final SoundEvent SHOCKWAVE = register("magic.shockwave");
	public static final SoundEvent THUNDER = register("magic.thunder");
	public static final SoundEvent RUMBLE = register("magic.rumble");
	public static final SoundEvent FREEZE = register("magic.freeze");
	public static final SoundEvent SHATTER = register("magic.shatter");
	public static final SoundEvent GUST = register("magic.gust");
	/** Мощный луч-залп (Зольтраак, Экскалибур, Камехамеха): ~3 с. */
	public static final SoundEvent BEAM_BLAST = register("magic.beam_blast");
	public static final SoundEvent TIMESTOP = register("magic.timestop");
	public static final SoundEvent DOMAIN = register("magic.domain");
	public static final SoundEvent TELEPORT = register("magic.teleport");
	public static final SoundEvent SUMMON = register("magic.summon");
	public static final SoundEvent VANISH = register("magic.vanish");
	public static final SoundEvent WARD_HIT = register("magic.ward_hit");

	// --- Петли потоков и долгих эффектов ---
	public static final SoundEvent LOOP_FLAMES = register("magic.loop.flames");
	public static final SoundEvent LOOP_BEAM = register("magic.loop.beam");
	public static final SoundEvent LOOP_DRAIN = register("magic.loop.drain");
	public static final SoundEvent LOOP_HEAL = register("magic.loop.heal");
	public static final SoundEvent LOOP_WARD = register("magic.loop.ward");
	public static final SoundEvent LOOP_WIND = register("magic.loop.wind");
	public static final SoundEvent LOOP_LIGHTNING = register("magic.loop.lightning");
	public static final SoundEvent LOOP_WATER = register("magic.loop.water");
	public static final SoundEvent LOOP_VOID = register("magic.loop.void");
	public static final SoundEvent LOOP_STORM = register("magic.loop.storm");
	/** Хор/шёпот под заклинательную фразу. */
	public static final SoundEvent LOOP_CHANT = register("magic.loop.chant");

	static {
		for (School s : School.values()) {
			CHARGE.put(s, register("magic.charge." + s.id));
			CAST.put(s, register("magic.cast." + s.id));
			IMPACT.put(s, register("magic.impact." + s.id));
		}
	}

	private MagicSounds() {}

	private static SoundEvent register(String name) {
		Identifier id = Magic.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	static void init() {
		// Регистрация — в статических полях.
	}

	/** Звук выпуска стихии. */
	public static SoundEvent cast(School school) {
		return CAST.get(school);
	}

	/** Звук попадания стихии. */
	public static SoundEvent impact(School school) {
		return IMPACT.get(school);
	}

	/** Звук выпуска заклинания (у слияния — первой стихии-составляющей). */
	public static SoundEvent castOf(Spell spell) {
		return CAST.get(spell.fusion() ? spell.combo.get(0) : spell.school);
	}

	public static SoundEvent impactOf(Spell spell) {
		return IMPACT.get(spell.fusion() ? spell.combo.get(0) : spell.school);
	}
}
