package com.suchki.magic.spells;

import static com.suchki.magic.CastStyle.BOLT;
import static com.suchki.magic.CastStyle.RITUAL;
import static com.suchki.magic.CastStyle.SELF;
import static com.suchki.magic.School.WIND;
import static com.suchki.magic.spells.SpellKit.DOWN;
import static com.suchki.magic.spells.SpellKit.UP;

import com.suchki.magic.Anchor;
import com.suchki.magic.Cast;
import com.suchki.magic.CircleLayout;
import com.suchki.magic.Magic;
import com.suchki.magic.MagicDamage;
import com.suchki.magic.MagicFx;
import com.suchki.magic.MagicParticles;
import com.suchki.magic.MagicSounds;
import com.suchki.magic.School;
import com.suchki.magic.Spell;
import com.suchki.magic.SpellProjectile;
import com.suchki.magic.Targeting;
import com.suchki.magic.Terrain;
import com.suchki.magic.Trail;
import com.suchki.magic.Zones;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Стихия ветра: от порыва под ногами и воздушной ладони до смерчей, бродящих за взглядом мага, и Дыхания
 * Штормового Дракона, выдирающего тоннель на сотню блоков. Ветер почти невидим — поэтому его рисуют облака, пыль,
 * сорванная листва и белые кольца сжатого воздуха; большие заклинания собираются на глазах: сфера обрастает
 * лезвиями, с неба спускается воронка, круги ствола вспыхивают один за другим за спиной у головы дракона.
 */
public final class WindSpells {
	/** Бледная зелень стихии — круги и кольца. */
	static final int PALE = 0x8CFFC8;
	/** Почти белый воздух — сжатые кольца, серпы, ядро. */
	static final int MIST = 0xF2FFF8;
	/** Голубовато-белый вихрь в ладони (Вихревая сфера, сюрикэн). */
	static final int AZURE = 0xBDEBFF;
	/** Серо-зелёные грозовые тучи. */
	static final int STORM = 0x5E7A72;
	/** Молнии внутри бури. */
	static final int ARC = 0xBFF6FF;

	// Восходящий Зефир: прыжок, рывок с Shift, добавка от каждой копии, длительность медленного падения.
	private static final double ZEPHYR_UP = 1.1;
	private static final double ZEPHYR_DASH = 1.4;
	private static final int ZEPHYR_TICKS = 1200;
	// Ладонь Ветра: дальность и половина раствора конуса.
	private static final double PALM_RANGE = 8.0;
	private static final double PALM_ANGLE = 25.0;
	// Незримые Серпы: скорость, разворот веера, полуширина лезвия, сколько мягких блоков срезает одно лезвие.
	private static final double SICKLE_SPEED = 2.6;
	private static final double SICKLE_FAN = 12.0;
	private static final double SICKLE_HALF = 1.25;
	private static final int SICKLE_CUT = 24;
	// Тропа Ветров: время полёта, скорость, на сколько сбивает сильный удар.
	private static final int FLY_TICKS = 600;
	private static final float FLY_SPEED = 0.06F;
	private static final int FLY_DISRUPT = 40;
	private static final float FLY_BREAK_DAMAGE = 6.0F;
	private static final String FLY_TAG = "suchki_windwalk";
	// Вихревая Сфера: рывок, сколько держит и перемалывает.
	private static final double SPHERE_DASH = 6.0;
	private static final int GRIND_TICKS = 10;
	// Воющий Смерч и Небесные Жернова: скорость, с которой смерч бредёт за прицелом.
	private static final double DRIFT = 0.15;
	private static final int CYCLONE_LIFE = 160;
	private static final int MAELSTROM_DESCENT = 15;
	private static final int MAELSTROM_LIFE = 200;
	private static final float MAELSTROM_SKY = 24.0F;
	// Вихревой Сюрикэн: радиус сферы лезвий и сколько она держится.
	private static final double SHURIKEN_R = 6.0;
	private static final int SHURIKEN_HOLD = 30;
	// Дыхание Штормового Дракона: вдох (головa дракона, круги ствола), длительность, длина и радиус луча, тоннель.
	private static final int BREATH_AT = 14;
	private static final int BREATH_TICKS = 60;
	private static final double BREATH_LENGTH = 128.0;
	private static final double BREATH_R = 4.0;
	private static final double BREATH_TUNNEL = 96.0;
	/** Дыхание не берёт обсидиан и укреплённый глубинный сланец (твёрдость 50 и выше). */
	private static final float BREATH_HARDNESS = 49.0F;

	/** Кому дан полёт «Тропы Ветров» (новый каст перекрывает прошлый). */
	private static final Map<UUID, Flight> FLIGHT = new HashMap<>();
	/** Кровотечение от лезвий ветра: сколько тиков осталось. */
	private static final Map<LivingEntity, int[]> BLEEDING = new WeakHashMap<>();

	private WindSpells() {}

