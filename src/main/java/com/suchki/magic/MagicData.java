package com.suchki.magic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Магия игрока: изученные заклинания, выбранное, перезарядки (игровое время готовности), выдан ли стартовый набор
 * и мультикаст (сколько копий заклинания выпускать за раз, 1…4).
 * Мана хранится отдельно ({@link Magic#MANA}) — она меняется часто, а это — редко.
 */
public record MagicData(List<String> known, String selected, Map<String, Long> cooldowns, boolean starter, int multicast) {
	public static final MagicData EMPTY = new MagicData(List.of(), "", Map.of(), false, 1);

	public static final Codec<MagicData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.listOf().optionalFieldOf("known", List.of()).forGetter(MagicData::known),
			Codec.STRING.optionalFieldOf("selected", "").forGetter(MagicData::selected),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("cooldowns", Map.of()).forGetter(MagicData::cooldowns),
			Codec.BOOL.optionalFieldOf("starter", false).forGetter(MagicData::starter),
			Codec.INT.optionalFieldOf("multicast", 1).forGetter(MagicData::multicast)
	).apply(i, MagicData::new));

	public static final StreamCodec<ByteBuf, MagicData> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

	public MagicData(List<String> known, String selected, Map<String, Long> cooldowns, boolean starter) {
		this(known, selected, cooldowns, starter, 1);
	}

	public boolean knows(String spell) {
		return known.contains(spell);
	}

	public Spell selectedSpell() {
		return knows(selected) ? Spells.get(selected) : null;
	}

	public long readyAt(String spell) {
		return cooldowns.getOrDefault(spell, 0L);
	}

	MagicData learn(String spell) {
		if (knows(spell)) return this;
		List<String> list = new ArrayList<>(known);
		list.add(spell);
		return new MagicData(List.copyOf(list), selected.isEmpty() ? spell : selected, cooldowns, starter, multicast);
	}

	MagicData select(String spell) {
		return new MagicData(known, spell, cooldowns, starter, multicast);
	}

	MagicData withStarter() {
		return new MagicData(known, selected, cooldowns, true, multicast);
	}

	MagicData withMulticast(int copies) {
		return new MagicData(known, selected, cooldowns, starter, copies);
	}

	MagicData forgetAll() {
		return new MagicData(List.of(), "", Map.of(), starter, multicast);
	}

	MagicData clearCooldowns() {
		return new MagicData(known, selected, Map.of(), starter, multicast);
	}

	/** Поставить перезарядку и выбросить давно истёкшие. */
	MagicData cooldown(String spell, long readyAt, long now) {
		Map<String, Long> map = new HashMap<>();
		for (Map.Entry<String, Long> e : cooldowns.entrySet()) if (e.getValue() > now) map.put(e.getKey(), e.getValue());
		map.put(spell, readyAt);
		return new MagicData(known, selected, Map.copyOf(map), starter, multicast);
	}
}
