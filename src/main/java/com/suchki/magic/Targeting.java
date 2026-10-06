package com.suchki.magic;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Прицел мага: куда смотрит луч от глаз (блок или существо) и где под этой точкой земля. Одинаково работает на
 * сервере и на клиенте — клиент рисует круг в точке прицела сам, сервер бьёт туда же.
 * Существа ищутся отрезками по 16 блоков, чтобы на 128 блоках не опрашивать огромную коробку.
 */
public final class Targeting {
	/** На сколько блоков вниз искать землю под точкой в воздухе. */
	public static final int GROUND_SEARCH = 64;
	private static final double STEP = 16.0;

	/** Куда попал прицел: точка, существо (или null), земля под точкой и смотрит ли луч в пустоту. */
	public record Aim(HitResult hit, Vec3 point, Entity entity, Vec3 ground, boolean miss) {}

	private Targeting() {}

	/** Обычный фильтр целей: живые существа, кроме самого мага и наблюдателей. */
	public static Predicate<Entity> living(Entity caster) {
		return e -> e instanceof LivingEntity l && l.isAlive() && e != caster && !e.isSpectator();
	}

	public static HitResult ray(Level level, Entity caster, Vec3 eye, Vec3 look, double range, Predicate<Entity> filter) {
		Vec3 end = eye.add(look.scale(range));
		BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
		Vec3 limit = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
		double len = limit.distanceTo(eye);
		Vec3 dir = len < 1.0E-6 ? look : limit.subtract(eye).scale(1.0 / len);
		for (double from = 0; from < len; from += STEP) {
			double to = Math.min(len, from + STEP);
			Vec3 a = eye.add(dir.scale(from));
			Vec3 b = eye.add(dir.scale(to));
			Entity best = null;
			Vec3 bestAt = null;
			double bestDist = Double.MAX_VALUE;
			for (Entity e : level.getEntities(caster, new AABB(a, b).inflate(1.5), filter::test)) {
				var hit = e.getBoundingBox().inflate(0.35).clip(a, b);
				if (hit.isEmpty()) continue;
				double d = eye.distanceToSqr(hit.get());
				if (d < bestDist) {
					bestDist = d;
					best = e;
					bestAt = hit.get();
				}
			}
			if (best != null) return new EntityHitResult(best, bestAt);
		}
		return block.getType() == HitResult.Type.MISS
				? BlockHitResult.miss(end, Direction.getApproximateNearest(look.x, look.y, look.z).getOpposite(), BlockPos.containing(end))
				: block;
	}

	/** Полный прицел: точка (центр существа или место на блоке), земля под ней. */
	public static Aim aim(Level level, Entity caster, Vec3 eye, Vec3 look, double range, Predicate<Entity> filter) {
		HitResult hit = ray(level, caster, eye, look, range, filter);
		Entity entity = hit instanceof EntityHitResult e ? e.getEntity() : null;
		Vec3 point = entity != null ? entity.position().add(0, entity.getBbHeight() * 0.5, 0) : hit.getLocation();
		Vec3 ground = entity != null ? ground(level, caster, entity.position().add(0, 0.5, 0)) : ground(level, caster, point.add(0, 0.5, 0));
		return new Aim(hit, point, entity, ground, hit.getType() == HitResult.Type.MISS);
	}

	public static Aim aim(Level level, Entity caster, double range) {
		return aim(level, caster, caster.getEyePosition(), caster.getViewVector(1.0F), range, living(caster));
	}

	/** Земля под точкой (верх блока) или сама точка, если до земли дальше {@link #GROUND_SEARCH}. */
	public static Vec3 ground(Level level, Entity caster, Vec3 from) {
		BlockHitResult down = level.clip(new ClipContext(from, from.add(0, -GROUND_SEARCH, 0), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.SOURCE_ONLY, caster));
		return down.getType() == HitResult.Type.MISS ? from.add(0, -0.5, 0) : down.getLocation();
	}
}
