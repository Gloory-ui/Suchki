package com.suchki.dev;

import static com.suchki.dev.SpellTestKit.arena;
import static com.suchki.dev.SpellTestKit.clean;
import static com.suchki.dev.SpellTestKit.dummy;
import static com.suchki.dev.SpellTestKit.face;
import static com.suchki.dev.SpellTestKit.far;
import static com.suchki.dev.SpellTestKit.fill;
import static com.suchki.dev.SpellTestKit.griefing;
import static com.suchki.dev.SpellTestKit.hitOn;
import static com.suchki.dev.SpellTestKit.hp;
import static com.suchki.dev.SpellTestKit.hurt;
import static com.suchki.dev.SpellTestKit.mage;
import static com.suchki.dev.SpellTestKit.run;
import static com.suchki.dev.SpellTestKit.solid;
import static com.suchki.dev.SpellTestKit.spell;

import com.suchki.magic.Magic;
import com.suchki.magic.MagicBlocks;
import com.suchki.magic.Minds;
import com.suchki.magic.SpellProjectile;
import com.suchki.magic.spells.LightSpells;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Проверки заклинаний стихии света: огоньки, кара, щит, вспышка, благословение, луч, святилище, суд, клинок и падение небес. */
final class LightSpellTests {
	private LightSpellTests() {}

	/** Площадки, чьи чанки держим загруженными, пока идёт проверка (иначе мишени у края площадки «не видны»). */
	private static final List<BlockPos> FORCED = new ArrayList<>();

	static void addTo(Map<String, DevTests.Test> tests) {
		add(tests, "Свеча Странника", LightSpellTests::candle);
		add(tests, "Кара", LightSpellTests::smite);
		add(tests, "Шестигранный Покров", LightSpellTests::ward);
		add(tests, "Солнечная Вспышка", LightSpellTests::flash);
		add(tests, "Благословение Рассвета", LightSpellTests::blessing);
		add(tests, "Солнечный Луч", LightSpellTests::sunbeam);
		add(tests, "Святилище", LightSpellTests::sanctuary);
		add(tests, "Небесный Суд", LightSpellTests::judgement);
		add(tests, "Клинок Обетованного Рассвета", LightSpellTests::dawnblade);
		add(tests, "Падение Небес", LightSpellTests::heavenfall);
	}

	private static void add(Map<String, DevTests.Test> tests, String name, DevTests.Test test) {
		tests.put("Магия: Свет — " + name, (level, o, r) -> {
			try {
				test.run(level, o, r);
			} finally {
				for (BlockPos c : FORCED) load(level, c, false);
				FORCED.clear();
			}
		});
	}

	/** Держать (или отпустить) чанки вокруг площадки на 24 блока и дождаться их загрузки. */
	private static void load(ServerLevel level, BlockPos c, boolean on) {
		int r = 24;
		for (int cx = (c.getX() - r) >> 4; cx <= (c.getX() + r) >> 4; cx++) {
			for (int cz = (c.getZ() - r) >> 4; cz <= (c.getZ() + r) >> 4; cz++) {
				level.setChunkForced(cx, cz, on);
				if (on) level.getChunk(cx, cz);
			}
		}
		if (!on) return;
		long until = System.nanoTime() + 40_000_000L;
		while (System.nanoTime() < until) {
			int n = 0;
			while (n < 10_000 && level.getChunkSource().pollTask()) n++;
			Thread.onSpinWait();
		}
	}

	private static float lost(LivingEntity e) {
		return e.getMaxHealth() - e.getHealth();
	}

	/** Убрать мишени, снаряды, следы заклинаний и состояние мага. */
	private static void tidy(ServerLevel level, BlockPos at, FakePlayer p) {
		clean();
		LightSpells.recover(p);
		p.setShiftKeyDown(false);
		p.removeAttached(Magic.CASTING);
		level.getEntitiesOfClass(Entity.class, new AABB(at).inflate(48), e -> !(e instanceof Player)).forEach(Entity::discard);
	}

