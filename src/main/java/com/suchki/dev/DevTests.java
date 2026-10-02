package com.suchki.dev;

import com.suchki.Suchki;
import com.suchki.enchant.GrindstoneSplit;
import com.suchki.enchant.SuchkiEnchantments;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * Самопроверка механик на тестовом сервере (только при -Dsuchki.audit=true, в игре этой команды нет).
 * /suchki_test — ставит блоки, ломает их «ненастоящим» игроком с нужными чарами и пишет итог в лог.
 */
public final class DevTests {
	private DevTests() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, ctx, env) -> dispatcher.register(
				Commands.literal("suchki_test").executes(c -> {
					List<String> report = run(c.getSource().getLevel());
					report.forEach(line -> c.getSource().sendSuccess(() -> Component.literal(line), false));
					return report.stream().anyMatch(l -> l.startsWith("FAIL")) ? 0 : 1;
				})));
	}

	private static List<String> run(ServerLevel level) {
		List<String> out = new ArrayList<>();
		try {
			drillAndSmelting(level, out);
			timber(level, out);
			beacon(level, out);
			grindstone(level, out);
		} catch (Exception e) {
			Suchki.LOGGER.error("suchki_test упал", e);
			out.add("FAIL исключение: " + e);
		}
		return out;
	}

	private static ItemStack tool(ServerLevel level, net.minecraft.world.item.Item item, Object... enchants) {
		ItemStack stack = new ItemStack(item);
		ItemEnchantments.Mutable m = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		var lookup = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		for (int i = 0; i < enchants.length; i += 2) {
			@SuppressWarnings("unchecked")
			Holder<Enchantment> h = lookup.getOrThrow((ResourceKey<Enchantment>) enchants[i]);
			m.set(h, (Integer) enchants[i + 1]);
		}
		stack.set(DataComponents.ENCHANTMENTS, m.toImmutable());
		return stack;
	}

	private static FakePlayer player(ServerLevel level, ItemStack held, float pitch) {
		FakePlayer p = FakePlayer.get(level);
		p.setGameMode(GameType.SURVIVAL);
		p.setItemInHand(InteractionHand.MAIN_HAND, held);
		p.setXRot(pitch);
		p.setYRot(0);
		return p;
	}

	private static int count(ServerLevel level, BlockPos center, int r, net.minecraft.world.item.Item item) {
		int n = 0;
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(r))) {
			if (e.getItem().is(item)) n += e.getItem().getCount();
		}
		return n;
	}

	private static void clearItems(ServerLevel level, BlockPos center, int r) {
		level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(r)).forEach(ItemEntity::discard);
	}

	/** Бур + Автоплавка: 3×3 из железной руды, игрок смотрит вниз — должны пропасть все 9 блоков и выпасть слитки. */
	private static void drillAndSmelting(ServerLevel level, List<String> out) {
		BlockPos c = new BlockPos(0, 100, 0);
		clearItems(level, c, 4);
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) level.setBlock(c.offset(dx, 0, dz), Blocks.IRON_ORE.defaultBlockState(), 3);
		FakePlayer p = player(level, tool(level, Items.DIAMOND_PICKAXE, SuchkiEnchantments.DRILL, 1, SuchkiEnchantments.SMELTING, 1), 90);
		p.gameMode.destroyBlock(c);
		int left = 0;
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (!level.getBlockState(c.offset(dx, 0, dz)).isAir()) left++;
		int ingots = count(level, c, 4, Items.IRON_INGOT);
		int raw = count(level, c, 4, Items.RAW_IRON);
		out.add((left == 0 ? "OK  " : "FAIL") + " Бур: осталось блоков " + left + " из 9");
		out.add((ingots >= 9 && raw == 0 ? "OK  " : "FAIL") + " Автоплавка: слитков " + ingots + ", сырого железа " + raw);
		clearItems(level, c, 4);
	}

	/** Лесоруб: ствол из 6 брёвен с листвой сверху — рубим нижнее, должен исчезнуть весь ствол. */
	private static void timber(ServerLevel level, List<String> out) {
		BlockPos base = new BlockPos(10, 100, 0);
		for (int y = 0; y < 6; y++) level.setBlock(base.above(y), Blocks.OAK_LOG.defaultBlockState(), 3);
		level.setBlock(base.above(6), Blocks.OAK_LEAVES.defaultBlockState(), 3);
		FakePlayer p = player(level, tool(level, Items.DIAMOND_AXE, SuchkiEnchantments.TIMBER, 1), 0);
		p.gameMode.destroyBlock(base);
		int left = 0;
		for (int y = 0; y < 6; y++) if (level.getBlockState(base.above(y)).is(Blocks.OAK_LOG)) left++;
		out.add((left == 0 ? "OK  " : "FAIL") + " Лесоруб: осталось брёвен " + left + " из 6");
		clearItems(level, base, 8);
	}

	/** Маяк: пирамида из 5 слоёв железных блоков — updateBase должен вернуть 5. */
	private static void beacon(ServerLevel level, List<String> out) throws Exception {
		BlockPos top = new BlockPos(40, 120, 0);
		for (int layer = 1; layer <= 5; layer++) {
			for (int dx = -layer; dx <= layer; dx++) for (int dz = -layer; dz <= layer; dz++) {
				level.setBlock(top.offset(dx, -layer, dz), Blocks.IRON_BLOCK.defaultBlockState(), 2);
			}
		}
		Method m = BeaconBlockEntity.class.getDeclaredMethod("updateBase", net.minecraft.world.level.Level.class, int.class, int.class, int.class);
		m.setAccessible(true);
		int tiers = (int) m.invoke(null, level, top.getX(), top.getY(), top.getZ());
		out.add((tiers == 5 ? "OK  " : "FAIL") + " Маяк: ярусов " + tiers + " (нужно 5)");
	}

	/** Разделение чар: меч с Остротой V и проклятием — на книгу уходит только Острота, цена 5. */
	private static void grindstone(ServerLevel level, List<String> out) {
		ItemStack sword = tool(level, Items.IRON_SWORD, Enchantments.SHARPNESS, 5, Enchantments.BINDING_CURSE, 1);
		int src = GrindstoneSplit.sourceSlot(sword, new ItemStack(Items.BOOK));
		ItemStack book = GrindstoneSplit.result(sword);
		ItemStack stripped = GrindstoneSplit.stripped(sword);
		int bookEnchants = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).size();
		int leftOnSword = stripped.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).size();
		boolean ok = src == 0 && bookEnchants == 1 && leftOnSword == 1 && GrindstoneSplit.cost(sword) == 5;
		out.add((ok ? "OK  " : "FAIL") + " Точильный камень: ячейка " + src + ", чар на книге " + bookEnchants
				+ ", на мече осталось " + leftOnSword + " (проклятие), цена " + GrindstoneSplit.cost(sword));
	}
}
