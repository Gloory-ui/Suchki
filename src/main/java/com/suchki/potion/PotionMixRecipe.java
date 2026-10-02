package com.suchki.potion;

import com.suchki.SuchkiConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Смесь зелий: 2–3 зелья одного вида (питьевые, взрывные или туманные) на верстаке → одно зелье
 * со всеми эффектами. Уже смешанное зелье считается за столько исходных, из скольких оно собрано;
 * всего в смеси — не больше {@link SuchkiConfig#maxPotionsInMix}. Лишние склянки возвращаются.
 */
public class PotionMixRecipe extends CustomRecipe {
	/** Имя для ключа перевода item.minecraft.potion.effect.suchki_mix и т. п. */
	public static final String MIX_NAME = "suchki_mix";
	private static final String COUNT_KEY = "suchki_mix_count";

	public PotionMixRecipe(CraftingBookCategory category) {
		super(category);
	}

	private static boolean isPotion(ItemStack stack) {
		return stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION);
	}

	private static int sourceCount(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) return 1;
		return Math.max(1, data.copyTag().getIntOr(COUNT_KEY, 1));
	}

	private static boolean hasEffects(ItemStack stack) {
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		return contents != null && contents.getAllEffects().iterator().hasNext();
	}

	/** Зелья из сетки, если набор подходит для смешивания, иначе null. */
	private static List<ItemStack> collect(CraftingInput input) {
		List<ItemStack> potions = new ArrayList<>();
		Item kind = null;
		int sources = 0;
		for (int i = 0; i < input.size(); i++) {
			ItemStack stack = input.getItem(i);
			if (stack.isEmpty()) continue;
			if (!isPotion(stack) || !hasEffects(stack)) return null;
			if (kind == null) kind = stack.getItem();
			else if (stack.getItem() != kind) return null;
			sources += sourceCount(stack);
			potions.add(stack);
		}
		if (potions.size() < 2 || sources > SuchkiConfig.INSTANCE.maxPotionsInMix) return null;
		return potions;
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		return collect(input) != null;
	}

	@Override
	public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
		List<ItemStack> potions = collect(input);
		if (potions == null) return ItemStack.EMPTY;

		// Одинаковые эффекты сливаются: берётся больший уровень, при равном — большая длительность.
		Map<Holder<MobEffect>, MobEffectInstance> merged = new LinkedHashMap<>();
		int sources = 0;
		for (ItemStack stack : potions) {
			sources += sourceCount(stack);
			for (MobEffectInstance effect : stack.get(DataComponents.POTION_CONTENTS).getAllEffects()) {
				merged.merge(effect.getEffect(), new MobEffectInstance(effect), PotionMixRecipe::stronger);
			}
		}

		List<MobEffectInstance> effects = new ArrayList<>(merged.values());
		ItemStack result = new ItemStack(potions.get(0).getItem());
		int color = PotionContents.getColorOptional(effects).orElse(PotionContents.BASE_POTION_COLOR);
		result.set(DataComponents.POTION_CONTENTS,
				new PotionContents(Optional.empty(), Optional.of(color), effects, Optional.of(MIX_NAME)));
		CompoundTag tag = new CompoundTag();
		tag.putInt(COUNT_KEY, sources);
		result.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return result;
	}

	private static MobEffectInstance stronger(MobEffectInstance a, MobEffectInstance b) {
		if (a.getAmplifier() != b.getAmplifier()) return a.getAmplifier() > b.getAmplifier() ? a : b;
		if (a.isInfiniteDuration()) return a;
		if (b.isInfiniteDuration()) return b;
		return a.getDuration() >= b.getDuration() ? a : b;
	}

	@Override
	public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
		// Из N зелий получается одно — остальные N−1 склянок возвращаются пустыми (кроме первой).
		NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
		boolean first = true;
		for (int i = 0; i < input.size(); i++) {
			if (!isPotion(input.getItem(i))) continue;
			if (first) first = false;
			else remaining.set(i, new ItemStack(Items.GLASS_BOTTLE));
		}
		return remaining;
	}

	@Override
	public RecipeSerializer<PotionMixRecipe> getSerializer() {
		return SuchkiPotions.MIX_SERIALIZER;
	}
}
