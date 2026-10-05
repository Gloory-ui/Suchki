package com.suchki.enchant;

import com.mojang.math.Transformation;
import com.suchki.skill.Skills;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Лесоруб: дерево не исчезает, а падает. Брёвна и листва превращаются во временные «блоки-картинки»
 * (дисплей-сущности), которые плавно заваливаются в сторону от игрока вокруг пня — с ускорением и
 * небольшим отскоком. При ударе о землю — щепки, звук, а добыча выпадает там, куда упало дерево.
 */
public final class TreeFall {
	/** Метка временных блоков: если сервер выключили посреди падения, при загрузке они удаляются. */
	private static final String TAG = "suchki_tree_fall";
	/** Ключевые кадры падения: тик, угол (градусы), длительность плавного перехода (тики). */
	private static final int[][] KEYFRAMES = {{1, 8, 5}, {6, 30, 5}, {11, 65, 4}, {15, 93, 3}, {18, 88, 2}};
	private static final int IMPACT_TICK = 18;
	private static final int END_TICK = 26;
	private static final int MAX_LEAVES = 250;

	private record Piece(Display.BlockDisplay display, BlockState state, Vector3f offset, List<ItemStack> drops) {}

	private static final class Fall {
		final ServerLevel level;
		final Vec3 pivot;
		final Vector3f axis;
		final List<Piece> pieces;
		int age;

		Fall(ServerLevel level, Vec3 pivot, Vector3f axis, List<Piece> pieces) {
			this.level = level;
			this.pivot = pivot;
			this.axis = axis;
			this.pieces = pieces;
		}
	}

	private static final List<Fall> FALLS = new ArrayList<>();
	/** Живые «блоки-картинки» текущих падений (регистрируются до появления в мире). */
	private static final java.util.Set<net.minecraft.world.entity.Entity> LIVE =
			java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

