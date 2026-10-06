package com.suchki.magic;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Невидимый магический свет (Светоч): ставится у головы мага и сам гаснет через пару секунд
 * (запланированный тик сохраняется в мире — после перезапуска свет тоже исчезнет).
 */
public final class MagicBlocks {
	static final class MagicLight extends Block {
		MagicLight(Properties properties) {
			super(properties);
		}

		@Override
		protected RenderShape getRenderShape(BlockState state) {
			return RenderShape.INVISIBLE;
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return Shapes.empty();
		}

		@Override
		protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
			level.removeBlock(pos, false);
		}
	}

	/** Сколько тиков горит один блок света после того, как маг ушёл. */
	private static final int LIGHT_TICKS = 30;

	public static final Block MAGIC_LIGHT = register("magic_light", BlockBehaviour.Properties.of()
			.replaceable().noCollision().noLootTable().noOcclusion().instabreak()
			.mapColor(MapColor.NONE).lightLevel(s -> 15));

	/** До какого игрового времени у игрока горит Светоч. */
	private static final Map<UUID, Long> CANDLES = new HashMap<>();

	private MagicBlocks() {}

	private static Block register(String name, BlockBehaviour.Properties props) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Magic.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, new MagicLight(props.setId(key)));
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (CANDLES.isEmpty() || server.getTickCount() % 5 != 0) return;
			CANDLES.entrySet().removeIf(e -> {
				ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
				if (p == null) return true;
				if (p.level().getGameTime() >= e.getValue()) return true;
				light(p);
				return false;
			});
		});
	}

	/** Светоч на {@code ticks} тиков. */
	public static void candle(ServerPlayer player, int ticks) {
		CANDLES.put(player.getUUID(), player.level().getGameTime() + ticks);
		light(player);
	}

	public static long candleUntil(ServerPlayer player) {
		return CANDLES.getOrDefault(player.getUUID(), 0L);
	}

	private static void light(ServerPlayer p) {
		ServerLevel level = p.level();
		BlockPos head = BlockPos.containing(p.getEyePosition()).above();
		BlockPos pos = level.getBlockState(head).isAir() ? head : BlockPos.containing(p.getEyePosition());
		BlockState state = level.getBlockState(pos);
		if (state.is(MAGIC_LIGHT)) {
			level.scheduleTick(pos, MAGIC_LIGHT, LIGHT_TICKS);
		} else if (state.isAir() && !state.is(Blocks.VOID_AIR)) {
			level.setBlock(pos, MAGIC_LIGHT.defaultBlockState(), Block.UPDATE_ALL);
			level.scheduleTick(pos, MAGIC_LIGHT, LIGHT_TICKS);
		}
		MagicFx.candleMote(level, p);
	}
}
