package com.suchki.magic;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * Фокус мага: посох или гримуар. Зажал ПКМ — пошёл каст выбранного заклинания (зарядка или поток),
 * отпустил — выпуск. Вся логика — в {@link Magic}; здесь только характеристики фокуса.
 */
public class FocusItem extends Item {
	/** Множитель скорости каста (больше — быстрее). */
	public final float castSpeed;
	/** Предел накопления (максимальная сила заряженного заклинания). */
	public final float cap;
	/** Множитель цены маны. */
	public final float costMult;
	/** Предел разгона потока. */
	public final float channelCap;
	/** Высший круг заклинаний, который выдерживает фокус. */
	public final int maxTier;
	/** Посох (держат двумя руками) или книга (колдует свободная рука). */
	public final boolean staff;

	public FocusItem(Properties properties, float castSpeed, float cap, float costMult, float channelCap, int maxTier, boolean staff) {
		super(properties);
		this.castSpeed = castSpeed;
		this.cap = cap;
		this.costMult = costMult;
		this.channelCap = channelCap;
		this.maxTier = maxTier;
		this.staff = staff;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		Component problem = Magic.startProblem(player, this);
		if (problem != null) {
			if (!level.isClientSide()) player.displayClientMessage(problem.copy().withStyle(ChatFormatting.RED), true);
			return InteractionResult.FAIL;
		}
		if (player instanceof ServerPlayer sp) Magic.start(sp, this);
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return 72000;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.NONE;
	}

	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
		if (entity instanceof ServerPlayer sp) Magic.tick(sp, this);
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
		if (entity instanceof ServerPlayer sp) Magic.release(sp);
		return true;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
		out.accept(Component.translatable("suchki.focus.tier", Component.translatable("suchki.tier." + maxTier))
				.withStyle(ChatFormatting.DARK_PURPLE));
		out.accept(Component.translatable("suchki.focus.speed", pct(castSpeed)).withStyle(ChatFormatting.GRAY));
		out.accept(Component.translatable("suchki.focus.cap", num(cap)).withStyle(ChatFormatting.GRAY));
		out.accept(Component.translatable("suchki.focus.cost", pct(costMult)).withStyle(ChatFormatting.GRAY));
		out.accept(Component.translatable(staff ? "suchki.focus.staff_hint" : "suchki.focus.book_hint").withStyle(ChatFormatting.DARK_GRAY));
	}

	private static String pct(float v) {
		return Math.round(v * 100) + "%";
	}

	private static String num(float v) {
		return v == Math.round(v) ? String.valueOf((int) v) : String.format(java.util.Locale.ROOT, "%.2f", v).replaceAll("0$", "").replace('.', ',');
	}
}
