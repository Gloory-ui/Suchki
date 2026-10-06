package com.suchki.magic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Все заклинания по порядку: школа за школой, внутри — по кругу. */
public final class Spells {
	private static final Map<String, Spell> BY_ID = new LinkedHashMap<>();
	private static List<Spell> sorted = List.of();

	private Spells() {}

	static void add(Spell spell) {
		if (BY_ID.put(spell.id, spell) != null) throw new IllegalStateException("заклинание " + spell.id + " уже есть");
	}

	public static void init() {
		if (!BY_ID.isEmpty()) return;
		com.suchki.magic.spells.DestructionSpells.register();
		com.suchki.magic.spells.RestorationSpells.register();
		com.suchki.magic.spells.LightSpells.register();
		com.suchki.magic.spells.DarkSpells.register();
		com.suchki.magic.spells.AlterationSpells.register();
		com.suchki.magic.spells.IllusionSpells.register();
		com.suchki.magic.spells.ConjurationSpells.register();
		List<Spell> list = new ArrayList<>(BY_ID.values());
		list.sort((a, b) -> a.school != b.school ? a.school.compareTo(b.school) : Integer.compare(a.tier, b.tier));
		sorted = Collections.unmodifiableList(list);
	}

	public static Spell get(String id) {
		return BY_ID.get(id);
	}

	public static List<Spell> all() {
		return sorted;
	}

	public static List<Spell> of(School school) {
		return sorted.stream().filter(s -> s.school == school).toList();
	}
}
