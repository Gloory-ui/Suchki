package com.suchki.skill;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.suchki.Suchki;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/** Деревья навыков из suchki/skills.json (генерируется tools/gen_skills.py). Одинаковы на сервере и клиенте. */
public final class SkillTree {
	public record AttrBonus(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {}

	public record Node(String id, String role, int branch, int tier, String name, String desc,
			List<AttrBonus> attrs, Map<Perk, Double> perks) {
		/** Сколько очков стоит ступень. */
		public int cost() {
			return tier;
		}
	}

	public record Role(String id, String name, Identifier icon, int color, String xpFrom, List<String> branches,
			List<Node> nodes) {
		public Node node(int branch, int tier) {
			for (Node n : nodes) if (n.branch == branch && n.tier == tier) return n;
			return null;
		}
	}

	private static Map<String, Role> roles = Map.of();
	private static Map<String, Node> nodes = Map.of();
	private static int maxLevel = 50;

	private SkillTree() {}

	public static void load() {
		try (InputStream in = SkillTree.class.getResourceAsStream("/suchki/skills.json")) {
			if (in == null) throw new IllegalStateException("нет suchki/skills.json в jar");
			JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			maxLevel = root.get("max_level").getAsInt();
			Map<String, Role> r = new LinkedHashMap<>();
			Map<String, Node> n = new LinkedHashMap<>();
			for (JsonElement re : root.getAsJsonArray("roles")) {
				JsonObject ro = re.getAsJsonObject();
				String roleId = ro.get("id").getAsString();
				List<Node> list = new ArrayList<>();
				for (JsonElement ne : ro.getAsJsonArray("nodes")) {
					Node node = parseNode(roleId, ne.getAsJsonObject());
					list.add(node);
					n.put(node.id(), node);
				}
				List<String> branches = new ArrayList<>();
				for (JsonElement b : ro.getAsJsonArray("branches")) branches.add(b.getAsString());
				r.put(roleId, new Role(roleId, ro.get("name").getAsString(), Identifier.parse(ro.get("icon").getAsString()),
						ro.get("color").getAsInt(), ro.get("xp_from").getAsString(), List.copyOf(branches), List.copyOf(list)));
			}
			roles = Collections.unmodifiableMap(r);
			nodes = Collections.unmodifiableMap(n);
			Suchki.LOGGER.info("Suchki: загружено ролей {}, навыков {}", roles.size(), nodes.size());
		} catch (Exception e) {
			throw new IllegalStateException("Не удалось загрузить деревья навыков", e);
		}
	}

	private static Node parseNode(String role, JsonObject o) {
		List<AttrBonus> attrs = new ArrayList<>();
		for (JsonElement ae : o.getAsJsonArray("attrs")) {
			JsonObject a = ae.getAsJsonObject();
			Identifier id = Identifier.parse(a.get("attribute").getAsString());
			Holder<Attribute> attr = BuiltInRegistries.ATTRIBUTE.get(id)
					.orElseThrow(() -> new IllegalStateException("неизвестный атрибут " + id + " в навыке " + o.get("id")));
			attrs.add(new AttrBonus(attr, a.get("amount").getAsDouble(),
					"add_multiplied_base".equals(a.get("operation").getAsString())
							? AttributeModifier.Operation.ADD_MULTIPLIED_BASE : AttributeModifier.Operation.ADD_VALUE));
		}
		Map<Perk, Double> perks = new EnumMap<>(Perk.class);
		JsonObject po = o.getAsJsonObject("perks");
		for (String key : po.keySet()) perks.put(Perk.valueOf(key), po.get(key).getAsDouble());
		return new Node(o.get("id").getAsString(), role, o.get("branch").getAsInt(), o.get("tier").getAsInt(),
				o.get("name").getAsString(), o.get("desc").getAsString(), List.copyOf(attrs), perks);
	}

	public static Map<String, Role> roles() {
		return roles;
	}

	public static Role role(String id) {
		return roles.get(id);
	}

	public static Node node(String id) {
		return nodes.get(id);
	}

	public static Iterable<Node> allNodes() {
		return nodes.values();
	}

	public static int maxLevel() {
		return maxLevel;
	}

	/** Сколько опыта роли нужно, чтобы перейти с уровня {@code level} на следующий. */
	public static int xpToNext(int level) {
		return 20 + 10 * level;
	}
}
