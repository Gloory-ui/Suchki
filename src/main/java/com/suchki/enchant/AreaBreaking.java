package com.suchki.enchant;

import com.suchki.SuchkiConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Бур (3×3 для кирки и лопаты) и Лесоруб (всё дерево целиком). Присел — работает как обычный инструмент.
 * Дополнительные блоки ломаются через destroyBlock игрока: дроп, Удача, Автоплавка и износ — как при обычной добыче.
 */
public final class AreaBreaking {
	/** Чтобы блоки, сломанные Буром/Лесорубом, не запускали их снова. */
	private static final ThreadLocal<Boolean> BUSY = ThreadLocal.withInitial(() -> false);

	private AreaBreaking() {}

	public static void register() {
		PlayerBlockBreakEvents.AFTER.register(AreaBreaking::afterBreak);
	}

	/** Сломать блоки как игрок. false — инструмент на исходе, дальше нельзя. */
	private static boolean breakAll(ServerPlayer player, List<BlockPos> blocks, float centerHardness) {
		Level level = player.level();
		BUSY.set(true);
		try {
			for (BlockPos p : blocks) {
				ItemStack held = player.getMainHandItem();
				// Не ломаем инструмент до конца и не трогаем то, что этим инструментом не добывается.
				if (held.isEmpty() || held.isDamageableItem() && held.getDamageValue() >= held.getMaxDamage() - 1) return false;
				BlockState other = level.getBlockState(p);
				float hardness = other.getDestroySpeed(level, p);
				if (other.isAir() || hardness < 0 || hardness > centerHardness + 2.0F || !held.isCorrectToolForDrops(other)) continue;
				player.gameMode.destroyBlock(p);
			}
			return true;
		} finally {
			BUSY.set(false);
		}
	}

	private static void afterBreak(Level level, Player player, BlockPos pos, BlockState state,
			net.minecraft.world.level.block.entity.BlockEntity be) {
		if (BUSY.get() || !(level instanceof ServerLevel) || !(player instanceof ServerPlayer sp) || player.isShiftKeyDown()) return;
		ItemStack tool = player.getMainHandItem();
		float centerHardness = state.getDestroySpeed(level, pos);
		if (Ench.level(player, SuchkiEnchantments.DRILL, tool) > 0 && tool.isCorrectToolForDrops(state)) {
			breakAll(sp, drillArea(player, pos), centerHardness); // Бур — сразу, это один удар
		} else if (Ench.level(player, SuchkiEnchantments.TIMBER, tool) > 0 && state.is(BlockTags.LOGS)) {
			List<BlockPos> logs = tree(level, pos, state);
			if (logs.isEmpty()) return;
			breakAll(sp, logs, centerHardness);
		}
	}

	/** Плоскость 3×3 вокруг блока, перпендикулярная взгляду игрока. */
	private static List<BlockPos> drillArea(Player player, BlockPos center) {
		Direction look = Direction.orderedByNearest(player)[0];
		List<BlockPos> out = new ArrayList<>(8);
		for (int a = -1; a <= 1; a++) {
			for (int b = -1; b <= 1; b++) {
				if (a == 0 && b == 0) continue;
				out.add(switch (look.getAxis()) {
					case Y -> center.offset(a, 0, b);
					case X -> center.offset(0, a, b);
					case Z -> center.offset(a, b, 0);
				});
			}
		}
		return out;
	}

	/**
	 * Связанные брёвна того же вида (включая диагонали), не больше лимита из конфига.
	 * Рубим, только если рядом есть листва — чтобы не разобрать постройку из брёвен.
	 */
	private static List<BlockPos> tree(Level level, BlockPos start, BlockState log) {
		int limit = SuchkiConfig.INSTANCE.timberMaxLogs;
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		List<BlockPos> logs = new ArrayList<>();
		boolean leaves = false;
		queue.add(start);
		seen.add(start);
		while (!queue.isEmpty() && logs.size() < limit) {
			BlockPos p = queue.poll();
			for (BlockPos n : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
				if (seen.contains(n)) continue;
				BlockState s = level.getBlockState(n);
				if (s.is(BlockTags.LEAVES)) leaves = true;
				if (s.is(log.getBlock()) && n.getY() >= start.getY()) {
					BlockPos copy = n.immutable();
					seen.add(copy);
					queue.add(copy);
					logs.add(copy);
				}
			}
		}
		return leaves ? logs : List.of();
	}
}
