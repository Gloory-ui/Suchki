package com.suchki.magic;

import java.util.ArrayList;
import java.util.Comparator;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Одно сотворение заклинания на сервере: кто колдует, с какой силой, и помощники для эффектов.
 * Сила {@link #power} — накопление (×1 при «готово», до предела фокуса) или разгон потока (×0,6…×1,5).
 *
 * <p>Мультикаст: заклинание выпускается {@link #copies} раз, у каждой копии ({@link #copy}) свой круг — точка вылета
 * {@link #hand()} — и своя цель {@link #aim()} (следующий враг рядом с прицелом или точка по кольцу вокруг него).
 * Заклинания пишутся как для одной копии: помощники сами берут точку и цель своей копии.
 */
public final class Cast {
	public final ServerLevel level;
	public final ServerPlayer caster;
	public final Spell spell;
	public final float power;
	/** Множитель от навыка «Сила чар» (1 + доля). Действует на урон и лечение. */
	public final float bonus;
	/** Номер копии (0 — главная) и сколько всего копий. */
	public final int copy;
	public final int copies;
	/** Откуда летит эта копия (центр её круга) или null — от руки мага. */
	private final Vec3 origin;
	/** Заранее выбранный прицел этой копии или null — считать по взгляду. */
	private final Targeting.Aim forced;
	private Targeting.Aim cached;

	public Cast(ServerLevel level, ServerPlayer caster, Spell spell, float power, float bonus) {
		this(level, caster, spell, power, bonus, 0, 1, null, null);
	}

	public Cast(ServerLevel level, ServerPlayer caster, Spell spell, float power, float bonus, int copy, int copies, Vec3 origin,
			Targeting.Aim forced) {
		this.level = level;
		this.caster = caster;
		this.spell = spell;
		this.power = power;
		this.bonus = bonus;
		this.copy = copy;
		this.copies = copies;
		this.origin = origin;
		this.forced = forced;
	}

	/** Та же копия, но с другой силой (для волн, затухающих ударов и т. п.). */
	public Cast withPower(float p) {
		return new Cast(level, caster, spell, p, bonus, copy, copies, origin, forced);
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

	public int color() {
		return spell.color();
	}

	public int lightColor() {
		return spell.lightColor();
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

	/** Откуда колдуют: центр круга этой копии или рука мага. Отсюда летят снаряды и лучи. */
	public Vec3 hand() {
		return origin != null ? origin : handOf(caster);
	}

	public static Vec3 handOf(LivingEntity e) {
		float yaw = e.getYRot() * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		double side = e.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
		return e.getEyePosition().add(e.getViewVector(1.0F).scale(0.7)).add(right.scale(0.3 * side)).add(0, -0.3, 0);
	}

	/** Направление полёта снаряда этой копии: по взгляду, а у копий — от их круга к их цели. */
	public Vec3 direction() {
		if (copies <= 1 || origin == null) return look();
		Vec3 d = aimPoint().subtract(hand());
		return d.lengthSqr() < 1.0E-4 ? look() : d.normalize();
	}

	/** Прицел этой копии на дальность заклинания. */
	public Targeting.Aim target() {
		if (forced != null) return forced;
		if (cached == null) cached = Targeting.aim(level, caster, eye(), look(), spell.range, Targeting.living(caster));
		return cached;
	}

	/** Луч от глаз: первое живое существо (с небольшим запасом по размеру) или блок. */
	public HitResult aim(double range) {
		return aim(range, Targeting.living(caster));
	}

	public HitResult aim(double range, Predicate<Entity> filter) {
		if (forced != null && (forced.entity() == null || filter.test(forced.entity()))) return forced.hit();
		return Targeting.ray(level, caster, eye(), look(), Math.min(range, Magic.MAX_RANGE), filter);
	}

	/** Прицел на дальность заклинания. */
	public HitResult aim() {
		return aim(spell.range);
	}

	/** Живая цель под прицелом или null. */
	public LivingEntity aimLiving(double range, Predicate<Entity> filter) {
		return aim(range, filter) instanceof EntityHitResult e && e.getEntity() instanceof LivingEntity l ? l : null;
	}

	/** Враг (или любая цель, которую можно задеть) под прицелом на дальность заклинания. */
	public LivingEntity aimFoe() {
		return aimLiving(spell.range, e -> e instanceof LivingEntity l && canHit(l) && !isAlly(l));
	}

	/** Точка под прицелом: центр существа, место на блоке или конец луча. */
	public Vec3 aimPoint(double range) {
		if (forced != null) return forced.point();
		HitResult hit = aim(range);
		if (hit instanceof EntityHitResult e) return e.getEntity().position().add(0, e.getEntity().getBbHeight() * 0.5, 0);
		return hit.getLocation();
	}

	public Vec3 aimPoint() {
		return target().point();
	}

	/** Точка на земле под прицелом (для ударов по месту): блок под прицелом или земля под концом луча. */
	public Vec3 aimGround(double range) {
		if (forced != null) return forced.ground();
		return Targeting.ground(level, caster, aimPoint(range).add(0, 0.5, 0));
	}

	public Vec3 aimGround() {
		return target().ground();
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

	/** Задевает ли удар по площади: всё, что можно задеть, кроме союзников. */
	public boolean isFoe(LivingEntity e) {
		return canHit(e) && !isAlly(e);
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

	/** Всё, что задевает удар по площади (враги и нейтральные мобы, но не союзники). */
	public List<LivingEntity> foesAround(Vec3 center, double radius) {
		return around(center, radius, this::isFoe);
	}

	public List<LivingEntity> alliesAround(Vec3 center, double radius) {
		List<LivingEntity> out = around(center, radius, e -> isAlly(e) && !(e instanceof ArmorStand) && !(e instanceof Mannequin));
		// Сам маг — всегда союзник (даже если его нет в списке существ мира, как у «ненастоящего» игрока).
		if (!out.contains(caster) && center(caster).distanceToSqr(center) <= radius * radius) out.add(caster);
		return out;
	}

	/** Существа вдоль отрезка (толщина — {@code width} блоков от оси), ближние первыми. */
	public List<LivingEntity> along(Vec3 from, Vec3 to, double width, Predicate<LivingEntity> filter) {
		List<LivingEntity> out = new ArrayList<>();
		Vec3 d = to.subtract(from);
		double len2 = d.lengthSqr();
		AABB box = new AABB(from, to).inflate(width + 1);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
			Vec3 c = center(e);
			double t = len2 < 1.0E-6 ? 0 : Mth.clamp(c.subtract(from).dot(d) / len2, 0, 1);
			Vec3 p = from.add(d.scale(t));
			double r = width + e.getBbWidth() * 0.5;
			if (p.distanceToSqr(c) <= r * r && filter.test(e)) out.add(e);
		}
		out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(from)));
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
		MagicFx.sound(level, from, MagicSounds.TELEPORT, 0.8F, 1.2F);
		MagicFx.sound(level, to, MagicSounds.TELEPORT, 0.8F, 1.4F);
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

	/** Притянуть к точке (сила — скорость в блоках за тик). */
	public static void pull(LivingEntity e, Vec3 to, double strength) {
		Vec3 d = to.subtract(center(e));
		if (d.lengthSqr() < 0.04) return;
		double k = strength * (1.0 - e.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE));
		Vec3 v = d.normalize().scale(k);
		e.push(v.x, v.y, v.z);
		e.hurtMarked = true;
	}

	// --- Урон и лечение ---

	public boolean hurt(LivingEntity target, float amount, MagicDamage kind) {
		return hurt(target, amount, kind, caster);
	}

	/**
	 * Магический урон. Неуязвимость после прошлого удара сбрасывается: у заклинаний свой ритм ударов
	 * (потоки бьют раз в полсекунды, залпы — каждым снарядом), иначе копии мультикаста и залпы терялись бы.
	 */
	public boolean hurt(LivingEntity target, float amount, MagicDamage kind, Entity direct) {
		if (amount <= 0 || !target.isAlive()) return false;
		target.invulnerableTime = 0;
		return target.hurtServer(level, kind.source(level, direct, caster), amount);
	}

	/** Лечение. */
	public void heal(LivingEntity target, float amount) {
		if (amount <= 0 || !target.isAlive()) return;
		target.heal(amount);
	}

	// --- Мультикаст: цели копий ---

	/**
	 * Цели для {@code n} копий: главная — прицел мага; остальные — ближайшие к прицелу враги в радиусе
	 * {@code spread}, а если врагов не хватает — точки по кольцу вокруг прицела.
	 */
	static List<Targeting.Aim> copyTargets(ServerLevel level, ServerPlayer caster, Spell spell, int n) {
		Targeting.Aim main = Targeting.aim(level, caster, caster.getEyePosition(), caster.getViewVector(1.0F), spell.range,
				Targeting.living(caster));
		List<Targeting.Aim> out = new ArrayList<>();
		out.add(main);
		if (n <= 1) return out;
		Cast probe = new Cast(level, caster, spell, 1, 1);
		double spread = Math.max(8.0, spell.circleRadius() * 2.0);
		List<LivingEntity> foes = probe.enemiesAround(main.point(), spread);
		foes.remove(main.entity());
		foes.sort(Comparator.comparingDouble(e -> e.distanceToSqr(main.point())));
		Vec3 look = caster.getViewVector(1.0F);
		for (int i = 1; i < n; i++) {
			if (i - 1 < foes.size()) {
				LivingEntity e = foes.get(i - 1);
				Vec3 c = center(e);
				out.add(new Targeting.Aim(new EntityHitResult(e, c), c, e, Targeting.ground(level, caster, e.position().add(0, 0.5, 0)), false));
			} else {
				Vec3 off = CircleLayout.copyOffset(spell, look, i, n);
				if (spell.anchor == Anchor.HAND || spell.anchor == Anchor.LINE || spell.anchor == Anchor.BACK
						|| spell.anchor == Anchor.ORBIT) {
					off = new Vec3(off.x, 0, off.z).scale(2.5);
				}
				Vec3 p = main.point().add(off);
				Vec3 g = Targeting.ground(level, caster, p.add(0, 1.0, 0));
				out.add(new Targeting.Aim(BlockHitResult.miss(p, net.minecraft.core.Direction.UP, net.minecraft.core.BlockPos.containing(p)),
						p, null, g, main.miss()));
			}
		}
		return out;
	}
}
