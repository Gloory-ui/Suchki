package com.suchki.magic.spells;

import static com.suchki.magic.CastStyle.BOLT;
import static com.suchki.magic.CastStyle.CHANNEL;
import static com.suchki.magic.CastStyle.RITUAL;
import static com.suchki.magic.CastStyle.SELF;
import static com.suchki.magic.School.LIGHT;

import com.suchki.magic.Anchor;
import com.suchki.magic.Cast;
import com.suchki.magic.CircleLayout;
import com.suchki.magic.Magic;
import com.suchki.magic.MagicBlocks;
import com.suchki.magic.MagicDamage;
import com.suchki.magic.MagicFx;
import com.suchki.magic.MagicParticles;
import com.suchki.magic.MagicSounds;
import com.suchki.magic.Minds;
import com.suchki.magic.Spell;
import com.suchki.magic.SpellProjectile;
import com.suchki.magic.Targeting;
import com.suchki.magic.Terrain;
import com.suchki.magic.Trail;
import com.suchki.magic.Zones;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Стихия света: от свечи странника и стрелы-кары до Небесного Суда, Клинка Обетованного Рассвета и Падения Небес.
 * Свет бьёт святым уроном (нежить — вдвойне и горит), лечит и бережёт своих, сжигает чужие стрелы.
 * Большие заклинания собираются на глазах: стрелы света повисают на ободе небесного круга, ореолы спускаются
 * с неба один за другим — и только потом падает столп.
 */
public final class LightSpells {
	/** Золото кругов и стрел. */
	static final int GOLD = 0xFFD84A;
	/** Тёплый белый — ядра лучей и вспышек. */
	static final int WHITE = 0xFFF6D8;
	/** Голубая кромка шестигранников Покрова. */
	static final int CYAN = 0x9FF4FF;
	/** Сине-белый свет Падения Небес. */
	static final int BLUE_WHITE = 0xCFE6FF;

	// Свеча Странника: время огонька, время неподвижного огонька, дальность установки, радиус «жжения» нежити.
	private static final int CANDLE_TICKS = 3600;
	private static final int ORB_TICKS = 6000;
	private static final double CANDLE_RANGE = 24.0;
	private static final double CANDLE_REACH = 3.0;
	private static final int MAX_ORBS = 8;
	// Кара: скорость, дальность, наведение.
	private static final float SMITE_SPEED = 2.4F;
	private static final double SMITE_RANGE = 40.0;
	private static final int SMITE_LIFE = 18;
	// Шестигранный Покров: запас щита, расстояние до щита, радиус купола, запрет после разрушения.
	private static final float WARD_POOL = 30.0F;
	private static final float DOME_POOL = 20.0F;
	private static final double WARD_DIST = 1.8;
	private static final double DOME_R = 2.5;
	private static final int WARD_LOCK = 60;
	/** Сколько запаса «съедает» сожжённая стрела. */
	private static final float WARD_PROJECTILE = 3.0F;
	/** Центры 14 шестигранников щита (вправо, вверх — в блоках): ряды 3-4-4-3. */
	private static final double[][] HEX = {
			{-1.0, 1.3}, {0.0, 1.3}, {1.0, 1.3},
			{-1.5, 0.43}, {-0.5, 0.43}, {0.5, 0.43}, {1.5, 0.43},
			{-1.5, -0.43}, {-0.5, -0.43}, {0.5, -0.43}, {1.5, -0.43},
			{-1.0, -1.3}, {0.0, -1.3}, {1.0, -1.3}};
	// Солнечная Вспышка: радиус и время ослепления.
	private static final double FLASH_R = 12.0;
	private static final int FLASH_TICKS = 100;
	// Благословение: радиус и время.
	private static final double BLESS_R = 8.0;
	private static final int BLESS_TICKS = 900;
	// Солнечный Луч: дальность, поворот за тик, когда луч разгорается, толщина (обычная/разгоревшаяся).
	private static final double BEAM_RANGE = 48.0;
	private static final double BEAM_TURN = Math.toRadians(4.0);
	private static final int BEAM_RAMP = 40;
	private static final double BEAM_W = 0.8;
	private static final double BEAM_W_HOT = 1.4;
	// Святилище: радиус и время.
	private static final double SANCT_R = 6.0;
	private static final int SANCT_TICKS = 300;
	// Небесный Суд: высота круга, радиус столпа, предупреждение, дождь стрел.
	private static final float JUDGE_SKY = 30.0F;
	private static final double JUDGE_R = 3.5;
	private static final int JUDGE_TELEGRAPH = 15;
	private static final int JUDGE_ARROWS = 30;
	private static final double JUDGE_RAIN_R = 8.0;
	private static final int JUDGE_MAX_HITS = 6;
	// Клинок Обетованного Рассвета: путь, скорость, полуширина и высота волны, где кончается траншея и полный урон.
	private static final double BLADE_RANGE = 96.0;
	private static final double BLADE_SPEED = 6.0;
	private static final double BLADE_HALF = 3.0;
	private static final double BLADE_HEIGHT = 8.0;
	private static final double BLADE_TRENCH = 64.0;
	private static final double BLADE_FAN = 15.0;
	// Падение Небес: высота и радиус круга, радиус столпа, этапы (ореолы, столп, обвал), глубина шахты.
	private static final float FALL_SKY = 60.0F;
	private static final float FALL_CIRCLE = 20.0F;
	private static final double FALL_R = 12.0;
	private static final double FALL_PUSH = 18.0;
	private static final int FALL_HALOS = 30;
	private static final int FALL_PILLAR = 80;
	private static final int FALL_DEPTH = 16;
	private static final float FALL_BOSS_CAP = 90.0F;

	/** Огонёк, что следует за магом (по одному на мага), и неподвижные огоньки (до 8 на мага). */
	private static final Map<UUID, Zones.Zone> FOLLOW = new HashMap<>();
	private static final Map<UUID, Deque<Zones.Zone>> ORBS = new HashMap<>();
	/** Покров каждого мага, пока держит. */
	private static final Map<UUID, Veil> VEILS = new HashMap<>();
	/** Куда смотрит Солнечный Луч каждой копии (луч поворачивается не быстрее 4° за тик). */
	private static final Map<String, Vec3> BEAMS = new HashMap<>();
	private static boolean hooked;

	private LightSpells() {}

