package com.suchki.magic.spells;

import static com.suchki.magic.CastStyle.BOLT;
import static com.suchki.magic.CastStyle.CHANNEL;
import static com.suchki.magic.CastStyle.RITUAL;
import static com.suchki.magic.School.FIRE;

import com.suchki.magic.Anchor;
import com.suchki.magic.Cast;
import com.suchki.magic.CircleLayout;
import com.suchki.magic.FocusItem;
import com.suchki.magic.Magic;
import com.suchki.magic.MagicDamage;
import com.suchki.magic.MagicFx;
import com.suchki.magic.MagicParticles;
import com.suchki.magic.MagicSounds;
import com.suchki.magic.Spell;
import com.suchki.magic.SpellProjectile;
import com.suchki.magic.Terrain;
import com.suchki.magic.Trail;
import com.suchki.magic.Zones;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Стихия огня: от огненной стрелы ученика до Багровой Эксплозии, сжигающей всю ману мага.
 * Большие заклинания собираются на глазах: круги вспыхивают ярус за ярусом, солнце растёт в небе, семь кругов
 * ствола загораются по одному — и только потом удар.
 */
public final class FireSpells {
	/** Багрянец кругов и колец. */
	static final int CRIMSON = 0xD4141E;
	/** Золотой жар — кольца ударных волн, искры. */
	static final int GOLD = 0xFFB52E;
	/** Добела раскалённое ядро вспышек. */
	static final int WHITE_HOT = 0xFFF2C8;

	// Огненная стрела: скорость и время полёта (≈64 блока).
	private static final float ARROW_SPEED = 2.2F;
	private static final int ARROW_LIFE = 30;
	// Поток пламени: длина и половина раствора конуса.
	private static final double STREAM_LENGTH = 7.0;
	private static final double STREAM_ANGLE = 28.0;
	// Огненная Длань: через сколько тиков взрываются метки, сколько меток, раствор конуса.
	private static final int MARK_DELAY = 12;
	private static final int MARK_MAX = 4;
	private static final double MARK_ANGLE = 35.0;
	// Огненный цветок: время полёта шара (≈64 блока при 1,6 б/т).
	private static final int FIREBALL_LIFE = 40;
	// Огненный столп.
	private static final int PILLAR_TELEGRAPH = 10;
	private static final int PILLAR_LIFE = 40;
	private static final float PILLAR_R = 3.0F;
	private static final float PILLAR_HEIGHT = 25.0F;
	// Звездопад: высота неба, скорость падения обычных и великого метеора.
	private static final double METEOR_SKY = 40.0;
	private static final double METEOR_SPEED = 1.6;
	private static final double GREAT_SPEED = 1.3;
	private static final int GREAT_AT = 80;
	// Солнечное колесо: радиус, путь (тиков = блоков), радиус задевания.
	private static final double WHEEL_R = 3.0;
	private static final int WHEEL_RANGE = 48;
	private static final double WHEEL_HIT = 3.2;
	private static final int BLOCKED = Integer.MIN_VALUE;
	// Гелиогенезис: высота солнца и этапы (рождение, висит, падает).
	private static final float SUN_HEIGHT = 22.0F;
	private static final int SUN_FORM = 16;
	private static final int SUN_HANG = 40;
	private static final int SUN_FALL = 10;
	// Багровая Эксплозия: когда вспыхивает земной круг, начинается «вдох», гремит взрыв; предел силы; бессилие.
	private static final int GROUND_CIRCLE_AT = 14;
	private static final int INHALE_AT = 30;
	private static final int EXPLOSION_AT = 50;
	private static final float EXPLOSION_CAP = 1.4F;
	private static final int COLLAPSE_TICKS = 200;
	private static final Identifier COLLAPSE = Identifier.fromNamespaceAndPath("suchki", "crimson_collapse");

	private FireSpells() {}

	public static void register() {
		// --- Ранг 1: Начальный ---

		// Огненная стрела: быстрый снаряд; перегретая (накоплено почти до предела фокуса) пробивает одну цель.
		Spell.of("firebolt", FIRE, 1, BOLT).mana(10).charge(10).cooldown(8).range(64).icon("blaze_powder")
				.model("blaze_powder").size(0.3F).trail(Trail.FIRE)
				.action(c -> {
					SpellKit.release(c);
					SpellProjectile p = SpellProjectile.shoot(c, ARROW_SPEED).life(ARROW_LIFE);
					if (overheated(c)) p.pierce(1);
					MagicFx.burst(c.level, c.hand(), ParticleTypes.FLAME, 6, 0.08, 0.05);
					MagicFx.burst(c.level, c.hand(), MagicParticles.spark(WHITE_HOT, 0.3F, 6), 3, 0.05, 0.02);
					return true;
				})
				.hit((c, p, r) -> {
					if (r instanceof EntityHitResult e && e.getEntity() instanceof LivingEntity t && c.isFoe(t)) {
						c.hurt(t, c.amount(6.0F), MagicDamage.FIRE, p);
						t.igniteForTicks(c.duration(60));
						Cast.knock(t, t.position().subtract(p.getDeltaMovement()), 0.3, 0.1);
					}
					flameFlower(c, hitPoint(r), 0.6F);
				})
				.register();

		// Поток пламени: конус огня из посоха, пока держишь; копии мультикаста бьют веером ±20°.
		Spell.of("flames", FIRE, 1, CHANNEL).mana(10).charge(0).cooldown(10).range(STREAM_LENGTH).icon("fire_charge")
				.loop(MagicSounds.LOOP_FLAMES)
				.channel(FireSpells::flamestream)
				.register();

		// --- Ранг 2: Средний ---

		// Огненная Длань: Вспышка — метки на врагах в конусе, через 0,6 с каждая взрывается. Копии — новые волны.
		Spell.of("flash_fire_hand", FIRE, 2, BOLT).mana(22).charge(16).cooldown(60).range(28).icon("blaze_rod")
				.action(c -> {
					SpellKit.release(c);
					flashFireHand(c);
					return true;
				})
				.register();

		// --- Ранг 3: Продвинутый ---

		// Огненный цветок: тяжёлый шар по дуге, взрыв с воронкой; у копий — наведение на своих врагов.
		Spell.of("fireball", FIRE, 3, BOLT).layers(2).mana(34).charge(22).cooldown(100).range(64).destroys().icon("fire_charge")
				.model("fire_charge").size(0.5F).trail(Trail.FIRE)
				.action(c -> {
					SpellKit.release(c);
					MagicFx.sound(c.level, c.hand(), MagicSounds.RELEASE_HEAVY, 0.7F, 1.3F);
					MagicFx.shockwave(c.level, c.hand(), c.direction(), 0.3F, 1.6F, GOLD, 8);
					SpellProjectile p = SpellProjectile.shoot(c, 1.6F).gravity(0.015).life(FIREBALL_LIFE);
					if (c.copies > 1) p.homing(0.04);
					// Шар взрывается и в конце полёта, если ни во что не попал.
					Zones.add(c, c.hand(), 0, FIREBALL_LIFE + 20, (z, age) -> {
						if (!p.isAlive()) {
							z.stop();
						} else if (p.tickCount >= FIREBALL_LIFE - 1) {
							Vec3 at = p.position();
							p.discard();
							fireballBlast(c, at);
							z.stop();
						}
					});
					return true;
				})
				.hit((c, p, r) -> fireballBlast(c, hitPoint(r)))
				.register();

		// --- Ранг 4: Святой ---

		// Огненный столп: круг под прицелом, предупреждение, столп пламени на 25 блоков вверх.
		Spell.of("pillar_of_fire", FIRE, 4, RITUAL).anchor(Anchor.TARGET).layers(2).mana(58).charge(30).cooldown(260).range(96)
				.destroys().icon("blaze_rod")
				.action(c -> {
					SpellKit.release(c);
					firePillar(c, c.aimGround());
					return true;
				})
				.register();

		// --- Ранг 5: Королевский ---

		// Звездопад Возмездия: небесный круг, 10 метеоров и великий метеор; копии — свой круг и 5 метеоров.
		Spell.of("meteor_shower", FIRE, 5, RITUAL).anchor(Anchor.SKY).layers(3).circle(10.0F).sky((float) METEOR_SKY).mana(110)
				.charge(56).cooldown(900).range(128).destroys().icon("magma_block")
				.action(c -> {
					SpellKit.release(c);
					starfall(c);
					return true;
				})
				.register();

		// Солнечное колесо: огненное колесо катится по земле, взбирается на ступени, прожигает путь.
		Spell.of("sun_wheel", FIRE, 5, BOLT).layers(2).mana(95).charge(44).cooldown(600).range(WHEEL_RANGE).destroys().icon("sunflower")
				.action(c -> {
					SpellKit.release(c);
					MagicFx.soundFar(c.level, c.hand(), MagicSounds.RELEASE_HEAVY, 48, 0.9F);
					sunWheel(c);
					return true;
				})
				.register();

		// --- Ранг 6: Императорский ---

		// Гелиогенезис: малое солнце рождается в небе, жжёт землю, падает и взрывается.
		Spell.of("heliogenesis", FIRE, 6, RITUAL).anchor(Anchor.SKY).layers(3).sky(SUN_HEIGHT).mana(180).charge(84).cooldown(2800)
				.range(128).destroys().icon("shroomlight")
				.action(c -> {
					SpellKit.release(c);
					heliogenesis(c);
					return true;
				})
				.register();

		// --- Ранг 7: Божественный ---

		// Багровая Эксплозия: вся мана в один взрыв; семь кругов ствола, вдох, взрыв, гриб — и маг без сил.
		Spell.of("crimson_explosion", FIRE, 7, RITUAL).anchor(Anchor.LINE).layers(7).mana(250).charge(140).cooldown(6000).range(128)
				.destroys().noMulticast().icon("tnt")
				.action(c -> {
					SpellKit.release(c);
					crimsonExplosion(c);
					return true;
				})
				.register();
	}

