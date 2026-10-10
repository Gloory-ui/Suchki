package com.suchki.dev;

import static com.suchki.dev.SpellTestKit.clean;
import static com.suchki.dev.SpellTestKit.dummy;
import static com.suchki.dev.SpellTestKit.face;
import static com.suchki.dev.SpellTestKit.far;
import static com.suchki.dev.SpellTestKit.griefing;
import static com.suchki.dev.SpellTestKit.hitOn;
import static com.suchki.dev.SpellTestKit.hp;
import static com.suchki.dev.SpellTestKit.hurt;
import static com.suchki.dev.SpellTestKit.mage;
import static com.suchki.dev.SpellTestKit.run;
import static com.suchki.dev.SpellTestKit.solid;
import static com.suchki.dev.SpellTestKit.spell;

import com.suchki.magic.Magic;
import com.suchki.magic.SpellProjectile;
import com.suchki.magic.Zones;
import com.suchki.magic.spells.WindSpells;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Проверки заклинаний стихии ветра: порыв и рывок, ладонь с отражением стрел, серпы с кровотечением и срезанной
 * листвой, полёт, вихревая сфера, смерчи (затягивание, подъём, финальный бросок, сорванные блоки), сюрикэн и
 * Дыхание Штормового Дракона с тоннелем.
 */
final class WindSpellTests {
	private WindSpellTests() {}

	static void addTo(Map<String, DevTests.Test> tests) {
		tests.put("Магия: Ветер — Восходящий Зефир и Ладонь Ветра", WindSpellTests::zephyrAndPalm);
		tests.put("Магия: Ветер — Незримые Серпы", WindSpellTests::sickles);
		tests.put("Магия: Ветер — Тропа Ветров", WindSpellTests::fly);
		tests.put("Магия: Ветер — Вихревая Сфера", WindSpellTests::spiralSphere);
		tests.put("Магия: Ветер — Воющий Смерч", WindSpellTests::cyclone);
		tests.put("Магия: Ветер — Вихревой Сюрикэн", WindSpellTests::shuriken);
		tests.put("Магия: Ветер — Небесные Жернова", WindSpellTests::maelstrom);
		tests.put("Магия: Ветер — Дыхание Штормового Дракона", WindSpellTests::dragonBreath);
	}

	/** Площадки, чьи чанки держим загруженными на время проверки. */
	private static final List<BlockPos> FORCED = new ArrayList<>();

	/**
	 * Отдельная площадка номер {@code i} с загруженными чанками: у ненастоящего игрока их никто не держит, и поиск
	 * существ их не видит, пока сервер не прогонит отложенные задачи — прогоняем сразу.
	 */
	private static BlockPos spot(ServerLevel level, BlockPos o, int i) {
		BlockPos c = far(o, i);
		FORCED.add(c);
		load(level, c, true);
		return c;
	}

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

	/** Отпустить чанки всех площадок проверки. */
	private static void release(ServerLevel level) {
		for (BlockPos c : FORCED) load(level, c, false);
		FORCED.clear();
	}

	private static float lost(LivingEntity e) {
		return e.getMaxHealth() - e.getHealth();
	}

	private static double flat(Vec3 v) {
		return Math.hypot(v.x, v.z);
	}

	/** Убрать мишени, снаряды, обломки и следы заклинаний вокруг площадки. */
	private static void tidy(ServerLevel level, BlockPos at, FakePlayer p) {
		clean();
		WindSpells.land(p);
		p.removeAllEffects();
		p.setShiftKeyDown(false);
		p.setDeltaMovement(Vec3.ZERO);
		level.getEntitiesOfClass(Entity.class, new AABB(at).inflate(48), e -> !(e instanceof Player)).forEach(Entity::discard);
	}

	private static int projectiles(ServerLevel level, BlockPos at) {
		return level.getEntitiesOfClass(SpellProjectile.class, new AABB(at).inflate(16)).size();
	}

	private static int count(ServerLevel level, BlockPos c, int r, Block b) {
		int n = 0;
		for (BlockPos q : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) if (level.getBlockState(q).is(b)) n++;
		return n;
	}

