package com.suchki.potion;

import com.suchki.Suchki;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistryBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;

/**
 * Сильные зелья в варочной стойке:
 * <ul>
 *   <li>эхо-осколок + зелье II уровня → то же зелье III уровня (Сила II → Сила III);</li>
 *   <li>аметистовый осколок + усиленное или продлённое зелье → вдвое дольше (Сила II 1:30 → 3:00).</li>
 * </ul>
 * Новые зелья называются как ванильные («Зелье силы»), уровень и время видны в подсказке.
 */
public final class StrongPotions {
	private record Mix(Holder<Potion> from, net.minecraft.world.item.Item ingredient, Holder<Potion> to) {}

	private static final List<Mix> MIXES = new ArrayList<>();

	private StrongPotions() {}

	public static void init() {
		// Снимок ванильных зелий до регистрации своих (иначе обойдём и новые).
		List<Holder.Reference<Potion>> vanilla = BuiltInRegistries.POTION.listElements()
				.filter(h -> h.key().identifier().getNamespace().equals("minecraft")).toList();
		for (Holder.Reference<Potion> potion : vanilla) {
			String path = potion.key().identifier().getPath();
			String base = path.replaceFirst("^(strong|long)_", "");
			boolean timed = !potion.value().hasInstantEffects();
			if (path.startsWith("strong_")) {
				Holder<Potion> third = register(path.replace("strong_", "") + "_iii", base, potion.value(), 1, 1.0);
				MIXES.add(new Mix(potion, Items.ECHO_SHARD, third));
				if (timed) {
					MIXES.add(new Mix(potion, Items.AMETHYST_SHARD, register("long_" + path, base, potion.value(), 0, 2.0)));
					MIXES.add(new Mix(third, Items.AMETHYST_SHARD, register("long_" + base + "_iii", base, third.value(), 0, 2.0)));
				}
			} else if (path.startsWith("long_") && timed) {
				MIXES.add(new Mix(potion, Items.AMETHYST_SHARD, register("very_" + path, base, potion.value(), 0, 2.0)));
			}
		}
		FabricBrewingRecipeRegistryBuilder.BUILD.register(builder -> {
			for (Mix m : MIXES) builder.addMix(m.from(), m.ingredient(), m.to());
		});
	}

	/** Копия зелья: уровень эффектов +{@code extraAmplifier}, длительность ×{@code durationFactor}. */
	private static Holder<Potion> register(String id, String translationName, Potion source, int extraAmplifier, double durationFactor) {
		MobEffectInstance[] effects = source.getEffects().stream().map(e -> new MobEffectInstance(e.getEffect(),
				e.isInfiniteDuration() ? e.getDuration() : (int) Math.round(e.getDuration() * durationFactor),
				e.getAmplifier() + extraAmplifier, e.isAmbient(), e.isVisible(), e.showIcon())).toArray(MobEffectInstance[]::new);
		return Registry.registerForHolder(BuiltInRegistries.POTION,
				Identifier.fromNamespaceAndPath(Suchki.MOD_ID, id), new Potion(translationName, effects));
	}
}