	// --- Огненная стрела ---

	/** «Перегрев»: накоплено не меньше 90% от предела фокуса в руке. */
	static boolean overheated(Cast c) {
		float cap = 1.75F;
		for (InteractionHand h : InteractionHand.values()) {
			if (c.caster.getItemInHand(h).getItem() instanceof FocusItem f) {
				cap = f.cap;
				break;
			}
		}
		return c.power >= 1.0F + 0.9F * (cap - 1.0F) - 0.001F;
	}

	/** Огненный цветок удара: лепестки пламени во все стороны, искры, дымок. */
	private static void flameFlower(Cast c, Vec3 at, float size) {
		SpellKit.impact(c, at, size);
		petals(c, at, 6, 0.16 * size, 2);
		MagicFx.burst(c.level, at, ParticleTypes.SMOKE, 4, 0.15, 0.02);
	}

	/** Лепестки пламени: {@code n} языков огня расходятся от точки по горизонтали. */
	private static void petals(Cast c, Vec3 at, int n, double speed, int per) {
		double turn = c.level.random.nextDouble() * Math.PI;
		for (int i = 0; i < n; i++) {
			double a = turn + Math.PI * 2 * i / n;
			for (int k = 0; k < per; k++) {
				double sp = speed * (1.0 + 0.45 * k);
				MagicFx.one(c.level, at, ParticleTypes.FLAME, Math.cos(a) * sp, 0.02 + 0.03 * k, Math.sin(a) * sp);
			}
		}
	}

	// --- Поток пламени ---

	private static void flamestream(Cast c, int t) {
		Vec3 hand = c.hand();
		Vec3 dir = fanned(c.look(), c.copy, 20);
		RandomSource rnd = c.level.random;
		for (int i = 0; i < 4; i++) {
			Vec3 v = dir.scale(0.45 + rnd.nextDouble() * 0.2).add(jitter(rnd, 0.1));
			MagicFx.one(c.level, hand, ParticleTypes.FLAME, v.x, v.y, v.z);
		}
		if (t % 2 == 0) {
			Vec3 v = dir.scale(0.42).add(jitter(rnd, 0.07));
			MagicFx.one(c.level, hand, MagicParticles.spark(t % 4 == 0 ? WHITE_HOT : c.color(), 0.3F, 12), v.x, v.y, v.z);
		}
		if (t % 5 == 0) {
			// Жар на конце струи: дым и дрожащий воздух.
			Vec3 tip = streamTip(c, hand, dir);
			MagicFx.burst(c.level, tip, ParticleTypes.SMOKE, 3, 0.4, 0.02);
			MagicFx.burst(c.level, tip, ParticleTypes.WHITE_ASH, 4, 0.6, 0.01);
			if (t % 20 == 0) melt(c, tip, 1.5, true);
		}
		if (t % 10 != 0) return;
		for (LivingEntity e : c.around(hand, STREAM_LENGTH, e -> c.isFoe(e) && Cast.inCone(e, hand, dir, STREAM_LENGTH, STREAM_ANGLE))) {
			c.hurt(e, c.amount(3.0F), MagicDamage.FIRE);
			e.igniteForTicks(c.duration(40));
		}
	}

	/** Где кончается струя: первый блок по пути или полная длина. */
	private static Vec3 streamTip(Cast c, Vec3 hand, Vec3 dir) {
		Vec3 end = hand.add(dir.scale(STREAM_LENGTH));
		HitResult hit = c.level.clip(new ClipContext(hand, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c.caster));
		return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
	}

	// --- Огненная Длань: Вспышка ---

	private static void flashFireHand(Cast c) {
		List<LivingEntity> marks = marks(c);
		// Копии мультикаста — новые волны по тем же меткам, каждая слабее и на 6 тиков позже.
		float k = c.copy == 0 ? 1.0F : 0.6F;
		int delay = MARK_DELAY + 6 * c.copy;
		Vec3 hand = c.hand();
		if (marks.isEmpty()) {
			Vec3 at = c.aimPoint();
			MagicFx.beam(c.level, hand, at, 0.1F, c.color(), WHITE_HOT, 3);
			fireMark(c, at.add(0, 0.6, 0), delay);
			Zones.later(c, delay, () -> flashBurst(c, null, at, k, 0));
			return;
		}
		for (int i = 0; i < marks.size(); i++) {
			LivingEntity e = marks.get(i);
			int n = i;
			MagicFx.beam(c.level, hand, Cast.center(e), 0.1F, c.color(), WHITE_HOT, 3);
			fireMark(c, e.position().add(0, e.getBbHeight() + 0.5, 0), delay + n);
			Zones.later(c, delay + n, () -> flashBurst(c, e, Cast.center(e), k, n));
		}
		MagicFx.sound(c.level, hand, MagicSounds.cast(FIRE), 0.9F, 1.6F);
	}

	/** До четырёх целей в конусе по взгляду, видимых магу: сначала враги, потом ближние. */
	private static List<LivingEntity> marks(Cast c) {
		Vec3 eye = c.eye();
		Vec3 look = c.look();
		double range = c.spell.range;
		List<LivingEntity> list = c.around(eye, range, e -> c.isFoe(e) && Cast.inCone(e, eye, look, range, MARK_ANGLE)
				&& c.caster.hasLineOfSight(e));
		list.sort(Comparator.comparing((LivingEntity e) -> !c.isEnemy(e)).thenComparingDouble(e -> e.distanceToSqr(eye)));
		return list.size() > MARK_MAX ? new ArrayList<>(list.subList(0, MARK_MAX)) : list;
	}

	/** Метка: маленький огненный круг крутится над головой цели до взрыва. */
	private static void fireMark(Cast c, Vec3 head, int ticks) {
		MagicFx.circle(c.level, FIRE, 2, c.color(), c.lightColor(), head, SpellKit.UP, 0.5F, 1, 0, 3, Math.max(1, ticks - 3), 4, 1.5F);
		MagicFx.burst(c.level, head, ParticleTypes.FLAME, 5, 0.2, 0.01);
	}

