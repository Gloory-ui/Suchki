package com.suchki.magic;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Одно сотворение заклинания на сервере: кто колдует, с какой силой, и помощники для эффектов.
 * Сила {@link #power} — накопление (×1 при «готово», до предела фокуса) или разгон потока (×0,6…×1,5).
 */
public final class Cast {
	public final ServerLevel level;
	public final ServerPlayer caster;
	public final Spell spell;
	public final float power;
	/** Множитель от навыка «Сила чар» (1 + доля). Действует на урон и лечение. */
	public final float bonus;

	public Cast(ServerLevel level, ServerPlayer caster, Spell spell, float power, float bonus) {
		this.level = level;
		this.caster = caster;
		this.spell = spell;
		this.power = power;
		this.bonus = bonus;
	}

	// --- Как сила влияет на заклинание ---

	/** Урон или лечение: × сила × навык. */
	public float amount(float base) {
		return base * power * bonus;
	}

	/** Радиус растёт как корень из силы. */
	public double radius(double base) {
		return base * Math.sqrt(Math.max(0.1F, power));
	}

	/** Длительность эффектов: ×(0,5 + 0,5·сила). */
	public int duration(int ticks) {
		return Math.max(1, Math.round(ticks * (0.5F + 0.5F * power)));
	}

	/** Мощное накопление (×1,75 и выше) — у части заклинаний особый бонус. */
	public boolean strong() {
		return power >= 1.75F;
	}

	// --- Где маг и куда смотрит ---

	public Vec3 eye() {
		return caster.getEyePosition();
	}

	public Vec3 look() {
		return caster.getViewVector(1.0F);
	}

	/** Вектор «вправо» от взгляда (по горизонтали). */
	public Vec3 right() {
		float yaw = caster.getYRot() * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
	}

	/** Примерное место руки, которой колдуют (откуда летят снаряды и лучи). */
	public Vec3 hand() {
		return handOf(caster);
	}

	public static Vec3 handOf(LivingEntity e) {
		float yaw = e.getYRot() * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		double side = e.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
		return e.getEyePosition().add(e.getViewVector(1.0F).scale(0.7)).add(right.scale(0.3 * side)).add(0, -0.3, 0);
	}

	/** Луч от глаз: первое живое существо (с небольшим запасом по размеру) или блок. */
	public HitResult aim(double range) {
		return aim(range, e -> e instanceof LivingEntity l && l.isAlive() && e != caster && !e.isSpectator());
	}

	public HitResult aim(double range, Predicate<Entity> filter) {
		Vec3 eye = eye();
		Vec3 end = eye.add(look().scale(range));
		BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
		Vec3 limit = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
		Entity best = null;
		Vec3 bestAt = null;
		double bestDist = Double.MAX_VALUE;
		AABB box = new AABB(eye, limit).inflate(1.5);
		for (Entity e : level.getEntities(caster, box, filter::test)) {
			var hit = e.getBoundingBox().inflate(0.35).clip(eye, limit);
			if (hit.isEmpty()) continue;
			double d = eye.distanceToSqr(hit.get());
			if (d < bestDist) {
				bestDist = d;
				best = e;
				bestAt = hit.get();
			}
		}
		if (best != null) return new EntityHitResult(best, bestAt);
		return block.getType() == HitResult.Type.MISS ? BlockHitResult.miss(end, caster.getDirection(), caster.blockPosition()) : block;
	}

	/** Живая цель под прицелом или null. */
	public LivingEntity aimLiving(double range, Predicate<Entity> filter) {
		return aim(range, filter) instanceof EntityHitResult e && e.getEntity() instanceof LivingEntity l ? l : null;
	}

	/** Точка под прицелом: центр существа, место на блоке или конец луча. */
	public Vec3 aimPoint(double range) {
		HitResult hit = aim(range);
		if (hit instanceof EntityHitResult e) return e.getEntity().position().add(0, e.getEntity().getBbHeight() * 0.5, 0);
		return hit.getLocation();
	}

	/** Точка на земле под прицелом (для ритуалов): блок под прицелом или земля под концом луча. */
	public Vec3 aimGround(double range) {
		Vec3 p = aimPoint(range);
		BlockHitResult down = level.clip(new ClipContext(p.add(0, 0.5, 0), p.add(0, -12, 0), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, caster));
		return down.getType() == HitResult.Type.MISS ? p : down.getLocation();
	}

	// --- Кто свой, кто чужой ---

	/** Свои: сам маг, игроки, чьи-то питомцы и призванные, жители, големы игроков, манекены. */
	public boolean isAlly(Entity e) {
		return allyOf(caster, e);
	}

	public static boolean allyOf(Entity caster, Entity e) {
		return e == caster || friendly(e);
	}

	/** «Мирные» для игроков существа: игроки, призванные, питомцы, жители, големы игроков, манекены. */
	public static boolean friendly(Entity e) {
		if (e instanceof Player || Minions.isSummon(e)) return true;
		if (e instanceof TamableAnimal t && t.isTame()) return true;
		if (e instanceof AbstractHorse h && h.isTamed()) return true;
		if (e instanceof IronGolem g && g.isPlayerCreated()) return true;
		return e instanceof AbstractVillager || e instanceof SnowGolem || e instanceof Allay || e instanceof ArmorStand
				|| e instanceof Mannequin;
	}

	/** Враг: монстр, злой нейтральный моб или моб, нацеленный на мага или его союзников. */
	public boolean isEnemy(LivingEntity e) {
		if (!e.isAlive() || isAlly(e)) return false;
		if (e instanceof Enemy) return true;
		if (e instanceof NeutralMob n && n.isAngry()) return true;
		return e instanceof Mob m && m.getTarget() != null && isAlly(m.getTarget());
	}

	/** Можно ли задеть прямым попаданием (снаряд, луч, цель под прицелом). */
	public boolean canHit(LivingEntity e) {
		return e.isAlive() && e != caster && !Minions.isOwnedBy(e, caster) && !(e instanceof ArmorStand)
				&& !(e instanceof Mannequin);
	}

	public static boolean isUndead(LivingEntity e) {
		return e.getType().is(EntityTypeTags.SENSITIVE_TO_SMITE);
	}

	/** Живые существа в радиусе (по центру тела). */
	public List<LivingEntity> around(Vec3 center, double radius, Predicate<LivingEntity> filter) {
		List<LivingEntity> out = new ArrayList<>();
		AABB box = new AABB(center, center).inflate(radius);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
			Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
			if (c.distanceToSqr(center) <= radius * radius && filter.test(e)) out.add(e);
		}
		return out;
	}

	public List<LivingEntity> enemiesAround(Vec3 center, double radius) {
		return around(center, radius, this::isEnemy);
	}

	public List<LivingEntity> alliesAround(Vec3 center, double radius) {
		List<LivingEntity> out = around(center, radius, e -> isAlly(e) && !(e instanceof ArmorStand) && !(e instanceof Mannequin));
		// Сам маг — всегда союзник (даже если его нет в списке существ мира, как у «ненастоящего» игрока).
		if (!out.contains(caster) && center(caster).distanceToSqr(center) <= radius * radius) out.add(caster);
		return out;
	}

	/** Середина тела. */
	public static Vec3 center(Entity e) {
		return e.position().add(0, e.getBbHeight() * 0.5, 0);
	}

	/** Попадает ли существо в конус от точки по направлению (угол — половина раствора). */
	public static boolean inCone(Entity e, Vec3 from, Vec3 dir, double range, double halfAngleDeg) {
		Vec3 to = center(e).subtract(from);
		double len = to.length();
		if (len > range || len < 0.01) return len < 0.01;
		return to.scale(1.0 / len).dot(dir) >= Math.cos(Math.toRadians(halfAngleDeg));
	}

	/** Где маг может стоять рядом с точкой (ноги): сама точка или до двух блоков выше; null — негде. */
	public Vec3 safeSpot(Vec3 feet) {
		for (int dy = 0; dy <= 2; dy++) {
			Vec3 p = feet.add(0, dy, 0);
			if (level.noCollision(caster, caster.getDimensions(caster.getPose()).makeBoundingBox(p))) return p;
		}
		return null;
	}

	/** Перенести мага в точку (ноги) с эффектом в начале и в конце. */
	public void teleport(Vec3 to, int color) {
		Vec3 from = caster.position();
		MagicFx.burst(level, from.add(0, 1, 0), MagicParticles.spark(color, 0.3F, 16), 16, 0.3, 0.05);
		MagicFx.burst(level, from.add(0, 1, 0), net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, 20, 0.3, 0.1);
		caster.teleportTo(to.x, to.y, to.z);
		caster.resetFallDistance();
		MagicFx.burst(level, to.add(0, 1, 0), MagicParticles.spark(color, 0.3F, 16), 16, 0.3, 0.05);
		MagicFx.burst(level, to.add(0, 1, 0), net.minecraft.core.particles.ParticleTypes.PORTAL, 30, 0.3, 0.3);
		MagicFx.sound(level, from, MagicSounds.CAST_TELEPORT, 0.8F, 1.2F);
		MagicFx.sound(level, to, MagicSounds.CAST_TELEPORT, 0.8F, 1.4F);
	}

	/** Оттолкнуть от точки (и подбросить). */
	public static void knock(LivingEntity e, Vec3 from, double strength, double up) {
		Vec3 d = e.position().subtract(from);
		Vec3 flat = new Vec3(d.x, 0, d.z);
		flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize().scale(strength * (1.0 - e.getAttributeValue(
				net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE)));
		e.push(flat.x, up, flat.z);
		e.hurtMarked = true;
	}

	// --- Урон и лечение ---

	public boolean hurt(LivingEntity target, float amount, MagicDamage kind) {
		return hurt(target, amount, kind, caster);
	}

	public boolean hurt(LivingEntity target, float amount, MagicDamage kind, Entity direct) {
		if (amount <= 0 || !target.isAlive()) return false;
		return target.hurtServer(level, kind.source(level, direct, caster), amount);
	}

	/** Лечение; нежить вместо лечения получает святой урон (если {@code hurtUndead}). */
	public void heal(LivingEntity target, float amount) {
		if (amount <= 0 || !target.isAlive()) return;
		target.heal(amount);
	}
}