	private static BlockPos spot(ServerLevel level, BlockPos o, int i) {
		BlockPos c = far(o, 240 + i);
		load(level, c, true);
		FORCED.add(c);
		return c;
	}

	// --- Ранг 1 ---

	private static void candle(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = spot(level, o, 0);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			long now = level.getGameTime();
			Zombie z = dummy(level, EntityType.ZOMBIE, at.offset(-1, 0, 0));
			Magic.castNow(p, spell("candlelight"), 1.0F);
			r.check(MagicBlocks.candleUntil(p) >= now + 3000, "Свеча: огонёк следует за магом 3 минуты (до " + (MagicBlocks.candleUntil(p) - now) + " тиков)");
			run(40);
			r.check(hurt(z) && lost(z) <= 2.5F, "Свеча: нежить у огонька дымится — 1 урона за 2 с (" + hp(z) + ")");
			tidy(level, at, p);

			// С Shift — неподвижный огонёк у стены, куда смотрит маг; свет держится.
			level.setBlock(at.offset(0, 1, 8), Blocks.STONE.defaultBlockState(), 2);
			p.setShiftKeyDown(true);
			Magic.castNow(p, spell("candlelight"), 1.0F);
			run(5);
			boolean lit = level.getBlockState(at.offset(0, 1, 7)).is(MagicBlocks.MAGIC_LIGHT);
			r.check(lit && LightSpells.orbs(p) == 1, "Свеча с Shift: неподвижный свет у стены в 7 блоках (" + lit + ", огоньков "
					+ LightSpells.orbs(p) + ")");
			for (int i = 0; i < 9; i++) Magic.castNow(p, spell("candlelight"), 1.0F);
			r.check(LightSpells.orbs(p) == 8, "Свеча: не больше 8 неподвижных огоньков (" + LightSpells.orbs(p) + ")");
			tidy(level, at, p);

			Magic.castNow(p, spell("candlelight"), 1.0F, 3);
			r.check(LightSpells.orbs(p) == 2 && MagicBlocks.candleUntil(p) > now, "Свеча ×3: один огонёк следует, два висят вокруг ("
					+ LightSpells.orbs(p) + ")");
			level.setBlock(at.offset(0, 1, 8), Blocks.AIR.defaultBlockState(), 2);
		} finally {
			tidy(level, at, p);
		}
	}

	private static int projectiles(ServerLevel level, BlockPos at) {
		return level.getEntitiesOfClass(SpellProjectile.class, new AABB(at).inflate(16)).size();
	}

	private static void smite(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = spot(level, o, 1);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			Zombie z = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 6));
			Mob sp = dummy(level, EntityType.PILLAGER, at.offset(3, 0, 6));
			Magic.hitNow(p, spell("smite"), hitOn(z), 1.0F);
			Magic.hitNow(p, spell("smite"), hitOn(sp), 1.0F);
			r.check(lost(z) >= 12.0F && z.isOnFire(), "Кара: нежить — 12 урона и горит (" + hp(z) + ")");
			r.check(lost(sp) >= 4.9F && lost(sp) < 8.0F && !sp.isOnFire(), "Кара: живой враг — 5 урона (" + hp(sp) + ")");
			tidy(level, at, p);

			int before = projectiles(level, at);
			Magic.castNow(p, spell("smite"), 1.0F);
			int one = projectiles(level, at) - before;
			Magic.castNow(p, spell("smite"), 1.0F, 3);
			int three = projectiles(level, at) - before - one;
			r.check(one == 1 && three == 3, "Кара: один сгусток, мультикаст ×3 — три (" + one + ", " + three + ")");
		} finally {
			tidy(level, at, p);
		}
	}

	// --- Ранг 2 ---

	private static void ward(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = spot(level, o, 2);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			Magic.channelNow(p, spell("ward"), 3, 1.0F);
			float pool = LightSpells.wardPool(p);
			r.check(pool >= 29.0F && !LightSpells.wardDome(p), "Покров: щит спереди с запасом 30 (" + pool + ")");

			// Стрела скелета у щита сгорает.
			Mob sk = dummy(level, EntityType.SKELETON, at.offset(0, 0, 10));
			Projectile arrow = DevTests.spawn(level, EntityType.ARROW, at.offset(0, 1, 2));
			arrow.setOwner(sk);
			Projectile own = DevTests.spawn(level, EntityType.ARROW, at.offset(1, 1, 2));
			own.setOwner(p);
			Magic.channelNow(p, spell("ward"), 1, 1.0F);
			r.check(arrow.isRemoved() && !own.isRemoved() && LightSpells.wardPool(p) < pool,
					"Покров: чужая стрела у щита сгорает, своя летит дальше (запас " + LightSpells.wardPool(p) + ")");
			own.discard();

			// Удар спереди: щит гасит 70% и платит из запаса.
			p.setAttached(Magic.CASTING, "ward");
			Zombie front = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 3));
			DamageSource hit = level.damageSources().mobAttack(front);
			float a = LightSpells.wardPool(p);
			boolean through = LightSpells.wardAbsorb(p, hit, 10.0F);
			float b = LightSpells.wardPool(p);
			float left = Magic.ward(p, hit, 10.0F);
			r.check(through && Math.abs(a - b - 7.0F) < 0.01F && left <= 3.01F,
					"Покров: удар 10 спереди — проходит 3, запас −7 (" + left + ", " + a + " → " + b + ")");

			// С Shift — купол: удар сзади глотает целиком.
			p.setShiftKeyDown(true);
			Magic.channelNow(p, spell("ward"), 1, 1.0F);
			Zombie back = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, -3));
			float d0 = LightSpells.wardPool(p);
			boolean passed = LightSpells.wardAbsorb(p, level.damageSources().mobAttack(back), 8.0F);
			r.check(LightSpells.wardDome(p) && d0 >= 19.0F && !passed && LightSpells.wardPool(p) < d0 - 7.9F,
					"Покров с Shift: купол (запас " + d0 + ") глотает удар сзади");

			// Слишком сильный удар разбивает Покров; поднять его сразу нельзя.
			LightSpells.wardAbsorb(p, hit, 100.0F);
			float broken = LightSpells.wardPool(p);
			Magic.channelNow(p, spell("ward"), 1, 1.0F);
			r.check(broken == 0 && LightSpells.wardPool(p) == 0, "Покров: разбит сильным ударом и 3 с не поднимается");
		} finally {
			tidy(level, at, p);
		}
	}

	private static void flash(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = spot(level, o, 3);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			Zombie seen = dummy(level, EntityType.ZOMBIE, at.offset(3, 0, 5));
			Zombie hidden = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, -6));
			for (int dx = -2; dx <= 2; dx++) {
				for (int dy = 0; dy <= 3; dy++) level.setBlock(at.offset(dx, dy, -3), Blocks.STONE.defaultBlockState(), 2);
			}
			Mob villager = dummy(level, EntityType.VILLAGER, at.offset(-3, 0, 4));
			Zombie far = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 14));
			Magic.castNow(p, spell("flash"), 1.0F);
			r.check(seen.hasEffect(MobEffects.BLINDNESS) && seen.hasEffect(MobEffects.SLOWNESS) && Minds.modeOf(seen) == Minds.Mode.CALM
					&& lost(seen) >= 4.5F, "Вспышка: враг ослеп, замедлен, забыл цель; нежить — 5 урона (" + hp(seen) + ", "
							+ seen.hasEffect(MobEffects.BLINDNESS) + ", " + seen.hasEffect(MobEffects.SLOWNESS) + ", " + Minds.modeOf(seen) + ")");
			r.check(!hidden.hasEffect(MobEffects.BLINDNESS) && !villager.hasEffect(MobEffects.BLINDNESS) && !far.hasEffect(MobEffects.BLINDNESS),
					"Вспышка: за стеной, свои и дальше 12 блоков — не задеты");
			Magic.castNow(p, spell("flash"), 1.0F, 2);
			r.check(far.hasEffect(MobEffects.BLINDNESS), "Вспышка ×2: вторая вспышка шире (+3 блока) — ослеп и враг в 14 блоках");
			for (int dx = -2; dx <= 2; dx++) {
				for (int dy = 0; dy <= 3; dy++) level.setBlock(at.offset(dx, dy, -3), Blocks.AIR.defaultBlockState(), 2);
			}
		} finally {
			tidy(level, at, p);
		}
	}

	// --- Ранг 3 ---

	private static void blessing(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = spot(level, o, 4);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			p.removeAllEffects();
			Mob near = dummy(level, EntityType.VILLAGER, at.offset(3, 0, 3));
			Mob far = dummy(level, EntityType.VILLAGER, at.offset(0, 0, 11));
			Zombie z = dummy(level, EntityType.ZOMBIE, at.offset(-3, 0, 3));
			Magic.castNow(p, spell("blessing"), 1.0F);
			r.check(near.hasEffect(MobEffects.STRENGTH) && near.hasEffect(MobEffects.RESISTANCE) && p.hasEffect(MobEffects.STRENGTH)
					&& p.hasEffect(MobEffects.RESISTANCE), "Благословение: Сила и Сопротивление магу и союзнику рядом");
			r.check(!z.hasEffect(MobEffects.STRENGTH) && !far.hasEffect(MobEffects.STRENGTH) && !p.hasEffect(MobEffects.LUCK),
					"Благословение: врагу и союзнику в 11 блоках — ничего; при ×1 без Удачи");
			Magic.castNow(p, spell("blessing"), 1.3F, 2);
			r.check(far.hasEffect(MobEffects.STRENGTH) && p.hasEffect(MobEffects.LUCK),
					"Благословение ×2 (накопление ×1,3): радиус 12 — союзник в 11 блоках, и Удача");
		} finally {
			p.removeAllEffects();
			tidy(level, at, p);
		}
	}

	private static void sunbeam(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = spot(level, o, 5);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			Zombie z = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 10));
			Magic.channelNow(p, spell("sunbeam"), 40, 1.0F);
			r.check(lost(z) >= 28.0F && z.isOnFire(), "Солнечный Луч: 2 с по нежити — 4 удара по 7,5 и поджог (" + hp(z) + ")");
			tidy(level, at, p);

			Mob raider = dummy(level, EntityType.PILLAGER, at.offset(0, 0, 10));
			Magic.channelNow(p, spell("sunbeam"), 60, 1.0F);
			r.check(lost(raider) >= 19.5F, "Солнечный Луч: разгорается — 4×3 за 2 с, потом по 4 (" + hp(raider) + ", ждём 20)");
			tidy(level, at, p);

			Zombie a = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 10));
			Zombie b = dummy(level, EntityType.ZOMBIE, at.offset(4, 0, 10));
			Magic.channelNow(p, spell("sunbeam"), 20, 1.0F, 2);
			r.check(hurt(a) && hurt(b), "Солнечный Луч ×2: второй луч бьёт следующего врага (" + hp(a) + ", " + hp(b) + ")");
			tidy(level, at, p);

			// Конец луча плавит стекло — только при включённом разрушении.
			for (boolean on : new boolean[] {true, false}) {
				level.setBlock(at.offset(0, 1, 12), Blocks.GLASS.defaultBlockState(), 2);
				griefing(on, () -> {
					Magic.channelNow(p, spell("sunbeam"), 10, 1.0F);
					run(1);
				});
				boolean glass = level.getBlockState(at.offset(0, 1, 12)).is(Blocks.GLASS);
				r.check(on != glass, "Солнечный Луч: стекло на конце луча " + (on ? "плавится" : "цело без разрушения"));
				tidy(level, at, p);
			}
			level.setBlock(at.offset(0, 1, 12), Blocks.AIR.defaultBlockState(), 2);
		} finally {
			tidy(level, at, p);
		}
	}

	// --- Ранг 4 ---

	private static void sanctuary(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = spot(level, o, 6);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			Mob villager = dummy(level, EntityType.VILLAGER, at.offset(2, 0, 2));
			Zombie z = dummy(level, EntityType.ZOMBIE, at.offset(3, 0, 0));
			Mob sk = dummy(level, EntityType.SKELETON, at.offset(0, 0, 20));
			Projectile arrow = DevTests.spawn(level, EntityType.ARROW, at.offset(0, 1, 3));
			arrow.setOwner(sk);
			Magic.castNow(p, spell("sanctuary"), 1.0F);
			run(10);
			r.check(villager.hasEffect(MobEffects.REGENERATION) && p.hasEffect(MobEffects.REGENERATION), "Святилище: свои регенерируют");
			r.check(lost(z) >= 1.9F && z.isOnFire() && z.getDeltaMovement().horizontalDistance() > 0.05,
					"Святилище: нежить горит, получает 2 урона и выталкивается (" + hp(z) + ")");
			r.check(arrow.isRemoved(), "Святилище: чужая стрела внутри сгорает");
			tidy(level, at, p);

			Zombie edge = dummy(level, EntityType.ZOMBIE, at.offset(7, 0, 0));
			Magic.castNow(p, spell("sanctuary"), 1.0F, 2);
			run(10);
			r.check(hurt(edge), "Святилище ×2: радиус 8 — задет и враг в 7 блоках (" + hp(edge) + ")");
		} finally {
			p.removeAllEffects();
			tidy(level, at, p);
		}
	}

	// --- Ранг 5 ---

	private static void judgement(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = spot(level, o, 7);
		FakePlayer p = mage(level, at);
		face(p, at, 15);
		try {
			Zombie z = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 6));
			Mob villager = dummy(level, EntityType.VILLAGER, at.offset(1, 0, 6));
			Magic.castNow(p, spell("judgement"), 1.0F);
			run(13);
			r.check(!hurt(z), "Небесный Суд: пока рисуется круг — урона нет");
			run(3);
			float pillar = lost(z);
			r.check(pillar >= 41.0F && z.hasEffect(MobEffects.BLINDNESS), "Небесный Суд: столп — 28×1,5 нежити и слепота (" + hp(z) + ")");
			run(40);
			r.check(lost(z) > pillar && !hurt(villager), "Небесный Суд: дождь стрел добивает, союзник цел (" + hp(z) + ", " + hp(villager) + ")");
			tidy(level, at, p);
			arena(level, at, 14, 8);

			Zombie a = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 6));
			Zombie b = dummy(level, EntityType.ZOMBIE, at.offset(5, 0, 9));
			Zombie c = dummy(level, EntityType.ZOMBIE, at.offset(-5, 0, 9));
			Magic.castNow(p, spell("judgement"), 1.0F, 3);
			run(20);
			r.check(lost(a) >= 41.0F && lost(b) >= 24.0F && lost(c) >= 24.0F,
					"Небесный Суд ×3: ещё два столпа (×0,6) по соседним врагам (" + hp(a) + ", " + hp(b) + ", " + hp(c) + ")");
		} finally {
			tidy(level, at, p);
		}
		for (boolean on : new boolean[] {true, false}) {
			BlockPos g = spot(level, o, on ? 8 : 9);
			FakePlayer q = mage(level, g);
			face(q, g, 15);
			try {
				BlockPos floor = g.offset(0, -1, 6);
				int before = solid(level, floor, 2);
				griefing(on, () -> {
					Magic.castNow(q, spell("judgement"), 1.0F);
					run(20);
				});
				int after = solid(level, floor, 2);
				r.check(on ? after < before - 5 : after == before, "Небесный Суд: воронка " + (on ? "оплавлена" : "не появляется без разрушения")
						+ " (" + before + " → " + after + ")");
			} finally {
				tidy(level, g, q);
			}
		}
	}

	// --- Ранг 6 ---

	private static void dawnblade(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = spot(level, o, 10);
		FakePlayer p = mage(level, at);
		arena(level, at, 24, 9);
		face(p, at, 0);
		try {
			Zombie z = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 12));
			Mob villager = dummy(level, EntityType.VILLAGER, at.offset(1, 0, 8));
			Zombie aside = dummy(level, EntityType.ZOMBIE, at.offset(8, 0, 8));
			Magic.castNow(p, spell("promised_dawnblade"), 1.0F);
			run(20);
			r.check(lost(z) >= 70.0F, "Клинок Рассвета: волна — 48×1,5 нежити (" + hp(z) + ")");
			r.check(!hurt(villager) && !hurt(aside), "Клинок Рассвета: союзник в волне и враг в 8 блоках сбоку целы");
			tidy(level, at, p);

			Zombie mid = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 18));
			Zombie left = dummy(level, EntityType.ZOMBIE, at.offset(-5, 0, 18));
			Zombie right = dummy(level, EntityType.ZOMBIE, at.offset(5, 0, 18));
			Magic.castNow(p, spell("promised_dawnblade"), 1.0F, 3);
			run(30);
			r.check(hurt(mid) && hurt(left) && hurt(right), "Клинок Рассвета ×3: веер из трёх волн ±15° (" + hp(left) + ", " + hp(mid) + ", "
					+ hp(right) + ")");
		} finally {
			tidy(level, at, p);
		}
		for (boolean on : new boolean[] {true, false}) {
			BlockPos g = spot(level, o, on ? 11 : 12);
			FakePlayer q = mage(level, g);
			face(q, g, 0);
			try {
				BlockPos floor = g.offset(0, -1, 8);
				int before = solid(level, floor, 1);
				griefing(on, () -> {
					Magic.castNow(q, spell("promised_dawnblade"), 1.0F);
					run(30);
				});
				int after = solid(level, floor, 1);
				r.check(on ? after < before - 3 : after == before, "Клинок Рассвета: траншея " + (on ? "прорезана" : "не появляется без разрушения")
						+ " (" + before + " → " + after + ")");
			} finally {
				tidy(level, g, q);
			}
		}
	}

	// --- Ранг 7 ---

	private static void heavenfall(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = spot(level, o, 13);
		FakePlayer p = mage(level, at);
		arena(level, at, 24, 9);
		face(p, at, 5);
		try {
			Zombie z = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 18));
			Zombie z2 = dummy(level, EntityType.ZOMBIE, at.offset(6, 0, 18));
			Mob villager = dummy(level, EntityType.VILLAGER, at.offset(-4, 0, 18));
			Zombie ring = dummy(level, EntityType.ZOMBIE, at.offset(15, 0, 18));
			Zombie marked = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, -20));
			Magic.castNow(p, spell("fallen_down"), 1.0F);
			r.check(marked.hasEffect(MobEffects.GLOWING), "Падение Небес: враги в 48 блоках помечены сиянием");
			run(29);
			r.check(!hurt(z), "Падение Небес: пока спускаются ореолы — урона нет");
			run(12);
			r.check(z2.hasEffect(MobEffects.LEVITATION) && hurt(z2), "Падение Небес: столп бьёт и поднимает врагов (" + hp(z2) + ")");
			run(90);
			r.check(!z.isAlive() || lost(z) >= 150.0F, "Падение Небес: 8×20 (нежить ×1,5) — враг в столпе повержен (" + hp(z) + ")");
			r.check(!hurt(villager), "Падение Небес: союзник в столпе цел (" + hp(villager) + ")");
			r.check(lost(ring) < 15.0F && ring.getDeltaMovement().horizontalDistance() > 0.1,
					"Падение Небес: враг за столпом не судим, а вытолкнут (" + hp(ring) + ")");
		} finally {
			tidy(level, at, p);
		}
		for (boolean on : new boolean[] {true, false}) {
			BlockPos g = spot(level, o, on ? 14 : 15);
			FakePlayer q = mage(level, g);
			arena(level, g, 24, 9);
			face(q, g, 5);
			try {
				BlockPos deep = g.offset(0, -5, 18);
				fill(level, deep, 2);
				int before = solid(level, deep, 2);
				griefing(on, () -> {
					Magic.castNow(q, spell("fallen_down"), 1.0F);
					run(140);
				});
				int after = solid(level, deep, 2);
				r.check(on ? after < before / 2 : after == before, "Падение Небес: шахта " + (on ? "прожжена вглубь" : "не появляется без разрушения")
						+ " (" + before + " → " + after + ")");
			} finally {
				tidy(level, g, q);
			}
		}
	}
}