	private static void flashBurst(Cast c, LivingEntity target, Vec3 fallback, float k, int n) {
		boolean live = target != null && target.isAlive();
		Vec3 at = live ? Cast.center(target) : fallback;
		if (live && c.isFoe(target)) {
			c.hurt(target, c.amount(9.0F * k), MagicDamage.FIRE);
			target.igniteForTicks(c.duration(80));
			Cast.knock(target, c.caster.position(), 0.4, 0.2);
		}
		for (LivingEntity e : c.foesAround(at, 2.0)) {
			if (e == target) continue;
			c.hurt(e, c.amount(4.0F * k), MagicDamage.FIRE);
			e.igniteForTicks(c.duration(80));
			Cast.knock(e, at, 0.4, 0.15);
		}
		MagicFx.sphere(c.level, at, 0.3F, 2.0F, c.color(), WHITE_HOT, 8);
		MagicFx.burst(c.level, at, MagicParticles.spark(WHITE_HOT, 0.5F, 8), 6, 0.2, 0.05);
		MagicFx.burst(c.level, at, ParticleTypes.FLAME, 16, 0.4, 0.12);
		MagicFx.flash(c.level, at, 8, 0.15F, WHITE_HOT, 4);
		MagicFx.sound(c.level, at, MagicSounds.impact(FIRE), 1.0F, 0.9F + 0.09F * n + 0.12F * c.copy);
	}

	// --- Огненный цветок ---

	private static void fireballBlast(Cast c, Vec3 at) {
		explode(c, at, c.radius(3.5), c.amount(16.0F), 0.5F, 1.0, c.duration(80));
		petals(c, at, 5, 0.3, 3);
		MagicFx.cloud(c.level, at.add(0, 1.5, 0), ParticleTypes.LARGE_SMOKE, 14, 0.4, 1.2, 0.4, 0.03);
		MagicFx.burst(c.level, at, ParticleTypes.ASH, 20, 1.5, 0.02);
		MagicFx.flash(c.level, at, 20, 0.2F, WHITE_HOT, 6);
		Terrain.crater(c, at, 2.0, 0.75, Terrain.Rim.FIRE);
		Terrain.scorch(c, at, 3.0, 0.3F);
	}

	// --- Огненный столп ---

	private static void firePillar(Cast c, Vec3 at) {
		// Предупреждение: двухъярусный круг дорисовывается на земле, из-под него поднимаются искры.
		MagicFx.circle(c.level, c.spell, at.add(0, 0.08, 0), SpellKit.UP, 3.6F, 2, 0.3F, PILLAR_TELEGRAPH, PILLAR_LIFE + 4, 16);
		MagicFx.sound(c.level, at, MagicSounds.RUMBLE, 1.6F, 1.2F);
		Zones.add(c, at, PILLAR_R, PILLAR_TELEGRAPH, (z, age) -> {
			RandomSource rnd = c.level.random;
			for (int i = 0; i < 4; i++) {
				Vec3 p = SpellKit.inDisc(rnd, at, PILLAR_R).add(0, 0.1, 0);
				MagicFx.one(c.level, p, ParticleTypes.FLAME, 0, 0.08 + 0.1 * rnd.nextDouble(), 0);
			}
			if (age % 3 == 0) MagicFx.burst(c.level, at.add(0, 0.2, 0), ParticleTypes.LAVA, 2, 1.5, 0);
		});
		Zones.later(c, PILLAR_TELEGRAPH, () -> erupt(c, at));
	}

	private static void erupt(Cast c, Vec3 at) {
		MagicFx.pillar(c.level, at, PILLAR_R, PILLAR_HEIGHT, c.color(), WHITE_HOT, PILLAR_LIFE);
		MagicFx.pillar(c.level, at, PILLAR_R * 0.5F, PILLAR_HEIGHT + 3, WHITE_HOT, 0xFFFFFF, PILLAR_LIFE - 6);
		MagicFx.flash(c.level, at, 48, 0.45F, WHITE_HOT, 8);
		MagicFx.shockwave(c.level, at.add(0, 0.2, 0), SpellKit.UP, 0.5F, 6.0F, GOLD, 14);
		MagicFx.burst(c.level, at.add(0, 1, 0), ParticleTypes.EXPLOSION, 4, 1.2, 0);
		MagicFx.soundFar(c.level, at, MagicSounds.PILLAR, 64, 0.85F);
		MagicFx.soundFar(c.level, at, MagicSounds.impact(FIRE), 48, 0.6F);
		Magic.shake(c.level, at, 1.4F, 18, 40);
		for (LivingEntity e : column(c, at, PILLAR_R, PILLAR_HEIGHT)) {
			c.hurt(e, c.amount(22.0F), MagicDamage.FIRE);
			SpellKit.launch(e, 1.4);
			e.igniteForTicks(c.duration(120));
		}
		Terrain.scorch(c, at, 4.0, 0.3F);
		melt(c, at, 4.0, false);
		Zones.add(c, at, PILLAR_R, PILLAR_LIFE, (z, age) -> {
			RandomSource rnd = c.level.random;
			for (int i = 0; i < 6; i++) {
				Vec3 p = SpellKit.inDisc(rnd, at, PILLAR_R * 0.9).add(0, rnd.nextDouble() * PILLAR_HEIGHT * 0.8, 0);
				MagicFx.one(c.level, p, ParticleTypes.FLAME, 0, 0.4 + rnd.nextDouble() * 0.4, 0);
			}
			if (age % 2 == 0) {
				// Искры дождём осыпаются вокруг столпа.
				Vec3 p = SpellKit.inDisc(rnd, at, PILLAR_R + 3).add(0, PILLAR_HEIGHT * (0.5 + 0.5 * rnd.nextDouble()), 0);
				MagicFx.one(c.level, p, MagicParticles.spark(GOLD, 0.25F, 30), 0, -0.25, 0);
			}
			if (age % 20 == 1) MagicFx.sound(c.level, at.add(0, 4, 0), MagicSounds.LOOP_FLAMES, 1.5F, 0.8F);
			if (age % 10 != 0) return;
			for (LivingEntity e : column(c, at, PILLAR_R, PILLAR_HEIGHT)) {
				c.hurt(e, c.amount(3.0F), MagicDamage.FIRE);
				e.igniteForTicks(c.duration(60));
			}
		});
	}

	// --- Звездопад Возмездия ---

	private static void starfall(Cast c) {
		boolean main = c.copy == 0;
		Vec3 ground = c.aimGround();
		Vec3 sky = ground.add(0, METEOR_SKY, 0);
		double r = main ? 10.0 : 7.0;
		int count = main ? 10 : 5;
		int life = main ? 96 : 50;
		// Небесный круг держится весь звездопад: ярусы снаружи внутрь, соседние крутятся навстречу.
		for (int i = 0; i < 3; i++) {
			int n = i;
			Zones.later(c, 5 * i, () -> MagicFx.circle(c.level, FIRE, 5, n == 1 ? CRIMSON : c.color(), c.lightColor(),
					sky.add(0, -0.8 * n, 0), SpellKit.DOWN, (float) (r * (1.0 - 0.25 * n)), 1, 0, 8, life - 5 * n, 20,
					n % 2 == 0 ? 0.12F : -0.16F));
		}
		MagicFx.soundFar(c.level, sky, MagicSounds.CIRCLE_BIG, 128, 0.8F);
		// Небо тлеет: угли и капли лавы сыплются из круга.
		Zones.add(c, sky, r, life, (z, age) -> {
			RandomSource rnd = c.level.random;
			MagicFx.one(c.level, SpellKit.inDisc(rnd, sky, r), ParticleTypes.FLAME, 0, -0.25, 0);
			if (age % 2 == 0) {
				MagicFx.one(c.level, SpellKit.inDisc(rnd, sky, r), MagicParticles.spark(age % 4 == 0 ? CRIMSON : GOLD, 0.4F, 40), 0, -0.2, 0);
			}
			if (age % 3 == 0) MagicFx.one(c.level, SpellKit.inDisc(rnd, sky, r * 0.8), ParticleTypes.FALLING_LAVA, 0, 0, 0);
		});
		for (int i = 0; i < count; i++) Zones.later(c, 8 + 7 * i, () -> meteor(c, ground, sky, r));
		if (!main) return;
		// Великий метеор: в центре неба раскрывается свой круг, и из него падает огромная глыба.
		Zones.later(c, GREAT_AT - 14, () -> {
			MagicFx.circle(c.level, FIRE, 6, WHITE_HOT, 0xFFFFFF, sky.add(0, -2.0, 0), SpellKit.DOWN, 4.0F, 2, 0.5F, 12, 24, 12, 0.5F);
			MagicFx.soundFar(c.level, sky, MagicSounds.CIRCLE_COMPLETE, 96, 0.6F);
		});
		Zones.later(c, GREAT_AT, () -> greatMeteor(c, ground, sky));
	}

