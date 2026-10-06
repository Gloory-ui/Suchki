package com.suchki.magic;

import com.suchki.SuchkiConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/**
 * Магия и местность. Разрушение (воронки, тоннели, выжигание) работает, только если включено в настройках
 * ({@code magicBlockDamage}); ломается понемногу за тик, чтобы большой взрыв не подвешивал сервер.
 * Не ломаются: неразрушимые блоки, очень прочные (обсидиан и крепче — кроме божественного ранга), сундуки и
 * другие хранилища, блоки под защитой спавна.
 *
 * <p>Временные блоки (ледяные стены, каменные шипы, трясина, водяная тюрьма) ставятся всегда — даже если
 * разрушение выключено — только в воздух/траву/воду и сами исчезают через заданное время.
 */
public final class Terrain {
	/** Сколько блоков ломается за тик (все воронки вместе). */
	public static final int BUDGET = 2500;
	/** Предельный радиус воронки (больше — слишком много блоков). */
	public static final double MAX_RADIUS = 18.0;

	/** Чем заканчивается воронка: края. */
	public enum Rim { NONE, FIRE, MAGMA, ICE, RUBBLE, GLASS, SCULK }

	private record Job(ServerLevel level, ServerPlayer caster, List<BlockPos> blocks, float dropChance, Rim rim, int[] index) {}

	private record Temp(ServerLevel level, BlockState original, BlockState placed, long until) {}

	private static final Deque<Job> JOBS = new ArrayDeque<>();
	private static final Map<BlockPos, Temp> TEMPS = new HashMap<>();
	/** Для самопроверки: сколько блоков сломано и поставлено временно. */
	private static int brokenTotal;