	public static void register() {
		hook();

		// --- Ранг 1: Начальный ---

		// Свеча Странника: огонёк света следует за магом; с Shift — неподвижный огонёк там, куда смотришь.
		Spell.of("candlelight", LIGHT, 1, SELF).anchor(Anchor.HAND).mana(8).charge(8).cooldown(20).range(CANDLE_RANGE)
				.multicast(4).icon("lantern")
				.action(LightSpells::candlelight)
				.register();

		// Кара: сгусток света с лёгким наведением; нежить получает ×2,5 и загорается.
		Spell.of("smite", LIGHT, 1, BOLT).mana(10).charge(10).cooldown(8).range(SMITE_RANGE).icon("glowstone_dust")
				.model("glowstone_dust").size(0.3F).trail(Trail.HOLY)
				.action(c -> {
					SpellKit.release(c);
					SpellProjectile.shoot(c, SMITE_SPEED).homing(0.05).life(SMITE_LIFE);
					MagicFx.burst(c.level, c.hand(), MagicParticles.spark(WHITE, 0.3F, 6), 4, 0.05, 0.03);
					MagicFx.burst(c.level, c.hand(), ParticleTypes.END_ROD, 3, 0.05, 0.04);
					return true;
				})
				.hit((c, p, r) -> {
					if (r instanceof EntityHitResult e && e.getEntity() instanceof LivingEntity t && c.isFoe(t)) {
						holyHit(c, t, c.amount(5.0F), p, 60);
					}
					starBurst(c, r.getLocation(), 0.6F);
				})
				.register();

		// --- Ранг 2: Средний ---

		// Шестигранный Покров: пока держишь — изогнутый щит из 14 шестигранников спереди (с Shift — купол).
		Spell.of("ward", LIGHT, 2, CHANNEL).mana(5).charge(0).cooldown(40).range(2).icon("shield")
				.loop(MagicSounds.LOOP_WARD)
				.channel(LightSpells::ward)
				.register();

		// Солнечная Вспышка: враги вокруг слепнут и теряют цель; нежить обжигает.
		Spell.of("flash", LIGHT, 2, SELF).anchor(Anchor.HAND).mana(18).charge(12).cooldown(100).range(FLASH_R).multicast(4)
				.icon("glow_ink_sac")
				.action(c -> {
					flash(c);
					return true;
				})
				.register();

		// --- Ранг 3: Продвинутый ---

		// Благословение Рассвета: Сила и Сопротивление союзникам вокруг.
		Spell.of("blessing", LIGHT, 3, SELF).anchor(Anchor.FEET).mana(36).charge(20).cooldown(600).range(BLESS_R).multicast(4)
				.icon("bell")
				.action(c -> {
					blessing(c);
					return true;
				})
				.register();

		// Солнечный Луч: пока держишь — луч солнца на 48 блоков; через 2 с разгорается шире и жарче.
		Spell.of("sunbeam", LIGHT, 3, CHANNEL).mana(14).charge(0).cooldown(20).range(BEAM_RANGE).beam().destroys()
				.loop(MagicSounds.LOOP_BEAM).icon("sunflower")
				.channel(LightSpells::sunbeam)
				.register();

		// --- Ранг 4: Святой ---

		// Святилище: священный круг — свои лечатся, нежить горит, враги не могут войти, стрелы сгорают.
		Spell.of("sanctuary", LIGHT, 4, RITUAL).anchor(Anchor.FEET).layers(2).mana(60).charge(30).cooldown(600).range(SANCT_R)
				.icon("beacon")
				.action(c -> {
					sanctuary(c);
					return true;
				})
				.register();

		// --- Ранг 5: Королевский ---

		// Небесный Суд: столп света из небесного круга, затем дождь из 30 стрел света.
		Spell.of("judgement", LIGHT, 5, RITUAL).anchor(Anchor.SKY).layers(2).circle(6.0F).sky(JUDGE_SKY).mana(100).charge(46)
				.cooldown(600).range(128).destroys().icon("nether_star")
				.action(c -> {
					SpellKit.release(c);
					judgement(c);
					return true;
				})
				.register();

		// --- Ранг 6: Императорский ---

		// Клинок Обетованного Рассвета: золотая стена света проходит 96 блоков и прорезает траншею.
		Spell.of("promised_dawnblade", LIGHT, 6, RITUAL).anchor(Anchor.LINE).layers(3).mana(165).charge(72).cooldown(2400)
				.range(BLADE_RANGE).destroys().icon("golden_sword")
				.action(c -> {
					SpellKit.release(c);
					Zones.later(c, 2 * c.copy, () -> dawnblade(c));
					return true;
				})
				.register();

		// --- Ранг 7: Божественный ---

		// Падение Небес: пятислойный круг, пять ореолов, столп света радиусом 12 судит только врагов.
		Spell.of("fallen_down", LIGHT, 7, RITUAL).anchor(Anchor.SKY).layers(5).circle(FALL_CIRCLE).sky(FALL_SKY).mana(280)
				.charge(120).cooldown(7200).range(128).destroys().noMulticast().icon("end_crystal")
				.action(c -> {
					SpellKit.release(c);
					heavenfall(c);
					return true;
				})
				.register();
	}

	// --- Общее: святой удар ---

	/** Святой удар: нежить получает ×2,5 и загорается на {@code ignite} тиков. */
	static void holyHit(Cast c, LivingEntity t, float base, Entity direct, int ignite) {
		boolean undead = Cast.isUndead(t);
		c.hurt(t, undead ? base * 2.5F : base, MagicDamage.HOLY, direct);
		if (undead) {
			t.igniteForTicks(c.duration(ignite));
			MagicFx.burst(c.level, Cast.center(t), ParticleTypes.SMOKE, 6, 0.25, 0.03);
		}
	}

	/** Белая звезда на месте удара. */
	static void starBurst(Cast c, Vec3 at, float size) {
		MagicFx.burst(c.level, at, ParticleTypes.END_ROD, Math.round(8 + 8 * size), 0.12 * size, 0.08 * size);
		MagicFx.burst(c.level, at, MagicParticles.spark(WHITE, 0.4F * size + 0.2F, 10), 6, 0.1, 0.05);
		MagicFx.burst(c.level, at, MagicParticles.spark(GOLD, 0.3F, 12), 5, 0.2 * size, 0.04);
		MagicFx.sound(c.level, at, MagicSounds.impactOf(c.spell), 0.7F, 1.5F);
	}

	static boolean isBoss(Entity e) {
		return e instanceof EnderDragon || e instanceof WitherBoss || e instanceof Warden;
	}