	public static void register() {
		// --- Ранг 1: Начальный ---

		// Восходящий Зефир: порыв подбрасывает на ~8 блоков (с Shift — рывок вперёд), долгое медленное падение.
		Spell.of("featherfall", WIND, 1, SELF).mana(10).charge(8).cooldown(30).range(3).icon("feather").multicast(4)
				.action(WindSpells::zephyr)
				.register();

		// Ладонь Ветра: сжатый удар воздуха в конусе — урон, мощный отброс, отражённые стрелы.
		Spell.of("gale_palm", WIND, 1, BOLT).mana(9).charge(8).cooldown(16).range(PALM_RANGE).icon("wind_charge")
				.action(WindSpells::galePalm)
				.register();

		// --- Ранг 2: Средний ---

		// Незримые Серпы: три почти прозрачных серпа насквозь, кровотечение; срезают листву и траву.
		Spell.of("zephyr_sickles", WIND, 2, BOLT).mana(16).charge(12).cooldown(24).range(40).icon("iron_hoe").destroys()
				.action(WindSpells::sickles)
				.register();

		// --- Ранг 3: Продвинутый ---

		// Тропа Ветров: управляемый полёт на 30 с; копии мультикаста дают крылья союзникам рядом.
		Spell.of("fly", WIND, 3, SELF).mana(32).charge(20).cooldown(300).range(6).icon("elytra").multicast(4)
				.action(WindSpells::windwalk)
				.register();

		// Вихревая Сфера: рывок с шаром вихря в ладони, перемалывает цель и отшвыривает её.
		Spell.of("spiral_sphere", WIND, 3, BOLT).mana(34).charge(22).cooldown(120).range(SPHERE_DASH).layers(2)
				.icon("heart_of_the_sea").size(0.6F).trail(Trail.WIND).destroys()
				.action(WindSpells::spiralSphere)
				.hit(WindSpells::sphereHit)
				.register();

		// --- Ранг 4: Святой ---

		// Воющий Смерч: смерч на 8 с бредёт за прицелом, затягивает, кружит и швыряет врагов.
		Spell.of("howling_cyclone", WIND, 4, RITUAL).mana(58).charge(32).cooldown(400).range(96).anchor(Anchor.TARGET).layers(2)
				.icon("breeze_rod").destroys()
				.action(WindSpells::cyclone)
				.register();

		// --- Ранг 5: Королевский ---

		// Вихревой Сюрикэн: сюрикэн из ветра раскрывается сферой микро-лезвий радиусом 6.
		Spell.of("spiral_shuriken", WIND, 5, BOLT).mana(110).charge(52).cooldown(800).range(64).layers(3)
				.icon("heavy_core").size(0.7F).trail(Trail.WIND).destroys()
				.action(WindSpells::shuriken)
				.hit(WindSpells::shurikenHit)
				.register();

		// --- Ранг 6: Императорский ---

		// Небесные Жернова: из облачного круга спускается торнадо на 10 с и в конце перемалывает всех.
		Spell.of("sky_maelstrom", WIND, 6, RITUAL).mana(160).charge(70).cooldown(2000).range(128).anchor(Anchor.SKY).layers(3)
				.sky(MAELSTROM_SKY).icon("phantom_membrane").destroys()
				.action(WindSpells::maelstrom)
				.register();

		// --- Ранг 7: Божественный ---

		// Дыхание Штормового Дракона: трёхсекундный луч бури на 128 блоков, тоннель сквозь всё на пути.
		Spell.of("storm_dragon_breath", WIND, 7, RITUAL).mana(270).charge(110).cooldown(6000).range(128).anchor(Anchor.LINE)
				.layers(6).icon("dragon_breath").destroys().noMulticast()
				.action(WindSpells::dragonBreath)
				.register();

		// Сильный удар сбивает с «Тропы Ветров»; после перезахода полёт не остаётся навсегда.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer p && taken > FLY_BREAK_DAMAGE && FLIGHT.containsKey(p.getUUID())) disrupt(p);
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer p = handler.player;
			if (p.getTags().contains(FLY_TAG)) land(p);
		});
	}

	// ==================================================================== Ранг 1

	/**
	 * Восходящий Зефир: +1,1 вверх (≈8 блоков) и 0,4 вперёд, с Shift — рывок 1,4 по взгляду; медленное падение и
	 * прыгучесть на 60 с; врагов у ног отталкивает. Каждая копия мультикаста — ещё порыв (+0,35 вверх или +0,4
	 * вперёд) и второе кольцо через 2 тика.
	 */
	private static boolean zephyr(Cast c) {
		ServerPlayer p = c.caster;
		Vec3 feet = p.position();
		Vec3 flat = flat(c.look());
		boolean dash = p.isShiftKeyDown();
		if (c.copy > 0) {
			Vec3 add = dash ? flat.scale(0.4) : new Vec3(0, 0.35, 0);
			p.push(add.x, add.y, add.z);
			p.hurtMarked = true;
			p.resetFallDistance();
			MagicFx.sound(c.level, feet, MagicSounds.GUST, 0.6F, 1.3F + 0.1F * c.copy);
			Zones.later(c, 2, () -> gustRing(c, p.position(), 2.0));
			return true;
		}
		SpellKit.release(c);
		Vec3 v = p.getDeltaMovement();
		if (dash) {
			p.setDeltaMovement(flat.x * ZEPHYR_DASH, Math.max(0, v.y) + 0.25, flat.z * ZEPHYR_DASH);
		} else {
			p.setDeltaMovement(v.x + flat.x * 0.4, Math.max(0, v.y) + ZEPHYR_UP, v.z + flat.z * 0.4);
		}
		p.hurtMarked = true;
		p.resetFallDistance();
		int dur = c.duration(ZEPHYR_TICKS);
		p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, dur, 0), p);
		p.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, dur, 0), p);
		for (LivingEntity e : c.foesAround(feet.add(0, 0.5, 0), 2.5)) Cast.knock(e, feet, 0.6, 0.2);
		// Спиральный столб облаков на 3 блока, кольцо пыли и бледные «крылья» за спиной.
		gustRing(c, feet, 2.5);
		for (int i = 0; i < 18; i++) {
			double a = i * 0.7;
			Vec3 at = feet.add(Math.cos(a) * 0.7, i * 3.0 / 18, Math.sin(a) * 0.7);
			MagicFx.one(c.level, at, ParticleTypes.CLOUD, -Math.sin(a) * 0.08, 0.08, Math.cos(a) * 0.08);
		}
		Vec3 right = new Vec3(-flat.z, 0, flat.x);
		Vec3 back = feet.add(0, 1.3, 0).subtract(flat.scale(0.35));
		for (int k = 1; k <= 5; k++) {
			for (int side = -1; side <= 1; side += 2) {
				Vec3 at = back.add(right.scale(side * 0.3 * k)).add(0, 0.12 * k, 0);
				MagicFx.one(c.level, at, MagicParticles.spark(k % 2 == 0 ? PALE : MIST, 0.18F, 12), -flat.x * 0.05, -0.04, -flat.z * 0.05);
			}
		}
		MagicFx.sound(c.level, feet, MagicSounds.GUST, 0.9F, 1.1F);
		MagicFx.sound(c.level, feet, SoundEvents.ELYTRA_FLYING, 0.25F, 1.8F);
		return true;
	}

	/** Кольцо порыва у ног: волна, облака наружу, клочья ветра. */
	private static void gustRing(Cast c, Vec3 at, double r) {
		MagicFx.shockwave(c.level, at.add(0, 0.15, 0), UP, 0.3F, (float) r * 1.6F, MIST, 10);
		MagicFx.ring(c.level, at.add(0, 0.2, 0), r * 0.5, ParticleTypes.CLOUD, 16, 0.25);
		MagicFx.burst(c.level, at.add(0, 0.4, 0), ParticleTypes.SMALL_GUST, 3, r * 0.3, 0.02);
	}

	/**
	 * Ладонь Ветра: конус 8 блоков (полураствор 25°) от круга этой копии — 5 урона ветром, отброс 1,4 по
	 * направлению удара и подброс 0,35 (×√силы), гасит огонь; чужие снаряды в конусе летят обратно (×1,2), предметы
	 * отлетают.
	 */
	private static boolean galePalm(Cast c) {
		SpellKit.release(c);
		Vec3 from = c.hand();
		Vec3 dir = c.direction();
		Vec3 push = flat(dir);
		double k = Math.sqrt(Math.max(0.1F, c.power));
		for (LivingEntity e : c.foesAround(from, PALM_RANGE + 1)) {
			if (!Cast.inCone(e, from, dir, PALM_RANGE, PALM_ANGLE)) continue;
			c.hurt(e, c.amount(5.0F), MagicDamage.WIND);
			e.clearFire();
			double res = 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
			e.push(push.x * 1.4 * k * res, 0.35 * k, push.z * 1.4 * k * res);
			e.hurtMarked = true;
			MagicFx.burst(c.level, Cast.center(e), ParticleTypes.SWEEP_ATTACK, 1, 0.1, 0);
		}
		AABB box = new AABB(from, from.add(dir.scale(PALM_RANGE))).inflate(4);
		for (Projectile pr : c.level.getEntitiesOfClass(Projectile.class, box, pr -> pr.getOwner() != c.caster)) {
			if (pr.getOwner() != null && c.isAlly(pr.getOwner())) continue;
			if (!Cast.inCone(pr, from, dir, PALM_RANGE, PALM_ANGLE) || pr.getDeltaMovement().lengthSqr() < 0.01) continue;
			// Отражённый снаряд теперь «принадлежит» магу и бьёт по тому, кто стрелял.
			pr.setDeltaMovement(pr.getDeltaMovement().scale(-1.2));
			pr.setOwner(c.caster);
			pr.hurtMarked = true;
			MagicFx.burst(c.level, pr.position(), ParticleTypes.CLOUD, 4, 0.1, 0.05);
		}
		for (ItemEntity item : c.level.getEntitiesOfClass(ItemEntity.class, box, i -> Cast.inCone(i, from, dir, PALM_RANGE, PALM_ANGLE))) {
			item.push(push.x * 0.8, 0.2, push.z * 0.8);
			item.hurtMarked = true;
		}
		// Круг схлопывается в белое кольцо сжатого воздуха, оно уходит на 6 блоков вперёд.
		SpellKit.repeat(c, 3, 1, i -> MagicFx.shockwave(c.level, from.add(dir.scale(1.0 + 2.5 * i)), dir, 0.4F + 0.5F * i,
				2.0F + 0.5F * i, MIST, 8));
		RandomSource rnd = c.level.random;
		for (int i = 0; i < 14; i++) {
			double d = 1 + rnd.nextDouble() * (PALM_RANGE - 1);
			Vec3 side = CircleLayout.right(dir).scale((rnd.nextDouble() - 0.5) * d * 0.8)
					.add(CircleLayout.up(dir).scale((rnd.nextDouble() - 0.5) * d * 0.8));
			Vec3 at = from.add(dir.scale(d)).add(side);
			MagicFx.one(c.level, at, ParticleTypes.CLOUD, dir.x * 0.4, dir.y * 0.4, dir.z * 0.4);
		}
		MagicFx.burst(c.level, from.add(dir.scale(2.5)), ParticleTypes.SWEEP_ATTACK, 3, 0.8, 0);
		MagicFx.sound(c.level, from, SoundEvents.WIND_CHARGE_BURST.value(), 0.8F, 0.8F);
		MagicFx.sound(c.level, from, MagicSounds.GUST, 0.8F, 0.9F);
		return true;
	}

	// ==================================================================== Ранг 2

	/**
	 * Незримые Серпы: три серпа веером ±12° летят по 2,6 блока за тик до 40 блоков (или до твёрдой стены), режут
	 * насквозь: каждому задетому 5 урона ветром и кровотечение 2 с (1 урон в секунду). С разрушением срезают
	 * листву, траву, цветы, лозы, паутину и снег — до 24 блоков на серп, 20% выпадают.
	 */
	private static boolean sickles(Cast c) {
		SpellKit.release(c);
		Vec3 from = c.hand();
		Vec3 dir = c.direction();
		for (int k = -1; k <= 1; k++) blade(c, from, rotY(dir, k * SICKLE_FAN));
		SpellKit.repeat(c, 3, 2, i -> MagicFx.sound(c.level, from, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, 1.5F + 0.15F * i));
		MagicFx.sound(c.level, from, MagicSounds.GUST, 0.6F, 1.6F);
		return true;
	}

	/** Один серп: летит, пока не упрётся в твёрдое (мягкое прорезает), режет врагов и лёгкие блоки. */
	private static void blade(Cast c, Vec3 from, Vec3 dir) {
		double reach = c.spell.range;
		double len = reach;
		for (double t = 0.5; t <= reach; t += 0.5) {
			BlockPos bp = BlockPos.containing(from.add(dir.scale(t)));
			BlockState s = c.level.getBlockState(bp);
			if (!s.getCollisionShape(c.level, bp).isEmpty() && !airy(s)) {
				len = t;
				break;
			}
		}
		double max = len;
		int life = Math.max(1, Mth.ceil(max / SICKLE_SPEED));
		MagicFx.slash(c.level, from, dir, (float) SICKLE_HALF * 2, (float) max, MIST, PALE, life + 2);
		Set<LivingEntity> hit = new HashSet<>();
		int[] cut = {0};
		boolean grief = Terrain.griefing(c.level);
		Zones.add(c, from, SICKLE_HALF, life, (z, age) -> {
			Vec3 a = from.add(dir.scale(Math.min(max, SICKLE_SPEED * (age - 1))));
			Vec3 b = from.add(dir.scale(Math.min(max, SICKLE_SPEED * age)));
			z.center = b;
			for (LivingEntity e : c.along(a, b, SICKLE_HALF, c::isFoe)) {
				if (!hit.add(e)) continue;
				c.hurt(e, c.amount(5.0F), MagicDamage.WIND);
				bleed(c, e, 40);
				MagicFx.burst(c.level, Cast.center(e), ParticleTypes.SWEEP_ATTACK, 1, 0.1, 0);
				MagicFx.burst(c.level, Cast.center(e), new DustParticleOptions(0x8A0018, 1.0F), 6, 0.2, 0.05);
				MagicFx.sound(c.level, b, MagicSounds.impactOf(c.spell), 0.7F, 1.4F);
			}
			if (grief && cut[0] < SICKLE_CUT) cut[0] += cutAlong(c, a, b, SICKLE_HALF, SICKLE_CUT - cut[0], WindSpells::airy, 0.2F);
			MagicFx.one(c.level, b, MagicParticles.spark(MIST, 0.2F, 6), 0, 0, 0);
			MagicFx.one(c.level, b, ParticleTypes.WHITE_SMOKE, dir.x * 0.1, 0, dir.z * 0.1);
			if (age == life && max < reach - 0.5) {
				MagicFx.burst(c.level, b, ParticleTypes.CLOUD, 6, 0.2, 0.05);
				MagicFx.burst(c.level, b, ParticleTypes.CRIT, 6, 0.2, 0.1);
			}
		});
	}

	// ==================================================================== Ранг 3

	/** Чей полёт идёт и на сколько его сбил последний сильный удар. */
	private static final class Flight {
		int disrupted;
	}

	/**
	 * Тропа Ветров: полёт на 30 с (скорость 0,06), пока в воздухе — 1 мана в секунду; кончилась мана или время —
	 * 5 с медленного падения. Удар сильнее 6 сбивает полёт на 2 с. Копия мультикаста дарит такой же полёт (без
	 * траты маны) ближайшему союзному игроку в 6 блоках.
	 */
	private static boolean windwalk(Cast c) {
		if (c.copy > 0) {
			ServerPlayer mate = null;
			for (Player pl : c.level.players()) {
				if (pl == c.caster || !(pl instanceof ServerPlayer sp) || !sp.isAlive() || FLIGHT.containsKey(sp.getUUID())) continue;
				if (sp.distanceToSqr(c.caster) > 36 || !c.isAlly(sp)) continue;
				if (mate == null || sp.distanceToSqr(c.caster) < mate.distanceToSqr(c.caster)) mate = sp;
			}
			if (mate != null) grantFlight(c, mate, false);
			return true;
		}
		SpellKit.release(c);
		if (c.caster.getAbilities().instabuild || c.caster.isSpectator()) return true;
		grantFlight(c, c.caster, true);
		return true;
	}

	private static void grantFlight(Cast c, ServerPlayer who, boolean drain) {
		if (who.getAbilities().instabuild || who.isSpectator()) return;
		Flight f = new Flight();
		FLIGHT.put(who.getUUID(), f);
		who.addTag(FLY_TAG);
		setFlight(who, true);
		who.getAbilities().flying = true;
		who.onUpdateAbilities();
		who.push(0, 0.35, 0);
		who.hurtMarked = true;
		who.resetFallDistance();
		Vec3 feet = who.position();
		MagicFx.ring(c.level, feet.add(0, 0.2, 0), 1.2, ParticleTypes.CLOUD, 20, 0.15);
		for (int i = 0; i < 24; i++) {
			double a = i * 0.55;
			MagicFx.one(c.level, feet.add(Math.cos(a) * 0.9, i * 0.1, Math.sin(a) * 0.9), MagicParticles.spark(i % 3 == 0 ? MIST : PALE,
					0.16F, 16), -Math.sin(a) * 0.06, 0.1, Math.cos(a) * 0.06);
		}
		MagicFx.sound(c.level, feet, MagicSounds.GUST, 1.0F, 0.8F);
		int life = c.duration(FLY_TICKS);
		Zones.add(c, feet, 0, life + 1, (z, age) -> {
			if (FLIGHT.get(who.getUUID()) != f) {
				z.stop();
				return;
			}
			if (who.isRemoved() || !who.isAlive() || who.getAbilities().instabuild || who.isSpectator()) {
				FLIGHT.remove(who.getUUID());
				z.stop();
				return;
			}
			z.center = who.position();
			if (f.disrupted > 0 && --f.disrupted == 0) setFlight(who, true);
			boolean air = !who.onGround();
			if (drain && air && age % 20 == 0) {
				float mana = Magic.mana(who);
				if (mana < 1.0F) {
					endFlight(c, who);
					z.stop();
					return;
				}
				who.setAttached(Magic.MANA, mana - 1.0F);
			}
			if (age >= life) {
				endFlight(c, who);
				z.stop();
				return;
			}
			// Под ногами крутятся маленькие вихри, за спиной — светлые полосы.
			if (air && age % 3 == 0) {
				Vec3 at = who.position();
				double a = age * 0.5;
				MagicFx.one(c.level, at.add(Math.cos(a) * 0.4, -0.1, Math.sin(a) * 0.4), ParticleTypes.CLOUD, -Math.sin(a) * 0.05, -0.04,
						Math.cos(a) * 0.05);
				MagicFx.one(c.level, at.add(0, 0.9, 0), MagicParticles.spark(PALE, 0.14F, 10), 0, 0, 0);
			}
			if (air && age % 40 == 0) MagicFx.sound(c.level, who.position(), SoundEvents.ELYTRA_FLYING, 0.15F, 1.6F);
		});
	}

	/** Полёт кончился: снять его и дать 5 с медленного падения; перья и облачко. */
	private static void endFlight(Cast c, ServerPlayer who) {
		land(who);
		MagicFx.burst(c.level, who.position().add(0, 1, 0), MagicParticles.mote(MIST, 0.15F, 40), 10, 0.5, 0.02);
		MagicFx.burst(c.level, who.position(), ParticleTypes.CLOUD, 8, 0.4, 0.02);
		MagicFx.sound(c.level, who.position(), MagicSounds.GUST, 0.5F, 1.6F);
	}

	private static void setFlight(ServerPlayer p, boolean on) {
		Abilities a = p.getAbilities();
		a.mayfly = on;
		if (!on) a.flying = false;
		a.setFlyingSpeed(on ? FLY_SPEED : 0.05F);
		p.onUpdateAbilities();
	}

	/** Снять полёт «Тропы Ветров» (конец, перезаход, самопроверка). */
	public static void land(ServerPlayer p) {
		FLIGHT.remove(p.getUUID());
		p.removeTag(FLY_TAG);
		if (p.getAbilities().instabuild || p.isSpectator()) return;
		setFlight(p, false);
		p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0), p);
	}

	/** Сильный удар сбивает с полёта на 2 с (вместо него — медленное падение). */
	public static void disrupt(ServerPlayer p) {
		Flight f = FLIGHT.get(p.getUUID());
		if (f == null) return;
		f.disrupted = FLY_DISRUPT;
		setFlight(p, false);
		p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, FLY_DISRUPT, 0), p);
	}

	/** Для самопроверки: летит ли игрок на «Тропе Ветров». */
	public static boolean flying(ServerPlayer p) {
		return FLIGHT.containsKey(p.getUUID()) && p.getAbilities().mayfly;
	}

	/**
	 * Вихревая Сфера: рывок до 6 блоков по взгляду к первому врагу (или до стены). Цель держится в точке касания
	 * 0,5 с — 5 ударов по 3 ветром, потом 6 и отброс 2,2 по взгляду (≈21 урона, ≈12 блоков полёта); остальных в 4
	 * блоках отталкивает волной, с разрушением — воронка радиуса 1,5. Никого — шар взрывается в конце рывка.
	 * Копии мультикаста — «теневые клоны»: самонаводящийся шар (1,2 б/т) перемалывает другую цель на ×0,7.
	 */
	private static boolean spiralSphere(Cast c) {
		SpellKit.release(c);
		if (c.copy > 0) {
			SpellProjectile.shoot(c.withPower(c.power * 0.7F), 1.2F).homing(0.1).life(30);
			MagicFx.sphere(c.level, c.hand(), 0.3F, 0.6F, AZURE, MIST, 8);
			return true;
		}
		ServerPlayer p = c.caster;
		Vec3 look = c.look();
		Vec3 eye = c.eye();
		BlockHitResult wall = c.level.clip(new ClipContext(eye, eye.add(look.scale(SPHERE_DASH)), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, p));
		double reach = wall.getType() == HitResult.Type.MISS ? SPHERE_DASH : wall.getLocation().distanceTo(eye);
		List<LivingEntity> line = c.along(eye, eye.add(look.scale(reach + 0.5)), 0.9, c::isFoe);
		LivingEntity target = line.isEmpty() ? null : line.get(0);
		Vec3 contact = target != null ? Cast.center(target) : eye.add(look.scale(Math.max(0, reach - 0.3)));
		// Рывок: маг вихрем переносится к цели и останавливается в шаге от неё.
		Vec3 flat = flat(look);
		Vec3 feet = p.position();
		double gap = target != null ? 0.9 + target.getBbWidth() * 0.5 : 0.8;
		double dash = Math.max(0, Math.hypot(contact.x - feet.x, contact.z - feet.z) - gap);
		Vec3 dest = null;
		for (double back = 0; back <= dash && dest == null; back += 0.5) dest = c.safeSpot(feet.add(flat.scale(dash - back)));
		if (dest != null && dest.distanceToSqr(feet) > 0.25) {
			MagicFx.line(c.level, feet.add(0, 1, 0), dest.add(0, 1, 0), ParticleTypes.CLOUD, 0.4);
			MagicFx.line(c.level, feet.add(0, 1.2, 0), dest.add(0, 1.2, 0), MagicParticles.spark(AZURE, 0.2F, 10), 0.5);
			p.teleportTo(dest.x, dest.y, dest.z);
			p.resetFallDistance();
		}
		MagicFx.sound(c.level, feet, MagicSounds.GUST, 0.9F, 1.4F);
		if (target != null) {
			grind(c, target, look);
		} else {
			MagicFx.helix(c.level, contact.subtract(look.scale(1.0)), contact.add(look.scale(0.8)), 0.8F, PALE, MIST, 8);
			SpellKit.blast(c, contact, 2.5, c.amount(6.0F), MagicDamage.WIND, 1.5, Terrain.Rim.NONE);
		}
		return true;
	}

	/** Шар клона долетел: перемолоть цель (×0,7 уже в силе снаряда) или взорваться о блок. */
	private static void sphereHit(Cast c, SpellProjectile proj, HitResult r) {
		Vec3 v = proj.getDeltaMovement();
		Vec3 dir = v.lengthSqr() > 1.0E-4 ? v.normalize() : c.look();
		if (r instanceof EntityHitResult e && e.getEntity() instanceof LivingEntity t && c.isFoe(t)) {
			grind(c, t, dir);
		} else if (r.getType() == HitResult.Type.BLOCK) {
			SpellKit.blast(c, r.getLocation(), 2.0, c.amount(6.0F), MagicDamage.WIND, 1.0, Terrain.Rim.NONE);
		}
	}

	/** Перемалывание: цель держится 10 тиков — 5 ударов по 3, затем 6 и отброс 2,2 вдоль {@code dir} и 0,5 вверх. */
	private static void grind(Cast c, LivingEntity t, Vec3 dir) {
		Vec3 hold = t.position();
		Vec3 at = Cast.center(t);
		boolean boss = isBoss(t);
		MagicFx.sphere(c.level, at, 0.6F, 0.9F, AZURE, MIST, GRIND_TICKS);
		MagicFx.helix(c.level, at.subtract(dir.scale(1.3)), at.add(dir.scale(1.3)), 1.1F, PALE, MIST, GRIND_TICKS + 2);
		MagicFx.shockwave(c.level, at, dir, 0.5F, 4.0F, MIST, 10);
		MagicFx.soundFar(c.level, at, MagicSounds.LOOP_WIND, 24, 1.8F);
		for (LivingEntity e : c.foesAround(at, 4.0)) if (e != t) Cast.knock(e, at, 0.6, 0.2);
		Terrain.crater(c, hold, 1.5, 0.75, Terrain.Rim.NONE);
		Zones.add(c, at, 1, GRIND_TICKS + 1, (z, age) -> {
			if (!t.isAlive()) {
				z.stop();
				return;
			}
			if (!boss) {
				t.setDeltaMovement(Vec3.ZERO);
				if (t.position().distanceToSqr(hold) > 0.04) t.teleportTo(hold.x, hold.y, hold.z);
			}
			if (age % 2 == 0 && age <= GRIND_TICKS) {
				c.hurt(t, c.amount(3.0F), MagicDamage.WIND);
				MagicFx.burst(c.level, Cast.center(t), ParticleTypes.SWEEP_ATTACK, 1, 0.2, 0);
				MagicFx.burst(c.level, Cast.center(t), ParticleTypes.CLOUD, 4, 0.3, 0.12);
			}
			if (age == GRIND_TICKS) {
				c.hurt(t, c.amount(6.0F), MagicDamage.WIND);
				double res = 1.0 - t.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
				t.setDeltaMovement(dir.x * 2.2 * res, 0.5 + Math.max(0, dir.y) * 2.2 * res, dir.z * 2.2 * res);
				t.hurtMarked = true;
				Vec3 now = Cast.center(t);
				MagicFx.shockwave(c.level, now, dir, 0.3F, 5.0F, PALE, 12);
				MagicFx.shockwave(c.level, now, UP, 0.3F, 4.0F, MIST, 10);
				MagicFx.burst(c.level, now, ParticleTypes.GUST, 2, 0.3, 0);
				MagicFx.soundFar(c.level, now, MagicSounds.EXPLOSION, 40, 1.3F);
				Magic.shake(c.level, now, 1.0F, 10, 24);
			}
		});
	}

	// ==================================================================== Ранг 4

	/**
	 * Воющий Смерч: в точке прицела (до 96 блоков) встаёт смерч (радиус 3 у земли, 6 наверху, высота 16) на 8 с и
	 * бредёт за прицелом мага по 0,15 блока за тик. Всех в 8 блоках затягивает (0,12 б/т), в воронке поднимает
	 * (до 12 блоков), кружит и раз в 0,5 с бьёт на 3 ветром; предметы и опыт засасывает, чужие снаряды отклоняет.
	 * В конце поднятых швыряет наружу (1,5) с уроном 6. С разрушением раз в 0,5 с срывает листву, траву, стекло,
	 * шерсть и сено в 4 блоках (20% выпадают). Мультикаст — смерчи кружат по кольцу радиуса 4 вокруг прицела.
	 */
	private static boolean cyclone(Cast c) {
		SpellKit.release(c);
		Vec3 at = c.aimGround();
		Vortex v = new Vortex(c, at);
		v.baseR = 3.0;
		v.topR = 6.0;
		v.height = 16.0;
		v.pullR = 8.0;
		v.pull = 0.12;
		v.lift = 0.3;
		v.maxLift = 12.0;
		v.damage = 3.0F;
		v.every = 10;
		v.orbit = c.copies > 1 ? 4.0 : 0.0;
		v.rips = WindSpells::loose;
		v.ripEvery = 10;
		v.ripMax = 12;
		v.ripR = 4.0;
		v.drops = 0.2F;
		v.place(0);
		MagicFx.circle(c.level, c.spell, v.base.add(0, 12, 0), DOWN, 5.0F, 2, 1.0F, 10, 40, 20);
		MagicFx.shockwave(c.level, v.base.add(0, 0.3, 0), UP, 1.0F, 9.0F, MIST, 14);
		MagicFx.cloud(c.level, v.base.add(0, 0.5, 0), ParticleTypes.POOF, 30, 3, 0.3, 3, 0.1);
		MagicFx.soundFar(c.level, v.base, MagicSounds.RELEASE_HEAVY, 64, 1.1F);
		MagicFx.soundFar(c.level, v.base, MagicSounds.GUST, 64, 0.7F);
		Magic.shake(c.level, v.base, 0.8F, 14, 32);
		Zones.add(c, v.base, v.pullR, CYCLONE_LIFE + 1, (z, age) -> {
			v.place(age);
			z.center = v.base;
			v.tick(age);
			if (age >= CYCLONE_LIFE) {
				// Финал: всех поднятых швыряет наружу.
				for (LivingEntity e : v.lifted) {
					if (!e.isAlive()) continue;
					Vec3 out = outward(e, v.base, c.level.random);
					double res = 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
					e.setDeltaMovement(out.x * 1.5 * res, 0.5, out.z * 1.5 * res);
					e.hurtMarked = true;
					c.hurt(e, c.amount(6.0F), MagicDamage.WIND);
				}
				MagicFx.shockwave(c.level, v.base.add(0, 4, 0), UP, 2.0F, 14.0F, MIST, 16);
				MagicFx.cloud(c.level, v.base.add(0, 6, 0), ParticleTypes.CLOUD, 50, 4, 5, 4, 0.3);
				MagicFx.soundFar(c.level, v.base, MagicSounds.GUST, 80, 0.6F);
				MagicFx.soundFar(c.level, v.base, MagicSounds.SHOCKWAVE, 64, 1.2F);
				z.stop();
			}
		});
		return true;
	}

	// ==================================================================== Ранг 5

	/**
	 * Вихревой Сюрикэн: снаряд (1,6 б/т, до 64 блоков) — сфера в кольце из четырёх лезвий. При попадании
	 * раскрывается сферой лезвий радиусом 6 на 1,5 с: раз в 3 тика всем внутри 1,6 урона ветром (10 ударов),
	 * кровотечение 5 с, их держит на месте; в конце — 12 урона и отброс (≈28 и ещё 5 кровью). С разрушением — шаровой
	 * кратер радиусом 4. Копии мультикаста летят в других врагов, сферы складываются.
	 */
	private static boolean shuriken(Cast c) {
		SpellKit.release(c);
		Vec3 dir = c.direction();
		Vec3 hand = c.hand().add(dir.scale(0.6));
		// Сюрикэн собирается у посоха: сфера, вокруг неё раскрывается кольцо лезвий.
		MagicFx.sphere(c.level, hand, 0.2F, 0.7F, MIST, AZURE, 8);
		MagicFx.shockwave(c.level, hand, dir, 0.6F, 2.5F, PALE, 8);
		MagicFx.soundFar(c.level, hand, MagicSounds.RELEASE_HEAVY, 48, 1.4F);
		SpellProjectile p = SpellProjectile.shoot(c, 1.6F).life(40);
		Zones.add(c, p.position(), 0, 41, (z, age) -> {
			if (p.isRemoved()) {
				z.stop();
				return;
			}
			Vec3 at = p.position().add(0, 0.15, 0);
			if (age % 2 == 0) MagicFx.shockwave(c.level, at, UP, 2.3F, 2.6F, MIST, 3);
			for (int k = 0; k < 4; k++) {
				double a = age * 0.7 + k * Math.PI / 2;
				MagicFx.one(c.level, at.add(Math.cos(a) * 2.4, 0, Math.sin(a) * 2.4), MagicParticles.spark(k % 2 == 0 ? MIST : PALE, 0.3F, 4),
						0, 0, 0);
			}
			if (age % 6 == 1) MagicFx.sound(c.level, at, MagicSounds.LOOP_WIND, 0.9F, 1.9F);
		});
		return true;
	}

	private static void shurikenHit(Cast c, SpellProjectile p, HitResult r) {
		Vec3 at = r instanceof EntityHitResult e ? Cast.center(e.getEntity()) : r.getLocation();
		bladeSphere(c, at);
	}

	/** Сфера микро-лезвий: держит и режет всех внутри 1,5 с, потом взрывается. */
	private static void bladeSphere(Cast c, Vec3 at) {
		double r = SHURIKEN_R;
		MagicFx.sphere(c.level, at, 0.5F, (float) r, MIST, AZURE, 8);
		MagicFx.barrier(c.level, at, (float) r, MIST, PALE, SHURIKEN_HOLD + 4);
		MagicFx.tornado(c.level, at.subtract(0, 2.5, 0), 1.6F, 5.0F, PALE, MIST, SHURIKEN_HOLD);
		MagicFx.flash(c.level, at, 32, 0.35F, MIST, 8);
		MagicFx.soundFar(c.level, at, MagicSounds.impactOf(c.spell), 64, 0.8F);
		Magic.shake(c.level, at, 1.2F, SHURIKEN_HOLD, 40);
		RandomSource rnd = c.level.random;
		Zones.add(c, at, r, SHURIKEN_HOLD + 1, (z, age) -> {
			if ((age - 1) % 3 == 0 && age <= SHURIKEN_HOLD - 2) {
				for (LivingEntity e : c.foesAround(at, r)) {
					c.hurt(e, c.amount(1.6F), MagicDamage.WIND);
					bleed(c, e, 100);
					e.setDeltaMovement(e.getDeltaMovement().scale(0.2));
					e.hurtMarked = true;
				}
				// Тонкие белые штрихи режут сферу во всех направлениях.
				for (int i = 0; i < 3; i++) {
					Vec3 d = new Vec3(rnd.nextGaussian(), rnd.nextGaussian() * 0.6, rnd.nextGaussian());
					if (d.lengthSqr() < 1.0E-4) continue;
					d = d.normalize();
					MagicFx.slash(c.level, at.subtract(d.scale(r * 0.7)), d, 1.6F, (float) r * 1.4F, MIST, AZURE, 5);
				}
				MagicFx.cloud(c.level, at, ParticleTypes.SWEEP_ATTACK, 4, r * 0.5, r * 0.4, r * 0.5, 0);
				MagicFx.cloud(c.level, at, ParticleTypes.CLOUD, 10, r * 0.5, r * 0.4, r * 0.5, 0.15);
				MagicFx.sound(c.level, at, MagicSounds.LOOP_WIND, 1.2F, 1.6F + 0.02F * age);
			}
			if (age == SHURIKEN_HOLD) {
				for (LivingEntity e : c.foesAround(at, r)) {
					c.hurt(e, c.amount(12.0F), MagicDamage.WIND);
					Cast.knock(e, at, 1.0, 0.4);
				}
				Terrain.crater(c, at, 4.0, 1.0, Terrain.Rim.NONE);
				MagicFx.sphere(c.level, at, 1.0F, (float) r + 1.5F, MIST, AZURE, 12);
				MagicFx.shockwave(c.level, at, UP, 1.0F, (float) r * 2.2F, PALE, 14);
				MagicFx.burst(c.level, at, ParticleTypes.GUST_EMITTER_LARGE, 1, 0, 0);
				MagicFx.flash(c.level, at, 48, 0.5F, MIST, 10);
				MagicFx.soundFar(c.level, at, MagicSounds.EXPLOSION, 80, 1.1F);
				MagicFx.soundFar(c.level, at, MagicSounds.GUST, 64, 0.7F);
				Magic.shake(c.level, at, 2.2F, 14, 48);
			}
		});
	}

	// ==================================================================== Ранг 6

	/**
	 * Небесные Жернова: облачный круг в 24 блоках над прицелом (до 128). Из него за 0,75 с спускается торнадо
	 * (радиус 4 у земли, 10 наверху, высота 24) и живёт 10 с, бредя за прицелом мага по 0,15 б/т. Всех в 12 блоках
	 * тянет к оси (боссов ×0,25), в воронке поднимает до 20 блоков; раз в 0,5 с — 4 урона ветром (и ещё 3 от
	 * обломков, если они кружат). Финал: всех в 8 блоках швыряет наружу и через 0,25 с вбивает в землю (−2,5) —
	 * 30 урона и падение. С разрушением раз в 5 тиков срывает до 8 лёгких блоков (листва, трава, песок, гравий,
	 * земля, снег, шерсть, стекло) в 5 блоках; в финале обломки разлетаются и падают блоками (половина — предметами).
	 * Мультикаст — меньшие смерчи (×0,7) кружат вокруг главного.
	 */
	private static boolean maelstrom(Cast c) {
		SpellKit.release(c);
		double s = c.copy > 0 ? 0.7 : 1.0;
		Vec3 at = c.aimGround();
		Vortex v = new Vortex(c, at);
		v.baseR = 4.0 * s;
		v.topR = 10.0 * s;
		v.height = 24.0 * s;
		v.pullR = 12.0 * s;
		v.pull = 0.1;
		v.lift = 0.25;
		v.maxLift = 20.0 * s;
		v.damage = (float) (4.0 * s);
		v.every = 10;
		v.debrisHit = (float) (3.0 * s);
		v.orbit = c.copy > 0 ? 12.0 : 0.0;
		v.rips = WindSpells::rubble;
		v.ripEvery = 5;
		v.ripMax = 8;
		v.ripR = 5.0 * s;
		v.keep = 40;
		v.place(0);
		Vec3 sky = v.base.add(0, MAELSTROM_SKY * s, 0);
		// Жернова: верхний круг в тучах и зеркальный нижний на земле, воронка спускается между ними.
		MagicFx.circle(c.level, c.spell, sky, DOWN, (float) (9 * s), 3, 1.6F, 6, MAELSTROM_DESCENT + MAELSTROM_LIFE, 30);
		MagicFx.circle(c.level, c.spell, v.base.add(0, 0.08, 0), UP, (float) (8 * s), 2, 0.6F, MAELSTROM_DESCENT, MAELSTROM_LIFE, 30);
		MagicFx.helix(c.level, sky, v.base, (float) (3 * s), MIST, PALE, MAELSTROM_DESCENT + 4);
		MagicFx.cloud(c.level, sky, ParticleTypes.LARGE_SMOKE, 60, 8 * s, 1.5, 8 * s, 0.02);
		MagicFx.soundFar(c.level, sky, MagicSounds.THUNDER, 128, 0.7F);
		MagicFx.soundFar(c.level, v.base, MagicSounds.LOOP_STORM, 96, 0.8F);
		SpellKit.repeat(c, 5, 3, i -> {
			Vec3 p = sky.add(0, -(MAELSTROM_SKY * s) * (i + 1) / 5.0, 0);
			MagicFx.ring(c.level, p, v.radiusAt(p.y - v.base.y), ParticleTypes.CLOUD, 24, 0.3);
		});
		List<LivingEntity> slam = new ArrayList<>();
		int total = MAELSTROM_DESCENT + MAELSTROM_LIFE;
		Zones.add(c, v.base, v.pullR, total + 6, (z, age) -> {
			Vec3 top = v.base.add(0, v.height, 0);
			if (age % 2 == 0) {
				// Тучи и дождь под верхним жерновом.
				MagicFx.cloud(c.level, top, ParticleTypes.LARGE_SMOKE, 6, 7 * s, 0.8, 7 * s, 0.01);
				MagicFx.cloud(c.level, top.subtract(0, 1, 0), ParticleTypes.FALLING_WATER, 16, 10 * s, 0.5, 10 * s, 0);
			}
			if (age % 7 == 0 && age < total) {
				RandomSource rnd = c.level.random;
				Vec3 a = SpellKit.inDisc(rnd, top, 8 * s);
				MagicFx.lightning(c.level, a, SpellKit.inDisc(rnd, top.subtract(0, 3 + rnd.nextInt(6), 0), 6 * s), ARC);
				if (rnd.nextInt(4) == 0) MagicFx.soundFar(c.level, a, MagicSounds.THUNDER, 96, 1.2F + rnd.nextFloat() * 0.3F);
			}
			if (age <= MAELSTROM_DESCENT) {
				// Воронка спускается из круга.
				Vec3 p = top.subtract(0, v.height * age / MAELSTROM_DESCENT, 0);
				MagicFx.ring(c.level, p, v.radiusAt(p.y - v.base.y), ParticleTypes.CLOUD, 18, -0.1);
				return;
			}
			int t = age - MAELSTROM_DESCENT;
			if (t < MAELSTROM_LIFE) {
				v.place(t);
				z.center = v.base;
				v.tick(t);
			} else if (t == MAELSTROM_LIFE) {
				// Финал: швырнуть наружу…
				for (LivingEntity e : c.foesAround(v.base.add(0, v.height * 0.5, 0), Math.max(8 * s, v.height * 0.5 + 2))) {
					if (Math.hypot(e.getX() - v.base.x, e.getZ() - v.base.z) > 8 * s) continue;
					slam.add(e);
					Vec3 out = outward(e, v.base, c.level.random);
					double res = 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
					e.setDeltaMovement(out.x * res, 0.6 * res, out.z * res);
					e.hurtMarked = true;
				}
				MagicFx.shockwave(c.level, v.base.add(0, v.height * 0.5, 0), UP, 2.0F, (float) (20 * s), MIST, 12);
				MagicFx.soundFar(c.level, v.base, MagicSounds.GUST, 128, 0.5F);
				v.fling();
			} else if (t == MAELSTROM_LIFE + 5) {
				// …и вбить в землю.
				for (LivingEntity e : slam) {
					if (!e.isAlive()) continue;
					e.setDeltaMovement(e.getDeltaMovement().x * 0.3, -2.5, e.getDeltaMovement().z * 0.3);
					e.hurtMarked = true;
					c.hurt(e, c.amount((float) (30.0 * s)), MagicDamage.WIND);
				}
				MagicFx.shockwave(c.level, v.base.add(0, 0.3, 0), UP, 1.0F, (float) (24 * s), PALE, 18);
				MagicFx.shockwave(c.level, v.base.add(0, 0.6, 0), UP, 0.5F, (float) (14 * s), MIST, 14);
				MagicFx.cloud(c.level, v.base.add(0, 0.5, 0), ParticleTypes.POOF, 60, 6 * s, 0.4, 6 * s, 0.25);
				MagicFx.flash(c.level, v.base, 64, 0.45F, MIST, 10);
				MagicFx.soundFar(c.level, v.base, MagicSounds.EXPLOSION_HUGE, 160, 0.8F);
				Magic.shake(c.level, v.base, (float) (3.0 * s), 20, 64);
				z.stop();
			}
		});
		return true;
	}

	/**
	 * Смерч, бредущий за прицелом: тянет, поднимает и кружит врагов, засасывает предметы, отклоняет снаряды, рвёт
	 * лёгкие блоки и кружит их обломками. Общий для «Воющего Смерча» и «Небесных Жерновов».
	 */
	private static final class Vortex {
		final Cast c;
		/** Радиус воронки у земли и наверху, высота. */
		double baseR, topR, height;
		/** Втягивание (радиус, б/т), подъём (б/т) и его предел над основанием. */
		double pullR, pull, lift, maxLift;
		/** Урон всем в воронке раз в {@link #every} тиков; урон обломков (если кружат). */
		float damage, debrisHit;
		int every = 10;
		/** Радиус кольца, по которому кружит копия мультикаста вокруг точки прицела (0 — стоит на ней). */
		double orbit;
		/** Какие блоки срывает, как часто, сколько за раз, в каком радиусе, доля выпадения. */
		Predicate<BlockState> rips;
		int ripEvery = 10, ripMax;
		double ripR;
		float drops;
		/** Сколько обломков держать до финала (0 — не держать: только кружат для вида). */
		int keep;
		final List<BlockState> debris = new ArrayList<>();
		/** Точка, куда тянет прицел мага, середина (без кружения копий) и основание воронки. */
		Vec3 goal, mid, base;
		final Set<LivingEntity> lifted = new HashSet<>();
		final Set<UUID> deflected = new HashSet<>();

		Vortex(Cast c, Vec3 at) {
			this.c = c;
			this.goal = at;
			this.mid = at;
			this.base = at;
		}

		double radiusAt(double h) {
			return baseR + (topR - baseR) * Mth.clamp(h / height, 0, 1);
		}

		/** Сдвинуть к прицелу мага (не быстрее {@link #DRIFT}) и по кольцу копий; основание — на землю. */
		void place(int age) {
			if (age > 0 && age % 5 == 1 && c.caster.isAlive() && !c.caster.isRemoved() && c.caster.level() == c.level) {
				goal = Targeting.aim(c.level, c.caster, c.eye(), c.look(), c.spell.range, Targeting.living(c.caster)).ground();
			}
			Vec3 d = new Vec3(goal.x - mid.x, 0, goal.z - mid.z);
			double len = d.length();
			if (len > 0.05) mid = mid.add(d.scale(Math.min(len, DRIFT) / len));
			Vec3 at = mid;
			if (orbit > 0 && c.copies > 1) {
				double a = age * 0.04 + Math.PI * 2 * c.copy / c.copies;
				at = at.add(Math.cos(a) * orbit, 0, Math.sin(a) * orbit);
			}
			base = Targeting.ground(c.level, c.caster, new Vec3(at.x, Math.max(goal.y, base.y) + 3, at.z));
		}

		void tick(int age) {
			spin(age);
			if (age % 2 == 0) vacuum();
			if (rips != null && age % ripEvery == 0) rip();
			// Вид: воронка (обновляется кусками — смерч бродит), пыль у земли, обломки по спирали, вой.
			if (age % 20 == 1) MagicFx.tornado(c.level, base, (float) baseR, (float) height, PALE, MIST, 30);
			RandomSource rnd = c.level.random;
			for (int i = 0; i < 3; i++) {
				double h = rnd.nextDouble() * height;
				double r = radiusAt(h) * (0.6 + 0.4 * rnd.nextDouble());
				double a = rnd.nextDouble() * Math.PI * 2;
				MagicFx.one(c.level, base.add(Math.cos(a) * r, h, Math.sin(a) * r), i == 0 ? ParticleTypes.WHITE_SMOKE : ParticleTypes.CLOUD,
						-Math.sin(a) * 0.4, 0.15, Math.cos(a) * 0.4);
			}
			if (age % 3 == 0) MagicFx.cloud(c.level, base.add(0, 0.3, 0), ParticleTypes.POOF, 4, baseR, 0.2, baseR, 0.05);
			if (!debris.isEmpty() && age % 2 == 0) {
				BlockState st = debris.get(rnd.nextInt(debris.size()));
				double h = rnd.nextDouble() * height * 0.7;
				double a = rnd.nextDouble() * Math.PI * 2;
				double r = radiusAt(h);
				MagicFx.one(c.level, base.add(Math.cos(a) * r, h, Math.sin(a) * r), new BlockParticleOption(ParticleTypes.BLOCK, st),
						-Math.sin(a) * 0.5, 0.2, Math.cos(a) * 0.5);
			}
			if (age % 20 == 1) MagicFx.soundFar(c.level, base, MagicSounds.LOOP_WIND, 48 + (float) height * 2, 0.6F);
		}

		/** Втянуть, поднять и закружить врагов; раз в {@link #every} тиков — урон тем, кто в воронке. */
		void spin(int age) {
			boolean strike = age % every == 0;
			AABB box = new AABB(base.x - pullR, base.y - 2, base.z - pullR, base.x + pullR, base.y + height, base.z + pullR);
			for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, box, c::isFoe)) {
				double dx = base.x - e.getX(), dz = base.z - e.getZ();
				double dist = Math.sqrt(dx * dx + dz * dz);
				if (dist > pullR) continue;
				double h = e.getY() - base.y;
				boolean boss = isBoss(e);
				double k = (boss ? 0.25 : 1.0) * (1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
				boolean inside = dist <= radiusAt(h) + e.getBbWidth() * 0.5;
				Vec3 v = e.getDeltaMovement();
				if (dist > 0.3) {
					double swirl = inside ? 0.3 : 0.08;
					double vx = v.x + (dx * pull - dz * swirl) / dist * k;
					double vz = v.z + (dz * pull + dx * swirl) / dist * k;
					double sp = Math.hypot(vx, vz);
					if (sp > 0.7) {
						vx *= 0.7 / sp;
						vz *= 0.7 / sp;
					}
					v = new Vec3(vx, v.y, vz);
				}
				if (inside && !boss) {
					lifted.add(e);
					v = new Vec3(v.x * 0.85, h < maxLift ? lift * k : Math.max(0, v.y), v.z * 0.85);
				}
				e.setDeltaMovement(v);
				e.hurtMarked = true;
				if (inside && strike) {
					c.hurt(e, c.amount(damage), MagicDamage.WIND);
					if (debrisHit > 0 && !debris.isEmpty()) c.hurt(e, c.amount(debrisHit), MagicDamage.EARTH);
				}
			}
		}

		/** Предметы и опыт засасывает в воронку, чужие снаряды отклоняет в случайную сторону. */
		void vacuum() {
			AABB box = new AABB(base.x - pullR, base.y - 2, base.z - pullR, base.x + pullR, base.y + height, base.z + pullR);
			RandomSource rnd = c.level.random;
			for (Entity e : c.level.getEntitiesOfClass(Entity.class, box,
					e -> e instanceof ItemEntity || e instanceof ExperienceOrb || e instanceof Projectile)) {
				double dx = base.x - e.getX(), dz = base.z - e.getZ();
				double dist = Math.sqrt(dx * dx + dz * dz);
				if (e instanceof Projectile pr) {
					if (pr.getOwner() == c.caster || dist > radiusAt(e.getY() - base.y) + 1 || !deflected.add(pr.getUUID())) continue;
					double sp = pr.getDeltaMovement().length();
					Vec3 d = new Vec3(rnd.nextGaussian(), rnd.nextGaussian() * 0.5 + 0.3, rnd.nextGaussian());
					if (d.lengthSqr() < 1.0E-4) continue;
					pr.setDeltaMovement(d.normalize().scale(sp));
					pr.hurtMarked = true;
				} else if (dist > 0.5) {
					e.setDeltaMovement(e.getDeltaMovement().add(dx / dist * 0.15, e.getY() - base.y < 4 ? 0.12 : 0.02, dz / dist * 0.15));
					e.hurtMarked = true;
				}
			}
		}

		/** Сорвать лёгкие блоки у основания (сверху вниз, до {@link #ripMax} за раз). */
		void rip() {
			if (!Terrain.griefing(c.level)) return;
			BlockPos o = BlockPos.containing(base);
			int r = Mth.ceil(ripR);
			int n = 0;
			RandomSource rnd = c.level.random;
			for (int dy = 8; dy >= -2 && n < ripMax; dy--) {
				for (int dx = -r; dx <= r && n < ripMax; dx++) {
					for (int dz = -r; dz <= r && n < ripMax; dz++) {
						if (dx * dx + dz * dz > ripR * ripR) continue;
						BlockPos p = o.offset(dx, dy, dz);
						BlockState s = c.level.getBlockState(p);
						if (!rips.test(s) || !Terrain.breakable(c.level, c.caster, p, s, 10)) continue;
						boolean held = keep > 0 && debris.size() < keep;
						if (keep == 0 && debris.size() < 16) debris.add(s);
						if (held) debris.add(s);
						c.level.destroyBlock(p, !held && rnd.nextFloat() < (keep > 0 ? 0.1F : drops));
						n++;
					}
				}
			}
		}

		/** Финал: обломки разлетаются наружу — половина падает блоками, половина предметами. */
		void fling() {
			if (keep == 0) return;
			RandomSource rnd = c.level.random;
			for (int i = 0; i < debris.size(); i++) {
				BlockState st = debris.get(i);
				double h = 2 + rnd.nextDouble() * height * 0.5;
				double a = rnd.nextDouble() * Math.PI * 2;
				double r = radiusAt(h);
				Vec3 at = base.add(Math.cos(a) * r, h, Math.sin(a) * r);
				Vec3 vel = new Vec3(Math.cos(a) * (0.6 + rnd.nextDouble() * 0.6), 0.3 + rnd.nextDouble() * 0.3, Math.sin(a) * (0.6 + rnd.nextDouble() * 0.6));
				BlockPos bp = BlockPos.containing(at);
				Entity e = null;
				if (i % 2 == 0 && c.level.getBlockState(bp).isAir() && !st.hasBlockEntity()) {
					e = FallingBlockEntity.fall(c.level, bp, st);
				} else if (!st.getBlock().asItem().getDefaultInstance().isEmpty()) {
					e = new ItemEntity(c.level, at.x, at.y, at.z, new ItemStack(st.getBlock().asItem()));
					c.level.addFreshEntity(e);
				}
				if (e != null) {
					e.setDeltaMovement(vel);
					e.hurtMarked = true;
				}
			}
			debris.clear();
		}
	}

	// ==================================================================== Ранг 7

	/**
	 * Дыхание Штормового Дракона: за спиной мага из ветра собирается голова дракона, шесть кругов ствола
	 * вспыхивают один за другим (0,7 с), затем 3 с бьёт луч бури по взгляду в момент выпуска: цилиндр радиусом 4 и
	 * длиной 128. Раз в 5 тиков всем внутри 12 урона ветром (до 144; боссам не больше 80, игрокам — 28), их несёт
	 * вдоль луча; внутри пляшут молнии. Маг прикован к месту, отдача 0,5. С разрушением — тоннель радиусом 3 на 96
	 * блоков сквозь всё, кроме бедрока, обсидиана и укреплённого сланца (руды остаются).
	 */
	private static boolean dragonBreath(Cast c) {
		SpellKit.release(c);
		Vec3 dir = c.look();
		Vec3 flat = flat(dir);
		Vec3 hand = c.hand();
		Vec3 start = hand.add(dir.scale(0.8));
		Vec3 head = c.caster.position().add(0, 3.0, 0).subtract(flat.scale(2.4));
		Vec3 right = new Vec3(-flat.z, 0, flat.x);
		// Маг прикован на всё дыхание.
		c.caster.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, BREATH_AT + BREATH_TICKS + 4, 9, false, false), c.caster);
		MagicFx.soundFar(c.level, head, SoundEvents.ENDER_DRAGON_GROWL, 128, 0.55F);
		MagicFx.soundFar(c.level, head, MagicSounds.RISER_SHORT, 96, 0.8F);
		// Голова дракона из ветра: пасть раскрывается, глаза загораются.
		SpellKit.repeat(c, 7, 2, i -> {
			double open = 0.15 + 0.12 * i;
			for (int k = 0; k < 10; k++) {
				double t = k / 9.0;
				Vec3 along = head.add(flat.scale(-0.6 + 2.4 * t));
				for (int jaw = -1; jaw <= 1; jaw += 2) {
					Vec3 p = along.add(0, jaw * open * t * 2.0, 0).add(right.scale(Math.sin(t * Math.PI) * 0.6 * (k % 2 == 0 ? 1 : -1)));
					MagicFx.one(c.level, p, jaw > 0 ? ParticleTypes.CLOUD : ParticleTypes.WHITE_SMOKE, flat.x * 0.05, 0, flat.z * 0.05);
				}
			}
			for (int side = -1; side <= 1; side += 2) {
				MagicFx.one(c.level, head.add(flat.scale(0.2)).add(right.scale(side * 0.5)).add(0, 0.5, 0), MagicParticles.spark(ARC, 0.35F, 6), 0, 0, 0);
			}
			MagicFx.one(c.level, head.add(flat.scale(-1.2)), MagicParticles.spark(PALE, 0.6F, 6), 0, 0, 0);
			Magic.shake(c.level, c.eye(), 0.3F + 0.1F * i, 4, 24);
		});
		// Шесть кругов ствола вспыхивают по одному, к стволу стягиваются спирали ветра.
		Vec3 far = hand.add(dir.scale(BREATH_LENGTH));
		for (int i = 0; i < 6; i++) {
			int n = i;
			Zones.later(c, 2 * i, () -> {
				Vec3 at = CircleLayout.lineStep(hand, far, n, 6);
				MagicFx.circle(c.level, School.WIND, 7, n % 2 == 0 ? PALE : MIST, MIST, at, dir, 1.5F + 0.5F * n, 1, 0, 2,
						BREATH_AT - 2 * n + BREATH_TICKS, 12, 0.6F + 0.25F * n);
				MagicFx.ring(c.level, at, 1.5 + 0.5 * n, ParticleTypes.CLOUD, 16, -0.15);
				MagicFx.sound(c.level, at, MagicSounds.CIRCLE_DRAW, 1.2F, 0.8F + 0.1F * n);
			});
		}
		Zones.later(c, BREATH_AT, () -> breath(c, start, dir));
		return true;
	}

	private static void breath(Cast c, Vec3 start, Vec3 dir) {
		Vec3 end = start.add(dir.scale(BREATH_LENGTH));
		MagicFx.beam(c.level, start, end, (float) BREATH_R, PALE, MIST, BREATH_TICKS);
		MagicFx.helix(c.level, start, end, (float) BREATH_R + 1.2F, MIST, PALE, BREATH_TICKS);
		MagicFx.shockwave(c.level, start, dir, 1.0F, 10.0F, MIST, 16);
		MagicFx.shockwave(c.level, start.add(dir.scale(3)), dir, 2.0F, 14.0F, PALE, 20);
		MagicFx.flash(c.level, start, 96, 0.8F, MIST, 16);
		MagicFx.soundFar(c.level, start, MagicSounds.RELEASE_ULTIMATE, 192, 1.0F);
		MagicFx.soundFar(c.level, start, MagicSounds.BEAM_BLAST, 160, 0.8F);
		MagicFx.soundFar(c.level, start, SoundEvents.ENDER_DRAGON_GROWL, 160, 0.45F);
		Magic.shake(c.level, start, 3.5F, BREATH_TICKS, 64);
		c.caster.push(-dir.x * 0.5, 0.05, -dir.z * 0.5);
		c.caster.hurtMarked = true;
		Map<LivingEntity, float[]> dealt = new HashMap<>();
		boolean grief = c.spell.destroys && Terrain.griefing(c.level);
		RandomSource rnd = c.level.random;
		Zones.add(c, start, BREATH_R, BREATH_TICKS + 1, (z, age) -> {
			if (age % 5 == 0) {
				int pulse = age / 5 - 1;
				Set<LivingEntity> hit = new HashSet<>();
				// Луч длинный — искать кусками по 16 блоков, а не одним огромным ящиком.
				for (double s = 0; s < BREATH_LENGTH; s += 16) {
					hit.addAll(c.along(start.add(dir.scale(s)), start.add(dir.scale(Math.min(BREATH_LENGTH, s + 16))), BREATH_R, c::isFoe));
				}
				for (LivingEntity e : hit) {
					float cap = isBoss(e) ? 80.0F : e instanceof Player ? 28.0F : Float.MAX_VALUE;
					float[] d = dealt.computeIfAbsent(e, k -> new float[1]);
					float dmg = Math.min(c.amount(12.0F), cap - d[0]);
					if (dmg > 0 && c.hurt(e, dmg, MagicDamage.WIND)) d[0] += dmg;
					double res = 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
					e.setDeltaMovement(dir.scale(1.5 * res));
					e.hurtMarked = true;
				}
				// Молнии внутри бури.
				for (int i = 0; i < 2; i++) {
					double s = 4 + rnd.nextDouble() * (BREATH_LENGTH - 8);
					Vec3 a = start.add(dir.scale(s)).add(CircleLayout.right(dir).scale((rnd.nextDouble() - 0.5) * BREATH_R * 1.6))
							.add(CircleLayout.up(dir).scale((rnd.nextDouble() - 0.5) * BREATH_R * 1.6));
					Vec3 b = a.add(dir.scale(4 + rnd.nextDouble() * 6)).add(CircleLayout.up(dir).scale((rnd.nextDouble() - 0.5) * 3));
					MagicFx.lightning(c.level, a, b, ARC);
				}
				Vec3 rumble = start.add(dir.scale(rnd.nextDouble() * BREATH_LENGTH * 0.6));
				MagicFx.soundFar(c.level, rumble, pulse % 3 == 0 ? MagicSounds.THUNDER : MagicSounds.GUST, 96, 0.6F + rnd.nextFloat() * 0.3F);
				// Тоннель продвигается вперёд кусками по 8 блоков.
				if (grief && pulse >= 0 && 8.0 * (pulse + 1) <= BREATH_TUNNEL) {
					Terrain.tunnel(c, start.add(dir.scale(8.0 * pulse)), start.add(dir.scale(8.0 * (pulse + 1))), 3.0, Terrain.Rim.NONE,
							BREATH_HARDNESS);
				}
			}
			// Рваные облака и пыль несутся по лучу.
			for (int i = 0; i < 4; i++) {
				double s = rnd.nextDouble() * BREATH_LENGTH * 0.7;
				Vec3 p = start.add(dir.scale(s)).add(CircleLayout.right(dir).scale((rnd.nextDouble() - 0.5) * BREATH_R * 2))
						.add(CircleLayout.up(dir).scale((rnd.nextDouble() - 0.5) * BREATH_R * 2));
				MagicFx.one(c.level, p, i == 0 ? ParticleTypes.GUST : ParticleTypes.CLOUD, dir.x * 1.5, dir.y * 1.5, dir.z * 1.5);
			}
			if (age == BREATH_TICKS) {
				// Хвост: ветер стихает, в тоннеле остаются полосы.
				MagicFx.beam(c.level, start, end, 1.2F, MIST, PALE, 24);
				MagicFx.shockwave(c.level, start, dir, 2.0F, 6.0F, PALE, 14);
				MagicFx.soundFar(c.level, start, MagicSounds.GUST, 128, 0.5F);
				MagicFx.soundFar(c.level, start.add(dir.scale(40)), MagicSounds.RUMBLE, 128, 0.6F);
			}
		});
	}

	// ==================================================================== Помощники

	/** Лёгкое, что срезает ветер: листва, трава, цветы, ростки, лозы, паутина, снежный покров (посевы не трогает). */
	static boolean airy(BlockState s) {
		if (s.isAir() || !s.getFluidState().isEmpty() || s.is(BlockTags.CROPS)) return false;
		return s.is(BlockTags.LEAVES) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS) || s.is(Blocks.COBWEB) || s.is(Blocks.SNOW)
				|| s.is(Blocks.VINE) || s.canBeReplaced();
	}

	/** Что срывает смерч: лёгкое плюс стекло, шерсть, ковры, сено. */
	static boolean loose(BlockState s) {
		return airy(s) || s.is(BlockTags.IMPERMEABLE) || s.is(BlockTags.WOOL) || s.is(BlockTags.WOOL_CARPETS) || s.is(Blocks.HAY_BLOCK);
	}

	/** Что выдирают Небесные Жернова: ещё песок, гравий, земля и снег. */
	static boolean rubble(BlockState s) {
		return loose(s) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(BlockTags.DIRT) || s.is(BlockTags.SNOW);
	}

	/** Срезать блоки {@code what} вдоль отрезка (радиус {@code r}), не больше {@code max}. Возвращает, сколько срезано. */
	private static int cutAlong(Cast c, Vec3 a, Vec3 b, double r, int max, Predicate<BlockState> what, float drops) {
		Vec3 d = b.subtract(a);
		double len = d.length();
		int ri = Mth.ceil(r);
		int n = 0;
		for (double t = 0; t <= len && n < max; t += 0.8) {
			Vec3 o = len < 1.0E-6 ? a : a.add(d.scale(t / len));
			BlockPos bo = BlockPos.containing(o);
			for (BlockPos p : BlockPos.betweenClosed(bo.offset(-ri, -ri, -ri), bo.offset(ri, ri, ri))) {
				if (n >= max) break;
				if (Vec3.atCenterOf(p).distanceToSqr(o) > r * r) continue;
				BlockState s = c.level.getBlockState(p);
				if (!what.test(s) || !Terrain.breakable(c.level, c.caster, p, s, 10)) continue;
				c.level.destroyBlock(p, c.level.random.nextFloat() < drops);
				n++;
			}
		}
		return n;
	}

	/** Кровотечение от лезвий ветра: 1 урон кровью в секунду и капли (повторное — продлевает). */
	static void bleed(Cast c, LivingEntity e, int ticks) {
		int[] left = BLEEDING.get(e);
		if (left != null) {
			left[0] = Math.max(left[0], ticks);
			return;
		}
		int[] l = {ticks};
		BLEEDING.put(e, l);
		Zones.add(c, Cast.center(e), 0, 20 * 60, (z, age) -> {
			if (!e.isAlive() || --l[0] <= 0) {
				BLEEDING.remove(e);
				z.stop();
				return;
			}
			if (age % 5 == 0) {
				MagicFx.one(c.level, e.position().add(0, e.getBbHeight() * 0.6, 0), new DustParticleOptions(0x8A0018, 1.0F), 0, -0.05, 0);
			}
			if (age % 20 == 0) c.hurt(e, 1.0F, MagicDamage.BLOOD);
		});
	}

	/** Для самопроверки: кровоточит ли существо от лезвий ветра. */
	public static boolean bleeding(Entity e) {
		return e instanceof LivingEntity l && BLEEDING.containsKey(l);
	}

	static boolean isBoss(LivingEntity e) {
		return e instanceof EnderDragon || e instanceof WitherBoss || e instanceof Warden || e instanceof ElderGuardian;
	}

	/** Направление от оси смерча к существу по горизонтали (на самой оси — случайное). */
	private static Vec3 outward(LivingEntity e, Vec3 axis, RandomSource rnd) {
		Vec3 d = new Vec3(e.getX() - axis.x, 0, e.getZ() - axis.z);
		if (d.lengthSqr() < 1.0E-4) {
			double a = rnd.nextDouble() * Math.PI * 2;
			return new Vec3(Math.cos(a), 0, Math.sin(a));
		}
		return d.normalize();
	}

	/** Направление по горизонтали. */
	static Vec3 flat(Vec3 v) {
		Vec3 f = new Vec3(v.x, 0, v.z);
		return f.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : f.normalize();
	}

	/** Повернуть вектор вокруг вертикали на {@code deg} градусов. */
	static Vec3 rotY(Vec3 v, double deg) {
		double a = Math.toRadians(deg);
		double cos = Math.cos(a), sin = Math.sin(a);
		return new Vec3(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos);
	}
}