	private static void meteor(Cast c, Vec3 ground, Vec3 sky, double r) {
		RandomSource rnd = c.level.random;
		Vec3 from = SpellKit.inDisc(rnd, sky, r * 0.9);
		Vec3 to;
		List<LivingEntity> foes = c.foesAround(ground, r + 2);
		if (!foes.isEmpty() && rnd.nextFloat() < 0.6F) {
			to = foes.get(rnd.nextInt(foes.size())).position();
		} else {
			Vec3 p = SpellKit.inDisc(rnd, ground, r);
			to = com.suchki.magic.Targeting.ground(c.level, c.caster, new Vec3(p.x, sky.y - 2, p.z));
		}
		Vec3 hit = to;
		fall(c, from, hit, METEOR_SPEED, 0.9F, () -> {
			explode(c, hit, c.radius(3.0), c.amount(18.0F), 0.5F, 1.0, c.duration(100));
			debris(c, hit, 12);
			Terrain.crater(c, hit, 2.0, 0.75, Terrain.Rim.MAGMA);
		});
	}

	private static void greatMeteor(Cast c, Vec3 ground, Vec3 sky) {
		fall(c, sky.add(0, -2.0, 0), ground, GREAT_SPEED, 2.6F, () -> {
			explode(c, ground, c.radius(5.0), c.amount(36.0F), 0.5F, 2.0, c.duration(140));
			MagicFx.shockwave(c.level, ground.add(0, 0.3, 0), SpellKit.UP, 1.0F, 14.0F, CRIMSON, 22);
			MagicFx.soundFar(c.level, ground, MagicSounds.EXPLOSION_HUGE, 160, 0.9F);
			MagicFx.flash(c.level, ground, 64, 0.6F, WHITE_HOT, 12);
			Magic.shake(c.level, ground, 2.5F, 30, 64);
			debris(c, ground, 30);
			smokeColumn(c, ground, 30);
			Terrain.crater(c, ground, 3.5, 0.75, Terrain.Rim.MAGMA);
		});
	}

	/** Падение с неба: маленький круг-портал, огненный след кометы, свист; удар — когда долетит. */
	private static void fall(Cast c, Vec3 from, Vec3 to, double speed, float width, Runnable impact) {
		int ticks = Math.max(6, Mth.ceil(from.distanceTo(to) / speed));
		MagicFx.circle(c.level, FIRE, 4, c.color(), c.lightColor(), from.add(0, 0.6, 0), SpellKit.DOWN, width * 1.6F, 1, 0, 4, 6, 8, 0.6F);
		MagicFx.comet(c.level, from, to, width, c.color(), WHITE_HOT, ticks);
		MagicFx.soundFar(c.level, from, MagicSounds.METEOR_FALL, 96, 0.7F + c.level.random.nextFloat() * 0.3F);
		Zones.later(c, ticks, impact);
	}

	/** Обломки земли в месте удара. */
	private static void debris(Cast c, Vec3 at, int count) {
		BlockPos below = BlockPos.containing(at).below();
		BlockState s = c.level.getBlockState(below);
		if (s.isAir()) s = Blocks.STONE.defaultBlockState();
		MagicFx.burst(c.level, at.add(0, 0.5, 0), new BlockParticleOption(ParticleTypes.BLOCK, s), count, 0.6, 0.35);
	}

	// --- Солнечное колесо ---

	private static void sunWheel(Cast c) {
		Vec3 dir = fanned(flat(c.look()), c.copy, 20);
		Vec3 axis = new Vec3(-dir.z, 0, dir.x);
		Vec3 start = c.caster.position().add(dir.scale(2.0));
		int[] feet = {Mth.floor(c.caster.getY() + 0.01)};
		int g = groundAt(c.level, start, feet[0]);
		if (g != BLOCKED) feet[0] = g;
		Vec3 first = new Vec3(start.x, feet[0] + WHEEL_R, start.z);
		Set<UUID> hit = new HashSet<>();
		int[] stuck = {0};
		// Круг у посоха вспыхивает и встаёт колесом.
		MagicFx.circle(c.level, FIRE, 5, c.color(), WHITE_HOT, first, axis, (float) WHEEL_R, 2, 0.25F, 4, 2, 8, 1.2F);
		MagicFx.flash(c.level, first, 16, 0.2F, WHITE_HOT, 5);
		Zones.add(c, first, WHEEL_R, WHEEL_RANGE, (z, age) -> {
			Vec3 ctr = z.center;
			wheelFx(c, ctr, dir, axis, age);
			wheelBurn(c, ctr, dir, hit);
			if (age >= WHEEL_RANGE) {
				wheelEnd(c, ctr);
				z.stop();
				return;
			}
			Vec3 next = ctr.add(dir);
			int ny = groundAt(c.level, next, feet[0]);
			if (ny == BLOCKED) {
				// Стена: при разрешённом разрушении колесо прожигает в ней проход, иначе взрывается о неё.
				// Проход прожигается от земли до середины колеса; несокрушимая стена — сразу взрыв.
				Vec3 low = ctr.subtract(0, WHEEL_R - 2.0, 0);
				if (Terrain.griefing(c.level) && stuck[0]++ < 4 && Terrain.tunnel(c, low, low.add(dir.scale(3.0)), 2.0, Terrain.Rim.FIRE) > 0) {
					return;
				}
				wheelEnd(c, ctr);
				z.stop();
				return;
			}
			stuck[0] = 0;
			feet[0] = ny;
			Vec3 moved = new Vec3(next.x, ny + WHEEL_R, next.z);
			Terrain.tunnel(c, ctr, moved, 1.5, Terrain.Rim.NONE);
			if (age % 2 == 0) Terrain.scorch(c, new Vec3(next.x, ny, next.z), 2.0, 0.35F);
			z.center = moved;
		});
	}

	private static void wheelFx(Cast c, Vec3 ctr, Vec3 dir, Vec3 axis, int age) {
		// Обод: языки пламени по кругу; колесо катится вперёд (верх обода летит по ходу).
		double spin = -age * 0.5;
		for (int i = 0; i < 12; i++) {
			double a = spin + Math.PI * 2 * i / 12;
			double cos = Math.cos(a), sin = Math.sin(a);
			Vec3 p = ctr.add(dir.scale(cos * WHEEL_R)).add(0, sin * WHEEL_R, 0);
			Vec3 v = dir.scale(sin * 0.18 + 0.1).add(0, -cos * 0.18, 0);
			MagicFx.one(c.level, p, ParticleTypes.FLAME, v.x, v.y, v.z);
		}
		// Спираль искр вокруг обода.
		double h = age * 0.9;
		for (int s = -1; s <= 1; s += 2) {
			Vec3 p = ctr.add(dir.scale(Math.cos(h + s) * WHEEL_R * 1.1)).add(0, Math.sin(h + s) * WHEEL_R * 1.1, 0)
					.add(axis.scale(s * 0.6 * Math.cos(h * 2)));
			MagicFx.one(c.level, p, MagicParticles.spark(s > 0 ? WHITE_HOT : GOLD, 0.35F, 10), 0, 0.02, 0);
		}
		if (age % 3 == 1) MagicFx.shockwave(c.level, ctr, axis, (float) WHEEL_R - 0.4F, (float) WHEEL_R + 0.2F, c.color(), 6);
		if (age % 6 == 1) MagicFx.shockwave(c.level, ctr, axis, 0.6F, 1.6F, WHITE_HOT, 6);
		// Горящая колея позади.
		MagicFx.burst(c.level, ctr.subtract(dir.scale(1.5)).add(0, 0.3 - WHEEL_R, 0), ParticleTypes.FLAME, 3, 0.4, 0.01);
		if (age % 8 == 1) MagicFx.sound(c.level, ctr, MagicSounds.cast(FIRE), 1.4F, 0.7F);
	}

