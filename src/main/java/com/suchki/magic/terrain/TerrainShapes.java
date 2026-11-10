package com.suchki.magic.terrain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Чистая геометрия разрушения. Методы не читают мир и при одинаковом seed возвращают одинаковые позиции —
 * это позволяет проверять форму отдельно от Minecraft и заранее ограничивать объём работы сервера.
 */
public final class TerrainShapes {
	public static final double MAX_CRATER_RADIUS = 18.0;
	public static final double MAX_TUNNEL_RADIUS = 6.0;
	public static final double MAX_LENGTH = 96.0;

	private TerrainShapes() {}

	/** Неровная чаша: глубокая снизу и более плоская над точкой удара. */
	public static List<BlockPos> crater(Vec3 center, double radius, double depth, long seed, int limit) {
		if (limit <= 0) return List.of();
		double r = Mth.clamp(radius, 0.5, MAX_CRATER_RADIUS);
		double down = Mth.clamp(depth, 0.5, r * 1.5);
		double up = Math.max(0.5, r * 0.42);
		BlockPos origin = BlockPos.containing(center);
		int horizontal = Mth.ceil(r) + 1;
		int minY = -Mth.ceil(down) - 1;
		int maxY = Mth.ceil(up) + 1;
		List<BlockPos> result = new ArrayList<>();
		for (int y = minY; y <= maxY; y++) {
			for (int x = -horizontal; x <= horizontal; x++) {
				for (int z = -horizontal; z <= horizontal; z++) {
					double vertical = y / (y < 0 ? down : up);
					double normalized = x * x / (r * r) + z * z / (r * r) + vertical * vertical;
					double roughness = noise(seed, origin.getX() + x, origin.getY() + y, origin.getZ() + z) * 0.16;
					if (normalized > 1.0 + roughness) continue;
					result.add(origin.offset(x, y, z));
				}
			}
		}
		// Центр раскрывается первым, затем край: эффект развивается предсказуемо при покадровой очереди.
		result.sort(Comparator.comparingDouble(p -> p.distSqr(origin)));
		return limited(result, limit);
	}

	/** Цилиндр вдоль отрезка. Повторные позиции устраняются без потери порядка от начала к концу. */
	public static List<BlockPos> tunnel(Vec3 from, Vec3 to, double radius, int limit) {
		if (limit <= 0) return List.of();
		Vec3 delta = to.subtract(from);
		double length = Math.min(MAX_LENGTH, delta.length());
		if (length < 0.25) return List.of();
		Vec3 direction = delta.normalize();
		double r = Mth.clamp(radius, 0.35, MAX_TUNNEL_RADIUS);
		int ri = Mth.ceil(r);
		Set<BlockPos> result = new LinkedHashSet<>();
		for (double along = 0; along <= length; along += 0.55) {
			Vec3 point = from.add(direction.scale(along));
			BlockPos origin = BlockPos.containing(point);
			for (int x = -ri; x <= ri; x++) {
				for (int y = -ri; y <= ri; y++) {
					for (int z = -ri; z <= ri; z++) {
						Vec3 center = Vec3.atCenterOf(origin.offset(x, y, z));
						Vec3 relative = center.subtract(from);
						double projected = relative.dot(direction);
						if (projected < -0.5 || projected > length + 0.5) continue;
						if (relative.subtract(direction.scale(projected)).lengthSqr() <= r * r) {
							result.add(origin.offset(x, y, z));
							if (result.size() >= limit) return List.copyOf(result);
						}
					}
				}
			}
		}
		return List.copyOf(result);
	}

	/**
	 * Рваная трещина по поверхности. Она слегка блуждает вбок, сужается к концам и уходит на {@code depth}
	 * блоков вниз; порядок идёт вдоль распространения разлома.
	 */
	public static List<BlockPos> fissure(Vec3 origin, Vec3 direction, double length, double width, int depth, long seed, int limit) {
		if (limit <= 0) return List.of();
		Vec3 flat = new Vec3(direction.x, 0, direction.z);
		if (flat.lengthSqr() < 1.0E-5) flat = new Vec3(0, 0, 1);
		flat = flat.normalize();
		Vec3 side = new Vec3(-flat.z, 0, flat.x);
		double len = Mth.clamp(length, 1.0, MAX_LENGTH);
		double half = Mth.clamp(width, 0.35, 3.0);
		int down = Mth.clamp(depth, 1, 14);
		Set<BlockPos> result = new LinkedHashSet<>();
		int steps = Math.max(2, Mth.ceil(len * 1.7));
		for (int step = 0; step <= steps; step++) {
			double t = step / (double) steps;
			double taper = Math.max(0.22, Math.sin(Math.PI * t));
			double wander = noise(seed, step, 0, 0) * Math.min(2.4, len * 0.055);
			Vec3 center = origin.add(flat.scale(len * t)).add(side.scale(wander));
			int lateral = Math.max(0, Mth.ceil(half * taper));
			for (int across = -lateral; across <= lateral; across++) {
				if (Math.abs(across) > half * taper + noise(seed ^ 0x6A09E667F3BCC909L, step, across, 0) * 0.45) continue;
				Vec3 lip = center.add(side.scale(across));
				for (int y = 0; y < down; y++) {
					double depthTaper = 1.0 - y / (double) down;
					if (Math.abs(across) > Math.max(0.25, half * taper * depthTaper)) continue;
					result.add(BlockPos.containing(lip.add(0, -y, 0)));
					if (result.size() >= limit) return List.copyOf(result);
				}
			}
		}
		return List.copyOf(result);
	}

	/** Кандидаты поверхности под сохраняющийся след между двумя точками. Высоту уточняет фасад по миру. */
	public static List<BlockPos> surfaceTrail(Vec3 from, Vec3 to, double width, long seed, int limit) {
		if (limit <= 0) return List.of();
		Vec3 delta = to.subtract(from);
		double length = Math.min(MAX_LENGTH, delta.length());
		if (length < 0.1) return List.of(BlockPos.containing(from));
		Vec3 direction = delta.normalize();
		Vec3 side = new Vec3(-direction.z, 0, direction.x);
		if (side.lengthSqr() < 1.0E-5) side = new Vec3(1, 0, 0);
		else side = side.normalize();
		double half = Mth.clamp(width, 0.25, 4.0);
		Set<BlockPos> result = new LinkedHashSet<>();
		int steps = Math.max(1, Mth.ceil(length * 1.6));
		for (int step = 0; step <= steps; step++) {
			double t = step / (double) steps;
			Vec3 center = from.add(direction.scale(length * t));
			int edge = Mth.ceil(half);
			for (int across = -edge; across <= edge; across++) {
				double irregular = noise(seed, step, across, 1) * 0.45;
				if (Math.abs(across) > half + irregular) continue;
				result.add(BlockPos.containing(center.add(side.scale(across))));
				if (result.size() >= limit) return List.copyOf(result);
			}
		}
		return List.copyOf(result);
	}

	/** Стабильный шум −0,5…0,5 без общего Random и зависимости от порядка обхода. */
	static double noise(long seed, int x, int y, int z) {
		long value = seed ^ x * 0x9E3779B185EBCA87L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L;
		value ^= value >>> 30;
		value *= 0xBF58476D1CE4E5B9L;
		value ^= value >>> 27;
		value *= 0x94D049BB133111EBL;
		value ^= value >>> 31;
		return ((value >>> 11) * 0x1.0p-53) - 0.5;
	}

	private static List<BlockPos> limited(List<BlockPos> source, int limit) {
		int end = Math.min(source.size(), Math.max(0, limit));
		return List.copyOf(source.subList(0, end));
	}
}
