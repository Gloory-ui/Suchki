package com.suchki.magic;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jetbrains.annotations.Nullable;

/**
 * Призрачное оружие и инструменты (заклинания Колдовства): не ломаются, исчезают по времени,
 * при выбрасывании и не кладутся в сундуки и мешки.
 */
public class SpectralItem extends Item {
	/** Сколько живёт предмет без отметки времени (например, взятый из творческой вкладки). */
	private static final long DEFAULT_LIFE = 2400;

	public SpectralItem(Properties properties) {
		super(properties);
	}

	public static boolean is(ItemStack stack) {
		return stack.getItem() instanceof SpectralItem;
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
		Long expires = stack.get(MagicItems.EXPIRES);
		long now = level.getGameTime();
		if (expires == null) {
			stack.set(MagicItems.EXPIRES, now + DEFAULT_LIFE);
		} else if (now >= expires) {
			stack.setCount(0);
			MagicFx.spectralVanish(level, entity.position().add(0, 1, 0));
		}
	}

	@Override
	public boolean canFitInsideContainerItems() {
		return false;
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
		out.accept(Component.translatable("suchki.spectral.hint").withStyle(ChatFormatting.DARK_AQUA));
	}
}
