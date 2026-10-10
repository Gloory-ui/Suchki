package com.suchki.magic.spells;

import static com.suchki.magic.CastStyle.BOLT;
import static com.suchki.magic.CastStyle.RITUAL;
import static com.suchki.magic.School.LIGHTNING;

import com.suchki.magic.Anchor;
import com.suchki.magic.Cast;
import com.suchki.magic.CircleLayout;
import com.suchki.magic.Magic;
import com.suchki.magic.MagicDamage;
import com.suchki.magic.MagicEffects;
import com.suchki.magic.MagicFx;
import com.suchki.magic.MagicParticles;
import com.suchki.magic.MagicSounds;
import com.suchki.magic.Spell;
import com.suchki.magic.Targeting;
import com.suchki.magic.Terrain;
import com.suchki.magic.Zones;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Стихия молнии: от монеты, разогнанной током, до Тысячи Громов Индры. Молния любит воду: мокрые цели (в воде или под
 * дождём) получают больше урона и дольше стоят в параличе, а цепи по ним прыгают дальше. Большие заклинания собирают
 * грозу на глазах: круги в небе вспыхивают по одному, тучи темнеют, разряды бьют всё чаще — и только потом главный удар.
 */
public final class LightningSpells {
	/** Золото монеты и рельсотрона. */
	static final int GOLD = 0xFFB238;
	/** Жёлтые искры рывка и оков. */
	static final int YELLOW = 0xFFE45C;
	/** Фиолетовый отлив цепных молний, дракона и Цилиня. */
	static final int VIOLET = 0x9C7CFF;
	/** Бело-голубое ядро разрядов. */
	static final int WHITE = 0xF2FCFF;
	/** Голубая метка удара на земле. */
	static final int MARK = 0x4FA8FF;
	/** Затемнение неба под грозовыми тучами. */
	static final int STORM = 0x0A0C1A;

	// Щелчок Монеты: дальность, толщина луча, урон второй цели.
	private static final double COIN_RANGE = 32.0;
	private static final double COIN_WIDTH = 0.8;
	private static final float COIN_SECOND = 0.6F;
	// Статические Оковы: радиус поиска цели у прицела, сколько держатся светящиеся стержни.
	private static final double SHACKLE_NEAR = 3.0;
	private static final int SHACKLE_GLOW = 40;
	// Громовой Росчерк: длина рывка, рывка копии, толщина «капсулы» урона.
	private static final double DASH_RANGE = 14.0;
	private static final double DASH_FOLLOW = 10.0;
	private static final double DASH_WIDTH = 1.5;
	// Цепная Молния: дальность, прыжок, предел прыжков, ослабление за прыжок.
	private static final double CHAIN_RANGE = 40.0;
	private static final double CHAIN_HOP = 8.0;
	private static final int CHAIN_MAX = 5;
	private static final float CHAIN_FADE = 0.8F;
	// Удар Небесного Грома: высота круга, предупреждение, радиусы полного и половинного урона, развилки.
	private static final float STRIKE_SKY = 16.0F;
	private static final int STRIKE_DELAY = 8;
	private static final double STRIKE_R = 2.5;
	private static final double STRIKE_OUTER = 4.0;
	private static final double STRIKE_FORK = 8.0;
	// Рельсотрон: дальность, сколько блоков пробивает тоннелем, предел твёрдости (обсидиан — 50 — не берёт).
	private static final double RAIL_RANGE = 128.0;
	private static final double RAIL_BORE = 48.0;
	private static final double RAIL_WIDTH = 1.2;
	private static final float RAIL_HARDNESS = 49.0F;
	private static final float RAIL_FADE = 0.85F;
	// Громовой Дракон: скорость, скорость прыжка, поворот за тик (первый полёт / прыжки), прыжки, дальность прыжка.
	private static final double DRAGON_SPEED = 1.2;
	private static final double DRAGON_LEAP = 1.8;
	private static final double DRAGON_TURN = Math.toRadians(12.0);
	private static final double DRAGON_LEAP_TURN = Math.toRadians(35.0);
	private static final int DRAGON_JUMPS = 6;
	private static final double DRAGON_REACH = 12.0;
	private static final double DRAGON_RANGE = 64.0;
	private static final int DRAGON_COIL = 4;
	private static final int DRAGON_LIFE = 200;
	private static final int DRAGON_BODY = 30;
	// Небесный Цилинь: высота кругов и туч, радиус грозы, когда падает зверь, радиус удара, остаточные разряды.
	private static final float QILIN_SKY = 24.0F;
	private static final double QILIN_CLOUD = 30.0;
	private static final double QILIN_STORM = 16.0;
	private static final int QILIN_AT = 40;
	private static final double QILIN_R = 8.0;
	private static final int QILIN_RESIDUAL = 20;
	// Тысяча Громов Индры: высота свода, радиус ливня, сколько длится ливень, когда падает копьё, радиус копья.
	private static final float INDRA_SKY = 40.0F;
	private static final double INDRA_R = 32.0;
	private static final int INDRA_BARRAGE = 120;
	private static final int INDRA_GAP = 6;
	private static final int SPEAR_AT = 130;
	private static final double SPEAR_R = 12.0;
	private static final double SPEAR_RING = 16.0;

	private LightningSpells() {}

	public static void register() {
		// --- Ранг 1: Начальный ---

		// Щелчок Монеты: монета, разогнанная током, прошивает первую цель и задевает вторую.
		Spell.of("flicked_coin", LIGHTNING, 1, BOLT).mana(10).charge(10).cooldown(12).range(COIN_RANGE).icon("gold_nugget")
				.action(LightningSpells::coin)
				.register();

		// --- Ранг 2: Средний ---

		// Статические Оковы: шесть разрядов вонзаются в цель и сковывают её параличом.
		Spell.of("paralyze", LIGHTNING, 2, BOLT).anchor(Anchor.TARGET).circle(1.2F).mana(20).charge(14).cooldown(100).range(64)
				.icon("chain")
				.action(LightningSpells::shackles)
				.register();

		// Громовой Росчерк: рывок молнией сквозь врагов; копии — рывки следом к следующим врагам.
		Spell.of("thunderclap_flash", LIGHTNING, 2, BOLT).mana(20).charge(12).cooldown(50).range(DASH_RANGE).icon("lightning_rod")
				.action(LightningSpells::thunderclap)
				.register();

		// --- Ранг 3: Продвинутый ---

		// Цепная Молния: разряд скачет от врага к врагу, слабея на каждом прыжке.
		Spell.of("chain_lightning", LIGHTNING, 3, BOLT).mana(32).charge(18).cooldown(40).range(CHAIN_RANGE).icon("copper_ingot")
				.action(LightningSpells::chain)
				.register();

		// Удар Небесного Грома: круг в небе над прицелом, метка на земле — и копьё грома.
		Spell.of("lightning_strike", LIGHTNING, 3, BOLT).anchor(Anchor.SKY).layers(2).circle(2.5F).sky(STRIKE_SKY).mana(34).charge(20)
				.cooldown(100).range(96).destroys().icon("lightning_rod")
				.action(c -> {
					SpellKit.release(c);
					heavenStrike(c, c.aimGround());
					return true;
				})
				.register();

		// --- Ранг 4: Святой ---

		// Рельсотрон: пять кругов-рельс, монета быстрее грома — луч на 128 блоков и тоннель в скалах.
		Spell.of("railgun", LIGHTNING, 4, BOLT).anchor(Anchor.LINE).layers(5).mana(60).charge(32).cooldown(260).range(RAIL_RANGE)
				.destroys().icon("gold_ingot")
				.action(LightningSpells::railgun)
				.register();

		// --- Ранг 5: Королевский ---

		// Громовой Дракон Цепей: змей из молний гонится за целью, обвивает её и перескакивает на следующих.
		Spell.of("thunder_dragon", LIGHTNING, 5, BOLT).layers(2).mana(95).charge(44).cooldown(600).range(DRAGON_RANGE)
				.icon("dragon_head")
				.action(LightningSpells::thunderDragon)
				.register();

		// --- Ранг 6: Императорский ---

		// Небесный Цилинь: над целью собирается гроза, потом с неба падает зверь-молния.
		Spell.of("heavenly_qilin", LIGHTNING, 6, RITUAL).anchor(Anchor.SKY).layers(3).sky(QILIN_SKY).mana(165).charge(72)
				.cooldown(2200).range(128).destroys().icon("trident")
				.action(LightningSpells::qilin)
				.register();

		// --- Ранг 7: Божественный ---

		// Тысяча Громов Индры: шесть кругов грозового свода, ливень молний и копьё, раскалывающее землю.
		Spell.of("indra_thousand_thunders", LIGHTNING, 7, RITUAL).anchor(Anchor.SKY).layers(6).circle(24.0F).sky(INDRA_SKY).mana(290)
				.charge(140).cooldown(7200).range(128).destroys().noMulticast().icon("lightning_rod")
				.action(LightningSpells::indra)
				.register();
	}

