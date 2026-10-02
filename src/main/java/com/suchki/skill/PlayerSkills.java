package com.suchki.skill;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Навыки игрока: активная роль и отдельный прогресс каждой роли (уровень, опыт, взятые навыки).
 * Неизменяемый объект — любое изменение создаёт новый, и Fabric сам сохраняет и отправляет его клиенту.
 */
public record PlayerSkills(Optional<String> active, Map<String, Progress> roles, long lastSwitch) {
	public record Progress(int level, int xp, List<String> nodes) {
		public static final Progress START = new Progress(1, 0, List.of());
		public static final Codec<Progress> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("level").forGetter(Progress::level),
				Codec.INT.fieldOf("xp").forGetter(Progress::xp),
				Codec.STRING.listOf().fieldOf("nodes").forGetter(Progress::nodes)
		).apply(i, Progress::new));

		/** Очки навыков: по одному за каждый уровень минус потраченные. */
		public int freePoints() {
			int spent = 0;
			for (String id : nodes) {
				SkillTree.Node n = SkillTree.node(id);
				if (n != null) spent += n.cost();
			}
			return level - spent;
		}

		public boolean has(String nodeId) {
			return nodes.contains(nodeId);
		}

		Progress withNode(String nodeId) {
			List<String> list = new ArrayList<>(nodes);
			list.add(nodeId);
			return new Progress(level, xp, List.copyOf(list));
		}
	}

	public static final PlayerSkills EMPTY = new PlayerSkills(Optional.empty(), Map.of(), 0L);

	public static final Codec<PlayerSkills> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.optionalFieldOf("active").forGetter(PlayerSkills::active),
			Codec.unboundedMap(Codec.STRING, Progress.CODEC).fieldOf("roles").forGetter(PlayerSkills::roles),
			Codec.LONG.optionalFieldOf("last_switch", 0L).forGetter(PlayerSkills::lastSwitch)
	).apply(i, PlayerSkills::new));

	public static final StreamCodec<ByteBuf, PlayerSkills> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

	public Progress progress(String role) {
		return roles.getOrDefault(role, Progress.START);
	}

	/** Прогресс активной роли, или null, если роль ещё не выбрана. */
	public Progress current() {
		return active.map(this::progress).orElse(null);
	}

	PlayerSkills with(String role, Progress p) {
		Map<String, Progress> copy = new HashMap<>(roles);
		copy.put(role, p);
		return new PlayerSkills(active, Map.copyOf(copy), lastSwitch);
	}

	PlayerSkills switchTo(String role, long now) {
		PlayerSkills s = roles.containsKey(role) ? this : with(role, Progress.START);
		return new PlayerSkills(Optional.of(role), s.roles, now);
	}

	/** Сумма значения особого эффекта по всем взятым навыкам активной роли. */
	public double perk(Perk perk) {
		Progress p = current();
		if (p == null) return 0.0;
		double sum = 0.0;
		for (String id : p.nodes) {
			SkillTree.Node n = SkillTree.node(id);
			if (n != null) sum += n.perks().getOrDefault(perk, 0.0);
		}
		return sum;
	}
}