	/** Существа в вертикальном столбе радиуса {@code r} и высоты {@code height} над {@code base}. */
	private static List<LivingEntity> column(Cast c, Vec3 base, double r, double height, Predicate<LivingEntity> filter) {
		AABB box = new AABB(base.x - r - 1, base.y - 2, base.z - r - 1, base.x + r + 1, base.y + height, base.z + r + 1);
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
			double dx = e.getX() - base.x;
			double dz = e.getZ() - base.z;
			double rr = r + e.getBbWidth() * 0.5;
			if (dx * dx + dz * dz <= rr * rr && filter.test(e)) out.add(e);
		}
		return out;
	}

	/** Выстрел «чужой» (владелец — не маг и не его союзник): такие Покров и Святилище сжигают. */
	private static boolean hostileShot(Cast c, Projectile p) {
		Entity owner = p.getOwner();
		return p.isAlive() && (owner == null || !c.isAlly(owner));
	}

	/** Сжечь снаряд: вспышка, искры, звон. */
	private static void burnShot(Cast c, Projectile p, int color) {
		Vec3 at = p.position();
		MagicFx.burst(c.level, at, MagicParticles.spark(color, 0.35F, 10), 8, 0.15, 0.06);
		MagicFx.burst(c.level, at, ParticleTypes.END_ROD, 4, 0.1, 0.05);
		MagicFx.sound(c.level, at, MagicSounds.WARD_HIT, 0.7F, 1.5F + c.level.random.nextFloat() * 0.2F);
		p.discard();
	}

	/** Поддержать невидимый свет в блоке (сам гаснет через 2 с после того, как перестали поддерживать). */
	private static void keepLight(ServerLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		if (s.is(MagicBlocks.MAGIC_LIGHT) || !s.isAir() || s.is(Blocks.VOID_AIR)) return;
		level.setBlock(pos, MagicBlocks.MAGIC_LIGHT.defaultBlockState(), Block.UPDATE_ALL);
		level.scheduleTick(pos, MagicBlocks.MAGIC_LIGHT, 40);
	}

	/** Убрать состояние мага (для самопроверки и повторных касаний). */
	public static void recover(ServerPlayer p) {
		UUID id = p.getUUID();
		Zones.Zone f = FOLLOW.remove(id);
		if (f != null) f.stop();
		Deque<Zones.Zone> orbs = ORBS.remove(id);
		if (orbs != null) orbs.forEach(Zones.Zone::stop);
		VEILS.remove(id);
		BEAMS.keySet().removeIf(k -> k.startsWith(p.getStringUUID()));
	}

	// --- Свеча Странника ---

	/** Сколько неподвижных огоньков у мага (для самопроверки). */
	public static int orbs(ServerPlayer p) {
		Deque<Zones.Zone> orbs = ORBS.get(p.getUUID());
		return orbs == null ? 0 : (int) orbs.stream().filter(z -> z.age() < z.life).count();
	}

	private static boolean candlelight(Cast c) {
		SpellKit.release(c);
		if (c.copy > 0) {
			// Копии мультикаста — неподвижные огоньки кольцом вокруг мага.
			double a = Math.PI * 2 * c.copy / c.copies + c.caster.getYRot() * Mth.DEG_TO_RAD;
			staticOrb(c, c.caster.position().add(Math.cos(a) * 2.5, 2.2, Math.sin(a) * 2.5));
		} else if (c.caster.isShiftKeyDown()) {
			Vec3 at = c.aimPoint(CANDLE_RANGE).subtract(c.look().scale(0.6));
			staticOrb(c, at);
		} else {
			followOrb(c);
		}
		MagicFx.sound(c.level, c.hand(), MagicSounds.CIRCLE_COMPLETE, 0.5F, 1.9F);
		return true;
	}

	/** Огонёк над плечом мага: свет 15, золотая пыль следом, нежить рядом дымится. */
	private static void followOrb(Cast c) {
		int life = c.duration(CANDLE_TICKS);
		MagicBlocks.candle(c.caster, life);
		Zones.Zone old = FOLLOW.remove(c.caster.getUUID());
		if (old != null) old.stop();
		MagicFx.burst(c.level, orbOf(c.caster), MagicParticles.spark(WHITE, 0.45F, 16), 8, 0.15, 0.03);
		FOLLOW.put(c.caster.getUUID(), Zones.add(c, orbOf(c.caster), CANDLE_REACH, life, (z, age) -> {
			if (!c.caster.isAlive() || c.caster.isRemoved() || c.caster.level() != c.level) {
				z.stop();
				return;
			}
			z.center = orbOf(c.caster);
			if (age % 6 == 0) MagicFx.one(c.level, z.center, MagicParticles.mote(GOLD, 0.1F, 18), 0, -0.01, 0);
			if (age % 40 == 0) burnUndead(c, z.center);
		}));
	}

	private static Vec3 orbOf(LivingEntity p) {
		float yaw = p.getYRot() * Mth.DEG_TO_RAD;
		return p.getEyePosition().add(-Mth.cos(yaw) * 0.6, 0.55, -Mth.sin(yaw) * 0.6);
	}

	/** Неподвижный огонёк на 5 минут; девятый гасит самый старый. */
	private static void staticOrb(Cast c, Vec3 at) {
		Deque<Zones.Zone> orbs = ORBS.computeIfAbsent(c.caster.getUUID(), k -> new ArrayDeque<>());
		while (orbs.size() >= MAX_ORBS) orbs.pollFirst().stop();
		BlockPos pos = BlockPos.containing(at);
		Vec3 center = Vec3.atCenterOf(pos);
		keepLight(c.level, pos);
		MagicFx.line(c.level, c.hand(), center, MagicParticles.spark(GOLD, 0.18F, 8), 0.6);
		MagicFx.burst(c.level, center, MagicParticles.spark(WHITE, 0.5F, 14), 8, 0.12, 0.03);
		orbs.addLast(Zones.add(c, center, CANDLE_REACH, c.duration(ORB_TICKS), (z, age) -> {
			keepLight(c.level, pos);
			if (age % 5 == 0) {
				double bob = Math.sin(age * 0.12) * 0.12;
				MagicFx.one(c.level, center.add(0, bob, 0), MagicParticles.spark(GOLD, 0.32F, 8), 0, 0, 0);
			}
			if (age % 40 == 0) burnUndead(c, center);
		}));
	}

	/** Нежить у огонька дымится: 1 святого урона. */
	private static void burnUndead(Cast c, Vec3 at) {
		for (LivingEntity e : c.around(at, CANDLE_REACH, l -> Cast.isUndead(l) && c.isFoe(l))) {
			c.hurt(e, c.amount(1.0F), MagicDamage.HOLY);
			MagicFx.burst(c.level, Cast.center(e), ParticleTypes.SMOKE, 5, 0.2, 0.03);
		}
	}

	// --- Шестигранный Покров ---

	/** Покров мага: запас, купол или щит, сколько слоёв (мультикаст), трещины, разрушен ли и до какого тика запрет. */
	private static final class Veil {
		final boolean dome;
		final int layers;
		final float max;
		float pool;
		int cracks;
		boolean broken;
		long lockUntil;

		Veil(boolean dome, int layers, float max) {
			this.dome = dome;
			this.layers = layers;
			this.max = max;
			this.pool = max;
		}
	}

	private static void hook() {
		if (hooked) return;
		hooked = true;
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(entity instanceof ServerPlayer p) || wardAbsorb(p, source, amount));
	}

	/** Сколько запаса осталось у Покрова мага (−1 — Покрова нет). Для самопроверки. */
	public static float wardPool(ServerPlayer p) {
		Veil v = VEILS.get(p.getUUID());
		return v == null ? -1 : v.broken ? 0 : v.pool;
	}

	public static boolean wardDome(ServerPlayer p) {
		Veil v = VEILS.get(p.getUUID());
		return v != null && v.dome;
	}

	/**
	 * Удар по магу под Покровом: спереди (±60°) урон снижает {@link Magic#ward} на 70% — Покров платит из запаса;
	 * купол глотает удар сбоку и сзади целиком, пока хватает запаса. Возвращает, пропускать ли урон.
	 */
	public static boolean wardAbsorb(ServerPlayer p, DamageSource source, float amount) {
		Veil v = VEILS.get(p.getUUID());
		if (v == null || v.broken || amount <= 0 || !"ward".equals(p.getAttached(Magic.CASTING))) return true;
		Vec3 from = source.getSourcePosition();
		if (from == null) return true;
		Vec3 to = from.subtract(p.getEyePosition());
		boolean front = to.lengthSqr() < 1.0E-4 || to.normalize().dot(p.getViewVector(1.0F)) >= Math.cos(Math.toRadians(60));
		ServerLevel level = p.level();
		Vec3 at = p.getEyePosition().add(to.lengthSqr() < 1.0E-4 ? p.getViewVector(1.0F) : to.normalize().scale(v.dome ? DOME_R : WARD_DIST));
		if (front) {
			ripple(level, at, amount);
			drain(p, v, amount * Magic.WARD_BLOCK);
			return true;
		}
		if (!v.dome || v.pool < amount) return true;
		ripple(level, at, amount);
		drain(p, v, amount);
		return false;
	}

	/** Рябь по щиту в месте удара, «тинь-бум» тем ниже, чем сильнее удар. */
	private static void ripple(ServerLevel level, Vec3 at, float amount) {
		MagicFx.burst(level, at, MagicParticles.spark(CYAN, 0.35F, 8), 8, 0.25, 0.05);
		MagicFx.burst(level, at, MagicParticles.spark(WHITE, 0.6F, 5), 2, 0.05, 0.0);
		MagicFx.sound(level, at, MagicSounds.WARD_HIT, 0.9F, 1.6F - Math.min(0.8F, amount / 20.0F));
	}

	/** Списать запас: трещины на 66% и 33%, на нуле — Покров разлетается стеклом, держать его нельзя 3 с. */
	private static void drain(ServerPlayer p, Veil v, float amount) {
		v.pool -= amount;
		ServerLevel level = p.level();
		Vec3 front = p.getEyePosition().add(p.getViewVector(1.0F).scale(WARD_DIST));
		int cracks = v.pool <= v.max * 0.33F ? 2 : v.pool <= v.max * 0.66F ? 1 : 0;
		if (cracks > v.cracks && v.pool > 0) {
			v.cracks = cracks;
			MagicFx.sound(level, front, MagicSounds.SHATTER, 0.5F, 1.7F - 0.2F * cracks);
			MagicFx.burst(level, front, MagicParticles.spark(WHITE, 0.2F, 10), 10 * cracks, 0.8, 0.02);
		}
		if (v.pool > 0) return;
		v.pool = 0;
		v.broken = true;
		v.lockUntil = level.getGameTime() + WARD_LOCK;
		Vec3 at = v.dome ? p.position().add(0, 1, 0) : front;
		MagicFx.burst(level, at, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState()), 40,
				v.dome ? DOME_R * 0.7 : 1.0, 0.15);
		MagicFx.burst(level, at, MagicParticles.spark(CYAN, 0.3F, 14), 20, v.dome ? DOME_R * 0.6 : 0.9, 0.12);
		MagicFx.sound(level, at, MagicSounds.SHATTER, 1.2F, 1.1F);
		p.releaseUsingItem();
	}

	private static void ward(Cast c, int t) {
		UUID id = c.caster.getUUID();
		Veil v = VEILS.get(id);
		if (c.copy > 0) {
			// Копии — дальние слои того же Покрова (внешний бьётся первым: гаснет, когда запас ниже его доли).
			if (v != null && !v.broken && v.pool > v.max * c.copy / v.layers) drawVeil(c, v, c.copy, t);
			return;
		}
		long now = c.level.getGameTime();
		if (t == 1) {
			if (v != null && v.broken && now < v.lockUntil) {
				MagicFx.burst(c.level, c.hand(), new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState()), 6, 0.2, 0.05);
				c.caster.releaseUsingItem();
				return;
			}
			boolean dome = c.caster.isShiftKeyDown();
			v = new Veil(dome, c.copies, (dome ? DOME_POOL : WARD_POOL) * c.bonus * c.copies);
			VEILS.put(id, v);
			Vec3 look = c.look();
			// Шестигранный круг рисуется ячейка за ячейкой там, где встанет щит (у купола — под ногами).
			if (dome) {
				MagicFx.circle(c.level, c.spell, c.caster.position().add(0, 0.06, 0), SpellKit.UP, (float) DOME_R, 10, 6, 10);
			} else {
				MagicFx.circle(c.level, c.spell, c.eye().add(look.scale(WARD_DIST)), look, 2.2F, 10, 4, 10);
			}
			MagicFx.sound(c.level, c.hand(), MagicSounds.castOf(c.spell), 0.6F, 1.5F);
		}
		if (v == null || v.broken) return;
		deflect(c, v);
		drawVeil(c, v, 0, t);
	}

	/** Сжечь чужие снаряды, пересекающие щит (или купол). */
	private static void deflect(Cast c, Veil v) {
		Vec3 eye = c.eye();
		Vec3 look = c.look();
		Vec3 center = v.dome ? c.caster.position().add(0, 1, 0) : eye.add(look.scale(WARD_DIST));
		double reach = v.dome ? DOME_R + 1.5 : 3.2;
		for (Projectile pr : c.level.getEntitiesOfClass(Projectile.class, new AABB(center, center).inflate(reach),
				pr -> hostileShot(c, pr))) {
			Vec3 pos = pr.position();
			if (v.dome) {
				if (pos.distanceTo(center) > DOME_R + 1.0) continue;
			} else {
				Vec3 rel = pos.subtract(eye);
				double along = rel.dot(look);
				if (along < 0.2 || along > WARD_DIST + 1.6 || rel.subtract(look.scale(along)).length() > 2.4) continue;
			}
			burnShot(c, pr, CYAN);
			drain(c.caster, v, WARD_PROJECTILE);
			if (v.broken) return;
		}
	}

	/**
	 * Шестигранники щита: каждые 3 тика — светящиеся центры всех ячеек и рёбра у четырёх случайных (мерцание);
	 * с трещинами ячейки дрожат. Купол — полупрозрачная сфера и искры по ней.
	 */
	private static void drawVeil(Cast c, Veil v, int layer, int t) {
		RandomSource rnd = c.level.random;
		if (v.dome) {
			Vec3 center = c.caster.position().add(0, 1, 0);
			double r = DOME_R + 0.4 * layer;
			if (t % 20 == 1) MagicFx.barrier(c.level, center, (float) r, GOLD, CYAN, 22);
			if (t % 2 == 0) {
				for (int i = 0; i < 3; i++) {
					Vec3 d = new Vec3(rnd.nextGaussian(), Math.abs(rnd.nextGaussian()), rnd.nextGaussian()).normalize();
					MagicFx.one(c.level, center.add(d.scale(r)), MagicParticles.spark(i == 0 ? CYAN : GOLD, 0.2F, 8), 0, 0, 0);
				}
			}
			return;
		}
		if (t % 3 != layer % 3) return;
		Vec3 eye = c.eye();
		Vec3 look = c.look();
		Vec3 right = CircleLayout.right(look);
		Vec3 up = CircleLayout.up(look);
		double dist = WARD_DIST + 0.4 * layer;
		float jitter = v.cracks * 0.04F;
		int edges = 4;
		for (int i = 0; i < HEX.length; i++) {
			double x = HEX[i][0];
			double y = HEX[i][1];
			// Щит выгнут: края ближе к магу.
			Vec3 p = eye.add(look.scale(dist - (x * x + y * y) * 0.12)).add(right.scale(x)).add(up.scale(y));
			if (jitter > 0) p = p.add(rnd.nextGaussian() * jitter, rnd.nextGaussian() * jitter, rnd.nextGaussian() * jitter);
			boolean cracked = v.cracks > 0 && (i * 7 + v.cracks) % (4 - v.cracks) == 0;
			MagicFx.one(c.level, p, MagicParticles.spark(cracked ? WHITE : GOLD, cracked ? 0.3F : 0.42F, 5), 0, 0, 0);
			if (edges > 0 && rnd.nextInt(HEX.length - i) < edges) {
				edges--;
				for (int k = 0; k < 6; k++) {
					double a = Math.PI / 3 * k + Math.PI / 6;
					Vec3 corner = p.add(right.scale(Math.cos(a) * 0.55)).add(up.scale(Math.sin(a) * 0.55));
					MagicFx.one(c.level, corner, MagicParticles.spark(CYAN, 0.16F, 6), 0, 0, 0);
				}
			}
		}
	}

	// --- Солнечная Вспышка ---

	private static void flash(Cast c) {
		SpellKit.release(c);
		double r = c.radius(FLASH_R + 3.0 * c.copy);
		int blind = c.duration(FLASH_TICKS + 40 * c.copy);
		Vec3 eye = c.eye();
		Vec3 front = eye.add(c.look().scale(0.8));
		for (LivingEntity e : c.enemiesAround(eye, r)) {
			if (!c.caster.hasLineOfSight(e)) continue;
			// Сначала ожог нежити: удар мага снимает умиротворение, а цель должна забыть его уже после вспышки.
			if (Cast.isUndead(e)) {
				c.hurt(e, c.amount(5.0F), MagicDamage.HOLY);
				MagicFx.burst(c.level, Cast.center(e), ParticleTypes.SMOKE, 8, 0.3, 0.04);
			}
			e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, blind, 0), c.caster);
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, c.duration(60), 1), c.caster);
			if (e instanceof Mob m && !isBoss(m)) Minds.apply(m, Minds.Mode.CALM, blind, c.caster.position(), null);
		}
		// Сфера света от r3 до полного радиуса за 4 тика, лучи во все стороны, вспышка у смотрящих.
		MagicFx.sphere(c.level, front, 3.0F, (float) r, WHITE, 0xFFFFFF, 4);
		MagicFx.flash(c.level, front, (float) r * 2, 0.45F, WHITE, 10);
		MagicFx.shockwave(c.level, c.caster.position().add(0, 0.15, 0), SpellKit.UP, 1.0F, (float) r, GOLD, 10);
		RandomSource rnd = c.level.random;
		for (int i = 0; i < 8; i++) {
			Vec3 d = new Vec3(rnd.nextGaussian(), rnd.nextGaussian() * 0.6, rnd.nextGaussian()).normalize();
			MagicFx.beam(c.level, front, front.add(d.scale(r * 0.6)), 0.08F, GOLD, WHITE, 5);
		}
		MagicFx.burst(c.level, front, ParticleTypes.END_ROD, 40, 0.3, 0.6);
		MagicFx.burst(c.level, front, MagicParticles.spark(WHITE, 3.0F, 6), 1, 0, 0);
		MagicFx.soundFar(c.level, front, MagicSounds.PEAK, 32, 1.6F);
	}

	// --- Благословение Рассвета ---

	private static void blessing(Cast c) {
		SpellKit.release(c);
		Vec3 feet = c.caster.position();
		double r = c.radius(BLESS_R + 4.0 * (c.copies - 1));
		if (c.copy > 0) {
			// Копии лишь расширяют круг (сделано у главной) — золотая волна до нового края.
			MagicFx.shockwave(c.level, feet.add(0, 0.2, 0), SpellKit.UP, 1.0F, (float) r, GOLD, 16);
			return;
		}
		int ticks = c.duration(BLESS_TICKS);
		int halos = 0;
		for (LivingEntity e : c.alliesAround(feet.add(0, 1, 0), r)) {
			e.addEffect(new MobEffectInstance(MobEffects.STRENGTH, ticks, 0), c.caster);
			e.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, ticks, 0), c.caster);
			if (c.power >= 1.2F) e.addEffect(new MobEffectInstance(MobEffects.LUCK, ticks, 0), c.caster);
			MagicFx.cloud(c.level, e.getEyePosition().add(0, 1.2, 0), MagicParticles.mote(GOLD, 0.14F, 26), 10, 0.4, 0.2, 0.4, 0.0);
			if (halos++ < 12) {
				MagicFx.circle(c.level, LIGHT, 1, GOLD, WHITE, e.getEyePosition().add(0, 0.55, 0), SpellKit.UP,
						0.4F + e.getBbWidth() * 0.2F, 1, 0, 4, 20, 8, 0.4F);
			}
		}
		MagicFx.shockwave(c.level, feet.add(0, 0.2, 0), SpellKit.UP, 1.0F, (float) r, GOLD, 16);
		MagicFx.burst(c.level, feet.add(0, 1, 0), MagicParticles.spark(WHITE, 0.4F, 18), 20, 0.6, 0.08);
		MagicFx.sound(c.level, feet, MagicSounds.CIRCLE_COMPLETE, 0.9F, 1.3F);
	}

	// --- Солнечный Луч ---

	private static void sunbeam(Cast c, int t) {
		String key = c.caster.getStringUUID() + "#" + c.copy;
		Vec3 from = c.hand();
		Vec3 want = c.copy == 0 ? c.look() : c.direction();
		Vec3 prev = BEAMS.get(key);
		Vec3 dir = t == 1 || prev == null ? want : turn(prev, want, BEAM_TURN);
		BEAMS.put(key, dir);
		Vec3 eye = c.copy == 0 ? c.eye() : from;
		HitResult hit = Targeting.ray(c.level, c.caster, eye, dir, BEAM_RANGE, e -> e instanceof LivingEntity l && c.isFoe(l));
		Vec3 end = hit.getLocation();
		boolean hot = t > BEAM_RAMP;
		RandomSource rnd = c.level.random;
		// Копии рисуют свой луч к своей цели; разгоревшийся луч у всех — шире, с белым ядром.
		if (t % 4 == 1 && (c.copy > 0 || hot)) {
			MagicFx.beam(c.level, from, end, hot ? 0.7F : 0.22F, GOLD, WHITE, 5);
		}
		if (t == BEAM_RAMP + 1) MagicFx.soundFar(c.level, from, MagicSounds.BEAM_BLAST, 32, 1.4F);
		if (t % 2 == 0) {
			MagicFx.burst(c.level, end, MagicParticles.spark(GOLD, hot ? 0.45F : 0.3F, 8), 3, 0.15, 0.06);
			MagicFx.one(c.level, end, ParticleTypes.END_ROD, rnd.nextGaussian() * 0.05, 0.06, rnd.nextGaussian() * 0.05);
		}
		if (t % 10 != 0) return;
		float dmg = c.amount(hot ? 4.0F : 3.0F);
		for (LivingEntity e : c.along(from, end, hot ? BEAM_W_HOT : BEAM_W, c::isFoe)) holyHit(c, e, dmg, c.caster, 40);
		MagicFx.one(c.level, end.add(0, 0.05, 0), MagicParticles.ring(GOLD, hot ? 1.4F : 0.8F, 8), 0, 1, 0);
		// Конец луча плавит хрупкое (стекло, лёд, листва, снег) и поджигает верх блоков.
		if (hit.getType() == HitResult.Type.BLOCK) {
			Terrain.shatter(c, end, hot ? 1.6 : 1.1);
			if (t % 20 == 0) Terrain.scorch(c, end, hot ? 1.6 : 1.0, 0.3F);
		}
	}

	/** Повернуть направление {@code a} к {@code b} не больше чем на {@code max} радиан. */
	private static Vec3 turn(Vec3 a, Vec3 b, double max) {
		double ang = Math.acos(Mth.clamp(a.dot(b), -1.0, 1.0));
		if (ang <= max) return b;
		if (ang > Math.PI - 0.01) return a.add(CircleLayout.right(a).scale(Math.tan(max))).normalize();
		double k = max / ang;
		double s = Math.sin(ang);
		return a.scale(Math.sin((1 - k) * ang) / s).add(b.scale(Math.sin(k * ang) / s)).normalize();
	}

	// --- Святилище ---

	private static void sanctuary(Cast c) {
		SpellKit.release(c);
		Vec3 center = c.caster.position();
		double r = c.radius(SANCT_R + 2.0 * (c.copies - 1));
		if (c.copy > 0) {
			// Копии раздвигают стену святилища (радиус уже учтён у главной) — кольцо света до края.
			MagicFx.shockwave(c.level, center.add(0, 0.3, 0), SpellKit.UP, (float) r - 2.0F, (float) r, WHITE, 14);
			return;
		}
		int life = c.duration(SANCT_TICKS);
		MagicFx.circle(c.level, c.spell, center.add(0, 0.06, 0), SpellKit.UP, (float) r, 2, 0.05F, 14, life - 14, 20);
		MagicFx.pillar(c.level, center, 1.2F, 18.0F, GOLD, WHITE, life);
		MagicFx.barrier(c.level, center, (float) r, GOLD, WHITE, life);
		MagicFx.soundFar(c.level, center, MagicSounds.PILLAR, 48, 1.2F);
		MagicFx.sound(c.level, center, MagicSounds.DOMAIN, 0.8F, 1.4F);
		Zones.add(c, center, r, life, (z, age) -> {
			RandomSource rnd = c.level.random;
			if (age % 2 == 0) MagicFx.one(c.level, SpellKit.inDisc(rnd, center, r).add(0, 0.1, 0), ParticleTypes.END_ROD, 0, 0.1, 0);
			if (age % 6 == 0) MagicFx.one(c.level, SpellKit.inDisc(rnd, center, r).add(0, 0.5 + rnd.nextDouble() * 2, 0),
					MagicParticles.mote(GOLD, 0.12F, 30), 0, 0.02, 0);
			if (age % 40 == 0) MagicFx.sound(c.level, center, MagicSounds.LOOP_WARD, 0.5F, 1.2F);
			// Чужие стрелы, влетевшие внутрь, сгорают.
			if (age % 2 == 0) {
				AABB box = new AABB(center.x - r, center.y - 1, center.z - r, center.x + r, center.y + r + 2, center.z + r);
				for (Projectile p : c.level.getEntitiesOfClass(Projectile.class, box, p -> hostileShot(c, p))) {
					if (p.position().subtract(center).horizontalDistance() <= r) burnShot(c, p, GOLD);
				}
			}
			if (age % 10 != 0) return;
			for (LivingEntity e : column(c, center, r, r + 2, l -> true)) {
				if (c.isAlly(e)) {
					e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0), c.caster);
				} else if (c.isFoe(e)) {
					if (Cast.isUndead(e)) {
						c.hurt(e, c.amount(2.0F), MagicDamage.HOLY);
						e.igniteForTicks(40);
					}
					if (c.isEnemy(e)) Cast.knock(e, center, 0.5, 0.1);
				}
			}
			if (center.distanceToSqr(c.caster.position()) <= r * r) {
				c.caster.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0), c.caster);
			}
		});
	}

	// --- Небесный Суд ---

	private static void judgement(Cast c) {
		boolean twin = c.copy > 0;
		float k = twin ? 0.6F : 1.0F;
		Vec3 ground = c.aimGround();
		Vec3 sky = ground.add(0, JUDGE_SKY, 0);
		double r = c.radius(JUDGE_R) * (twin ? 0.8 : 1.0);
		// Руна на земле и стрелы света, повисшие остриём вниз на ободе небесного круга.
		MagicFx.circle(c.level, c.spell, ground.add(0, 0.08, 0), SpellKit.UP, (float) r, 6, JUDGE_TELEGRAPH, 12);
		if (!twin) {
			for (int i = 0; i < 12; i++) {
				double a = Math.PI * 2 * i / 12;
				Vec3 rim = sky.add(Math.cos(a) * 5.4, -0.5, Math.sin(a) * 5.4);
				MagicFx.beam(c.level, rim, rim.add(0, -2.4, 0), 0.08F, GOLD, WHITE, JUDGE_TELEGRAPH + JUDGE_ARROWS);
			}
		}
		SpellKit.skyTelegraph(c, ground, 6.0F * k, JUDGE_SKY, JUDGE_TELEGRAPH, () -> {
			judgementPillar(c, ground, sky, r, k);
			if (!twin) arrowRain(c, ground, sky);
		});
	}

	private static void judgementPillar(Cast c, Vec3 ground, Vec3 sky, double r, float k) {
		for (LivingEntity e : column(c, ground, r, JUDGE_SKY + 10, c::isFoe)) {
			float dmg = c.amount(28.0F * k) * (Cast.isUndead(e) ? 1.5F : 1.0F);
			c.hurt(e, dmg, MagicDamage.HOLY);
			e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, c.duration(60), 0), c.caster);
			Cast.knock(e, ground, 0.4, 0.6);
		}
		MagicFx.pillar(c.level, ground, (float) r, 40.0F, GOLD, WHITE, 18);
		MagicFx.beam(c.level, sky, ground, (float) r * 0.5F, WHITE, 0xFFFFFF, 10);
		MagicFx.shockwave(c.level, ground.add(0, 0.2, 0), SpellKit.UP, 1.0F, 8.0F * k, GOLD, 14);
		MagicFx.burst(c.level, ground.add(0, 1, 0), ParticleTypes.END_ROD, 60, r * 0.4, 0.3);
		MagicFx.burst(c.level, ground.add(0, 1, 0), MagicParticles.spark(GOLD, 0.6F, 20), 30, r * 0.4, 0.15);
		MagicFx.soundFar(c.level, ground, MagicSounds.PILLAR, 96, 0.8F);
		MagicFx.soundFar(c.level, ground, MagicSounds.EXPLOSION, 64, 1.3F);
		if (c.copy == 0) {
			MagicFx.flash(c.level, ground, 64, 0.6F, WHITE, 10);
			Magic.shake(c.level, ground, 2.0F, 18, 40);
		}
		// Оплавленная воронка: стекло по краю, выжженная земля вокруг.
		Terrain.crater(c, ground, 2.5 * k, 0.8, Terrain.Rim.GLASS);
		Terrain.scorch(c, ground, 4.0 * k, 0.12F);
	}

	/** Дождь из 30 стрел света за 1,5 с: 60% — во врагов в радиусе 8, по 3 урона, не больше 6 стрел в одну цель. */
	private static void arrowRain(Cast c, Vec3 ground, Vec3 sky) {
		Map<LivingEntity, Integer> hits = new HashMap<>();
		SpellKit.repeat(c, JUDGE_ARROWS, 1, i -> {
			RandomSource rnd = c.level.random;
			Vec3 at = null;
			if (rnd.nextFloat() < 0.6F) {
				List<LivingEntity> foes = c.foesAround(ground, JUDGE_RAIN_R);
				foes.removeIf(e -> hits.getOrDefault(e, 0) >= JUDGE_MAX_HITS);
				if (!foes.isEmpty()) at = Cast.center(foes.get(rnd.nextInt(foes.size())));
			}
			if (at == null) at = Targeting.ground(c.level, c.caster, SpellKit.inDisc(rnd, ground, JUDGE_RAIN_R).add(0, 3, 0));
			Vec3 from = SpellKit.inDisc(rnd, sky, 5.0).add(0, -1, 0);
			MagicFx.beam(c.level, from, at, 0.12F, GOLD, WHITE, 4);
			MagicFx.burst(c.level, at, MagicParticles.spark(WHITE, 0.35F, 8), 5, 0.15, 0.08);
			MagicFx.burst(c.level, at, ParticleTypes.END_ROD, 3, 0.1, 0.1);
			if (i % 2 == 0) MagicFx.sound(c.level, at, MagicSounds.impactOf(c.spell), 0.8F, 1.6F + rnd.nextFloat() * 0.3F);
			for (LivingEntity e : c.foesAround(at, 1.2)) {
				int n = hits.getOrDefault(e, 0);
				if (n >= JUDGE_MAX_HITS) continue;
				hits.put(e, n + 1);
				c.hurt(e, c.amount(3.0F), MagicDamage.HOLY);
			}
		});
	}

	// --- Клинок Обетованного Рассвета ---

	private static void dawnblade(Cast c) {
		Vec3 look = c.look();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		if (flat.lengthSqr() < 1.0E-4) flat = Vec3.directionFromRotation(0, c.caster.getYRot());
		flat = flat.normalize();
		if (c.copy > 0) {
			int side = c.copy % 2 == 1 ? 1 : -1;
			flat = yaw(flat, side * BLADE_FAN * ((c.copy + 1) / 2));
		}
		Vec3 dir = flat;
		Vec3 start = c.caster.position().add(dir.scale(1.5));
		Vec3 right = CircleLayout.right(dir);
		// Меч из света над магом и удар сверху вниз; потом — летящая золотая стена.
		if (c.copy == 0) {
			Vec3 hilt = c.hand();
			MagicFx.beam(c.level, hilt, hilt.add(0, 12, 0).add(dir.scale(2)), 0.6F, GOLD, WHITE, 8);
			MagicFx.beam(c.level, hilt.add(0, 12, 0).add(dir.scale(2)), start.add(dir.scale(6)), 0.6F, WHITE, 0xFFFFFF, 6);
			MagicFx.soundFar(c.level, start, MagicSounds.RELEASE_HEAVY, 96, 0.9F);
			Magic.shake(c.level, start, 0.8F, 14, 48);
		}
		MagicFx.slash(c.level, start.add(0, BLADE_HEIGHT * 0.5, 0), dir, (float) BLADE_HALF * 2, (float) BLADE_RANGE, GOLD, WHITE,
				(int) (BLADE_RANGE / BLADE_SPEED));
		MagicFx.soundFar(c.level, start, MagicSounds.BEAM_BLAST, 128, 0.8F);
		Set<LivingEntity> struck = new HashSet<>();
		int life = (int) Math.ceil(BLADE_RANGE / BLADE_SPEED);
		Zones.add(c, start, BLADE_HALF, life, (z, age) -> {
			double d0 = (age - 1) * BLADE_SPEED;
			double d1 = Math.min(BLADE_RANGE, age * BLADE_SPEED);
			Vec3 a = start.add(dir.scale(d0));
			Vec3 b = start.add(dir.scale(d1));
			z.center = b;
			List<LivingEntity> hit = new ArrayList<>(c.along(a.add(0, 1.5, 0), b.add(0, 1.5, 0), BLADE_HALF, c::isFoe));
			hit.addAll(c.along(a.add(0, 5.5, 0), b.add(0, 5.5, 0), BLADE_HALF, c::isFoe));
			for (LivingEntity e : hit) {
				if (!struck.add(e)) continue;
				double dist = e.position().subtract(start).horizontalDistance();
				float dmg = c.amount(48.0F) * (Cast.isUndead(e) ? 1.5F : 1.0F) * (dist > BLADE_TRENCH ? 0.75F : 1.0F);
				if (isBoss(e)) dmg = Math.min(dmg, c.amount(40.0F));
				c.hurt(e, dmg, MagicDamage.HOLY);
				Cast.knock(e, e.position().subtract(dir), 1.5, 0.4);
				MagicFx.burst(c.level, Cast.center(e), MagicParticles.spark(WHITE, 0.5F, 12), 10, 0.4, 0.15);
			}
			// Стена света: искры по всей ширине и высоте фронта.
			RandomSource rnd = c.level.random;
			for (int i = 0; i < 6; i++) {
				Vec3 p = b.add(right.scale((rnd.nextDouble() * 2 - 1) * BLADE_HALF)).add(0, rnd.nextDouble() * BLADE_HEIGHT, 0);
				MagicFx.one(c.level, p, i % 2 == 0 ? MagicParticles.spark(GOLD, 0.6F, 12) : ParticleTypes.END_ROD,
						dir.x * 0.4, 0.05, dir.z * 0.4);
			}
			if (age % 4 == 0) MagicFx.soundFar(c.level, b, MagicSounds.GUST, 48, 1.4F);
			// Траншея 3 в ширину и 2 в глубину на первых 64 блоках; листва и трава в высоте волны испаряются, стволы срезаны.
			if (d0 < BLADE_TRENCH) {
				Terrain.tunnel(c, a.add(0, -0.5, 0), b.add(0, -0.5, 0), 1.5, Terrain.Rim.NONE);
				Terrain.shatter(c, b.add(0, BLADE_HEIGHT * 0.5, 0), BLADE_HALF + 1);
				cutTrunks(c, b, right);
			} else {
				afterglow(c, b, right);
			}
		});
		// Через полсекунды после волны песок на дне траншеи становится стеклом, вдоль неё тлеет золото.
		Zones.later(c, life + 10, () -> {
			if (Terrain.griefing(c.level)) glassTrench(c, start, dir, right);
			for (double s = 2; s < BLADE_TRENCH; s += 4) afterglow(c, start.add(dir.scale(s)), right);
		});
	}

	private static Vec3 yaw(Vec3 v, double deg) {
		double a = Math.toRadians(deg);
		double cos = Math.cos(a);
		double sin = Math.sin(a);
		return new Vec3(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos);
	}

	/** Срезать стволы в высоте волны (от 1 до 8 блоков над землёй) на ширину волны. */
	private static void cutTrunks(Cast c, Vec3 at, Vec3 right) {
		if (!Terrain.griefing(c.level)) return;
		BlockPos o = BlockPos.containing(at);
		int w = (int) Math.ceil(BLADE_HALF);
		for (BlockPos p : BlockPos.betweenClosed(o.offset(-w, 1, -w), o.offset(w, (int) BLADE_HEIGHT, w))) {
			BlockState s = c.level.getBlockState(p);
			if (s.is(BlockTags.LOGS) && Terrain.breakable(c.level, c.caster, p, s, 100)) {
				c.level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
	}

	private static void glassTrench(Cast c, Vec3 start, Vec3 dir, Vec3 right) {
		for (double s = 0; s < BLADE_TRENCH; s += 1.0) {
			for (int l = -1; l <= 1; l++) {
				BlockPos floor = BlockPos.containing(start.add(dir.scale(s)).add(right.scale(l))).below(3);
				BlockState st = c.level.getBlockState(floor);
				if ((st.is(Blocks.SAND) || st.is(Blocks.RED_SAND)) && c.level.mayInteract(c.caster, floor)) {
					c.level.setBlock(floor, Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
		}
	}

	/** Золотые искры, тлеющие на земле. */
	private static void afterglow(Cast c, Vec3 at, Vec3 right) {
		RandomSource rnd = c.level.random;
		for (int i = 0; i < 2; i++) {
			Vec3 p = at.add(right.scale((rnd.nextDouble() * 2 - 1) * 1.5)).add(0, 0.1, 0);
			MagicFx.one(c.level, p, MagicParticles.mote(GOLD, 0.15F, 40), 0, 0.02, 0);
		}
	}

	// --- Падение Небес ---

	private static void heavenfall(Cast c) {
		Vec3 ground = c.aimGround();
		Vec3 sky = ground.add(0, FALL_SKY, 0);
		int total = FALL_HALOS + FALL_PILLAR;
		// 0. Пять слоёв неба держатся до конца; враги на 48 блоков вокруг помечены сиянием.
		MagicFx.circle(c.level, LIGHT, 7, BLUE_WHITE, WHITE, sky, SpellKit.DOWN, FALL_CIRCLE, 5, 2.5F, 4, total, 30, 0.04F);
		MagicFx.circle(c.level, c.spell, ground.add(0, 0.1, 0), SpellKit.UP, (float) FALL_R, 20, total - 10, 20);
		MagicFx.sphere(c.level, sky.add(0, -1, 0), 1.0F, 5.0F, WHITE, 0xFFFFFF, FALL_HALOS);
		MagicFx.soundFar(c.level, sky, MagicSounds.RELEASE_ULTIMATE, 256, 0.8F);
		MagicFx.soundFar(c.level, ground, MagicSounds.DOMAIN, 160, 0.7F);
		for (LivingEntity e : c.enemiesAround(ground, 48)) {
			e.addEffect(new MobEffectInstance(MobEffects.GLOWING, total + 20, 0), c.caster);
		}
		// 1. Небо трескается сине-белым светом; пять ореолов спускаются один за другим.
		for (int i = 0; i < 5; i++) {
			int n = i;
			Zones.later(c, n * 6, () -> {
				RandomSource rnd = c.level.random;
				for (int k = 0; k < 2; k++) {
					double a = rnd.nextDouble() * Math.PI * 2;
					Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
					MagicFx.lightningBolt(c.level, sky.add(out.scale(FALL_CIRCLE * 0.9)),
							sky.add(out.scale(FALL_CIRCLE * 1.7)).add(0, rnd.nextGaussian() * 3, 0), 0.5F, BLUE_WHITE, 8);
				}
				double h = FALL_SKY - 10.0 * (n + 1);
				MagicFx.circle(c.level, LIGHT, 5, BLUE_WHITE, WHITE, ground.add(0, h, 0), SpellKit.DOWN, (float) FALL_R + 0.5F, 1, 0,
						4, total - n * 6, 12, n % 2 == 0 ? 0.2F : -0.2F);
				MagicFx.shockwave(c.level, ground.add(0, h, 0), SpellKit.UP, (float) FALL_R + 4, (float) FALL_R, WHITE, 10);
				MagicFx.soundFar(c.level, ground.add(0, h, 0), MagicSounds.CIRCLE_COMPLETE, 160, 0.6F + 0.12F * n);
			});
		}
		Zones.later(c, FALL_HALOS - 6, () -> MagicFx.soundFar(c.level, ground, MagicSounds.RISER_SHORT, 160, 0.6F));
		// 2. Столп света на 4 с.
		Zones.later(c, FALL_HALOS, () -> heavenPillar(c, ground, sky));
	}

	private static void heavenPillar(Cast c, Vec3 ground, Vec3 sky) {
		MagicFx.pillar(c.level, ground, (float) FALL_R, FALL_SKY + 10, BLUE_WHITE, WHITE, FALL_PILLAR);
		MagicFx.beam(c.level, sky, ground, (float) FALL_R * 0.5F, WHITE, 0xFFFFFF, FALL_PILLAR);
		MagicFx.flash(c.level, ground, 160, 0.9F, WHITE, 14);
		MagicFx.shockwave(c.level, ground.add(0, 0.5, 0), SpellKit.UP, (float) FALL_R, (float) FALL_PUSH + 6, BLUE_WHITE, 16);
		MagicFx.soundFar(c.level, ground, MagicSounds.BEAM_BLAST, 256, 0.6F);
		MagicFx.soundFar(c.level, ground, MagicSounds.EXPLOSION_HUGE, 256, 0.8F);
		Magic.shake(c.level, ground, 3.0F, FALL_PILLAR, 96);
		Map<LivingEntity, Float> dealt = new HashMap<>();
		int[] depth = {0};
		Zones.add(c, ground, FALL_R, FALL_PILLAR, (z, age) -> {
			RandomSource rnd = c.level.random;
			// Внутри столпа по спирали поднимаются искры; земля раскалена добела.
			for (int i = 0; i < 4; i++) {
				double a = age * 0.35 + i * Math.PI / 2;
				double rr = FALL_R * (0.3 + 0.6 * ((age * 7 + i * 13) % 20) / 20.0);
				MagicFx.one(c.level, ground.add(Math.cos(a) * rr, 1 + rnd.nextDouble() * 30, Math.sin(a) * rr),
						MagicParticles.spark(i % 2 == 0 ? WHITE : BLUE_WHITE, 0.6F, 20), 0, 0.4, 0);
			}
			if (age % 3 == 0) MagicFx.one(c.level, SpellKit.inDisc(rnd, ground, FALL_R).add(0, 0.2, 0), ParticleTypes.END_ROD, 0, 0.3, 0);
			if (age % 20 == 1) MagicFx.soundFar(c.level, ground, MagicSounds.LOOP_BEAM, 128, 0.5F);
			if (age % 10 != 0) return;
			// Суд: только враги — 20 святого урона, нежить ×1,5, боссы не больше 90 за всё; свои внутри лечатся.
			for (LivingEntity e : column(c, ground, FALL_R, FALL_SKY, l -> true)) {
				if (c.isAlly(e)) {
					c.heal(e, c.amount(2.0F));
					continue;
				}
				if (!c.isEnemy(e)) continue;
				float dmg = c.amount(20.0F) * (Cast.isUndead(e) ? 1.5F : 1.0F);
				if (isBoss(e)) {
					float done = dealt.getOrDefault(e, 0.0F);
					dmg = Math.min(dmg, c.amount(FALL_BOSS_CAP) - done);
					if (dmg <= 0) continue;
					dealt.put(e, done + dmg);
				} else {
					e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 15, 0), c.caster);
				}
				c.hurt(e, dmg, MagicDamage.HOLY);
				MagicFx.burst(c.level, Cast.center(e), MagicParticles.spark(WHITE, 0.5F, 10), 6, 0.3, 0.1);
			}
			if (Mth.square(c.caster.getX() - ground.x) + Mth.square(c.caster.getZ() - ground.z) <= FALL_R * FALL_R) {
				c.heal(c.caster, c.amount(2.0F));
			}
			// Кольцо за столпом: стоящих рядом выталкивает наружу.
			for (LivingEntity e : column(c, ground, FALL_PUSH, FALL_SKY, l -> c.isFoe(l) && !isBoss(l))) {
				if (e.position().subtract(ground).horizontalDistance() > FALL_R) Cast.knock(e, ground, 0.8, 0.1);
			}
			// Столп вгрызается в землю: на 2 блока глубже каждые полсекунды (до 16), края оплавляются.
			if (depth[0] < FALL_DEPTH) {
				depth[0] += 2;
				boolean last = depth[0] >= FALL_DEPTH;
				Terrain.crater(c, ground.add(0, 1 - depth[0], 0), FALL_R - 2, 0.2, last ? Terrain.Rim.GLASS : Terrain.Rim.NONE);
				if (last) Zones.later(c, 20, () -> vitrify(c, ground));
			}
		});
		// 3. Обвал: вспышка, кольцо света до 40 блоков, 6 урона врагам и отбрасывание; падают перья света.
		Zones.later(c, FALL_PILLAR - 10, () -> collapse(c, ground, sky));
	}

	private static void collapse(Cast c, Vec3 ground, Vec3 sky) {
		MagicFx.flash(c.level, ground, 192, 1.0F, WHITE, 18);
		MagicFx.shockwave(c.level, ground.add(0, 1.0, 0), SpellKit.UP, (float) FALL_R, 40.0F, WHITE, 24);
		MagicFx.shockwave(c.level, ground.add(0, 6.0, 0), SpellKit.UP, (float) FALL_R, 32.0F, BLUE_WHITE, 20);
		MagicFx.sphere(c.level, ground, (float) FALL_R, 20.0F, WHITE, 0xFFFFFF, 12);
		MagicFx.soundFar(c.level, ground, MagicSounds.EXPLOSION_HUGE, 256, 0.6F);
		MagicFx.soundFar(c.level, ground, MagicSounds.THUNDER, 256, 0.7F);
		Magic.shake(c.level, ground, 4.0F, 30, 128);
		for (LivingEntity e : c.enemiesAround(ground, 40)) {
			c.hurt(e, c.amount(6.0F), MagicDamage.HOLY);
			if (!isBoss(e)) Cast.knock(e, ground, 1.5, 0.4);
		}
		// Перья света медленно падают из гаснущего неба.
		Zones.add(c, ground, 20, 60, (z, age) -> {
			RandomSource rnd = c.level.random;
			for (int i = 0; i < 3; i++) {
				MagicFx.one(c.level, SpellKit.inDisc(rnd, ground, 20).add(0, 6 + rnd.nextDouble() * 14, 0),
						MagicParticles.mote(i == 0 ? BLUE_WHITE : WHITE, 0.2F, 60), 0, -0.05, 0);
			}
		});
	}

	/** Край шахты стекленеет: стекло и кальцит кольцом по земле. */
	private static void vitrify(Cast c, Vec3 ground) {
		if (!Terrain.griefing(c.level)) return;
		BlockPos o = BlockPos.containing(ground);
		for (int i = 0; i < 72; i++) {
			double a = Math.PI * 2 * i / 72;
			BlockPos p = o.offset((int) Math.round(Math.cos(a) * (FALL_R - 1.5)), -1, (int) Math.round(Math.sin(a) * (FALL_R - 1.5)));
			for (int dy = 2; dy >= -3; dy--) {
				BlockPos q = p.above(dy);
				BlockState s = c.level.getBlockState(q);
				if (s.isAir()) continue;
				if (Terrain.breakable(c.level, c.caster, q, s, 100)) {
					c.level.setBlock(q, (i % 3 == 0 ? Blocks.CALCITE : Blocks.GLASS).defaultBlockState(), Block.UPDATE_ALL);
				}
				break;
			}
		}
	}
}
