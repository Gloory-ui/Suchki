package com.suchki.magic.terrain;

import com.suchki.Suchki;
import com.suchki.SuchkiConfig;
import com.suchki.magic.Cast;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

/**
 * Безопасное серверное ядро магии местности. Сначала чистая геометрия строит ограниченный список позиций,
 * затем изменения выполняются очередью с общим бюджетом на тик. Перед каждой мутацией защита проверяется снова:
 * чужая постройка или контейнер, появившиеся во время эффекта, не будут уничтожены устаревшим заданием.
 */
public final class MagicTerrain {
	/** Значение бюджета по умолчанию; фактический бюджет берётся из конфигурации. */
	public static final int BLOCKS_PER_TICK = 512;
	/** Жёсткий предел очереди: серия заклинаний не может бесконечно накапливать память. */
	public static final int MAX_QUEUED_BLOCKS = 48_000;
	/** Datapack-тег для блоков, которые никакая магия местности не меняет. */
	public static final TagKey<Block> IMMUNE = TagKey.create(Registries.BLOCK,
			Identifier.fromNamespaceAndPath(Suchki.MOD_ID, "magic_terrain_immune"));
	/** Datapack-настраиваемый список природных блоков для безопасного режима. */
	public static final TagKey<Block> BREAKABLE = TagKey.create(Registries.BLOCK,
			Identifier.fromNamespaceAndPath(Suchki.MOD_ID, "magic_terrain_breakable"));

	/**
	 * Политика одного эффекта. Плагины/конфиг сервера смогут создавать свои профили без изменения заклинаний.
	 * SAFE сохраняет block entities, уважает mobGriefing, ограничивает разрушение природными типами блоков,
	 * не ломает обсидиан и ограничивает эффект 6000 блоками. Ваниль не хранит происхождение блока, поэтому
	 * даже naturalOnly не отличает природный камень от камня, поставленного игроком; для полной защиты нужны claims.
	 */
	public record Policy(boolean enabled, boolean respectMobGriefing, boolean naturalOnly, int maxBlocks, float maxResistance,
			float dropChance) {
		public static final Policy SAFE = new Policy(true, true, true, 6_000, 1_200.0F, 0.0F);

		public Policy {
			maxBlocks = Mth.clamp(maxBlocks, 1, 12_000);
			maxResistance = Mth.clamp(maxResistance, 1.0F, 3_600_000.0F);
			dropChance = Mth.clamp(dropChance, 0.0F, 1.0F);
		}
	}

	/** Снимок серверной конфигурации для нового каста; уже поставленные задания сохраняют исходные лимиты. */
	public static Policy configuredPolicy() {
		SuchkiConfig config = SuchkiConfig.INSTANCE;
		return new Policy(config.magicTerrainDestruction, config.magicTerrainRespectMobGriefing, config.magicTerrainNaturalOnly,
				config.magicTerrainMaxBlocksPerCast(), 1_200.0F, config.magicTerrainDropChance());
	}

	/** Сохраняющийся след поверхности; каждый вид меняет только обычные природные блоки. */
	public enum Trace {
		SCORCH,
		FROST,
		SCULK,
		RUBBLE
	}

	private enum Operation { BREAK, TRACE }

	private static final class Job {
		final ServerLevel level;
		final ServerPlayer caster;
		final List<BlockPos> blocks;
		final Policy policy;
		final Operation operation;
		final Trace trace;
		final long seed;
		int index;

		Job(ServerLevel level, ServerPlayer caster, List<BlockPos> blocks, Policy policy, Operation operation, Trace trace, long seed) {
			this.level = level;
			this.caster = caster;
			this.blocks = blocks;
			this.policy = policy;
			this.operation = operation;
			this.trace = trace;
			this.seed = seed;
		}
	}

	private static final Deque<Job> JOBS = new ArrayDeque<>();
	private static int queuedBlocks;
	private static long mutatedTotal;
	private static boolean initialized;

	private MagicTerrain() {}

