package com.suchki.mixin;

import com.suchki.enchant.Perks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Спавнер, добытый Шёлковым касанием, при установке снова призывает своего моба. */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
	@Inject(
			method = "updateCustomBlockEntityTag(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/block/state/BlockState;)Z",
			at = @At("RETURN"))
	private void suchki$restoreSpawner(BlockPos pos, Level level, Player player, ItemStack stack, BlockState state,
			CallbackInfoReturnable<Boolean> cir) {
		if (level.isClientSide()) return;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || !(level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner)) return;
		data.copyTag().getString(Perks.SPAWNER_ENTITY_KEY)
				.map(Identifier::tryParse)
				.flatMap(BuiltInRegistries.ENTITY_TYPE::getOptional)
				.ifPresent(type -> {
					spawner.setEntityId(type, level.getRandom());
					spawner.setChanged();
					level.sendBlockUpdated(pos, state, state, 3);
				});
	}
}
