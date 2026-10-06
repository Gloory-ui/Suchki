package com.suchki.magic;

import com.mojang.serialization.Codec;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.UseEffects;

/** Предметы магии: фокусы, книги заклинаний, призрачное оружие и инструменты; своя вкладка творческого режима. */
public final class MagicItems {
	/** Какое заклинание записано в книге. */
	public static final DataComponentType<String> SPELL = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Magic.id("spell"), DataComponentType.<String>builder().persistent(Codec.STRING)
					.networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
	/** Когда исчезнет призрачный предмет (игровое время). */
	public static final DataComponentType<Long> EXPIRES = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Magic.id("expires_at"), DataComponentType.<Long>builder().persistent(Codec.LONG)
					.networkSynchronized(ByteBufCodecs.VAR_LONG).build());

	/** Пока маг колдует — шагом (50% скорости), бежать нельзя. */
	private static final UseEffects CASTING_MOVE = new UseEffects(false, true, 0.5F);

	public static final FocusItem APPRENTICE_STAFF = register("apprentice_staff", p -> new FocusItem(p, 1.0F, 1.75F, 1.0F, 1.2F, 3, true),
			new Item.Properties().stacksTo(1).component(DataComponents.USE_EFFECTS, CASTING_MOVE));
	public static final FocusItem GRIMOIRE = register("grimoire", p -> new FocusItem(p, 1.25F, 1.5F, 0.85F, 1.3F, 4, false),
			new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).component(DataComponents.USE_EFFECTS, CASTING_MOVE));
	public static final FocusItem ARCHMAGE_STAFF = register("archmage_staff", p -> new FocusItem(p, 1.0F, 2.25F, 0.9F, 1.5F, 5, true),
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant().component(DataComponents.USE_EFFECTS, CASTING_MOVE));

	public static final Map<School, SpellTomeItem> TOMES = new EnumMap<>(School.class);

	public static final SpectralItem SPECTRAL_BLADE = register("spectral_blade", SpectralItem::new,
			spectral().sword(ToolMaterial.NETHERITE, 3.0F, -2.4F));
	public static final SpectralItem SPECTRAL_PICKAXE = register("spectral_pickaxe", SpectralItem::new,
			spectral().pickaxe(ToolMaterial.DIAMOND, 1.0F, -2.8F));
	public static final SpectralItem SPECTRAL_AXE = register("spectral_axe", SpectralItem::new,
			spectral().axe(ToolMaterial.DIAMOND, 5.0F, -3.0F));
	public static final SpectralItem SPECTRAL_SHOVEL = register("spectral_shovel", SpectralItem::new,
			spectral().shovel(ToolMaterial.DIAMOND, 1.5F, -3.0F));

	static {
		for (School school : School.values()) {
			TOMES.put(school, register("tome_" + school.id, p -> new SpellTomeItem(p, school),
					new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
		}
	}

	public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Magic.id("magic"),
			FabricItemGroup.builder()
					.title(Component.translatable("itemGroup.suchki.magic"))
					.icon(() -> new ItemStack(ARCHMAGE_STAFF))
					.displayItems((params, out) -> {
						out.accept(APPRENTICE_STAFF);
						out.accept(GRIMOIRE);
						out.accept(ARCHMAGE_STAFF);
						for (Spell spell : Spells.all()) out.accept(tome(spell));
					})
					.build());

	private MagicItems() {}

	static void init() {
		// Регистрация — в статических полях.
	}

	private static Item.Properties spectral() {
		return new Item.Properties().stacksTo(1).rarity(Rarity.RARE).component(DataComponents.UNBREAKABLE, Unit.INSTANCE);
	}

	private static <T extends Item> T register(String name, Function<Item.Properties, T> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Magic.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
	}

	/** Книга с заклинанием. */
	public static ItemStack tome(Spell spell) {
		ItemStack stack = new ItemStack(TOMES.get(spell.school));
		stack.set(SPELL, spell.id);
		return stack;
	}
}
