package com.suchki.magic;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** Книга заклинаний одной школы; какое заклинание в ней — в компоненте {@link MagicItems#SPELL}. ПКМ — изучить. */
public class SpellTomeItem extends Item {
	public final School school;

	public SpellTomeItem(Properties properties, School school) {
		super(properties);
		this.school = school;
	}

	public static Spell spellOf(ItemStack stack) {
		String id = stack.get(MagicItems.SPELL);
		return id == null ? null : Spells.get(id);
	}

	@Override
	public Component getName(ItemStack stack) {
		Spell spell = spellOf(stack);
		return spell == null ? super.getName(stack) : Component.translatable("item.suchki.tome.named", spell.displayName());
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		Spell spell = spellOf(stack);
		if (spell == null) return InteractionResult.PASS;
		if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
		switch (Magic.learn(sp, spell)) {
			case LEARNED -> {
				if (!sp.hasInfiniteMaterials()) stack.shrink(1);
				sp.sendSystemMessage(Component.translatable("suchki.magic.learned", spell.displayName())
						.withStyle(s -> s.withColor(spell.school.color)));
				MagicFx.learned(sp.level(), sp, spell);
			}
			case NOT_MAGE -> sp.displayClientMessage(Component.translatable("suchki.magic.learn_not_mage").withStyle(ChatFormatting.RED), true);
			case LOW_LEVEL -> sp.displayClientMessage(Component.translatable("suchki.magic.learn_level", spell.requiredLevel())
					.withStyle(ChatFormatting.RED), true);
			case KNOWN -> sp.displayClientMessage(Component.translatable("suchki.magic.learn_known").withStyle(ChatFormatting.GRAY), true);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
		Spell spell = spellOf(stack);
		if (spell == null) return;
		describe(spell, out);
		out.accept(Component.translatable("suchki.tome.hint", spell.requiredLevel()).withStyle(ChatFormatting.DARK_GRAY));
	}

	/** Описание заклинания для подсказок (книга, колесо выбора). */
	public static void describe(Spell spell, Consumer<Component> out) {
		out.accept(Component.translatable("suchki.spell.line", spell.school.displayName(),
				Component.translatable("suchki.tier." + spell.tier)).withStyle(s -> s.withColor(spell.school.color)));
		out.accept(Component.translatable("suchki.style." + spell.style.name().toLowerCase(java.util.Locale.ROOT))
				.withStyle(ChatFormatting.GRAY));
		out.accept(Component.translatable(spell.charged() ? "suchki.spell.mana" : "suchki.spell.mana_per_second", spell.mana)
				.withStyle(ChatFormatting.AQUA));
		out.accept(spell.description().copy().withStyle(ChatFormatting.WHITE));
	}
}