	private TreeFall() {}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
		// Временные блоки, оставшиеся от прерванного падения (выключение сервера), убираем при загрузке.
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.getTags().contains(TAG) && !LIVE.contains(entity)) entity.discard();
		});
	}

	/** Сколько деревьев падает сейчас (для самопроверки). */
	public static int active() {
		return FALLS.size();
	}

	/**
	 * Повалить дерево. {@code cut} — срубленный блок (уже сломан). false — повалить нельзя
	 * (не хватает прочности топора), тогда вызывающий рубит по-старому.
	 */
	public static boolean start(ServerPlayer player, BlockPos cut, List<BlockPos> logs) {
		ServerLevel level = player.level();
		ItemStack tool = player.getMainHandItem();
		List<BlockPos> allowed = new ArrayList<>();
		for (BlockPos p : logs) {
			BlockState s = level.getBlockState(p);
			if (s.is(BlockTags.LOGS) && PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, p, s, level.getBlockEntity(p))) {
				allowed.add(p);
			}
		}
		if (allowed.isEmpty()) return true;
		if (tool.isDamageableItem() && tool.getMaxDamage() - tool.getDamageValue() - 1 < allowed.size()) return false;
		List<BlockPos> leaves = leaves(level, allowed);

		// Падаем от игрока; если он прямо под деревом — туда, куда смотрит.
		Vec3 pivot = new Vec3(cut.getX() + 0.5, cut.getY() + 1, cut.getZ() + 0.5);
		Vec3 away = pivot.subtract(player.position()).multiply(1, 0, 1);
		if (away.lengthSqr() < 0.25) away = player.getLookAngle().multiply(1, 0, 1);
		if (away.lengthSqr() < 1e-4) away = Vec3.atLowerCornerOf(player.getDirection().getUnitVec3i());
		away = away.normalize();
		Vector3f axis = new Vector3f((float) away.z, 0, (float) -away.x).normalize();

		List<Piece> pieces = new ArrayList<>();
		int radius = 2;
		for (BlockPos p : allowed) radius = Math.max(radius, (int) Math.ceil(Math.sqrt(p.distSqr(cut))) + 2);
		for (BlockPos p : allowed) pieces.add(piece(level, player, tool, p, pivot, radius, true));
		for (BlockPos p : leaves) pieces.add(piece(level, player, tool, p, pivot, radius, false));

		// Убираем блоки (сначала листву) и платим как за обычную рубку: прочность, статистика, опыт роли.
		for (BlockPos p : leaves) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		for (BlockPos p : allowed) {
			BlockState s = level.getBlockState(p);
			level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			player.awardStat(Stats.BLOCK_MINED.get(s.getBlock()));
		}
		tool.hurtAndBreak(allowed.size(), player, EquipmentSlot.MAINHAND);
		Skills.addXp(player, "lumberjack", 2.0 * allowed.size());
		level.playSound(null, pivot.x, pivot.y, pivot.z, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 0.8F, 0.55F);
		FALLS.add(new Fall(level, pivot, axis, pieces));
		return true;
	}

	/** Листва этого дерева: связная с брёвнами, не поставленная игроком, в пределах 4 блоков. */
	private static List<BlockPos> leaves(ServerLevel level, List<BlockPos> logs) {
		Map<BlockPos, Integer> depth = new HashMap<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		for (BlockPos log : logs) {
			depth.put(log, 0);
			queue.add(log);
		}
		List<BlockPos> out = new ArrayList<>();
		while (!queue.isEmpty() && out.size() < MAX_LEAVES) {
			BlockPos p = queue.poll();
			int d = depth.get(p);
			if (d >= 4) continue;
			for (Direction dir : Direction.values()) {
				BlockPos n = p.relative(dir);
				if (depth.containsKey(n)) continue;
				BlockState s = level.getBlockState(n);
				if (s.is(BlockTags.LEAVES) && s.hasProperty(LeavesBlock.PERSISTENT) && !s.getValue(LeavesBlock.PERSISTENT)) {
					depth.put(n, d + 1);
					queue.add(n);
					out.add(n);
				}
			}
		}
		return out;
	}

	private static Piece piece(ServerLevel level, ServerPlayer player, ItemStack tool, BlockPos p, Vec3 pivot, int radius, boolean log) {
		BlockState state = level.getBlockState(p);
		// Брёвна — как срубленные этим топором (Автоплавка, навыки, Магнит), листва — как при опадании.
		List<ItemStack> drops = log ? Block.getDrops(state, level, p, level.getBlockEntity(p), player, tool)
				: Block.getDrops(state, level, p, level.getBlockEntity(p));
		Display.BlockDisplay d = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
		d.setBlockState(state);
		d.setPos(pivot.x, pivot.y, pivot.z);
		Vector3f offset = new Vector3f((float) (p.getX() - pivot.x), (float) (p.getY() - pivot.y), (float) (p.getZ() - pivot.z));
		d.setTransformation(new Transformation(new Vector3f(offset), new Quaternionf(), null, null));
		d.setWidth(radius * 2);
		d.setHeight(radius * 2);
		d.addTag(TAG);
		LIVE.add(d);
		level.addFreshEntity(d);
		return new Piece(d, state, offset, drops);
	}

	private static void tick() {
		for (Iterator<Fall> it = FALLS.iterator(); it.hasNext(); ) {
			Fall f = it.next();
			f.age++;
			for (int[] k : KEYFRAMES) {
				if (k[0] == f.age) rotate(f, k[1], k[2]);
			}
			if (f.age == IMPACT_TICK) impact(f);
			if (f.age >= END_TICK) {
				finish(f);
				it.remove();
			}
		}
	}

	private static void rotate(Fall f, float degrees, int duration) {
		Quaternionf r = new Quaternionf().rotationAxis((float) Math.toRadians(degrees), f.axis);
		for (Piece p : f.pieces) {
			Vector3f t = r.transform(new Vector3f(p.offset));
			p.display.setTransformation(new Transformation(t, new Quaternionf(r), null, null));
			p.display.setTransformationInterpolationDelay(0);
			p.display.setTransformationInterpolationDuration(duration);
		}
	}

	/** Где блок оказался после падения (центр). */
	private static Vec3 landed(Fall f, Piece p) {
		Quaternionf r = new Quaternionf().rotationAxis((float) Math.toRadians(90), f.axis);
		Vector3f c = r.transform(new Vector3f(p.offset).add(0.5F, 0.5F, 0.5F));
		return f.pivot.add(c.x, c.y, c.z);
	}

	private static void impact(Fall f) {
		Vec3 top = f.pivot;
		int shown = 0;
		for (Piece p : f.pieces) {
			Vec3 at = landed(f, p);
			if (at.distanceToSqr(f.pivot) > top.distanceToSqr(f.pivot)) top = at;
			if (shown++ % 2 == 0) {
				f.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, p.state), at.x, at.y, at.z, 4, 0.3, 0.2, 0.3, 0.05);
			}
		}
		f.level.playSound(null, top.x, top.y, top.z, SoundEvents.GENERIC_BIG_FALL, SoundSource.BLOCKS, 0.9F, 0.7F);
		f.level.playSound(null, top.x, top.y, top.z, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 0.7F, 0.6F);
	}

	private static void finish(Fall f) {
		for (Piece p : f.pieces) {
			Vec3 at = landed(f, p);
			BlockPos pos = BlockPos.containing(at);
			for (ItemStack s : p.drops) Block.popResource(f.level, pos, s);
			p.display.discard();
			LIVE.remove(p.display);
		}
	}
}