	private Terrain() {}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Terrain::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			restoreAll();
			JOBS.clear();
		});
	}

	/** Можно ли сейчас ломать блоки магией. */
	public static boolean griefing(ServerLevel level) {
		SuchkiConfig cfg = SuchkiConfig.INSTANCE;
		if (!cfg.magicBlockDamage) return false;
		return !cfg.magicRespectMobGriefing || level.getGameRules().get(GameRules.MOB_GRIEFING);
	}

	/** Ломается ли блок магией с пределом прочности {@code maxResistance}. */
	public static boolean breakable(ServerLevel level, ServerPlayer caster, BlockPos pos, BlockState state, float maxResistance) {
		if (state.isAir() || !state.getFluidState().isEmpty() && state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) {
			return false;
		}
		if (state.getDestroySpeed(level, pos) < 0) return false;
		if (state.getBlock().getExplosionResistance() >= maxResistance) return false;
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof Container) return false;
		return caster == null || level.mayInteract(caster, pos);
	}

	/** Предел прочности по рангу: божественный ломает и обсидиан, остальные — нет. */
	public static float resistanceFor(Cast c) {
		return c.spell.tier >= Spell.MAX_TIER ? 3000.0F : 1200.0F;
	}

	/**
	 * Воронка: эллипсоид радиуса {@code radius} (по вертикали ×{@code depth}) с неровным краем.
	 * Возвращает, сколько блоков будет сломано (0 — разрушение выключено).
	 */
	public static int crater(Cast c, Vec3 center, double radius, double depth, Rim rim) {
		if (!griefing(c.level)) return 0;
		double r = Math.min(MAX_RADIUS, radius * SuchkiConfig.INSTANCE.magicCraterScale);
		if (r < 0.8) return 0;
		float maxRes = resistanceFor(c);
		BlockPos o = BlockPos.containing(center);
		int ri = Mth.ceil(r) + 1;
		List<BlockPos> list = new ArrayList<>();
		long seed = c.level.random.nextLong();
		for (int dx = -ri; dx <= ri; dx++) {
			for (int dy = -ri; dy <= ri; dy++) {
				for (int dz = -ri; dz <= ri; dz++) {
					double yy = dy / Math.max(0.2, dy < 0 ? depth : 0.85);
					double d = Math.sqrt(dx * dx + yy * yy + dz * dz) + noise(seed, dx, dy, dz) * 0.9;
					if (d > r) continue;
					BlockPos p = o.offset(dx, dy, dz);
					BlockState s = c.level.getBlockState(p);
					if (breakable(c.level, c.caster, p, s, maxRes)) list.add(p);
				}
			}
		}
		list.sort(Comparator.comparingDouble(p -> -p.distSqr(o)));
		queue(c, list, rim);
		return list.size();
	}

	/** Тоннель вдоль отрезка радиуса {@code radius}. */
	public static int tunnel(Cast c, Vec3 from, Vec3 to, double radius, Rim rim) {
		if (!griefing(c.level)) return 0;
		double r = Math.min(6.0, radius * SuchkiConfig.INSTANCE.magicCraterScale);
		float maxRes = resistanceFor(c);
		Vec3 d = to.subtract(from);
		double len = d.length();
		if (len < 0.5 || r < 0.4) return 0;
		Vec3 dir = d.scale(1.0 / len);
		java.util.Set<BlockPos> seen = new java.util.HashSet<>();
		List<BlockPos> list = new ArrayList<>();
		int ri = Mth.ceil(r);
		for (double t = 0; t <= len; t += 0.7) {
			BlockPos o = BlockPos.containing(from.add(dir.scale(t)));
			for (int dx = -ri; dx <= ri; dx++) {
				for (int dy = -ri; dy <= ri; dy++) {
					for (int dz = -ri; dz <= ri; dz++) {
						BlockPos p = o.offset(dx, dy, dz);
						if (!seen.add(p)) continue;
						Vec3 pc = Vec3.atCenterOf(p).subtract(from);
						double along = pc.dot(dir);
						if (along < 0 || along > len) continue;
						if (pc.subtract(dir.scale(along)).lengthSqr() > r * r) continue;
						BlockState s = c.level.getBlockState(p);
						if (breakable(c.level, c.caster, p, s, maxRes)) list.add(p);
					}
				}
			}
		}
		list.sort(Comparator.comparingDouble(p -> Vec3.atCenterOf(p).distanceToSqr(from)));
		queue(c, list, rim);
		return list.size();
	}

	/** Разбить хрупкое в радиусе (стекло, листва, лёд, панели) — даже слабые заклинания ветра и звука. */
	public static int shatter(Cast c, Vec3 center, double radius) {
		if (!griefing(c.level)) return 0;
		BlockPos o = BlockPos.containing(center);
		int ri = Mth.ceil(Math.min(12, radius));
		List<BlockPos> list = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(o.offset(-ri, -ri, -ri), o.offset(ri, ri, ri))) {
			if (p.distSqr(o) > radius * radius) continue;
			BlockState s = c.level.getBlockState(p);
			if (fragile(s) && breakable(c.level, c.caster, p, s, 10)) list.add(p.immutable());
		}
		queue(c, list, Rim.NONE);
		return list.size();
	}

	private static boolean fragile(BlockState s) {
		return s.is(BlockTags.LEAVES) || s.is(BlockTags.ICE) || s.is(Blocks.GLASS) || s.is(Blocks.GLASS_PANE)
				|| s.is(BlockTags.IMPERMEABLE) || s.is(BlockTags.FLOWERS) || s.is(Blocks.SHORT_GRASS) || s.is(Blocks.TALL_GRASS)
				|| s.getBlock().getExplosionResistance() < 0.6F && !s.isAir() && s.getFluidState().isEmpty();
	}

	/** Выжечь поверхность: трава → земля, листва сгорает, местами огонь. Работает при включённом разрушении. */
	public static void scorch(Cast c, Vec3 center, double radius, float fireChance) {
		if (!griefing(c.level)) return;
		BlockPos o = BlockPos.containing(center);
		int ri = Mth.ceil(Math.min(14, radius));
		for (int dx = -ri; dx <= ri; dx++) {
			for (int dz = -ri; dz <= ri; dz++) {
				if (dx * dx + dz * dz > radius * radius) continue;
				for (int dy = 4; dy >= -4; dy--) {
					BlockPos p = o.offset(dx, dy, dz);
					BlockState s = c.level.getBlockState(p);
					if (s.isAir()) continue;
					if (!c.level.mayInteract(c.caster, p)) break;
					if (s.is(BlockTags.LEAVES) || s.is(BlockTags.REPLACEABLE_BY_TREES) || s.is(BlockTags.FLOWERS)) {
						c.level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
						continue;
					}
					if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.PODZOL) || s.is(Blocks.MYCELIUM)) {
						c.level.setBlock(p, Blocks.COARSE_DIRT.defaultBlockState(), 3);
					}
					BlockPos up = p.above();
					if (c.level.random.nextFloat() < fireChance && c.level.getBlockState(up).isAir()) {
						c.level.setBlock(up, BaseFireBlock.getState(c.level, up), 3);
					}
					break;
				}
			}
		}
	}

	private static double noise(long seed, int x, int y, int z) {
		long h = seed ^ (x * 73856093L) ^ (y * 19349663L) ^ (z * 83492791L);
		h = (h ^ (h >>> 13)) * 0x5bd1e995L;
		return ((h >>> 20) & 0xFFFF) / 65535.0 - 0.5;
	}

	private static void queue(Cast c, List<BlockPos> blocks, Rim rim) {
		if (blocks.isEmpty()) return;
		JOBS.add(new Job(c.level, c.caster, blocks, (float) SuchkiConfig.INSTANCE.magicDropChance, rim, new int[1]));
	}

	private static void tick(MinecraftServer server) {
		int budget = BUDGET;
		while (budget > 0 && !JOBS.isEmpty()) {
			Job job = JOBS.peek();
			budget -= work(job, budget);
			if (job.index[0] >= job.blocks.size()) {
				JOBS.poll();
				finishRim(job);
			}
		}
		if (server.getTickCount() % 5 == 0 && !TEMPS.isEmpty()) restoreDue(false);
	}

	private static int work(Job job, int budget) {
		int done = 0;
		while (done < budget && job.index[0] < job.blocks.size()) {
			BlockPos p = job.blocks.get(job.index[0]++);
			BlockState s = job.level.getBlockState(p);
			if (s.isAir() || TEMPS.containsKey(p)) continue;
			if (job.level.random.nextFloat() < job.dropChance) {
				Block.dropResources(s, job.level, p, job.level.getBlockEntity(p), job.caster, ItemStack.EMPTY);
			}
			job.level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
			brokenTotal++;
			done++;
		}
		return Math.max(1, done);
	}

	/** Край воронки: огонь, магма, лёд, щебень… на дне и стенках. */
	private static void finishRim(Job job) {
		if (job.rim == Rim.NONE) return;
		for (BlockPos p : job.blocks) {
			if (job.level.random.nextFloat() > 0.22F) continue;
			BlockPos below = p.below();
			BlockState floor = job.level.getBlockState(below);
			if (!job.level.getBlockState(p).isAir() || floor.isAir() || !floor.isSolidRender()) continue;
			switch (job.rim) {
				case FIRE -> job.level.setBlock(p, BaseFireBlock.getState(job.level, p), 3);
				case MAGMA -> job.level.setBlock(below, job.level.random.nextBoolean() ? Blocks.MAGMA_BLOCK.defaultBlockState()
						: Blocks.BLACKSTONE.defaultBlockState(), 3);
				case ICE -> job.level.setBlock(below, Blocks.PACKED_ICE.defaultBlockState(), 3);
				case RUBBLE -> job.level.setBlock(below, job.level.random.nextBoolean() ? Blocks.COBBLESTONE.defaultBlockState()
						: Blocks.GRAVEL.defaultBlockState(), 3);
				case GLASS -> job.level.setBlock(below, Blocks.TINTED_GLASS.defaultBlockState(), 3);
				case SCULK -> job.level.setBlock(below, Blocks.SCULK.defaultBlockState(), 3);
				default -> {
				}
			}
		}
	}

	// --- Временные блоки ---

	/**
	 * Поставить временный блок на {@code ticks} тиков. Ставится только в воздух, траву, снег, огонь или воду
	 * (если {@code solid} — ещё и вместо обычных блоков, кроме хранилищ и неразрушимых, и только при включённом
	 * разрушении). По истечении возвращается прежний блок.
	 */
	public static boolean temp(Cast c, BlockPos pos, BlockState state, int ticks, boolean solid) {
		BlockPos p = pos.immutable();
		BlockState cur = c.level.getBlockState(p);
		Temp old = TEMPS.get(p);
		if (old != null) {
			if (c.level.getBlockState(p) == old.placed) {
				c.level.setBlock(p, state, 3);
				TEMPS.put(p, new Temp(c.level, old.original, state, Math.max(old.until, c.level.getGameTime() + ticks)));
				return true;
			}
			TEMPS.remove(p);
		}
		boolean soft = cur.isAir() || cur.canBeReplaced() || cur.is(BlockTags.FIRE) || cur.getFluidState().is(Fluids.WATER)
				|| cur.getFluidState().is(Fluids.FLOWING_WATER);
		if (!soft) {
			if (!solid || !griefing(c.level) || !breakable(c.level, c.caster, p, cur, 1200)) return false;
		}
		if (!c.level.mayInteract(c.caster, p)) return false;
		BlockState original = cur.getFluidState().isSource() ? cur : soft && !cur.getFluidState().isEmpty() ? Blocks.AIR.defaultBlockState() : cur;
		c.level.setBlock(p, state, 3);
		TEMPS.put(p, new Temp(c.level, original, state, c.level.getGameTime() + ticks));
		return true;
	}

	/** Временная сфера (полая или сплошная) из блоков. Возвращает, сколько поставлено. */
	public static int tempSphere(Cast c, Vec3 center, double radius, BlockState state, int ticks, boolean hollow) {
		BlockPos o = BlockPos.containing(center);
		int ri = Mth.ceil(radius);
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(o.offset(-ri, -ri, -ri), o.offset(ri, ri, ri))) {
			double d = Math.sqrt(p.distSqr(o));
			if (d > radius || hollow && d < radius - 1.1) continue;
			if (temp(c, p, state, ticks + c.level.random.nextInt(10), false)) n++;
		}
		return n;
	}

	/** Заморозить воду в радиусе (вода → лёд) на время. */
	public static int freezeWater(Cast c, Vec3 center, double radius, int ticks) {
		BlockPos o = BlockPos.containing(center);
		int ri = Mth.ceil(Math.min(24, radius));
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(o.offset(-ri, -3, -ri), o.offset(ri, 3, ri))) {
			if (p.distSqr(o) > radius * radius) continue;
			BlockState s = c.level.getBlockState(p);
			if (s.getFluidState().is(Fluids.WATER) && s.getFluidState().isSource() && c.level.getBlockState(p.above()).isAir()) {
				if (temp(c, p, Blocks.FROSTED_ICE.defaultBlockState(), ticks, false)) n++;
			}
		}
		return n;
	}

	/** Вернуть временные блоки, срок которых вышел (или все). */
	private static void restoreDue(boolean all) {
		Iterator<Map.Entry<BlockPos, Temp>> it = TEMPS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<BlockPos, Temp> e = it.next();
			Temp t = e.getValue();
			if (!all && t.level.getGameTime() < t.until) continue;
			it.remove();
			if (t.level.getBlockState(e.getKey()) == t.placed) t.level.setBlock(e.getKey(), t.original, 3);
		}
	}

	/** Вернуть все временные блоки (остановка сервера, самопроверка). */
	public static void restoreAll() {
		restoreDue(true);
	}

	public static boolean isTemp(BlockPos pos) {
		return TEMPS.containsKey(pos);
	}

	// --- Для самопроверки ---

	/** Доломать всё, что стоит в очереди, прямо сейчас. */
	public static void flushNow() {
		while (!JOBS.isEmpty()) {
			Job job = JOBS.poll();
			work(job, Integer.MAX_VALUE);
			finishRim(job);
		}
	}

	public static int brokenTotal() {
		return brokenTotal;
	}

	public static int tempCount() {
		return TEMPS.size();
	}

	public static int queued() {
		int n = 0;
		for (Job j : JOBS) n += j.blocks.size() - j.index[0];
		return n;
	}
}