	public static void init() {
		if (initialized) return;
		initialized = true;
		ServerTickEvents.END_SERVER_TICK.register(MagicTerrain::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
	}

	/** Активно ли разрушение для мира с учётом политики и gamerule. */
	public static boolean enabled(ServerLevel level, Policy policy) {
		return policy.enabled() && (!policy.respectMobGriefing() || level.getGameRules().get(GameRules.MOB_GRIEFING));
	}

	public static int crater(Cast cast, Vec3 center, double radius, double depth) {
		Policy policy = remainingPolicy(cast);
		if (policy == null) return 0;
		int queued = crater(cast.level, cast.caster, center, radius, depth, effectSeed(cast, center), policy);
		cast.accountTerrainBlocks(queued);
		return queued;
	}

	public static int crater(ServerLevel level, ServerPlayer caster, Vec3 center, double radius, double depth, long seed, Policy policy) {
		return queueBreak(level, caster, TerrainShapes.crater(center, radius, depth, seed, policy.maxBlocks()), policy, seed);
	}

	public static int tunnel(Cast cast, Vec3 from, Vec3 to, double radius) {
		Policy policy = remainingPolicy(cast);
		if (policy == null) return 0;
		int queued = tunnel(cast.level, cast.caster, from, to, radius, effectSeed(cast, from), policy);
		cast.accountTerrainBlocks(queued);
		return queued;
	}

	public static int tunnel(ServerLevel level, ServerPlayer caster, Vec3 from, Vec3 to, double radius, long seed, Policy policy) {
		return queueBreak(level, caster, TerrainShapes.tunnel(from, to, radius, policy.maxBlocks()), policy, seed);
	}

	public static int fissure(Cast cast, Vec3 origin, Vec3 direction, double length, double width, int depth) {
		Policy policy = remainingPolicy(cast);
		if (policy == null) return 0;
		int queued = fissure(cast.level, cast.caster, origin, direction, length, width, depth, effectSeed(cast, origin), policy);
		cast.accountTerrainBlocks(queued);
		return queued;
	}

	public static int fissure(ServerLevel level, ServerPlayer caster, Vec3 origin, Vec3 direction, double length, double width, int depth,
			long seed, Policy policy) {
		return queueBreak(level, caster, TerrainShapes.fissure(origin, direction, length, width, depth, seed, policy.maxBlocks()), policy, seed);
	}

	/**
	 * Оставить след между точками. Точки должны быть близки к поверхности; поиск пола идёт на 4 блока вверх и
	 * на 12 вниз, чтобы не загружать далёкие чанки и не прожигать пещеры под полётом высоко в небе.
	 */
	public static int trail(Cast cast, Vec3 from, Vec3 to, double width, Trace trace) {
		Policy policy = remainingPolicy(cast);
		if (policy == null) return 0;
		int queued = trail(cast.level, cast.caster, from, to, width, trace, effectSeed(cast, from), policy);
		cast.accountTerrainBlocks(queued);
		return queued;
	}

	private static Policy remainingPolicy(Cast cast) {
		Policy configured = configuredPolicy();
		int remaining = cast.terrainBlocksRemaining(configured.maxBlocks());
		if (remaining <= 0) return null;
		return new Policy(configured.enabled(), configured.respectMobGriefing(), configured.naturalOnly(), remaining,
				configured.maxResistance(), configured.dropChance());
	}

	public static int trail(ServerLevel level, ServerPlayer caster, Vec3 from, Vec3 to, double width, Trace trace, long seed,
			Policy policy) {
		if (!enabled(level, policy)) return 0;
		List<BlockPos> candidates = TerrainShapes.surfaceTrail(from, to, width, seed, policy.maxBlocks());
		Set<BlockPos> surface = new LinkedHashSet<>();
		for (BlockPos candidate : candidates) {
			BlockPos floor = findSurface(level, candidate);
			if (floor != null && traceMutable(level, caster, floor, policy, trace)) surface.add(floor);
			if (surface.size() >= policy.maxBlocks()) break;
		}
		return enqueue(level, caster, new ArrayList<>(surface), policy, Operation.TRACE, trace, seed);
	}

	/** Проверка защиты доступна заклинаниям, которые хотят сначала оценить собственную точку удара. */
	public static boolean breakable(ServerLevel level, ServerPlayer caster, BlockPos pos, BlockState state, Policy policy) {
		if (!level.hasChunkAt(pos) || state.isAir() || !state.getFluidState().isEmpty()) return false;
		if (state.is(IMMUNE) || state.getDestroySpeed(level, pos) < 0.0F) return false;
		if (state.getBlock().getExplosionResistance() >= policy.maxResistance()) return false;
		if (level.getBlockEntity(pos) != null) return false;
		if (policy.naturalOnly() && !state.is(BREAKABLE)) return false;
		return caster == null || level.mayInteract(caster, pos);
	}

	private static int queueBreak(ServerLevel level, ServerPlayer caster, List<BlockPos> candidates, Policy policy, long seed) {
		if (!enabled(level, policy)) return 0;
		List<BlockPos> accepted = new ArrayList<>(Math.min(candidates.size(), policy.maxBlocks()));
		for (BlockPos pos : candidates) {
			BlockState state = level.getBlockState(pos);
			if (breakable(level, caster, pos, state, policy)) accepted.add(pos.immutable());
			if (accepted.size() >= policy.maxBlocks()) break;
		}
		return enqueue(level, caster, accepted, policy, Operation.BREAK, null, seed);
	}

	private static int enqueue(ServerLevel level, ServerPlayer caster, List<BlockPos> positions, Policy policy, Operation operation,
			Trace trace, long seed) {
		int capacity = Math.max(0, MAX_QUEUED_BLOCKS - queuedBlocks);
		int count = Math.min(Math.min(positions.size(), policy.maxBlocks()), capacity);
		if (count <= 0) return 0;
		List<BlockPos> blocks = List.copyOf(positions.subList(0, count));
		JOBS.addLast(new Job(level, caster, blocks, policy, operation, trace, seed));
		queuedBlocks += count;
		return count;
	}

	private static void tick(MinecraftServer server) {
		int budget = SuchkiConfig.INSTANCE.magicTerrainBlocksPerTick();
		while (budget > 0 && !JOBS.isEmpty()) {
			Job job = JOBS.peekFirst();
			if (job.level.getServer() != server || !enabled(job.level, job.policy)) {
				discard(job);
				continue;
			}
			int inspected = work(job, budget);
			budget -= Math.max(1, inspected);
			if (job.index >= job.blocks.size()) JOBS.removeFirst();
		}
	}

	private static int work(Job job, int budget) {
		int inspected = 0;
		while (inspected < budget && job.index < job.blocks.size()) {
			BlockPos pos = job.blocks.get(job.index++);
			queuedBlocks--;
			inspected++;
			boolean changed = job.operation == Operation.BREAK ? breakBlock(job, pos) : applyTrace(job, pos);
			if (changed) mutatedTotal++;
		}
		return inspected;
	}

	private static boolean breakBlock(Job job, BlockPos pos) {
		BlockState state = job.level.getBlockState(pos);
		if (!breakable(job.level, job.caster, pos, state, job.policy)) return false;
		if (chance(job.seed, pos, 17) < job.policy.dropChance()) {
			Block.dropResources(state, job.level, pos, null, job.caster, ItemStack.EMPTY);
		}
		return job.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
	}

	private static boolean applyTrace(Job job, BlockPos pos) {
		BlockState state = job.level.getBlockState(pos);
		if (!traceMutable(job.level, job.caster, pos, job.policy, job.trace)) return false;
		return switch (job.trace) {
			case SCORCH -> scorch(job, pos, state);
			case FROST -> frost(job, pos, state);
			case SCULK -> replaceNatural(job.level, pos, state, Blocks.SCULK.defaultBlockState());
			case RUBBLE -> replaceNatural(job.level, pos, state,
					chance(job.seed, pos, 41) < 0.55 ? Blocks.GRAVEL.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
		};
	}

	private static boolean scorch(Job job, BlockPos pos, BlockState state) {
		boolean changed = false;
		if (state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS) || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS)) {
			changed = job.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		} else if (isSoil(state)) {
			changed = job.level.setBlock(pos, Blocks.COARSE_DIRT.defaultBlockState(), 3);
		} else if (isRock(state)) {
			changed = job.level.setBlock(pos, Blocks.BLACKSTONE.defaultBlockState(), 3);
		} else if (state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL)) {
			changed = job.level.setBlock(pos, Blocks.BLACKSTONE.defaultBlockState(), 3);
		} else if (state.is(Blocks.NETHERRACK)) {
			BlockState scar = chance(job.seed, pos, 91) < 0.28
					? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
			changed = job.level.setBlock(pos, scar, 3);
		} else if (state.is(Blocks.END_STONE)) {
			changed = job.level.setBlock(pos, Blocks.BASALT.defaultBlockState(), 3);
		}
		BlockPos above = pos.above();
		if (chance(job.seed, pos, 73) < 0.09 && job.level.getBlockState(above).isAir()) {
			job.level.setBlock(above, BaseFireBlock.getState(job.level, above), 3);
			changed = true;
		}
		return changed;
	}

	private static boolean frost(Job job, BlockPos pos, BlockState state) {
		// Водонасыщенная постройка (ступень, забор и т. п.) не должна превращаться целиком в лёд.
		if (state.is(Blocks.WATER)) {
			return job.level.setBlock(pos, Blocks.ICE.defaultBlockState(), 3);
		}
		BlockPos above = pos.above();
		BlockState snow = Blocks.SNOW.defaultBlockState();
		if (natural(state) && job.level.getBlockState(above).isAir() && snow.canSurvive(job.level, above)) {
			return job.level.setBlock(above, snow, 3);
		}
		return false;
	}

	private static boolean replaceNatural(ServerLevel level, BlockPos pos, BlockState state, BlockState replacement) {
		return natural(state) && level.setBlock(pos, replacement, 3);
	}

	private static boolean traceMutable(ServerLevel level, ServerPlayer caster, BlockPos pos, Policy policy, Trace trace) {
		if (!level.hasChunkAt(pos)) return false;
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || state.is(IMMUNE) || level.getBlockEntity(pos) != null) return false;
		if (caster != null && !level.mayInteract(caster, pos)) return false;
		if (trace == Trace.FROST && state.is(Blocks.WATER)) return true;
		return state.getDestroySpeed(level, pos) >= 0.0F && state.getBlock().getExplosionResistance() < policy.maxResistance()
				&& (natural(state) || trace == Trace.SCORCH && (state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS)
				|| state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS)));
	}

	private static boolean natural(BlockState state) {
		return isSoil(state) || isRock(state) || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL)
				|| state.is(Blocks.NETHERRACK) || state.is(Blocks.END_STONE);
	}

	private static boolean isSoil(BlockState state) {
		return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL)
				|| state.is(Blocks.MYCELIUM) || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.MUD) || state.is(Blocks.DIRT_PATH);
	}

	private static boolean isRock(BlockState state) {
		// Не включаем булыжник: он часто является частью постройки игрока, а происхождение блока мир не хранит.
		return state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.TUFF) || state.is(Blocks.CALCITE);
	}

	private static BlockPos findSurface(ServerLevel level, BlockPos candidate) {
		for (int y = candidate.getY() + 4; y >= candidate.getY() - 12; y--) {
			BlockPos pos = new BlockPos(candidate.getX(), y, candidate.getZ());
			if (!level.hasChunkAt(pos)) return null;
			BlockState state = level.getBlockState(pos);
			if (!state.isAir()) return pos;
		}
		return null;
	}

	private static long effectSeed(Cast cast, Vec3 point) {
		long seed = cast.level.getSeed() ^ cast.caster.getUUID().getMostSignificantBits() ^ cast.caster.getUUID().getLeastSignificantBits();
		seed ^= cast.level.getGameTime() * 0x9E3779B97F4A7C15L;
		seed ^= cast.spell.id.hashCode() * 0xC2B2AE3D27D4EB4FL;
		seed ^= BlockPos.containing(point).asLong();
		return seed;
	}

	private static double chance(long seed, BlockPos pos, int salt) {
		return TerrainShapes.noise(seed ^ salt * 0x9E3779B97F4A7C15L, pos.getX(), pos.getY(), pos.getZ()) + 0.5;
	}

	private static void discard(Job job) {
		queuedBlocks -= job.blocks.size() - job.index;
		JOBS.removeFirst();
	}

	public static void clear() {
		JOBS.clear();
		queuedBlocks = 0;
	}

	/** Для gametest: выполнить всю очередь без ожидания тиков. */
	public static void flushNow() {
		while (!JOBS.isEmpty()) {
			Job job = JOBS.peekFirst();
			if (!enabled(job.level, job.policy)) {
				discard(job);
				continue;
			}
			work(job, Integer.MAX_VALUE);
			if (job.index >= job.blocks.size()) JOBS.removeFirst();
		}
	}

	public static int queuedBlocks() {
		return queuedBlocks;
	}

	public static int queuedJobs() {
		return JOBS.size();
	}

	public static long mutatedTotal() {
		return mutatedTotal;
	}
}