	// --- Общее ---

	/** Мокрая цель: в воде или под дождём. Молния бьёт таких сильнее. */
	static boolean wet(LivingEntity e) {
		return e.isInWaterOrRain();
	}

	static boolean isBoss(LivingEntity e) {
		return e instanceof EnderDragon || e instanceof WitherBoss || e instanceof Warden || e instanceof ElderGuardian;
	}

	/** Паралич: боссам — четверть (не меньше 0,5 с), игрокам — не дольше 1,5 с. */
	static void paralyze(Cast c, LivingEntity e, int ticks) {
		int t = c.duration(ticks);
		if (isBoss(e)) t = Math.max(10, t / 4);
		else if (e instanceof Player) t = Math.min(t, 30);
		e.addEffect(new MobEffectInstance(MagicEffects.PARALYSIS, t, 0), c.caster);
	}

	/** Цель под прицелом (если её можно задеть) или ближайший к точке прицела враг в радиусе {@code near}. */
	static LivingEntity victim(Cast c, double near) {
		Targeting.Aim aim = c.target();
		if (aim.entity() instanceof LivingEntity l && l.isAlive() && c.isFoe(l)) return l;
		return nearest(c, aim.point(), near, Set.of());
	}

	/** Ближайший к точке враг в радиусе, которого ещё не било ({@code skip} — UUID задетых). */
	static LivingEntity nearest(Cast c, Vec3 at, double radius, Set<UUID> skip) {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : c.enemiesAround(at, radius)) {
			if (skip.contains(e.getUUID())) continue;
			double d = Cast.center(e).distanceToSqr(at);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	/** Первая твёрдая преграда на отрезке (или его конец). */
	static Vec3 wall(Cast c, Vec3 from, Vec3 to) {
		BlockHitResult b = c.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c.caster));
		return b.getType() == HitResult.Type.MISS ? to : b.getLocation();
	}

	/** Разряд в точке: искры, колечко, треск. */
	static void zap(Cast c, Vec3 at, int color, float size) {
		MagicFx.burst(c.level, at, MagicParticles.spark(color, 0.3F + 0.1F * size, 10), Math.round(6 + 8 * size), 0.2 * size, 0.08 * size);
		MagicFx.burst(c.level, at, ParticleTypes.ELECTRIC_SPARK, Math.round(6 + 6 * size), 0.3 * size, 0.2);
		if (size >= 1.0F) MagicFx.shockwave(c.level, at, SpellKit.UP, 0.2F, 1.4F * size, color, 8);
		MagicFx.sound(c.level, at, MagicSounds.impactOf(c.spell), Math.min(2.0F, 0.5F + 0.3F * size), 1.3F - 0.1F * size);
	}

	/** Ванильная молния только для вида: без огня и урона (но со своим громом и вспышкой неба). */
	static void vanillaBolt(ServerLevel level, Vec3 at) {
		LightningBolt b = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (b == null) return;
		b.setVisualOnly(true);
		b.snapTo(at);
		level.addFreshEntity(b);
	}

	/** Песок под ударом молнии сплавляется в стекло (только при разрешённом разрушении). */
	static int glassify(Cast c, Vec3 at, double r) {
		if (!Terrain.griefing(c.level)) return 0;
		BlockPos o = BlockPos.containing(at);
		int ri = Mth.ceil(r);
		int n = 0;
		float maxRes = Terrain.resistanceFor(c);
		for (BlockPos p : BlockPos.betweenClosed(o.offset(-ri, -2, -ri), o.offset(ri, 1, ri))) {
			if (p.distSqr(o) > r * r) continue;
			BlockState s = c.level.getBlockState(p);
			if (!s.is(Blocks.SAND) && !s.is(Blocks.RED_SAND)) continue;
			if (!Terrain.breakable(c.level, c.caster, p, s, maxRes)) continue;
			c.level.setBlock(p, Blocks.GLASS.defaultBlockState(), 3);
			n++;
		}
		return n;
	}

	/** Удар молнии с неба в точку: рисунок, гром, вспышка (без урона). */
	static void boltDown(Cast c, Vec3 top, Vec3 at, float width, int color, boolean vanilla) {
		MagicFx.lightningBolt(c.level, top, at, width, color, 6 + Math.round(width * 4));
		if (vanilla) vanillaBolt(c.level, at);
		MagicFx.burst(c.level, at.add(0, 0.3, 0), MagicParticles.spark(WHITE, 0.4F, 10), 10 + Math.round(width * 10), 0.3, 0.15);
		MagicFx.burst(c.level, at.add(0, 0.3, 0), ParticleTypes.ELECTRIC_SPARK, 8 + Math.round(width * 8), 0.5, 0.3);
		MagicFx.shockwave(c.level, at.add(0, 0.15, 0), SpellKit.UP, 0.2F, 1.5F + 2.5F * width, color, 8);
	}

	/**
	 * Выжженная ямка от удара молнии в землю. Центр опущен в верхний блок земли: иначе маленькая воронка вокруг точки
	 * на поверхности задевает только воздух над ней.
	 */
	static void scar(Cast c, Vec3 ground, double r) {
		Terrain.crater(c, ground.add(0, -0.6, 0), r, 0.75, Terrain.Rim.FIRE);
	}

	/** Поворот направления {@code from} к {@code to} не больше чем на {@code max} радиан. */
	static Vec3 turn(Vec3 from, Vec3 to, double max) {
		Vec3 a = from.normalize();
		Vec3 b = to.normalize();
		double ang = Math.acos(Mth.clamp(a.dot(b), -1.0, 1.0));
		if (ang <= max) return b;
		double s = Math.sin(ang);
		if (s < 1.0E-4) return a.add(0, 0.3, 0).normalize();
		double t = max / ang;
		return a.scale(Math.sin((1 - t) * ang) / s).add(b.scale(Math.sin(t * ang) / s)).normalize();
	}

	/** Расстояние от ног существа до точки удара (по ногам честнее, чем по центру тела). */
	private static double feet(LivingEntity e, Vec3 at) {
		return e.position().distanceTo(at);
	}

	// --- Щелчок Монеты ---

	private static boolean coin(Cast c) {
		SpellKit.release(c);
		Vec3 from = c.hand();
		Vec3 dir = c.direction();
		Vec3 far = from.add(dir.scale(COIN_RANGE));
		Vec3 end = wall(c, from, far);
		boolean blocked = end.distanceToSqr(far) > 1.0E-4;
		List<LivingEntity> hit = c.along(from, end, COIN_WIDTH, c::isFoe);
		float dmg = c.amount(7.0F);
		for (int i = 0; i < Math.min(2, hit.size()); i++) {
			LivingEntity e = hit.get(i);
			c.hurt(e, wet(e) ? dmg * 1.3F : dmg, MagicDamage.SHOCK);
			// Монета намагничивает: изредка цель вязнет.
			if (c.level.random.nextFloat() < 0.15F) SpellKit.slow(c, e, 20, 0);
			zap(c, Cast.center(e), GOLD, 0.6F);
			dmg *= COIN_SECOND;
		}
		// Монетка подпрыгивает перед кругом и срывается тонким оранжевым лучом.
		MagicFx.burst(c.level, from, MagicParticles.spark(GOLD, 0.35F, 8), 6, 0.06, 0.04);
		MagicFx.one(c.level, from.add(0, 0.25, 0), MagicParticles.glyph(GOLD, 0.25F, 6), 0, 0.05, 0);
		MagicFx.beam(c.level, from, end, 0.15F, GOLD, WHITE, 4);
		MagicFx.lightningBolt(c.level, from, end, 0.1F, GOLD, 4);
		if (blocked) {
			// Рикошет о камень: сноп искр, блоки целы.
			MagicFx.burst(c.level, end, ParticleTypes.ELECTRIC_SPARK, 12, 0.15, 0.25);
			MagicFx.burst(c.level, end, MagicParticles.spark(GOLD, 0.3F, 8), 8, 0.1, 0.1);
			MagicFx.sound(c.level, end, MagicSounds.impactOf(c.spell), 0.7F, 1.6F);
		}
		return true;
	}

	// --- Статические Оковы ---

	private static boolean shackles(Cast c) {
		LivingEntity t = victim(c, SHACKLE_NEAR);
		if (t == null) {
			MagicFx.burst(c.level, c.aimGround().add(0, 0.3, 0), ParticleTypes.ELECTRIC_SPARK, 10, 0.4, 0.1);
			return false;
		}
		SpellKit.release(c);
		Vec3 waist = Cast.center(t);
		paralyze(c, t, wet(t) ? 120 : 80);
		c.hurt(t, c.amount(4.0F), MagicDamage.SHOCK);
		// Круг под целью и шесть стержней с шести сторон — по одному, с металлическим щелчком.
		MagicFx.circle(c.level, c.spell, t.position().add(0, 0.08, 0), SpellKit.UP, 1.2F, 6, SHACKLE_GLOW, 10);
		double reach = 2.4 + t.getBbWidth();
		for (int k = 0; k < 6; k++) {
			int n = k;
			Zones.later(c, k, () -> {
				double a = Math.PI * 2 * n / 6 + 0.3;
				Vec3 out = new Vec3(Math.cos(a), n % 2 == 0 ? 0.5 : -0.15, Math.sin(a)).normalize().scale(reach);
				Vec3 from = waist.add(out);
				MagicFx.beam(c.level, from, waist, 0.07F, YELLOW, WHITE, SHACKLE_GLOW - n);
				MagicFx.burst(c.level, from, MagicParticles.spark(YELLOW, 0.3F, 8), 4, 0.05, 0.02);
				MagicFx.sound(c.level, waist, MagicSounds.impactOf(c.spell), 0.6F, 1.3F + 0.08F * n);
			});
		}
		// Пока горят стержни — гул статики и капли искр.
		Zones.add(c, waist, 2, SHACKLE_GLOW, (z, age) -> {
			if (!t.isAlive()) {
				z.stop();
				return;
			}
			RandomSource rnd = c.level.random;
			if (age % 3 == 0) {
				Vec3 p = Cast.center(t).add((rnd.nextDouble() - 0.5) * t.getBbWidth(), (rnd.nextDouble() - 0.5), (rnd.nextDouble() - 0.5) * t.getBbWidth());
				MagicFx.one(c.level, p, MagicParticles.spark(YELLOW, 0.2F, 10), 0, -0.08, 0);
			}
			if (age % 20 == 0) MagicFx.sound(c.level, Cast.center(t), MagicSounds.LOOP_LIGHTNING, 0.35F, 1.4F);
		});
		return true;
	}

	// --- Громовой Росчерк ---

	private static boolean thunderclap(Cast c) {
		SpellKit.release(c);
		Vec3 start = c.caster.position();
		Vec3 dir;
		double len;
		if (c.copy > 0) {
			// Копия — следующий рывок к врагу у прицела (или прямо дальше).
			Vec3 d = c.aimPoint().subtract(start.add(0, 0.9, 0));
			dir = d.lengthSqr() < 0.01 ? flat(c.look()) : d.normalize();
			len = DASH_FOLLOW;
		} else {
			dir = Math.abs(c.look().y) < 0.35 ? flat(c.look()) : c.look();
			len = DASH_RANGE;
		}
		Vec3 body = start.add(0, 0.9, 0);
		Vec3 stop = wall(c, body, body.add(dir.scale(len)));
		double run = Math.max(0, stop.distanceTo(body) - (stop.distanceToSqr(body.add(dir.scale(len))) > 1.0E-4 ? 0.5 : 0));
		// Ищем, где встать: от конца пути назад, пока не найдётся место.
		Vec3 dest = null;
		for (double back = run; back >= 0; back -= 0.5) {
			Vec3 spot = c.safeSpot(start.add(dir.scale(back)));
			if (spot != null) {
				dest = spot;
				break;
			}
		}
		if (dest == null) dest = start;
		Vec3 endBody = dest.add(0, 0.9, 0);
		// Всё на пути: удар и миг паралича.
		for (LivingEntity e : c.along(body, endBody, DASH_WIDTH, c::isFoe)) {
			c.hurt(e, c.amount(9.0F), MagicDamage.SHOCK);
			paralyze(c, e, 10);
			zap(c, Cast.center(e), YELLOW, 0.7F);
		}
		// Золотой зигзаг вдоль пути, искровой след на старте, лепестки искр на финише.
		MagicFx.lightningBolt(c.level, body, endBody, 0.45F, YELLOW, 6);
		MagicFx.burst(c.level, body, MagicParticles.spark(YELLOW, 0.45F, 14), 18, 0.35, 0.05);
		MagicFx.sphere(c.level, body, 0.2F, 1.2F, YELLOW, WHITE, 6);
		c.caster.teleportTo(dest.x, dest.y, dest.z);
		c.caster.resetFallDistance();
		c.caster.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 10, 1), c.caster);
		for (int k = 0; k < 8; k++) {
			double a = Math.PI * 2 * k / 8;
			MagicFx.one(c.level, endBody, MagicParticles.spark(k % 2 == 0 ? YELLOW : WHITE, 0.35F, 10), Math.cos(a) * 0.3, 0.05, Math.sin(a) * 0.3);
		}
		MagicFx.soundFar(c.level, endBody, MagicSounds.THUNDER, 32, 1.4F + 0.1F * c.copy);
		MagicFx.sound(c.level, endBody, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6F, 1.6F);
		return true;
	}

	private static Vec3 flat(Vec3 v) {
		Vec3 f = new Vec3(v.x, 0, v.z);
		return f.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : f.normalize();
	}

	// --- Цепная Молния ---

	private static boolean chain(Cast c) {
		LivingEntity first = victim(c, 4.0);
		if (first == null) {
			MagicFx.burst(c.level, c.hand(), ParticleTypes.ELECTRIC_SPARK, 8, 0.2, 0.1);
			return false;
		}
		SpellKit.release(c);
		int hops = Math.min(CHAIN_MAX, 3 + Math.max(0, (int) Math.floor((c.power - 1.0F) / 0.2F + 1.0E-4)));
		if (wet(first)) hops += 2;
		// Цепь выбирается сразу: каждый следующий — ближайший незадетый враг в 8 блоках от предыдущего.
		List<LivingEntity> chain = new ArrayList<>();
		Set<UUID> seen = new HashSet<>();
		chain.add(first);
		seen.add(first.getUUID());
		LivingEntity cur = first;
		for (int i = 0; i < hops; i++) {
			LivingEntity next = nearest(c, Cast.center(cur), CHAIN_HOP, seen);
			if (next == null) break;
			chain.add(next);
			seen.add(next.getUUID());
			cur = next;
		}
		float base = c.amount(12.0F);
		for (int i = 0; i < chain.size(); i++) {
			int n = i;
			Zones.later(c, 2 * i, () -> {
				LivingEntity t = chain.get(n);
				if (!t.isAlive()) return;
				Vec3 from = n == 0 ? c.hand() : Cast.center(chain.get(n - 1));
				Vec3 to = Cast.center(t);
				MagicFx.lightningBolt(c.level, from, to, 0.35F, n % 2 == 0 ? c.color() : VIOLET, 6);
				c.hurt(t, base * (float) Math.pow(CHAIN_FADE, n), MagicDamage.SHOCK);
				paralyze(c, t, 10);
				MagicFx.sphere(c.level, to, 0.2F, 1.4F, VIOLET, WHITE, 5);
				zap(c, to, c.color(), 0.8F);
				MagicFx.sound(c.level, to, MagicSounds.impactOf(c.spell), 0.8F, 0.9F + 0.12F * n);
				if (n == 0) MagicFx.flash(c.level, to, 16, 0.12F, WHITE, 4);
			});
		}
		return true;
	}

	// --- Удар Небесного Грома ---

	private static void heavenStrike(Cast c, Vec3 ground) {
		Vec3 sky = ground.add(0, STRIKE_SKY, 0);
		// Круг в небе вспыхивает заново и сжимается, на земле проступает голубая метка, искры капают дождём.
		MagicFx.circle(c.level, c.spell, sky, SpellKit.DOWN, 2.5F, 2, 0.6F, 3, STRIKE_DELAY - 3, 6);
		MagicFx.circle(c.level, LIGHTNING, 3, MARK, WHITE, ground.add(0, 0.08, 0), SpellKit.UP, (float) STRIKE_R, 1, 0, STRIKE_DELAY, 2, 8,
				0.6F);
		MagicFx.soundFar(c.level, ground, MagicSounds.RISER_SHORT, 48, 1.4F);
		Zones.add(c, sky, 3, STRIKE_DELAY, (z, age) -> {
			RandomSource rnd = c.level.random;
			for (int i = 0; i < 2; i++) {
				MagicFx.one(c.level, SpellKit.inDisc(rnd, sky, 2.2), MagicParticles.spark(WHITE, 0.2F, 14), 0, -0.9, 0);
			}
			if (age % 2 == 0) MagicFx.cloud(c.level, sky.add(0, 1.2, 0), ParticleTypes.LARGE_SMOKE, 3, 1.6, 0.3, 1.6, 0.01);
		});
		Zones.later(c, STRIKE_DELAY, () -> strike(c, ground, sky));
	}

	private static void strike(Cast c, Vec3 ground, Vec3 sky) {
		boltDown(c, sky, ground, 0.9F, c.color(), true);
		MagicFx.flash(c.level, ground, 20, 0.3F, WHITE, 5);
		MagicFx.soundFar(c.level, ground, MagicSounds.THUNDER, 96, 1.0F);
		Magic.shake(c.level, ground, 0.8F, 8, 16);
		Set<UUID> hit = new HashSet<>();
		for (LivingEntity e : c.foesAround(ground, STRIKE_OUTER + 1.0)) {
			double d = feet(e, ground);
			if (d > STRIKE_OUTER) continue;
			float dmg = c.amount(d <= STRIKE_R ? 18.0F : 9.0F);
			c.hurt(e, wet(e) ? dmg * 1.3F : dmg, MagicDamage.SHOCK);
			paralyze(c, e, 20);
			hit.add(e.getUUID());
		}
		scar(c, ground, 1.2);
		// Стекло — после воронки: иначе очередь разрушения выламывает уже сплавленный песок.
		Zones.later(c, 3, () -> glassify(c, ground, 2.2));
		// Разветвления: ещё двум врагам рядом.
		Zones.later(c, 3, () -> {
			for (int i = 0; i < 2; i++) {
				LivingEntity e = nearest(c, ground, STRIKE_FORK, hit);
				if (e == null) break;
				hit.add(e.getUUID());
				MagicFx.lightningBolt(c.level, ground.add(0, 3.0, 0), Cast.center(e), 0.3F, VIOLET, 5);
				c.hurt(e, c.amount(7.0F), MagicDamage.SHOCK);
				zap(c, Cast.center(e), c.color(), 0.6F);
			}
		});
	}

	// --- Рельсотрон ---

	private static boolean railgun(Cast c) {
		SpellKit.release(c);
		Vec3 from = c.hand();
		Vec3 dir = c.direction();
		Vec3 far = from.add(dir.scale(RAIL_RANGE));
		Vec3 end;
		if (Terrain.griefing(c.level)) {
			// Первые 48 блоков монета проходит насквозь (кроме обсидиана и прочного), потом бьёт в первую стену.
			Vec3 bore = boreEnd(c, from, from.add(dir.scale(RAIL_BORE)));
			Terrain.tunnel(c, from.add(dir.scale(1.5)), bore, 1.0, Terrain.Rim.NONE, RAIL_HARDNESS);
			end = bore.distanceToSqr(from) < (RAIL_BORE - 0.5) * (RAIL_BORE - 0.5) ? bore : wall(c, bore, far);
		} else {
			end = wall(c, from, far);
		}
		boolean terminus = end.distanceToSqr(far) > 1.0E-4;
		// Все на линии: первый — полный урон, дальше ×0,85; отброс вдоль луча и поджог.
		float dmg = c.amount(26.0F);
		for (LivingEntity e : c.along(from, end, RAIL_WIDTH, c::isFoe)) {
			c.hurt(e, dmg, MagicDamage.SHOCK);
			e.igniteForTicks(c.duration(40));
			e.push(dir.x * 0.8, 0.15 + dir.y * 0.4, dir.z * 0.8);
			e.hurtMarked = true;
			dmg *= RAIL_FADE;
		}
		if (terminus) {
			Terrain.crater(c, end, 1.5, 0.75, Terrain.Rim.FIRE);
			SpellKit.impact(c, end, 1.5F);
			MagicFx.soundFar(c.level, end, MagicSounds.THUNDER, 64, 1.2F);
		}
		// Луч: толстое золото, слепящее ядро, зигзаги вдоль, кольца у каждого круга-рельса.
		MagicFx.beam(c.level, from, end, 0.6F, GOLD, WHITE, 8);
		MagicFx.beam(c.level, from, end, 0.22F, WHITE, WHITE, 6);
		MagicFx.lightningBolt(c.level, from, end, 0.3F, GOLD, 6);
		Vec3 point = c.aimPoint();
		for (int i = 0; i < c.spell.layers; i++) {
			Vec3 ring = CircleLayout.lineStep(from, point, i, c.spell.layers);
			MagicFx.shockwave(c.level, ring, dir, 0.4F, 1.6F + 0.25F * i, i % 2 == 0 ? GOLD : WHITE, 8);
		}
		MagicFx.burst(c.level, from, MagicParticles.spark(GOLD, 0.5F, 12), 16, 0.2, 0.15);
		MagicFx.flash(c.level, from, 32, 0.4F, GOLD, 6);
		MagicFx.soundFar(c.level, from, MagicSounds.BEAM_BLAST, 96, 1.3F);
		MagicFx.soundFar(c.level, from, MagicSounds.RELEASE_HEAVY, 96, 1.1F);
		Magic.shake(c.level, c.eye(), 0.8F, 10, 12);
		// Отдача.
		c.caster.push(-dir.x * 0.6, -dir.y * 0.3, -dir.z * 0.6);
		c.caster.hurtMarked = true;
		return true;
	}

	/** Куда монета пробьёт скалу: до первого блока, который слишком прочен (обсидиан, бедрок), или до конца. */
	private static Vec3 boreEnd(Cast c, Vec3 from, Vec3 to) {
		Vec3 d = to.subtract(from);
		int steps = (int) Math.ceil(d.length() * 2);
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int i = 1; i <= steps; i++) {
			Vec3 p = from.add(d.scale(i / (double) steps));
			m.set(Mth.floor(p.x), Mth.floor(p.y), Mth.floor(p.z));
			BlockState s = c.level.getBlockState(m);
			if (s.isAir()) continue;
			float hard = s.getDestroySpeed(c.level, m);
			if (hard < 0 || hard > RAIL_HARDNESS) return from.add(d.scale((i - 1) / (double) steps));
		}
		return to;
	}

	// --- Громовой Дракон Цепей ---

	/** Дракон в полёте: голова, скорость, цель, урон следующего укуса, сколько прыжков осталось. */
	private static final class Dragon {
		Vec3 head;
		Vec3 vel;
		LivingEntity target;
		float damage;
		int jumps = DRAGON_JUMPS;
		int coil;
		double reach = DRAGON_REACH;
		double flown;
		boolean leaping;
		final Set<UUID> hit = new HashSet<>();
		final ArrayDeque<Vec3> body = new ArrayDeque<>();
	}

	private static boolean thunderDragon(Cast c) {
		SpellKit.release(c);
		Dragon d = new Dragon();
		d.head = c.hand();
		d.vel = c.direction().scale(DRAGON_SPEED);
		d.target = victim(c, 6.0);
		d.damage = c.amount(30.0F);
		// Голова дракона собирается из искр и ревёт.
		MagicFx.sphere(c.level, d.head, 0.2F, 1.4F, c.color(), WHITE, 8);
		MagicFx.burst(c.level, d.head, MagicParticles.spark(WHITE, 0.5F, 14), 20, 0.4, 0.08);
		MagicFx.soundFar(c.level, d.head, MagicSounds.RELEASE_HEAVY, 64, 0.8F);
		MagicFx.sound(c.level, d.head, SoundEvents.ENDER_DRAGON_GROWL, 0.6F, 1.6F);
		Zones.add(c, d.head, DRAGON_REACH, DRAGON_LIFE, (z, age) -> {
			boolean done = dragonTick(c, d, age);
			z.center = d.head;
			if (done || age >= DRAGON_LIFE - 1) {
				if (!done) dissipate(c, d.head);
				z.stop();
			}
		});
		return true;
	}

	/** Тик дракона; true — рассеялся. */
	private static boolean dragonTick(Cast c, Dragon d, int age) {
		if (d.coil > 0) {
			// Обвивает жертву кольцами, потом прыгает к следующей.
			Vec3 ctr = Cast.center(d.target);
			double a = (DRAGON_COIL - d.coil) * 1.7;
			double r = 0.5 + d.target.getBbWidth();
			Vec3 p = ctr.add(Math.cos(a) * r, (d.coil - 2) * 0.45, Math.sin(a) * r);
			MagicFx.lightning(c.level, d.head, p, c.color());
			d.head = p;
			grow(d);
			if (--d.coil > 0) return false;
			LivingEntity next = d.jumps > 0 ? nearest(c, ctr, d.reach, d.hit) : null;
			if (next == null) {
				dissipate(c, d.head);
				return true;
			}
			MagicFx.lightningBolt(c.level, ctr, Cast.center(next), 0.4F, VIOLET, 6);
			MagicFx.sound(c.level, ctr, MagicSounds.impactOf(c.spell), 0.8F, 1.5F);
			d.target = next;
			d.jumps--;
			d.leaping = true;
			d.vel = Cast.center(next).subtract(d.head).normalize().scale(DRAGON_LEAP);
			return false;
		}
		if (d.target != null && (!d.target.isAlive() || d.target.isRemoved())) d.target = null;
		if (d.target == null && age % 4 == 0) d.target = nearest(c, d.head, d.hit.isEmpty() ? 8.0 : d.reach, d.hit);
		double speed = d.leaping ? DRAGON_LEAP : DRAGON_SPEED;
		Vec3 next;
		if (d.target != null) {
			Vec3 to = Cast.center(d.target);
			Vec3 want = to.subtract(d.head);
			if (want.length() <= speed + 1.0) {
				next = to;
			} else {
				d.vel = turn(d.vel, want, d.leaping ? DRAGON_LEAP_TURN : DRAGON_TURN).scale(speed);
				next = d.head.add(d.vel);
			}
		} else {
			if (!d.hit.isEmpty()) {
				dissipate(c, d.head);
				return true;
			}
			next = d.head.add(d.vel);
			Vec3 w = wall(c, d.head, next);
			if (w.distanceToSqr(next) > 1.0E-4 || d.flown > DRAGON_RANGE) {
				dissipate(c, w);
				return true;
			}
		}
		d.flown += next.distanceTo(d.head);
		drawDragon(c, d, next, age);
		d.head = next;
		grow(d);
		if (d.target != null && d.target.getBoundingBox().inflate(0.8).contains(d.head)) bite(c, d);
		return false;
	}

	private static void grow(Dragon d) {
		d.body.addFirst(d.head);
		while (d.body.size() > DRAGON_BODY) d.body.removeLast();
	}

	/** Укус: урон, паралич, кольца вокруг жертвы. */
	private static void bite(Cast c, Dragon d) {
		LivingEntity t = d.target;
		boolean wet = wet(t);
		c.hurt(t, wet ? d.damage * 1.4F : d.damage, MagicDamage.SHOCK);
		paralyze(c, t, 40);
		d.hit.add(t.getUUID());
		d.damage *= 0.85F;
		d.reach = DRAGON_REACH + (wet ? 6.0 : 0.0);
		d.coil = DRAGON_COIL;
		Vec3 at = Cast.center(t);
		zap(c, at, WHITE, 1.2F);
		MagicFx.sphere(c.level, at, 0.3F, 2.2F, c.color(), WHITE, 6);
		MagicFx.flash(c.level, at, 20, 0.15F, WHITE, 4);
		MagicFx.soundFar(c.level, at, MagicSounds.THUNDER, 48, 1.1F + 0.08F * d.hit.size());
	}

	/** Тело дракона: зигзаг от головы к шее, толстые сегменты по всему телу, искры с головы. */
	private static void drawDragon(Cast c, Dragon d, Vec3 next, int age) {
		MagicFx.lightning(c.level, d.head, next, WHITE);
		MagicFx.burst(c.level, next, MagicParticles.spark(WHITE, 0.55F, 8), 3, 0.15, 0.02);
		MagicFx.burst(c.level, next, MagicParticles.spark(c.color(), 0.35F, 12), 2, 0.3, 0.02);
		if (age % 3 == 0 && d.body.size() > 6) {
			Vec3[] pts = d.body.toArray(new Vec3[0]);
			for (int i = 0; i + 6 < pts.length; i += 6) {
				MagicFx.lightningBolt(c.level, pts[i], pts[i + 6], i == 0 ? 0.45F : 0.3F, i % 12 == 0 ? c.color() : VIOLET, 4);
			}
		}
		if (age % 10 == 0) MagicFx.sound(c.level, next, MagicSounds.LOOP_LIGHTNING, 0.7F, 1.0F);
	}

	private static void dissipate(Cast c, Vec3 at) {
		MagicFx.burst(c.level, at, MagicParticles.spark(WHITE, 0.4F, 12), 16, 0.6, 0.12);
		MagicFx.burst(c.level, at, ParticleTypes.ELECTRIC_SPARK, 16, 0.8, 0.2);
		MagicFx.sound(c.level, at, MagicSounds.impactOf(c.spell), 0.6F, 0.7F);
	}

	// --- Небесный Цилинь ---

	private static boolean qilin(Cast c) {
		SpellKit.release(c);
		Vec3 ground = c.aimGround();
		if (c.copy > 0) {
			qilinEcho(c, ground);
			return true;
		}
		Vec3 sky = ground.add(0, QILIN_SKY, 0);
		Vec3 cloud = ground.add(0, QILIN_CLOUD, 0);
		// Малый круг на земле и три круга в небе: вспыхивают по одному, соседние крутятся навстречу.
		MagicFx.circle(c.level, LIGHTNING, 6, VIOLET, WHITE, ground.add(0, 0.08, 0), SpellKit.UP, 4.0F, 1, 0, 10, QILIN_AT - 6, 12, 0.3F);
		float[] radii = {9.0F, 6.5F, 4.0F};
		for (int i = 0; i < 3; i++) {
			int n = i;
			Zones.later(c, 4 * i, () -> {
				MagicFx.circle(c.level, LIGHTNING, 6, n == 1 ? VIOLET : c.color(), WHITE, sky.add(0, 1.5 * n, 0), SpellKit.DOWN, radii[n], 1, 0,
						6, QILIN_AT - 4 * n, 12, n % 2 == 0 ? 0.12F : -0.16F);
				MagicFx.soundFar(c.level, sky, MagicSounds.CIRCLE_COMPLETE, 64, 0.7F + 0.15F * n);
			});
		}
		// Гроза: тучи темнеют, ливень, разряды бьют в ближайшего к центру врага.
		MagicFx.flash(c.level, ground, 48, 0.3F, STORM, QILIN_AT + 20);
		MagicFx.soundFar(c.level, ground, MagicSounds.RUMBLE, 128, 0.6F);
		MagicFx.soundFar(c.level, ground, MagicSounds.LOOP_STORM, 96, 0.9F);
		Zones.add(c, ground, QILIN_STORM, QILIN_AT, (z, age) -> storm(c, ground, cloud, age));
		// Зверь пикирует: комета из туч, потом удар.
		Zones.later(c, QILIN_AT - 10, () -> {
			MagicFx.comet(c.level, cloud.add(6, 6, 0), ground, 2.5F, VIOLET, WHITE, 10);
			MagicFx.soundFar(c.level, cloud, MagicSounds.RISER_SHORT, 128, 0.6F);
			MagicFx.sound(c.level, cloud, SoundEvents.ENDER_DRAGON_GROWL, 6.0F, 0.6F);
		});
		Zones.later(c, QILIN_AT, () -> qilinStrike(c, ground, cloud));
		return true;
	}

	/** Тик грозы над целью Цилиня. */
	private static void storm(Cast c, Vec3 ground, Vec3 cloud, int age) {
		RandomSource rnd = c.level.random;
		if (age % 2 == 0) MagicFx.cloud(c.level, cloud, ParticleTypes.LARGE_SMOKE, 8, QILIN_STORM * 0.5, 0.8, QILIN_STORM * 0.5, 0.01);
		for (int i = 0; i < 5; i++) {
			Vec3 p = SpellKit.inDisc(rnd, ground, QILIN_STORM).add(0, 10 + rnd.nextDouble() * 10, 0);
			MagicFx.one(c.level, p, MagicParticles.spark(0x9FC8FF, 0.08F, 14), 0, -1.4, 0);
		}
		// Зарницы в тучах.
		if (age % 10 == 5) {
			Vec3 a = SpellKit.inDisc(rnd, cloud, QILIN_STORM * 0.7);
			MagicFx.lightningBolt(c.level, a, SpellKit.inDisc(rnd, cloud, QILIN_STORM * 0.7).add(0, -2, 0), 0.4F, VIOLET, 4);
		}
		if (age % 20 == 0) {
			for (LivingEntity e : c.enemiesAround(ground, QILIN_R)) SpellKit.slow(c, e, 30, 0);
		}
		if (age % 8 != 0) return;
		LivingEntity t = nearest(c, ground, QILIN_STORM, Set.of());
		Vec3 at = t != null ? t.position() : Targeting.ground(c.level, c.caster, SpellKit.inDisc(rnd, ground, 12.0).add(0, 4, 0));
		boltDown(c, at.add(rnd.nextGaussian() * 2, QILIN_CLOUD - (at.y - ground.y), rnd.nextGaussian() * 2), at, 0.5F, c.color(), false);
		MagicFx.soundFar(c.level, at, MagicSounds.THUNDER, 64, 0.8F + rnd.nextFloat() * 0.4F);
		if (t != null) {
			c.hurt(t, c.amount(8.0F), MagicDamage.SHOCK);
			paralyze(c, t, 10);
		}
	}

	private static void qilinStrike(Cast c, Vec3 ground, Vec3 cloud) {
		// Зверь-молния: толстая спираль с кометой-головой, пять молний вокруг, белая вспышка, кольцо на 16 блоков.
		MagicFx.helix(c.level, cloud, ground, 2.0F, VIOLET, WHITE, 16);
		MagicFx.lightningBolt(c.level, cloud, ground, 2.0F, c.color(), 12);
		MagicFx.pillar(c.level, ground, 2.5F, (float) QILIN_CLOUD, c.color(), WHITE, 16);
		for (int k = 0; k < 5; k++) {
			double a = Math.PI * 2 * k / 5;
			vanillaBolt(c.level, Targeting.ground(c.level, c.caster, ground.add(Math.cos(a) * 3.5, 3, Math.sin(a) * 3.5)));
		}
		MagicFx.flash(c.level, ground, 96, 0.8F, WHITE, 12);
		MagicFx.shockwave(c.level, ground.add(0, 0.3, 0), SpellKit.UP, 1.0F, 16.0F, WHITE, 18);
		MagicFx.shockwave(c.level, ground.add(0, 1.2, 0), SpellKit.UP, 0.5F, 11.0F, VIOLET, 14);
		MagicFx.dome(c.level, ground, (float) QILIN_R, VIOLET, WHITE, 24);
		MagicFx.burst(c.level, ground.add(0, 0.5, 0), MagicParticles.spark(WHITE, 0.6F, 20), 60, 2.5, 0.4);
		MagicFx.burst(c.level, ground.add(0, 0.5, 0), ParticleTypes.ELECTRIC_SPARK, 60, 4.0, 0.5);
		MagicFx.soundFar(c.level, ground, MagicSounds.EXPLOSION_HUGE, 128, 0.9F);
		MagicFx.soundFar(c.level, ground, MagicSounds.THUNDER, 160, 0.6F);
		Magic.shake(c.level, ground, 2.5F, 24, 64);
		// 55 урона в центре, к краю (8 блоков) — половина; паралич на 2 с и подброс.
		for (LivingEntity e : c.foesAround(ground, QILIN_R + 1.0)) {
			double d = feet(e, ground);
			if (d > QILIN_R) continue;
			float k = (float) (1.0 - 0.5 * d / QILIN_R);
			c.hurt(e, c.amount(55.0F) * k, MagicDamage.SHOCK);
			paralyze(c, e, 40);
			SpellKit.launch(e, 1.2 * k);
		}
		Terrain.crater(c, ground, 4.0, 0.5, Terrain.Rim.FIRE);
		Terrain.scorch(c, ground, 7.0, 0.12F);
		// Стекло — после воронки: иначе очередь разрушения выламывает уже сплавленный песок.
		Zones.later(c, 3, () -> glassify(c, ground, 5.0));
		// Остаточные разряды: 1 с ток бежит к трём ближайшим врагам.
		Zones.add(c, ground, 12, QILIN_RESIDUAL, (z, age) -> {
			if (age % 5 != 0) return;
			List<LivingEntity> foes = c.enemiesAround(ground, 12.0);
			foes.sort((a, b) -> Double.compare(a.distanceToSqr(ground), b.distanceToSqr(ground)));
			for (int i = 0; i < Math.min(3, foes.size()); i++) {
				LivingEntity e = foes.get(i);
				MagicFx.lightningBolt(c.level, ground.add(0, 0.5, 0), Cast.center(e), 0.25F, i % 2 == 0 ? VIOLET : c.color(), 4);
				c.hurt(e, c.amount(3.0F), MagicDamage.SHOCK);
			}
			MagicFx.sound(c.level, ground, MagicSounds.impactOf(c.spell), 1.0F, 1.4F);
		});
	}

	/** Копия Цилиня: свой малый удар (радиус 5, ×0,6) по следующему врагу. */
	private static void qilinEcho(Cast c, Vec3 ground) {
		double r = 5.0;
		Vec3 cloud = ground.add(0, QILIN_CLOUD, 0);
		MagicFx.circle(c.level, LIGHTNING, 6, VIOLET, WHITE, cloud.add(0, -4, 0), SpellKit.DOWN, 4.0F, 1, 0, 6, 6, 8, 0.2F);
		SpellKit.telegraph(c, ground, (float) r, 10, () -> {
			MagicFx.helix(c.level, cloud, ground, 1.2F, VIOLET, WHITE, 10);
			boltDown(c, cloud, ground, 1.2F, c.color(), true);
			MagicFx.shockwave(c.level, ground.add(0, 0.3, 0), SpellKit.UP, 0.5F, 9.0F, WHITE, 12);
			MagicFx.soundFar(c.level, ground, MagicSounds.THUNDER, 96, 0.8F);
			for (LivingEntity e : c.foesAround(ground, r + 1.0)) {
				double d = feet(e, ground);
				if (d > r) continue;
				c.hurt(e, c.amount(55.0F * 0.6F) * (float) (1.0 - 0.5 * d / r), MagicDamage.SHOCK);
				paralyze(c, e, 20);
			}
			Terrain.crater(c, ground, 2.0, 0.5, Terrain.Rim.FIRE);
		});
	}

	// --- Тысяча Громов Индры ---

	/** Состояние ливня молний: кто когда получил удар, кэш врагов под сводом. */
	private static final class Barrage {
		final Map<Integer, Integer> lastHit = new HashMap<>();
		List<LivingEntity> foes = List.of();
	}

	private static boolean indra(Cast c) {
		SpellKit.release(c);
		MagicFx.soundFar(c.level, c.eye(), MagicSounds.RELEASE_ULTIMATE, 160, 0.9F);
		Vec3 ground = c.aimGround();
		Vec3 sky = ground.add(0, INDRA_SKY, 0);
		// 1. Шесть кругов свода вспыхивают снаружи внутрь, между готовыми прыгают дуги.
		float[] radii = {24.0F, 20.0F, 16.0F, 12.0F, 8.0F, 4.5F};
		for (int i = 0; i < radii.length; i++) {
			int n = i;
			Zones.later(c, 3 * i, () -> {
				int col = n % 3 == 0 ? c.color() : n % 3 == 1 ? VIOLET : WHITE;
				Vec3 at = sky.add(0, -0.8 * n, 0);
				MagicFx.circle(c.level, LIGHTNING, 7, col, WHITE, at, SpellKit.DOWN, radii[n], 1, 0, 6, SPEAR_AT - 3 * n, 16,
						n % 2 == 0 ? 0.05F : -0.07F);
				MagicFx.soundFar(c.level, at, MagicSounds.CIRCLE_COMPLETE, 128, 0.5F + 0.1F * n);
				if (n > 0) {
					double a = c.level.random.nextDouble() * Math.PI * 2;
					Vec3 outer = sky.add(Math.cos(a) * radii[n - 1], -0.8 * (n - 1), Math.sin(a) * radii[n - 1]);
					Vec3 inner = at.add(Math.cos(a + 0.4) * radii[n], 0, Math.sin(a + 0.4) * radii[n]);
					MagicFx.lightningBolt(c.level, outer, inner, 0.5F, VIOLET, 6);
				}
			});
		}
		// 2. Небо темнеет, гроза на весь ливень.
		MagicFx.flash(c.level, ground, 128, 0.4F, STORM, SPEAR_AT + 10);
		MagicFx.soundFar(c.level, ground, MagicSounds.RUMBLE, 160, 0.5F);
		Barrage b = new Barrage();
		Zones.add(c, ground, INDRA_R, INDRA_BARRAGE, (z, age) -> {
			RandomSource rnd = c.level.random;
			if (age % 2 == 0) MagicFx.cloud(c.level, sky.add(0, 2, 0), ParticleTypes.LARGE_SMOKE, 12, INDRA_R * 0.6, 1.0, INDRA_R * 0.6, 0.01);
			for (int i = 0; i < 6; i++) {
				Vec3 p = SpellKit.inDisc(rnd, ground, INDRA_R).add(0, 12 + rnd.nextDouble() * 14, 0);
				MagicFx.one(c.level, p, MagicParticles.spark(0x9FC8FF, 0.08F, 16), 0, -1.6, 0);
			}
			if (age % 20 == 0) MagicFx.soundFar(c.level, ground, MagicSounds.LOOP_STORM, 128, 0.8F);
			if (age % 10 == 0) b.foes = c.enemiesAround(ground, INDRA_R);
			if (age % 2 == 0) indraBolt(c, b, ground, age);
		});
		// 3. Копьё собирается в центре свода: ядро набухает, спираль света стекает вниз.
		Zones.later(c, SPEAR_AT - 24, () -> {
			MagicFx.sphere(c.level, sky.add(0, -4, 0), 0.5F, 4.0F, c.color(), WHITE, 24);
			MagicFx.helix(c.level, sky, sky.add(0, -10, 0), 3.0F, VIOLET, WHITE, 24);
			MagicFx.soundFar(c.level, sky, MagicSounds.RISER_LONG, 192, 0.8F);
		});
		Zones.later(c, SPEAR_AT, () -> indraSpear(c, ground, sky));
		Zones.later(c, SPEAR_AT + 24, () -> MagicFx.soundFar(c.level, ground, MagicSounds.THUNDER, 192, 0.5F));
		Zones.later(c, SPEAR_AT + 50, () -> MagicFx.soundFar(c.level, ground, MagicSounds.RUMBLE, 192, 0.4F));
		return true;
	}

	/** Одна молния ливня: 70% — во врага (не чаще раза в 6 тиков на каждого), иначе в случайную точку свода. */
	private static void indraBolt(Cast c, Barrage b, Vec3 ground, int age) {
		RandomSource rnd = c.level.random;
		Vec3 at = null;
		if (!b.foes.isEmpty() && rnd.nextFloat() < 0.7F) {
			for (int tries = 0; tries < 4 && at == null; tries++) {
				LivingEntity e = b.foes.get(rnd.nextInt(b.foes.size()));
				Integer last = b.lastHit.get(e.getId());
				if (e.isAlive() && (last == null || age - last >= INDRA_GAP)) at = e.position();
			}
		}
		if (at == null) at = Targeting.ground(c.level, c.caster, SpellKit.inDisc(rnd, ground, INDRA_R).add(0, 6, 0));
		Vec3 top = new Vec3(at.x + rnd.nextGaussian() * 3, ground.y + INDRA_SKY, at.z + rnd.nextGaussian() * 3);
		boltDown(c, top, at, 0.6F, rnd.nextBoolean() ? c.color() : WHITE, age % 6 == 0);
		MagicFx.sound(c.level, at, MagicSounds.THUNDER, 3.0F, 0.8F + rnd.nextFloat() * 0.6F);
		for (LivingEntity e : c.foesAround(at, 3.0)) {
			if (feet(e, at) > 2.0) continue;
			Integer last = b.lastHit.get(e.getId());
			if (last != null && age - last < INDRA_GAP) continue;
			b.lastHit.put(e.getId(), age);
			float dmg = c.amount(10.0F);
			c.hurt(e, wet(e) ? dmg * 1.3F : dmg, MagicDamage.SHOCK);
			paralyze(c, e, 10);
		}
		scar(c, at, 1.0);
	}

	private static void indraSpear(Cast c, Vec3 ground, Vec3 sky) {
		// Копьё Индры: столп-молния в центр, белая вспышка для всех в 128 блоках, кольцо на 40 блоков.
		MagicFx.beam(c.level, sky, ground, 4.0F, WHITE, WHITE, 20);
		MagicFx.lightningBolt(c.level, sky, ground, 3.0F, c.color(), 16);
		MagicFx.pillar(c.level, ground, 4.0F, INDRA_SKY, c.color(), WHITE, 30);
		MagicFx.flash(c.level, ground, 128, 1.0F, WHITE, 20);
		MagicFx.shockwave(c.level, ground.add(0, 0.4, 0), SpellKit.UP, 2.0F, 40.0F, WHITE, 30);
		MagicFx.shockwave(c.level, ground.add(0, 1.5, 0), SpellKit.UP, 1.0F, 24.0F, VIOLET, 22);
		MagicFx.sphere(c.level, ground, 1.0F, 14.0F, WHITE, WHITE, 16);
		MagicFx.dome(c.level, ground, (float) SPEAR_R, c.color(), WHITE, 40);
		MagicFx.burst(c.level, ground.add(0, 1, 0), MagicParticles.spark(WHITE, 0.7F, 24), 80, 4.0, 0.6);
		MagicFx.burst(c.level, ground.add(0, 1, 0), ParticleTypes.ELECTRIC_SPARK, 80, 6.0, 0.8);
		MagicFx.soundFar(c.level, ground, MagicSounds.EXPLOSION_HUGE, 192, 0.8F);
		MagicFx.soundFar(c.level, ground, MagicSounds.THUNDER, 192, 0.5F);
		MagicFx.soundFar(c.level, ground, MagicSounds.BEAM_BLAST, 160, 0.6F);
		Magic.shake(c.level, ground, 3.0F, 40, 128);
		// 120 урона в центре, к краю (12 блоков) — 40%; боссам не больше 80, игрокам — 30.
		for (LivingEntity e : c.foesAround(ground, SPEAR_R + 1.0)) {
			double d = feet(e, ground);
			if (d > SPEAR_R) continue;
			float k = (float) (1.0 - 0.6 * d / SPEAR_R);
			float dmg = c.amount(120.0F) * k;
			if (isBoss(e)) dmg = Math.min(dmg, 80.0F);
			else if (e instanceof Player) dmg = Math.min(dmg, 30.0F);
			c.hurt(e, dmg, MagicDamage.SHOCK);
			paralyze(c, e, 60);
			Cast.knock(e, ground, 2.5 * k, 0.6 * k);
		}
		Terrain.crater(c, ground, 8.0, 0.65, Terrain.Rim.GLASS);
		Terrain.scorch(c, ground, 12.0, 0.1F);
		// Стекло — после воронки: иначе очередь разрушения выламывает уже сплавленный песок.
		Zones.later(c, 3, () -> glassify(c, ground, 9.0));
		// Кольцо из двенадцати молний на 16 блоков, одна за другой.
		for (int k = 0; k < 12; k++) {
			int n = k;
			Zones.later(c, 2 + n, () -> {
				double a = Math.PI * 2 * n / 12;
				Vec3 at = Targeting.ground(c.level, c.caster, ground.add(Math.cos(a) * SPEAR_RING, 6, Math.sin(a) * SPEAR_RING));
				boltDown(c, sky.add(Math.cos(a) * radiusAt(n), 0, Math.sin(a) * radiusAt(n)), at, 1.0F, n % 2 == 0 ? WHITE : VIOLET, n % 3 == 0);
				for (LivingEntity e : c.foesAround(at, 3.0)) {
					if (feet(e, at) <= 2.0) c.hurt(e, c.amount(10.0F), MagicDamage.SHOCK);
				}
				scar(c, at, 1.0);
				MagicFx.sound(c.level, at, MagicSounds.THUNDER, 4.0F, 0.9F + 0.03F * n);
			});
		}
	}

	/** Молнии кольца срываются с края свода. */
	private static double radiusAt(int n) {
		return 20.0 + (n % 3) * 2.0;
	}
}