	private static void put(ServerLevel level, BlockPos c, int rx, int ry, int rz, Block b) {
		for (BlockPos q : BlockPos.betweenClosed(c.offset(-rx, -ry, -rz), c.offset(rx, ry, rz))) level.setBlock(q, b.defaultBlockState(), 2);
	}

	// --- Ранг 1 ---

	private static void zephyrAndPalm(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = spot(level, o, 100);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			// Восходящий Зефир: подброс вверх, медленное падение и прыгучесть, враг у ног отлетает без урона.
			Zombie near = dummy(level, EntityType.ZOMBIE, at.offset(1, 0, 1));
			p.setDeltaMovement(Vec3.ZERO);
			Magic.castNow(p, spell("featherfall"), 1.0F);
			Vec3 v = p.getDeltaMovement();
			r.check(v.y >= 1.0 && p.hasEffect(MobEffects.SLOW_FALLING) && p.hasEffect(MobEffects.JUMP_BOOST),
					"Восходящий Зефир: подброс " + String.format("%.2f", v.y) + " (ждём ≥1,0), медленное падение и прыгучесть");
			r.check(flat(near.getDeltaMovement()) > 0.3 && !hurt(near), "Восходящий Зефир: враг у ног отброшен без урона ("
					+ String.format("%.2f", flat(near.getDeltaMovement())) + ")");
			tidy(level, at, p);

			// С Shift — рывок вперёд (на юг, +Z).
			p.setShiftKeyDown(true);
			Magic.castNow(p, spell("featherfall"), 1.0F);
			v = p.getDeltaMovement();
			r.check(v.z >= 1.3 && v.y < 0.6, "Восходящий Зефир с Shift: рывок вперёд (" + String.format("%.2f / %.2f", v.z, v.y) + ")");
			tidy(level, at, p);

			// Мультикаст ×3: каждая копия добавляет порыв.
			Magic.castNow(p, spell("featherfall"), 1.0F, 3);
			v = p.getDeltaMovement();
			r.check(v.y >= 1.7, "Восходящий Зефир ×3: копии добавляют подъём (" + String.format("%.2f", v.y) + ", ждём ≈1,8)");
			tidy(level, at, p);

			// Ладонь Ветра: враг в конусе — урон, отброс, огонь гаснет; позади и сбоку — никого.
			Zombie front = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 5));
			Zombie behind = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, -4));
			Zombie side = dummy(level, EntityType.ZOMBIE, at.offset(6, 0, 2));
			front.igniteForSeconds(5);
			Projectile arrow = DevTests.spawn(level, EntityType.ARROW, at.offset(1, 1, 4));
			arrow.setOwner(behind);
			arrow.setDeltaMovement(0, 0, -1.5);
			Magic.castNow(p, spell("gale_palm"), 1.0F);
			r.check(lost(front) >= 4.5F && front.getDeltaMovement().z >= 1.0 && !front.isOnFire(),
					"Ладонь Ветра: 5 урона, отброс вперёд и потушен (" + hp(front) + ", " + String.format("%.2f", front.getDeltaMovement().z) + ")");
			r.check(!hurt(behind) && !hurt(side), "Ладонь Ветра: позади и вне конуса никого (" + hp(behind) + ", " + hp(side) + ")");
			r.check(arrow.getDeltaMovement().z > 1.0 && arrow.getOwner() == p,
					"Ладонь Ветра: стрела развёрнута обратно и теперь «наша» (" + String.format("%.2f", arrow.getDeltaMovement().z) + ")");
			tidy(level, at, p);

			// Мультикаст ×3: каждая копия бьёт свою цель у прицела.
			Zombie a = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 5));
			Zombie b = dummy(level, EntityType.ZOMBIE, at.offset(3, 0, 6));
			Zombie c = dummy(level, EntityType.ZOMBIE, at.offset(-3, 0, 6));
			Magic.castNow(p, spell("gale_palm"), 1.0F, 3);
			run(10);
			r.check(hurt(a) && hurt(b) && hurt(c), "Ладонь Ветра ×3: задеты три цели (" + hp(a) + ", " + hp(b) + ", " + hp(c) + ")");
		} finally {
			tidy(level, at, p);
			release(level);
		}
	}

	// --- Ранг 2 ---

	private static void sickles(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = spot(level, o, 101);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			// Насквозь: две цели на одной линии, сбоку и позади — целы; кровотечение.
			Zombie a = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 5));
			Zombie b = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 12));
			Zombie side = dummy(level, EntityType.ZOMBIE, at.offset(9, 0, 6));
			Zombie behind = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, -5));
			Magic.castNow(p, spell("zephyr_sickles"), 1.0F);
			run(8);
			r.check(lost(a) >= 4.5F && lost(b) >= 4.5F && WindSpells.bleeding(a) && WindSpells.bleeding(b),
					"Незримые Серпы: насквозь — по 5 урона двум целям и кровотечение (" + hp(a) + ", " + hp(b) + ")");
			r.check(!hurt(side) && !hurt(behind), "Незримые Серпы: сбоку и позади никого (" + hp(side) + ", " + hp(behind) + ")");
			float was = a.getHealth();
			run(40);
			r.check(a.getHealth() < was && !WindSpells.bleeding(a), "Незримые Серпы: кровотечение отнимает здоровье и проходит ("
					+ hp(a) + ")");
			tidy(level, at, p);

			// Мультикаст ×3: копии бьют разные цели у прицела.
			Zombie m1 = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 10));
			Zombie m2 = dummy(level, EntityType.ZOMBIE, at.offset(5, 0, 12));
			Zombie m3 = dummy(level, EntityType.ZOMBIE, at.offset(-5, 0, 12));
			Magic.castNow(p, spell("zephyr_sickles"), 1.0F, 3);
			run(12);
			r.check(hurt(m1) && hurt(m2) && hurt(m3), "Незримые Серпы ×3: задеты три цели (" + hp(m1) + ", " + hp(m2) + ", " + hp(m3) + ")");
			tidy(level, at, p);

			// Листва на пути: с разрушением срезается, без — цела; камень за ней цел всегда.
			BlockPos wall = at.offset(0, 1, 9);
			BlockPos stone = at.offset(0, 1, 12);
			for (boolean grief : new boolean[] {false, true}) {
				put(level, wall, 1, 1, 0, Blocks.OAK_LEAVES);
				put(level, stone, 1, 1, 0, Blocks.STONE);
				int before = count(level, wall, 1, Blocks.OAK_LEAVES);
				griefing(grief, () -> {
					Magic.castNow(p, spell("zephyr_sickles"), 1.0F);
					run(20);
				});
				int after = count(level, wall, 1, Blocks.OAK_LEAVES);
				int rock = count(level, stone, 1, Blocks.STONE);
				r.check(grief ? after < before && rock == 9 : after == before,
						"Незримые Серпы " + (grief ? "с разрушением: листва срезана, камень цел" : "без разрушения: листва цела") + " (листвы "
								+ before + " → " + after + ", камня " + rock + "/9)");
				tidy(level, at, p);
				put(level, wall, 1, 1, 0, Blocks.AIR);
				put(level, stone, 1, 1, 0, Blocks.AIR);
			}
		} finally {
			tidy(level, at, p);
			release(level);
		}
	}

	// --- Ранг 3 ---

	private static void fly(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = spot(level, o, 102);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			p.setOnGround(false);
			Magic.castNow(p, spell("fly"), 1.0F);
			r.check(WindSpells.flying(p) && p.getAbilities().mayfly && p.getAbilities().flying
					&& Math.abs(p.getAbilities().getFlyingSpeed() - 0.06F) < 0.001F, "Тропа Ветров: полёт включён, скорость 0,06");

			// Сильный удар сбивает на 2 с, потом полёт возвращается.
			WindSpells.disrupt(p);
			r.check(!p.getAbilities().mayfly && p.hasEffect(MobEffects.SLOW_FALLING), "Тропа Ветров: удар сбивает с ветра (медленное падение)");
			run(41);
			r.check(p.getAbilities().mayfly && WindSpells.flying(p), "Тропа Ветров: через 2 с полёт возвращается");

			// В воздухе тратит 1 ману в секунду.
			float mana = Magic.mana(p);
			run(40);
			float spent = mana - Magic.mana(p);
			r.check(spent >= 1.5F && spent <= 2.5F, "Тропа Ветров: в воздухе 1 мана в секунду (за 2 с ушло " + String.format("%.1f", spent) + ")");

			// Конец времени — полёт снят, медленное падение.
			p.removeAllEffects();
			run(560);
			r.check(!WindSpells.flying(p) && !p.getAbilities().mayfly && p.hasEffect(MobEffects.SLOW_FALLING),
					"Тропа Ветров: через 30 с полёт кончается, остаётся медленное падение");
			tidy(level, at, p);

			// Мана кончилась — полёт обрывается раньше.
			p.setOnGround(false);
			Magic.castNow(p, spell("fly"), 1.0F);
			p.setAttached(Magic.MANA, 1.5F);
			run(41);
			r.check(!WindSpells.flying(p) && !p.getAbilities().mayfly, "Тропа Ветров: без маны полёт обрывается");
		} finally {
			tidy(level, at, p);
			release(level);
			p.setAttached(Magic.MANA, Magic.maxMana(p));
		}
	}

	private static void spiralSphere(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = spot(level, o, 103);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			// Рывок к цели, 0,5 с перемалывания и бросок вперёд.
			Zombie z = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 5));
			Zombie by = dummy(level, EntityType.ZOMBIE, at.offset(2, 0, 6));
			double z0 = p.getZ();
			Magic.castNow(p, spell("spiral_sphere"), 1.0F);
			r.check(p.getZ() - z0 >= 2.5, "Вихревая Сфера: рывок к цели (" + String.format("%.1f", p.getZ() - z0) + " блоков)");
			run(GRIND);
			r.check(lost(z) >= 19.0F && z.getDeltaMovement().z >= 1.8,
					"Вихревая Сфера: ≈21 урона и бросок вперёд (" + hp(z) + ", " + String.format("%.2f", z.getDeltaMovement().z) + ")");
			r.check(flat(by.getDeltaMovement()) > 0.3, "Вихревая Сфера: соседа отталкивает волна");
			tidy(level, at, p);

			// Никого впереди — взрыв в конце рывка без ошибок.
			face(p, at, 0);
			r.check(Magic.castNow(p, spell("spiral_sphere"), 1.0F), "Вихревая Сфера: без цели взрывается в конце рывка");
			tidy(level, at, p);

			// Мультикаст ×3: копии — самонаводящиеся шары; шар клона перемалывает цель на ×0,7.
			face(p, at, 0);
			int before = projectiles(level, at);
			Magic.castNow(p, spell("spiral_sphere"), 1.0F, 3);
			r.check(projectiles(level, at) - before == 2, "Вихревая Сфера ×3: два шара-клона (" + (projectiles(level, at) - before) + ")");
			tidy(level, at, p);
			face(p, at, 0);
			Zombie t = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 8));
			Magic.hitNow(p, spell("spiral_sphere"), hitOn(t), 0.7F);
			run(GRIND);
			r.check(lost(t) >= 13.0F && lost(t) < 19.0F, "Вихревая Сфера: шар клона ×0,7 (" + hp(t) + ", ждём ≈15)");
			tidy(level, at, p);

			// Воронка под целью: с разрушением — есть, без — пол цел.
			for (boolean grief : new boolean[] {false, true}) {
				BlockPos arena = spot(level, o, grief ? 104 : 105);
				FakePlayer q = mage(level, arena);
				face(q, arena, 0);
				Zombie g = dummy(level, EntityType.ZOMBIE, arena.offset(0, 0, 4));
				int before2 = solid(level, arena.offset(0, -1, 4), 2);
				griefing(grief, () -> {
					Magic.castNow(q, spell("spiral_sphere"), 1.0F);
					run(GRIND);
				});
				int after = solid(level, arena.offset(0, -1, 4), 2);
				r.check(grief ? after < before2 : after == before2, "Вихревая Сфера " + (grief ? "с разрушением: воронка под целью"
						: "без разрушения: пол цел") + " (" + before2 + " → " + after + ")");
				tidy(level, arena, q);
				g.discard();
			}
		} finally {
			tidy(level, at, p);
			release(level);
		}
	}

	private static final int GRIND = 12;

	// --- Ранг 4 ---

	/** Взгляд вниз так, чтобы прицел упал на пол примерно в 8 блоках впереди. */
	private static final float AIM_8 = 11.5F;

	private static void cyclone(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = spot(level, o, 106);
		FakePlayer p = mage(level, at);
		face(p, at, AIM_8);
		try {
			Zombie in = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 8));
			Zombie edge = dummy(level, EntityType.ZOMBIE, at.offset(6, 0, 8));
			Zombie away = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, -10));
			Magic.castNow(p, spell("howling_cyclone"), 1.0F);
			run(30);
			r.check(in.getDeltaMovement().y > 0.2 && lost(in) >= 8.0F, "Воющий Смерч: в воронке поднимает и бьёт (" + hp(in) + ", вверх "
					+ String.format("%.2f", in.getDeltaMovement().y) + ")");
			r.check(edge.getDeltaMovement().x < -0.05, "Воющий Смерч: врага в 6 блоках тянет к смерчу ("
					+ String.format("%.2f", edge.getDeltaMovement().x) + ")");
			r.check(!hurt(away) && away.getDeltaMovement().lengthSqr() < 0.01, "Воющий Смерч: дальних не трогает");
			run(135);
			r.check(lost(in) >= 45.0F && flat(in.getDeltaMovement()) >= 1.0, "Воющий Смерч: за 8 с ≈54 урона и в конце швыряет прочь ("
					+ hp(in) + ", " + String.format("%.2f", flat(in.getDeltaMovement())) + ")");
			tidy(level, at, p);

			// Мультикаст ×2: два смерча кружат вокруг прицела.
			face(p, at, AIM_8);
			int zones = Zones.active();
			Magic.castNow(p, spell("howling_cyclone"), 1.0F, 2);
			r.check(Zones.active() - zones >= 2, "Воющий Смерч ×2: два смерча (" + (Zones.active() - zones) + " зон)");
			tidy(level, at, p);

			// Сорванные блоки: шерсть и листва у основания — с разрушением исчезают, камень пола цел; без — всё цело.
			for (boolean grief : new boolean[] {false, true}) {
				BlockPos arena = spot(level, o, grief ? 107 : 108);
				FakePlayer q = mage(level, arena);
				face(q, arena, AIM_8);
				BlockPos base = arena.offset(0, 0, 8);
				put(level, base.offset(2, 0, 0), 0, 0, 1, Blocks.WHITE_WOOL);
				put(level, base.offset(-2, 0, 0), 0, 0, 1, Blocks.OAK_LEAVES);
				int soft = count(level, base, 3, Blocks.WHITE_WOOL) + count(level, base, 3, Blocks.OAK_LEAVES);
				int floor = solid(level, base.below(), 1);
				griefing(grief, () -> {
					Magic.castNow(q, spell("howling_cyclone"), 1.0F);
					run(25);
				});
				int left = count(level, base, 3, Blocks.WHITE_WOOL) + count(level, base, 3, Blocks.OAK_LEAVES);
				int floorAfter = solid(level, base.below(), 1);
				r.check(grief ? left < soft && floorAfter == floor : left == soft, "Воющий Смерч " + (grief ? "с разрушением: срывает шерсть и листву, пол цел"
						: "без разрушения: блоки целы") + " (" + soft + " → " + left + ", пол " + floor + " → " + floorAfter + ")");
				tidy(level, arena, q);
			}
		} finally {
			tidy(level, at, p);
			release(level);
		}
	}

	// --- Ранг 5 ---

	private static void shuriken(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = spot(level, o, 109);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			// Сфера лезвий радиусом 6: режет всех внутри 1,5 с, держит, кровотечение; дальше — никого.
			Zombie a = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 6));
			Zombie b = dummy(level, EntityType.ZOMBIE, at.offset(4, 0, 6));
			Zombie out = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 14));
			Magic.hitNow(p, spell("spiral_shuriken"), hitOn(a), 1.0F);
			run(10);
			r.check(lost(a) >= 4.0F && lost(a) < 12.0F && WindSpells.bleeding(b), "Вихревой Сюрикэн: сфера режет понемногу и пускает кровь ("
					+ hp(a) + ")");
			run(22);
			r.check(lost(a) >= 26.0F && lost(b) >= 26.0F, "Вихревой Сюрикэн: ≈28 урона всем в радиусе 6 (" + hp(a) + ", " + hp(b) + ")");
			r.check(!hurt(out), "Вихревой Сюрикэн: за сферой никого (" + hp(out) + ")");
			tidy(level, at, p);

			// Снаряд; мультикаст ×2 — два сюрикэна.
			int before = projectiles(level, at);
			Magic.castNow(p, spell("spiral_shuriken"), 1.0F);
			int one = projectiles(level, at) - before;
			Magic.castNow(p, spell("spiral_shuriken"), 1.0F, 2);
			int two = projectiles(level, at) - before - one;
			r.check(one == 1 && two == 2, "Вихревой Сюрикэн: один снаряд, ×2 — два (" + one + ", " + two + ")");
			tidy(level, at, p);

			// Кратер радиусом 4: с разрушением есть, без — пол цел.
			for (boolean grief : new boolean[] {false, true}) {
				BlockPos arena = spot(level, o, grief ? 110 : 111);
				FakePlayer q = mage(level, arena);
				BlockPos hit = arena.offset(0, -1, 8);
				int before2 = solid(level, hit, 4);
				griefing(grief, () -> {
					Magic.hitNow(q, spell("spiral_shuriken"), new BlockHitResult(Vec3.atCenterOf(hit).add(0, 0.5, 0), Direction.UP, hit, false), 1.0F);
					run(32);
				});
				int after = solid(level, hit, 4);
				r.check(grief ? before2 - after >= 30 : after == before2, "Вихревой Сюрикэн " + (grief ? "с разрушением: кратер" : "без разрушения: пол цел")
						+ " (" + before2 + " → " + after + ")");
				tidy(level, arena, q);
			}
		} finally {
			tidy(level, at, p);
			release(level);
		}
	}

	// --- Ранг 6 ---

	private static void maelstrom(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = spot(level, o, 112);
		FakePlayer p = mage(level, at);
		face(p, at, AIM_8);
		try {
			Zombie in = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 8));
			Zombie edge = dummy(level, EntityType.ZOMBIE, at.offset(10, 0, 8));
			Zombie away = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, -12));
			Magic.castNow(p, spell("sky_maelstrom"), 1.0F);
			run(14);
			r.check(!hurt(in), "Небесные Жернова: пока воронка спускается — урона нет");
			run(30);
			r.check(lost(in) >= 7.5F && in.getDeltaMovement().y > 0.15, "Небесные Жернова: в воронке поднимает и бьёт (" + hp(in) + ")");
			r.check(edge.getDeltaMovement().x < -0.05 && !hurt(edge), "Небесные Жернова: врага в 10 блоках тянет к оси ("
					+ String.format("%.2f", edge.getDeltaMovement().x) + ")");
			r.check(!hurt(away), "Небесные Жернова: дальних не трогает");
			run(185);
			r.check(lost(in) >= 100.0F, "Небесные Жернова: 10 с перемалывания и финал — ≥100 урона (" + hp(in) + ", ждём ≈110)");
			r.check(in.getDeltaMovement().y <= -2.0, "Небесные Жернова: в финале вбивает в землю ("
					+ String.format("%.2f", in.getDeltaMovement().y) + ")");
			tidy(level, at, p);

			// Мультикаст ×2: малый смерч кружит вокруг главного.
			face(p, at, AIM_8);
			int zones = Zones.active();
			Magic.castNow(p, spell("sky_maelstrom"), 1.0F, 2);
			r.check(Zones.active() - zones >= 2, "Небесные Жернова ×2: два смерча (" + (Zones.active() - zones) + " зон)");
			tidy(level, at, p);

			// Выдирает землю: с разрушением — земли меньше, камень цел; без — всё цело.
			for (boolean grief : new boolean[] {false, true}) {
				BlockPos arena = spot(level, o, grief ? 113 : 114);
				FakePlayer q = mage(level, arena);
				face(q, arena, AIM_8);
				BlockPos base = arena.offset(0, -1, 8);
				put(level, base, 3, 0, 3, Blocks.DIRT);
				int dirt = count(level, base, 3, Blocks.DIRT);
				griefing(grief, () -> {
					Magic.castNow(q, spell("sky_maelstrom"), 1.0F);
					run(40);
				});
				int left = count(level, base, 3, Blocks.DIRT);
				r.check(grief ? left < dirt : left == dirt, "Небесные Жернова " + (grief ? "с разрушением: земля выдрана" : "без разрушения: земля цела")
						+ " (" + dirt + " → " + left + ")");
				tidy(level, arena, q);
			}
		} finally {
			tidy(level, at, p);
			release(level);
		}
	}

	// --- Ранг 7 ---

	private static void dragonBreath(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = spot(level, o, 115);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			Zombie a = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 7));
			Zombie b = dummy(level, EntityType.ZOMBIE, at.offset(2, 0, 13));
			Zombie side = dummy(level, EntityType.ZOMBIE, at.offset(9, 0, 8));
			Zombie behind = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, -6));
			Magic.castNow(p, spell("storm_dragon_breath"), 1.0F);
			r.check(p.hasEffect(MobEffects.SLOWNESS), "Дыхание Дракона: маг прикован на всё дыхание");
			run(12);
			r.check(!hurt(a), "Дыхание Дракона: пока собирается голова и круги ствола — урона нет");
			run(70);
			r.check(lost(a) >= 120.0F && lost(b) >= 120.0F, "Дыхание Дракона: 3 с луча — ≈144 урона всем на линии (" + hp(a) + ", " + hp(b) + ")");
			r.check(a.getDeltaMovement().z >= 1.4, "Дыхание Дракона: несёт вдоль луча (" + String.format("%.2f", a.getDeltaMovement().z) + ")");
			r.check(!hurt(side) && !hurt(behind), "Дыхание Дракона: вне луча и позади никого (" + hp(side) + ", " + hp(behind) + ")");
			tidy(level, at, p);

			// Тоннель: камень на пути пробит, обсидиан цел; без разрушения — всё цело.
			for (boolean grief : new boolean[] {false, true}) {
				BlockPos arena = spot(level, o, grief ? 116 : 117);
				FakePlayer q = mage(level, arena);
				face(q, arena, 0);
				BlockPos rock = arena.offset(0, 1, 7);
				BlockPos obs = arena.offset(0, 1, 11);
				put(level, rock, 1, 1, 1, Blocks.STONE);
				level.setBlock(obs, Blocks.OBSIDIAN.defaultBlockState(), 2);
				int before = count(level, rock, 1, Blocks.STONE);
				griefing(grief, () -> {
					Magic.castNow(q, spell("storm_dragon_breath"), 1.0F);
					run(80);
				});
				int after = count(level, rock, 1, Blocks.STONE);
				boolean obsidian = level.getBlockState(obs).is(Blocks.OBSIDIAN);
				r.check(grief ? after <= before / 3 && obsidian : after == before && obsidian, "Дыхание Дракона " + (grief
						? "с разрушением: тоннель сквозь камень, обсидиан цел" : "без разрушения: камень цел") + " (" + before + " → " + after + ")");
				tidy(level, arena, q);
				put(level, rock, 1, 1, 1, Blocks.AIR);
				level.setBlock(obs, Blocks.AIR.defaultBlockState(), 2);
			}
		} finally {
			tidy(level, at, p);
			release(level);
		}
	}
}
