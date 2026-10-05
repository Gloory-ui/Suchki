package com.suchki.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.suchki.enchant.Ench;
import com.suchki.enchant.SuchkiEnchantments;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import com.suchki.skill.Perk;
import com.suchki.skill.Skills;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Автоплавка: руда и сырые металлы выпадают сразу переплавленными (Удача тоже учитывается).
 * Навыки: шанс двойной руды, брёвен и урожая.
 * Магнит: добыча сразу кладётся в инвентарь; что не влезло — выпадает как обычно.
 */
@Mixin(Block.class)
public abstract class BlockDropsMixin {
	@ModifyReturnValue(
			method = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;",
			at = @At("RETURN"))
	private static List<ItemStack> suchki$enchantedDrops(List<ItemStack> drops, BlockState state, ServerLevel level, BlockPos pos,
			BlockEntity be, Entity entity, ItemStack tool) {
		if (entity == null) return drops;
		if (!tool.isEmpty() && Ench.level(level.registryAccess(), SuchkiEnchantments.SMELTING, tool) > 0) drops = smelt(drops, level);
		if (entity instanceof Player p && skillDouble(p, state, level, tool)) {
			List<ItemStack> doubled = new ArrayList<>(drops.size() * 2);
			for (ItemStack stack : drops) {
				doubled.add(stack);
				doubled.add(stack.copy());
			}
			drops = doubled;
		}
		if (entity instanceof Player player && !tool.isEmpty() && Ench.level(level.registryAccess(), SuchkiEnchantments.MAGNET, tool) > 0) {
			List<ItemStack> left = new ArrayList<>(drops.size());
			boolean picked = false;
			for (ItemStack stack : drops) {
				int before = stack.getCount();
				player.getInventory().add(stack);
				picked |= stack.getCount() < before;
				if (!stack.isEmpty()) left.add(stack);
			}
			if (picked) {
				level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F,
						(level.random.nextFloat() - level.random.nextFloat()) * 1.4F + 2.0F);
			}
			drops = left;
		}
		return drops;
	}

	/** Навыки Шахтёра, Лесоруба и Фермера: шанс удвоить добычу руды, брёвен и созревшего урожая. */
	private static boolean skillDouble(Player player, BlockState state, ServerLevel level, ItemStack tool) {
		if (state.is(ConventionalBlockTags.ORES)) {
			return Ench.level(level.registryAccess(), Enchantments.SILK_TOUCH, tool) == 0 && Skills.roll(player, Perk.ORE_EXTRA);
		}
		if (state.is(BlockTags.LOGS)) return Skills.roll(player, Perk.LOG_EXTRA);
		if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) return Skills.roll(player, Perk.CROP_EXTRA);
		return false;
	}

	private static List<ItemStack> smelt(List<ItemStack> drops, ServerLevel level) {
		List<ItemStack> out = new ArrayList<>(drops.size());
		for (ItemStack stack : drops) {
			if (!stack.is(ConventionalItemTags.RAW_MATERIALS) && !stack.is(ConventionalItemTags.ORES)) {
				out.add(stack);
				continue;
			}
			SingleRecipeInput input = new SingleRecipeInput(stack);
			ItemStack smelted = level.recipeAccess().getRecipeFor(RecipeType.SMELTING, input, level)
					.map(r -> r.value().assemble(input, level.registryAccess()))
					.orElse(ItemStack.EMPTY);
			out.add(smelted.isEmpty() ? stack : smelted.copyWithCount(smelted.getCount() * stack.getCount()));
		}
		return out;
	}
}