	private static void wheelBurn(Cast c, Vec3 ctr, Vec3 dir, Set<UUID> hit) {
		for (LivingEntity e : c.foesAround(ctr, WHEEL_HIT)) {
			if (!hit.add(e.getUUID())) continue;
			c.hurt(e, c.amount(28.0F), MagicDamage.FIRE);
			e.igniteForTicks(c.duration(160));
			Cast.knock(e, e.position().subtract(dir), 1.0, 0.6);
			MagicFx.burst(c.level, Cast.center(e), ParticleTypes.FLAME, 14, 0.4, 0.1);
			MagicFx.sound(c.level, Cast.center(e), MagicSounds.impact(FIRE), 1.0F, 0.8F);
		}
	}

	private static void wheelEnd(Cast c, Vec3 ctr) {
		Vec3 low = ctr.subtract(0, WHEEL_R - 1.0, 0);
		explode(c, low, c.radius(4.0), c.amount(14.0F), 0.5F, 1.2, c.duration(100));
		MagicFx.flash(c.level, low, 32, 0.35F, WHITE_HOT, 8);
		Terrain.crater(c, low.subtract(0, 0.5, 0), 2.0, 0.75, Terrain.Rim.FIRE);
	}

	/**
	 * Уровень ног колеса (первый свободный блок над землёй) в столбце {@code pos}: подъём до 2 блоков, спуск до 6;
	 * над обрывом — на блок ниже (колесо падает); {@link #BLOCKED} — стена.
	 */
	private static int groundAt(ServerLevel level, Vec3 pos, int feet) {
		int x = Mth.floor(pos.x), z = Mth.floor(pos.z);
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dy = 2; dy >= -6; dy--) {
			int y = feet + dy;
			if (free(level, m.set(x, y, z)) && free(level, m.set(x, y + 1, z)) && !free(level, m.set(x, y - 1, z))) return y;
		}
		return free(level, m.set(x, feet, z)) && free(level, m.set(x, feet + 1, z)) ? feet - 1 : BLOCKED;
	}

	private static boolean free(ServerLevel level, BlockPos p) {
		return level.getBlockState(p).getCollisionShape(level, p).isEmpty();
	}

	// --- Гелиогенезис ---

	private static void heliogenesis(Cast c) {
		boolean twin = c.copy > 0;
		float k = twin ? 0.6F : 1.0F;
		Vec3 ground = c.aimGround();
		Vec3 sun = ground.add(0, SUN_HEIGHT, 0);
		// 1. Три круга над солнцем — один за другим, снаружи внутрь, соседние крутятся навстречу.
		float[] radii = {9.5F, 7.0F, 4.5F};
		for (int i = 0; i < 3; i++) {
			int n = i;
			Zones.later(c, 5 * i, () -> {
				MagicFx.circle(c.level, FIRE, 6, n == 1 ? GOLD : c.color(), WHITE_HOT, sun.add(0, 3.0 - n, 0), SpellKit.DOWN,
						radii[n] * k, 1, 0, 8, SUN_FORM + SUN_HANG - 5 * n, 14, n % 2 == 0 ? 0.1F : -0.14F);
				MagicFx.sound(c.level, sun, MagicSounds.CIRCLE_COMPLETE, 2.0F, 0.7F + 0.15F * n);
			});
		}
		MagicFx.soundFar(c.level, sun, MagicSounds.CIRCLE_BIG, 128, 0.7F);
		// 2. Рождение: в центре кругов набухает ядро, к нему стягивается свет.
		Zones.later(c, 6, () -> {
			MagicFx.sphere(c.level, sun, 0.5F, 3.0F * k, c.color(), WHITE_HOT, SUN_FORM);
			MagicFx.soundFar(c.level, sun, MagicSounds.RISER_SHORT, 96, 0.7F);
		});
		Zones.add(c, sun, 6, SUN_FORM, (z, age) -> {
			RandomSource rnd = c.level.random;
			for (int i = 0; i < 4; i++) {
				Vec3 d = unit(rnd);
				Vec3 p = sun.add(d.scale(6.0 * k));
				MagicFx.one(c.level, p, MagicParticles.spark(i % 2 == 0 ? GOLD : WHITE_HOT, 0.35F, 14), -d.x * 0.4 * k, -d.y * 0.4 * k,
						-d.z * 0.4 * k);
			}
		});
		// 3. Солнце висит и жжёт землю.
		Zones.later(c, SUN_FORM, () -> sunHang(c, ground, sun, k));
		// 4. Падение и взрыв.
		Zones.later(c, SUN_FORM + SUN_HANG, () -> {
			MagicFx.comet(c.level, sun, ground, 3.0F * k, c.color(), WHITE_HOT, SUN_FALL);
			MagicFx.soundFar(c.level, sun, MagicSounds.METEOR_FALL, 128, 0.5F);
		});
		Zones.later(c, SUN_FORM + SUN_HANG + SUN_FALL, () -> sunImpact(c, ground, k));
	}

	private static void sunHang(Cast c, Vec3 ground, Vec3 sun, float k) {
		double reach = 14.0 * k;
		MagicFx.barrier(c.level, sun, 3.2F * k, c.color(), WHITE_HOT, SUN_HANG);
		if (c.copy == 0) MagicFx.flash(c.level, ground, 32, 0.18F, c.color(), SUN_HANG);
		melt(c, ground, reach, false);
		Zones.add(c, sun, reach, SUN_HANG, (z, age) -> {
			RandomSource rnd = c.level.random;
			if (age % 10 == 1) MagicFx.sphere(c.level, sun, 2.6F * k, 3.5F * k, c.color(), WHITE_HOT, 12);
			// Корона брызжет искрами, свет и лава каплями стекают вниз.
			for (int i = 0; i < 3; i++) {
				Vec3 d = unit(rnd);
				MagicFx.one(c.level, sun.add(d.scale(3.0 * k)), MagicParticles.spark(i == 0 ? WHITE_HOT : GOLD, 0.45F, 16),
						d.x * 0.25, d.y * 0.25, d.z * 0.25);
			}
			MagicFx.one(c.level, SpellKit.inDisc(rnd, sun, 2.0 * k).add(0, -2.6 * k, 0), ParticleTypes.FALLING_LAVA, 0, 0, 0);
			if (age % 2 == 0) MagicFx.one(c.level, SpellKit.inDisc(rnd, sun, 1.5 * k).add(0, -3.0 * k, 0), ParticleTypes.END_ROD, 0, -0.3, 0);
			// Земля под солнцем дымится.
			if (age % 2 == 1) MagicFx.one(c.level, SpellKit.inDisc(rnd, ground, reach * 0.8).add(0, 0.2, 0), ParticleTypes.SMOKE, 0, 0.05, 0);
			if (age % 20 == 1) MagicFx.soundFar(c.level, sun, MagicSounds.LOOP_FLAMES, 64, 0.6F);
			if (age % 10 != 0) return;
			boolean tick = age % 20 == 0;
			for (LivingEntity e : c.foesAround(ground, reach)) {
				e.igniteForTicks(c.duration(60));
				if (tick) c.hurt(e, c.amount(3.0F), MagicDamage.FIRE);
			}
		});
	}

	private static void sunImpact(Cast c, Vec3 ground, float k) {
		double r = c.radius(10.0 * k);
		explode(c, ground, r, c.amount(55.0F * k), 0.35F, 2.0, c.duration(300));
		// Львиная грива: огненное кольцо бежит до 16 блоков.
		fireRing(c, ground, 1.0, 16.0 * k, 14, c.amount(8.0F), 1.0, Set.of());
		MagicFx.shockwave(c.level, ground.add(0, 1.2, 0), SpellKit.UP, 1.0F, 16.0F * k, WHITE_HOT, 18);
		MagicFx.dome(c.level, ground, (float) r, c.color(), WHITE_HOT, 30);
		MagicFx.sphere(c.level, ground, 1.0F, (float) r * 1.3F, WHITE_HOT, 0xFFFFFF, 14);
		MagicFx.soundFar(c.level, ground, MagicSounds.SHOCKWAVE, 128, 0.7F);
		if (c.copy == 0) {
			MagicFx.flash(c.level, ground, 96, 0.8F, WHITE_HOT, 16);
			Magic.shake(c.level, ground, 3.0F, 40, 96);
		}
		smokeColumn(c, ground, 60);
		Terrain.crater(c, ground, 8.0 * k, 0.75, Terrain.Rim.MAGMA);
		Terrain.scorch(c, ground, 14.0 * k, 0.12F);
		Zones.later(c, 4, () -> glassify(c, ground, 10.0 * k));
		burningGround(c, ground, 5.0 * k, 100, 4.0F);
	}

	/** Горящая земля: огонь и дым, каждую секунду урон всем в столбе над кругом (и на дне воронки). */
	private static void burningGround(Cast c, Vec3 at, double r, int life, float perSecond) {
		Vec3 base = at.subtract(0, 7, 0);
		Zones.add(c, at, r, life, (z, age) -> {
			RandomSource rnd = c.level.random;
			for (int i = 0; i < 3; i++) {
				MagicFx.one(c.level, SpellKit.inDisc(rnd, at, r).add(0, 0.1, 0), ParticleTypes.FLAME, 0, 0.06 + rnd.nextDouble() * 0.08, 0);
			}
			if (age % 4 == 0) MagicFx.one(c.level, SpellKit.inDisc(rnd, at, r).add(0, 0.4, 0), ParticleTypes.LARGE_SMOKE, 0, 0.04, 0);
			if (age % 20 != 0) return;
			for (LivingEntity e : column(c, base, r, 10)) {
				c.hurt(e, c.amount(perSecond), MagicDamage.FIRE);
				e.igniteForTicks(c.duration(60));
			}
		});
	}

	/** Столб дыма над местом взрыва (дым костра поднимается высоко и долго висит). */
	private static void smokeColumn(Cast c, Vec3 at, int ticks) {
		Zones.add(c, at, 2, ticks, (z, age) -> {
			if (age % 2 != 0) return;
			RandomSource rnd = c.level.random;
			for (int i = 0; i < 2; i++) {
				MagicFx.one(c.level, SpellKit.inDisc(rnd, at, 2.5).add(0, 1, 0), ParticleTypes.CAMPFIRE_COSY_SMOKE, 0, 0.12 + rnd.nextDouble() * 0.1, 0);
			}
			MagicFx.one(c.level, SpellKit.inDisc(rnd, at, 2.0).add(0, 2, 0), ParticleTypes.LARGE_SMOKE, 0, 0.25, 0);
		});
	}

	// --- Багровая Эксплозия ---

	private static void crimsonExplosion(Cast c) {
		float k = allMana(c);
		Cast boosted = c.withPower(k);
		Vec3 hand = c.hand();
		Vec3 look = c.look();
		Vec3 point = c.aimPoint();
		Vec3 ground = c.aimGround();
		Vec3 core = ground.add(0, 1.5, 0);
		MagicFx.soundFar(c.level, hand, MagicSounds.RELEASE_ULTIMATE, 256, 0.9F);
		// 1. Ствол: семь кругов вспыхивают по одному от посоха к цели и загораются кольцом огня.
		for (int i = 0; i < 7; i++) {
			int n = i;
			Zones.later(c, 2 * i, () -> {
				Vec3 at = CircleLayout.lineStep(hand, point, n, 7);
				float r = 1.2F + 2.8F * n / 6.0F;
				MagicFx.circle(c.level, FIRE, 7, n % 2 == 0 ? CRIMSON : c.color(), WHITE_HOT, at, look, r, 1, 0, 3,
						EXPLOSION_AT - 2 * n, 6, n % 2 == 0 ? 0.4F : -0.4F);
				ringOfFire(c, at, look, r);
				MagicFx.sound(c.level, at, MagicSounds.CIRCLE_COMPLETE, 1.2F, 0.6F + 0.1F * n);
				Magic.shake(c.level, at, 0.3F + 0.1F * n, 6, 24);
			});
		}
		// 2. Последним — круг радиусом 16 на земле у цели; небо темнеет, поднимаются красные угли.
		Zones.later(c, GROUND_CIRCLE_AT, () -> {
			MagicFx.circle(c.level, FIRE, 7, CRIMSON, WHITE_HOT, ground.add(0, 0.1, 0), SpellKit.UP, 16.0F, 2, 0.4F, 14,
					EXPLOSION_AT - GROUND_CIRCLE_AT - 14, 6, 0.08F);
			MagicFx.soundFar(c.level, ground, MagicSounds.CIRCLE_BIG, 160, 0.7F);
			MagicFx.flash(c.level, ground, 96, 0.35F, 0x30040A, EXPLOSION_AT - GROUND_CIRCLE_AT);
			Zones.add(c, ground, 16, EXPLOSION_AT - GROUND_CIRCLE_AT, (z, age) -> {
				RandomSource rnd = c.level.random;
				for (int i = 0; i < 4; i++) {
					MagicFx.one(c.level, SpellKit.inDisc(rnd, ground, 16).add(0, 0.2, 0), MagicParticles.spark(i % 2 == 0 ? CRIMSON : GOLD,
							0.3F, 40), 0, 0.08 + rnd.nextDouble() * 0.08, 0);
				}
			});
		});
		// 3. Вдох: свет и пламя стягиваются в багровую точку, всё затихает.
		Zones.later(c, INHALE_AT, () -> {
			MagicFx.core(c.level, core, 0.4F, 2.5F, CRIMSON, WHITE_HOT, EXPLOSION_AT - INHALE_AT);
			MagicFx.soundFar(c.level, core, MagicSounds.RISER_SHORT, 128, 1.4F);
			Zones.add(c, core, 16, EXPLOSION_AT - INHALE_AT, (z, age) -> {
				RandomSource rnd = c.level.random;
				double reach = 16.0 * (1.0 - age / (double) (EXPLOSION_AT - INHALE_AT)) + 2.0;
				for (int i = 0; i < 8; i++) {
					Vec3 d = unit(rnd);
					Vec3 p = core.add(d.scale(reach));
					MagicFx.one(c.level, p, MagicParticles.spark(i % 3 == 0 ? WHITE_HOT : CRIMSON, 0.4F, 12), -d.x * reach * 0.09,
							-d.y * reach * 0.09, -d.z * reach * 0.09);
				}
			});
		});
		// 4. Взрыв.
		Zones.later(c, EXPLOSION_AT, () -> detonate(c, boosted, ground, k));
	}

	/** «Вся мана»: остаток маны мага вливается во взрыв. Сила = всё потраченное / 250, не больше ×1,4. */
	private static float allMana(Cast c) {
		ServerPlayer p = c.caster;
		if (p.hasInfiniteMaterials()) return Math.min(EXPLOSION_CAP, c.power);
		float rest = Magic.mana(p);
		p.setAttached(Magic.MANA, 0.0F);
		return Math.min(EXPLOSION_CAP, c.power + rest / 250.0F);
	}

	private static void detonate(Cast c, Cast boosted, Vec3 at, float k) {
		double r = Math.min(18.0, 16.0 * Math.sqrt(k));
		Set<UUID> struck = new HashSet<>();
		for (LivingEntity e : c.foesAround(at, r)) {
			double d = Math.min(1.0, Cast.center(e).distanceTo(at) / r);
			float dmg = boosted.amount(160.0F) * (float) (1.0 - 0.75 * d);
			c.hurt(e, capped(e, dmg), MagicDamage.FIRE);
			e.igniteForTicks(300);
			Cast.knock(e, at, 4.0 * (1.0 - 0.5 * d), 1.2);
			struck.add(e.getUUID());
		}
		// Ударная волна бежит до 48 блоков.
		fireRing(c, at, r, 48.0, 24, boosted.amount(8.0F), 1.5, struck);
		MagicFx.flash(c.level, at, 96, 1.0F, 0xFFE8D8, 24);
		Magic.shake(c.level, at, 4.0F, 50, 96);
		MagicFx.soundFar(c.level, at, MagicSounds.EXPLOSION_HUGE, 256, 0.75F);
		MagicFx.soundFar(c.level, at, MagicSounds.SHOCKWAVE, 192, 0.6F);
		MagicFx.soundFar(c.level, at, MagicSounds.RUMBLE, 160, 0.6F);
		Zones.later(c, 40, () -> MagicFx.soundFar(c.level, at, MagicSounds.THUNDER, 192, 0.5F));
		MagicFx.sphere(c.level, at, 0.5F, (float) r * 0.7F, 0xFFFFFF, WHITE_HOT, 10);
		MagicFx.sphere(c.level, at, 1.0F, (float) r * 1.3F, CRIMSON, WHITE_HOT, 26);
		MagicFx.dome(c.level, at, (float) r, CRIMSON, c.lightColor(), 50);
		MagicFx.shockwave(c.level, at.add(0, 0.3, 0), SpellKit.UP, 1.0F, 48.0F, WHITE_HOT, 24);
		MagicFx.shockwave(c.level, at.add(0, 0.6, 0), SpellKit.UP, 1.0F, 40.0F, CRIMSON, 30);
		MagicFx.burst(c.level, at, ParticleTypes.EXPLOSION_EMITTER, 6, r * 0.3, 0);
		MagicFx.burst(c.level, at, ParticleTypes.FLAME, 120, r * 0.4, 0.4);
		mushroom(c, at);
		debrisRain(c, at);
		// Воронка и выжженный горящий край.
		Terrain.crater(c, at, 14.0, 0.7, Terrain.Rim.FIRE);
		for (int i = 0; i < 8; i++) {
			double a = Math.PI * 2 * i / 8;
			Terrain.scorch(c, at.add(Math.cos(a) * 17, 0, Math.sin(a) * 17), 4.0, 0.3F);
		}
		collapse(c);
	}

	/** Огненный гриб: столб пламени и шапка, растущая до 50 блоков. */
	private static void mushroom(Cast c, Vec3 at) {
		MagicFx.pillar(c.level, at, 4.0F, 50.0F, CRIMSON, WHITE_HOT, 60);
		Zones.add(c, at, 12, 70, (z, age) -> {
			RandomSource rnd = c.level.random;
			double h = Math.min(46.0, 8.0 + age * 0.9);
			Vec3 cap = at.add(0, h, 0);
			double spread = 4.0 + age * 0.12;
			if (age % 8 == 1) MagicFx.sphere(c.level, cap, (float) spread * 0.6F, (float) spread * 1.2F, CRIMSON, GOLD, 14);
			if (age % 2 == 0) {
				for (int i = 0; i < 3; i++) {
					Vec3 d = unit(rnd);
					Vec3 p = cap.add(d.x * spread, d.y * spread * 0.4, d.z * spread);
					MagicFx.one(c.level, p, i == 0 ? ParticleTypes.FLAME : ParticleTypes.CAMPFIRE_COSY_SMOKE, d.x * 0.1, 0.05, d.z * 0.1);
				}
				MagicFx.one(c.level, SpellKit.inDisc(rnd, at, 3.0).add(0, rnd.nextDouble() * h, 0), ParticleTypes.LARGE_SMOKE, 0, 0.3, 0);
			}
		});
	}

	/** Дождь обломков и углей после взрыва. */
	private static void debrisRain(Cast c, Vec3 at) {
		BlockState stone = Blocks.STONE.defaultBlockState();
		BlockState dirt = Blocks.DIRT.defaultBlockState();
		Zones.add(c, at, 24, 90, (z, age) -> {
			RandomSource rnd = c.level.random;
			for (int i = 0; i < 3; i++) {
				Vec3 p = SpellKit.inDisc(rnd, at, 24).add(0, 20 + rnd.nextDouble() * 15, 0);
				MagicFx.one(c.level, p, new BlockParticleOption(ParticleTypes.BLOCK, i == 0 ? dirt : stone), 0, -0.6, 0);
			}
			MagicFx.one(c.level, SpellKit.inDisc(rnd, at, 20).add(0, 15 + rnd.nextDouble() * 15, 0), MagicParticles.spark(GOLD, 0.3F, 40),
					0, -0.3, 0);
			if (age % 5 == 0) MagicFx.burst(c.level, SpellKit.inDisc(rnd, at, 14).add(0, 1, 0), ParticleTypes.LAVA, 2, 0.5, 0);
		});
	}

	/**
	 * Бессилие после Эксплозии: замедление V, слабость III, не прыгнуть, не бежать, мана на нуле (не колдовать и не
	 * восстанавливаться) 10 с. Союзник, просидевший рядом на корточках 3 с, поднимает мага раньше.
	 */
	private static void collapse(Cast c) {
		ServerPlayer p = c.caster;
		p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, COLLAPSE_TICKS, 4), p);
		p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, COLLAPSE_TICKS, 2), p);
		AttributeInstance jump = p.getAttribute(Attributes.JUMP_STRENGTH);
		if (jump != null) jump.addOrUpdateTransientModifier(new AttributeModifier(COLLAPSE, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		boolean free = p.hasInfiniteMaterials();
		int[] helped = {0};
		MagicFx.burst(c.level, p.position().add(0, 0.2, 0), ParticleTypes.LARGE_SMOKE, 12, 0.4, 0.02);
		MagicFx.sound(c.level, p.position(), MagicSounds.impact(com.suchki.magic.School.EARTH), 0.8F, 0.6F);
		Zones.add(c, p.position(), 2, COLLAPSE_TICKS, (z, age) -> {
			boolean end = age >= COLLAPSE_TICKS || !p.isAlive() || p.isRemoved();
			if (!end) {
				p.setSprinting(false);
				if (!free) p.setAttached(Magic.MANA, 0.0F);
				boolean ally = false;
				for (Player o : c.level.players()) {
					if (o != p && o.isAlive() && o.isShiftKeyDown() && o.distanceToSqr(p) <= 6.25) ally = true;
				}
				helped[0] = ally ? helped[0] + 1 : 0;
				if (age % 10 == 0) MagicFx.burst(c.level, p.position().add(0, 0.3, 0), ParticleTypes.SMOKE, 3, 0.3, 0.01);
				if (helped[0] >= 60) {
					end = true;
					p.removeEffect(MobEffects.SLOWNESS);
					p.removeEffect(MobEffects.WEAKNESS);
					MagicFx.burst(c.level, p.position().add(0, 1, 0), MagicParticles.spark(GOLD, 0.3F, 16), 12, 0.4, 0.05);
				}
			}
			if (end) {
				recover(p);
				z.stop();
			}
		});
	}

	/** Снять запрет прыжка после бессилия. */
	public static void recover(LivingEntity p) {
		AttributeInstance jump = p.getAttribute(Attributes.JUMP_STRENGTH);
		if (jump != null) jump.removeModifier(COLLAPSE);
	}

	/** Маг ещё без сил после Багровой Эксплозии (для проверок). */
	public static boolean collapsed(LivingEntity p) {
		AttributeInstance jump = p.getAttribute(Attributes.JUMP_STRENGTH);
		return jump != null && jump.hasModifier(COLLAPSE);
	}

	private static float capped(LivingEntity e, float dmg) {
		if (boss(e)) return Math.min(dmg, 90.0F);
		if (e instanceof Player) return Math.min(dmg, 30.0F);
		return dmg;
	}

	private static boolean boss(LivingEntity e) {
		EntityType<?> t = e.getType();
		return t == EntityType.ENDER_DRAGON || t == EntityType.WITHER || t == EntityType.WARDEN;
	}

	/** Кольцо пламени по кругу в плоскости с нормалью {@code normal}. */
	private static void ringOfFire(Cast c, Vec3 at, Vec3 normal, float r) {
		Vec3 u = CircleLayout.right(normal);
		Vec3 v = CircleLayout.up(normal);
		int n = Math.min(28, 10 + Math.round(r * 4));
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n;
			Vec3 d = u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
			MagicFx.one(c.level, at.add(d.scale(r)), ParticleTypes.FLAME, d.x * 0.05, d.y * 0.05 + 0.02, d.z * 0.05);
		}
	}

	// --- Общее ---

	/**
	 * Огненный взрыв: урон {@code damage} в центре и {@code damage·edge} на краю, поджог, отбрасывание наружу;
	 * огненная сфера, ударное кольцо, пламя и дым, звук по размеру, тряска. Возвращает задетых.
	 */
	private static List<LivingEntity> explode(Cast c, Vec3 at, double radius, float damage, float edge, double knock, int ignite) {
		List<LivingEntity> hit = c.foesAround(at, radius);
		for (LivingEntity e : hit) {
			double d = Math.min(1.0, Cast.center(e).distanceTo(at) / radius);
			float k = (float) (1.0 - (1.0 - edge) * d);
			c.hurt(e, damage * k, MagicDamage.FIRE);
			e.igniteForTicks(ignite);
			Cast.knock(e, at, knock * k, 0.2 + 0.25 * knock * k);
		}
		float r = (float) radius;
		MagicFx.sphere(c.level, at, r * 0.15F, r * 1.1F, c.color(), WHITE_HOT, 10 + (int) r);
		MagicFx.shockwave(c.level, at.add(0, 0.2, 0), SpellKit.UP, 0.5F, r * 1.6F, GOLD, 14);
		MagicFx.burst(c.level, at, ParticleTypes.EXPLOSION_EMITTER, r >= 6 ? 3 : 1, radius * 0.25, 0);
		MagicFx.burst(c.level, at, ParticleTypes.FLAME, (int) (20 + r * 6), radius * 0.35, 0.18);
		MagicFx.burst(c.level, at, MagicParticles.spark(c.color(), 0.5F, 18), (int) (12 + r * 3), radius * 0.3, 0.15);
		MagicFx.burst(c.level, at, ParticleTypes.LARGE_SMOKE, (int) (8 + r * 2), radius * 0.3, 0.05);
		MagicFx.burst(c.level, at, ParticleTypes.LAVA, (int) (4 + r), radius * 0.2, 0);
		if (radius >= 8) {
			MagicFx.soundFar(c.level, at, MagicSounds.EXPLOSION_HUGE, 160, 1.0F);
			MagicFx.flash(c.level, at, r * 8, Math.min(1.0F, r / 14.0F), WHITE_HOT, 14);
		} else {
			MagicFx.soundFar(c.level, at, MagicSounds.EXPLOSION, 32 + r * 8, 0.9F + c.level.random.nextFloat() * 0.2F);
		}
		MagicFx.sound(c.level, at, MagicSounds.impact(FIRE), 1.2F, 0.8F);
		Magic.shake(c.level, at, (float) Math.min(4.0, 0.5 + radius * 0.25), 12 + (int) radius, radius * 8);
		return hit;
	}

	/**
	 * Огненное кольцо, бегущее от центра от {@code from} до {@code to} за {@code ticks} тиков: каждый, кого оно догнало,
	 * получает удар один раз (кроме {@code skip}).
	 */
	private static void fireRing(Cast c, Vec3 at, double from, double to, int ticks, float damage, double knock, Set<UUID> skip) {
		Set<UUID> done = new HashSet<>(skip);
		MagicFx.shockwave(c.level, at.add(0, 0.4, 0), SpellKit.UP, (float) from, (float) to, c.color(), ticks);
		Zones.add(c, at, to, ticks, (z, age) -> {
			double r = from + (to - from) * age / (double) ticks;
			if (age % 2 == 0) MagicFx.ring(c.level, at.add(0, 0.5, 0), r, ParticleTypes.FLAME, Math.min(40, 10 + (int) r), 0.15);
			if (age % 3 != 0 && age != ticks) return;
			for (LivingEntity e : c.foesAround(at, r)) {
				if (!done.add(e.getUUID())) continue;
				c.hurt(e, capped(e, damage), MagicDamage.FIRE);
				e.igniteForTicks(c.duration(80));
				Cast.knock(e, at, knock, 0.4);
			}
		});
	}

	/** Существа-враги в вертикальном столбе радиуса {@code r} и высоты {@code h} над точкой. */
	private static List<LivingEntity> column(Cast c, Vec3 base, double r, double h) {
		AABB box = new AABB(base.x - r - 1, base.y - 1, base.z - r - 1, base.x + r + 1, base.y + h, base.z + r + 1);
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, box, c::isFoe)) {
			double dx = e.getX() - base.x, dz = e.getZ() - base.z;
			double rr = r + e.getBbWidth() * 0.5;
			if (dx * dx + dz * dz <= rr * rr) out.add(e);
		}
		return out;
	}

	/**
	 * Растопить снег и лёд в радиусе. {@code always} — даже когда магии нельзя менять местность (струя пламени топит
	 * только снежный покров и лёд под собой); временные блоки других заклинаний не трогаются.
	 */
	private static void melt(Cast c, Vec3 center, double radius, boolean always) {
		if (!always && !Terrain.griefing(c.level)) return;
		BlockPos o = BlockPos.containing(center);
		int ri = Mth.ceil(Math.min(14, radius));
		for (BlockPos p : BlockPos.betweenClosed(o.offset(-ri, -3, -ri), o.offset(ri, 3, ri))) {
			int dx = p.getX() - o.getX(), dz = p.getZ() - o.getZ();
			if (dx * dx + dz * dz > radius * radius) continue;
			BlockState s = c.level.getBlockState(p);
			boolean snow = s.is(Blocks.SNOW) || s.is(Blocks.POWDER_SNOW);
			boolean ice = s.is(Blocks.ICE) || s.is(Blocks.FROSTED_ICE);
			if (!snow && !ice || Terrain.isTemp(p) || !c.level.mayInteract(c.caster, p)) continue;
			if (snow) {
				c.level.removeBlock(p, false);
			} else {
				c.level.setBlock(p, Blocks.WATER.defaultBlockState(), 3);
			}
			MagicFx.burst(c.level, Vec3.atCenterOf(p), ParticleTypes.CLOUD, 2, 0.3, 0.02);
		}
	}

	/** Песок, оплавленный жаром взрыва, становится стеклом (только при разрешённом разрушении). */
	private static void glassify(Cast c, Vec3 center, double radius) {
		if (!Terrain.griefing(c.level)) return;
		BlockPos o = BlockPos.containing(center);
		int ri = Mth.ceil(Math.min(12, radius));
		for (BlockPos p : BlockPos.betweenClosed(o.offset(-ri, -ri, -ri), o.offset(ri, 3, ri))) {
			if (p.distSqr(o) > radius * radius) continue;
			BlockState s = c.level.getBlockState(p);
			if ((s.is(Blocks.SAND) || s.is(Blocks.RED_SAND)) && !Terrain.isTemp(p) && c.level.mayInteract(c.caster, p)) {
				c.level.setBlock(p, s.is(Blocks.RED_SAND) ? Blocks.ORANGE_STAINED_GLASS.defaultBlockState() : Blocks.GLASS.defaultBlockState(), 3);
			}
		}
	}

	private static Vec3 hitPoint(HitResult r) {
		return r instanceof EntityHitResult e ? Cast.center(e.getEntity()) : r.getLocation();
	}

	/** Направление копии мультикаста: главная — прямо, остальные веером ±{@code step}°, ±2·{@code step}°… */
	private static Vec3 fanned(Vec3 dir, int copy, double step) {
		if (copy == 0) return dir;
		int side = copy % 2 == 1 ? 1 : -1;
		return yaw(dir, side * step * ((copy + 1) / 2));
	}

	/** Повернуть направление вокруг вертикали на {@code deg} градусов. */
	private static Vec3 yaw(Vec3 v, double deg) {
		double a = Math.toRadians(deg), cos = Math.cos(a), sin = Math.sin(a);
		return new Vec3(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos);
	}

	private static Vec3 flat(Vec3 v) {
		Vec3 f = new Vec3(v.x, 0, v.z);
		return f.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : f.normalize();
	}

	private static Vec3 jitter(RandomSource rnd, double s) {
		return new Vec3((rnd.nextDouble() - 0.5) * 2 * s, (rnd.nextDouble() - 0.5) * 2 * s, (rnd.nextDouble() - 0.5) * 2 * s);
	}

	/** Случайное направление (единичный вектор). */
	private static Vec3 unit(RandomSource rnd) {
		double y = rnd.nextDouble() * 2 - 1;
		double a = rnd.nextDouble() * Math.PI * 2;
		double h = Math.sqrt(1 - y * y);
		return new Vec3(Math.cos(a) * h, y, Math.sin(a) * h);
	}
}
