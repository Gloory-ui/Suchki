package com.suchki.dev;

import static com.suchki.dev.SpellTestKit.dummy;
import static com.suchki.dev.SpellTestKit.face;
import static com.suchki.dev.SpellTestKit.far;
import static com.suchki.dev.SpellTestKit.fill;
import static com.suchki.dev.SpellTestKit.griefing;
import static com.suchki.dev.SpellTestKit.hp;
import static com.suchki.dev.SpellTestKit.mage;
import static com.suchki.dev.SpellTestKit.run;
import static com.suchki.dev.SpellTestKit.solid;
import static com.suchki.dev.SpellTestKit.spell;

import com.suchki.magic.Cast;
import com.suchki.magic.Magic;
import com.suchki.magic.Minions;
import com.suchki.magic.Spell;
import com.suchki.magic.Terrain;
import com.suchki.magic.spells.SpaceSpells;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Проверки заклинаний Пространства: каждое заклинание творится на мишенях и делает то, что обещает описание. */
final class SpaceSpellTests {
	private SpaceSpellTests() {}

	private static final String P = "Магия: Пространство — ";

	static void addTo(Map<String, DevTests.Test> tests) {
		tests.put(P + "Длань Владыки", SpaceSpellTests::telekinesis);
		tests.put(P + "Скачок и двойники", SpaceSpellTests::blink);
		tests.put(P + "Метка Возвращения", SpaceSpellTests::recall);
		tests.put(P + "Переход Дверей", SpaceSpellTests::doors);
		tests.put(P + "Лазурь", SpaceSpellTests::lapseBlue);
		tests.put(P + "Багрянец", SpaceSpellTests::reversalRed);
		tests.put(P + "Вихрь Измерений", SpaceSpellTests::kamui);
		tests.put(P + "Мгновение, Застынь", SpaceSpellTests::timeStop);
		tests.put(P + "Рассечение Сущего", SpaceSpellTests::realitySlash);
		tests.put(P + "Опустошение Планеты", SpaceSpellTests::planetary);
	}

	private static double lost(LivingEntity e) {
		return e.getMaxHealth() - e.getHealth();
	}

	/** Мишень обычного здоровья (без 200 у {@link SpellTestKit#dummy}): её можно схватить или изгнать. */
	private static Zombie small(ServerLevel level, BlockPos at) {
		Zombie z = DevTests.spawn(level, EntityType.ZOMBIE, at);
		z.setPersistenceRequired();
		return z;
	}

	// --- Начальный ---

	private static void telekinesis(ServerLevel level, BlockPos o, DevTests.Report r) {
		FakePlayer p = mage(level, o);
		face(p, o, 0, 0);
		Spell s = spell("telekinesis");
		ItemEntity item = new ItemEntity(level, o.getX() + 0.5, o.getY() + 1.5, o.getZ() + 9.5, new ItemStack(Items.DIAMOND));
		level.addFreshEntity(item);
		Magic.channelNow(p, s, 4, 1.0F);
		r.check(item.getDeltaMovement().z < -0.2, "Длань Владыки: предмет на линии взгляда летит к магу (скорость " + item.getDeltaMovement() + ")");
		item.discard();
		run(1);

		Zombie z = small(level, o.offset(0, 0, 6));
		Magic.channelNow(p, s, 4, 1.0F);
		r.check(z.getDeltaMovement().z < -0.1, "Длань Владыки: мелкий моб схвачен и тянется к руке (скорость " + z.getDeltaMovement() + ")");
		// Поток кончился — бросок по взгляду, удар о стену — 4 урона пустотой.
		run(1);
		r.check(z.getDeltaMovement().z > 1.5, "Длань Владыки: отпустил — моб брошен вперёд (скорость " + z.getDeltaMovement() + ")");
		z.horizontalCollision = true;
		run(3);
		r.check(lost(z) >= 3.5, "Длань Владыки: брошенный моб разбивается о стену (" + hp(z) + ")");

		Zombie big = dummy(level, EntityType.ZOMBIE, o.offset(2, 0, 6));
		face(p, o, -18, 0);
		Magic.channelNow(p, s, 4, 1.0F);
		r.check(big.getDeltaMovement().lengthSqr() < 1.0E-4, "Длань Владыки: крупную цель (200 здоровья) не поднять");
		run(1);
	}

	// --- Средний ---

	private static void blink(ServerLevel level, BlockPos o, DevTests.Report r) {
		FakePlayer p = mage(level, o);
		face(p, o, 0, 10);
		Vec3 from = p.position();
		boolean ok = Magic.castNow(p, spell("blink"), 1.0F);
		double d = p.position().distanceTo(from);
		r.check(ok && d > 5 && d < 17 && p.getZ() > from.z, String.format("Скачок: маг перенёсся к прицелу на %.1f блока", d));

		face(p, o, 0, 10);
		Zombie foe = dummy(level, EntityType.ZOMBIE, o.offset(4, 0, -4));
		Magic.castNow(p, spell("blink"), 1.0F, 3);
		List<Mannequin> twins = level.getEntitiesOfClass(Mannequin.class, new AABB(from, from).inflate(4), m -> Minions.isOwnedBy(m, p));
		r.check(twins.size() == 2 && p.position().distanceTo(from) > 5,
				"Скачок ×3: маг перенёсся один раз, на месте ухода — 2 двойника мага (" + twins.size() + ")");
		r.check(foe.isAlive() && twins.stream().allMatch(m -> m.isCurrentlyGlowing()), "Скачок ×3: двойники светятся и отвлекают врагов");
	}

	// --- Продвинутый ---

	private static void recall(ServerLevel level, BlockPos o, DevTests.Report r) {
		FakePlayer p = mage(level, o);
		face(p, o, 0, 0);
		p.removeAttached(SpaceSpells.MARK);
		Spell s = spell("recall");
		p.setShiftKeyDown(true);
		boolean marked = Magic.castNow(p, s, 1.0F) && p.getAttached(SpaceSpells.MARK) != null;
		p.setShiftKeyDown(false);
		r.check(marked, "Метка Возвращения: с Shift ставится метка");
		face(p, o.offset(10, 0, 3), 0, 0);
		boolean back = Magic.castNow(p, s, 1.0F);
		Vec3 home = Vec3.atBottomCenterOf(o);
		r.check(back && p.position().distanceTo(home) < 1.0, String.format("Метка Возвращения: маг вернулся к метке (до неё %.1f)",
				p.position().distanceTo(home)));

		// Без метки — к точке возрождения (здесь — к точке появления мира).
		p.removeAttached(SpaceSpells.MARK);
		face(p, o, 0, 0);
		TeleportTransition spawn = p.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING);
		Vec3 dest = spawn.position();
		boolean ok = Magic.castNow(p, s, 1.0F);
		if (spawn.newLevel() == level && dest.distanceTo(home) < 2) {
			r.check(!ok, "Метка Возвращения: уже у точки возрождения — срыв");
		} else {
			// Точка появления мира каждый раз выбирается заново в квадрате появления (±10 блоков).
			r.check(ok && p.level() == spawn.newLevel() && p.position().distanceTo(home) > 2 && p.position().distanceTo(dest) < 30,
					String.format("Метка Возвращения: без метки — к точке возрождения (до неё %.1f)", p.position().distanceTo(dest)));
		}
		face(p, o, 0, 0);
	}

	private static void doors(ServerLevel level, BlockPos o, DevTests.Report r) {
		FakePlayer p = mage(level, o);
		face(p, o, 0, 10);
		Spell s = spell("door_crossing");
		boolean ok = Magic.castNow(p, s, 1.0F);
		Zombie z = dummy(level, EntityType.ZOMBIE, o.offset(0, 0, 2));
		run(2);
		r.check(ok && z.getZ() > o.getZ() + 6, String.format("Переход Дверей: моб вошёл в дверь перед магом и вышел у прицела (z %+.1f)",
				z.getZ() - o.getZ()));
		Arrow arrow = EntityType.ARROW.create(level, EntitySpawnReason.COMMAND);
		arrow.setPos(o.getX() + 0.5, o.getY() + 1.5, o.getZ() + 2.3);
		arrow.setDeltaMovement(0, 0, 1.5);
		level.addFreshEntity(arrow);
		run(1);
		r.check(arrow.getZ() > o.getZ() + 6 && arrow.getDeltaMovement().z > 1.0,
				String.format("Переход Дверей: стрела прошла насквозь и летит дальше (z %+.1f, скорость %.1f)", arrow.getZ() - o.getZ(),
						arrow.getDeltaMovement().z));
		arrow.discard();

		boolean closed = Magic.castNow(p, s, 1.0F);
		Zombie after = dummy(level, EntityType.ZOMBIE, o.offset(0, 0, 2));
		run(2);
		r.check(closed && after.getZ() < o.getZ() + 4, "Переход Дверей: повторный каст закрывает двери");

		// «Охотничьи угодья»: дверь под врагом, вторая — в 25 блоках над ним (прочие мишени убраны с линии прицела).
		z.discard();
		after.discard();
		Zombie prey = dummy(level, EntityType.ZOMBIE, o.offset(-3, 0, 7));
		face(p, o, 23, 5);
		p.setShiftKeyDown(true);
		boolean hunt = Magic.castNow(p, s, 1.0F);
		p.setShiftKeyDown(false);
		run(2);
		r.check(hunt && prey.getY() > o.getY() + 12, String.format("Переход Дверей (Shift): пол ушёл из-под врага — он падает с высоты %.0f",
				prey.getY() - o.getY()));
		run(70);
	}

	// --- Святой ---

	private static void lapseBlue(ServerLevel level, BlockPos o, DevTests.Report r) {
		FakePlayer p = mage(level, o);
		face(p, o, 0, 0);
		Spell s = spell("lapse_blue");
		Zombie core = dummy(level, EntityType.ZOMBIE, o.offset(0, 0, 8));
		Zombie side = dummy(level, EntityType.ZOMBIE, o.offset(5, 0, 8));
		ItemEntity loot = new ItemEntity(level, o.getX() - 6.5, o.getY() + 0.5, o.getZ() + 8.5, new ItemStack(Items.EMERALD));
		level.addFreshEntity(loot);
		boolean[] ok = new boolean[1];
		griefing(false, () -> {
			ok[0] = Magic.castNow(p, s, 1.0F);
			run(5);
		});
		r.check(ok[0] && side.getDeltaMovement().x < -0.02, "Лазурь: враг в 5 блоках тянется к ядру (скорость " + side.getDeltaMovement() + ")");
		griefing(false, () -> run(80));
		r.check(lost(core) >= 30, "Лазурь: в ядре — урон каждые 0,5 с и схлопывание (" + hp(core) + ")");
		r.check(lost(side) < 0.01, "Лазурь: дальний враг не ранен, только притянут (" + hp(side) + ")");
		r.check(loot.position().distanceTo(Cast.center(core)) < 2.5, "Лазурь: добыча стянута к ядру");
		loot.discard();

		// Разрушение: ядро у каменной стены.
		for (boolean on : new boolean[] {true, false}) {
			BlockPos f = far(o, on ? 0 : 1);
			FakePlayer q = mage(level, f);
			face(q, f, 0, 0);
			BlockPos wall = f.offset(0, 1, 8);
			fill(level, wall, 2);
			int before = solid(level, wall, 2);
			griefing(on, () -> {
				Magic.castNow(q, s, 1.0F);
				run(82);
			});
			int after = solid(level, wall, 2);
			r.check(on ? after < before : after == before, "Лазурь: схлопывание " + (on ? "ломает" : "не ломает (разрушение выкл.)")
					+ " камень (" + before + " → " + after + ")");
		}
	}

	// --- Королевский ---

	private static void reversalRed(ServerLevel level, BlockPos o, DevTests.Report r) {
		FakePlayer p = mage(level, o);
		face(p, o, 0, 0);
		Spell s = spell("reversal_red");
		Zombie a = dummy(level, EntityType.ZOMBIE, o.offset(0, 0, 5));
		Zombie b = dummy(level, EntityType.ZOMBIE, o.offset(0, 0, 9));
		boolean[] ok = new boolean[1];
		griefing(false, () -> {
			ok[0] = Magic.castNow(p, s, 1.0F);
			run(3);
		});
		r.check(ok[0] && lost(a) >= 25 && a.getDeltaMovement().z > 1.0,
				"Багрянец: первый на пути — 30 урона и отброшен (" + hp(a) + ", скорость " + a.getDeltaMovement() + ")");
		r.check(lost(b) >= 18 && lost(b) < lost(a), "Багрянец: второй — чуть слабее (" + hp(b) + ")");
		griefing(false, () -> run(30));

		for (boolean on : new boolean[] {true, false}) {
			BlockPos f = far(o, on ? 2 : 3);
			FakePlayer q = mage(level, f);
			face(q, f, 0, 0);
			BlockPos wall = f.offset(0, 1, 8);
			fill(level, wall, 2);
			int before = solid(level, wall, 2);
			griefing(on, () -> {
				Magic.castNow(q, s, 1.0F);
				run(40);
			});
			int after = solid(level, wall, 2);
			r.check(on ? after < before : after == before, "Багрянец: " + (on ? "прошивает" : "не ломает (разрушение выкл.)")
					+ " каменную стену (" + before + " → " + after + ")");
		}
	}

	private static void kamui(ServerLevel level, BlockPos o, DevTests.Report r) {
		FakePlayer p = mage(level, o);
		face(p, o, 0, 0);
		Spell s = spell("kamui");
		Zombie big = dummy(level, EntityType.ZOMBIE, o.offset(0, 0, 8));
		boolean ok = Magic.castNow(p, s, 1.0F);
		run(45);
		r.check(ok && lost(big) >= 20 && big.isAlive() && big.getY() > o.getY() + 1.5,
				String.format("Вихрь Измерений: крепкая цель — 26 урона и падает с высоты (%s, высота %+.1f)", hp(big), big.getY() - o.getY()));
		big.discard();

		// Израненный (60 из 200 — после вихря меньше 30%) изгоняется: исчезает у ног мага.
		Zombie weak = dummy(level, EntityType.ZOMBIE, o.offset(0, 0, 8));
		weak.setHealth(60);
		boolean ok2 = Magic.castNow(p, s, 1.0F);
		run(45);
		r.check(ok2 && !weak.isAlive() && weak.position().distanceTo(p.position()) < 2,
				String.format("Вихрь Измерений: израненный моб изгнан — исчез у ног мага (до мага %.1f)", weak.position().distanceTo(p.position())));

		// ×3: три вихря — три разные цели у прицела.
		List<Zombie> foes = List.of(dummy(level, EntityType.ZOMBIE, o.offset(0, 0, 9)), dummy(level, EntityType.ZOMBIE, o.offset(3, 0, 9)),
				dummy(level, EntityType.ZOMBIE, o.offset(-3, 0, 9)));
		Magic.castNow(p, s, 1.0F, 3);
		run(45);
		long hit = foes.stream().filter(z -> lost(z) >= 20).count();
		r.check(hit == 3, "Вихрь Измерений ×3: закручены 3 разные цели (" + hit + ")");
	}

	// --- Императорский ---

	private static void timeStop(ServerLevel level, BlockPos o, DevTests.Report r) {
		FakePlayer p = mage(level, o);
		face(p, o, 0, 0);
		Spell s = spell("time_stop");
		Zombie z = dummy(level, EntityType.ZOMBIE, o.offset(0, 0, 6));
		Arrow arrow = EntityType.ARROW.create(level, EntitySpawnReason.COMMAND);
		arrow.setPos(o.getX() + 4.5, o.getY() + 3, o.getZ() + 4.5);
		arrow.setDeltaMovement(0, 0, 1.2);
		level.addFreshEntity(arrow);
		boolean ok = Magic.castNow(p, s, 1.0F);
		r.check(ok && z.getTags().contains("suchki.time_stopped") && z.isNoGravity() && arrow.isNoGravity()
				&& arrow.getDeltaMovement().lengthSqr() < 1.0E-6, "Мгновение, Застынь: моб и стрела застыли");
		z.hurtServer(level, level.damageSources().generic(), 10.0F);
		r.check(z.getHealth() >= z.getMaxHealth(), "Мгновение, Застынь: удар по застывшему копится (" + hp(z) + ")");
		run(100);
		r.check(z.getTags().contains("suchki.time_stopped"), "Мгновение, Застынь: через 5 с время ещё стоит");
		run(25);
		r.check(!z.getTags().contains("suchki.time_stopped") && !z.isNoGravity() && z.isNoAi(),
				"Мгновение, Застынь: время пошло — тяжесть вернулась, свой ИИ (выключенный у мишени) не тронут");
		r.check(lost(z) > 9.0 && lost(z) <= 10.01, "Мгновение, Застынь: накопленный удар прошёл разом (" + hp(z) + ")");
		r.check(!arrow.isNoGravity() && arrow.getDeltaMovement().z > 1.0, "Мгновение, Застынь: стрела летит дальше");
		arrow.discard();

		// ×3: каждая копия продлевает остановку на 1,5 с — всего 9 с.
		Zombie z3 = dummy(level, EntityType.ZOMBIE, o.offset(3, 0, 6));
		Magic.castNow(p, s, 1.0F, 3);
		run(150);
		boolean still = z3.getTags().contains("suchki.time_stopped");
		run(35);
		r.check(still && !z3.getTags().contains("suchki.time_stopped"), "Мгновение, Застынь ×3: остановка длится 9 с");
	}

	private static void realitySlash(ServerLevel level, BlockPos o, DevTests.Report r) {
		FakePlayer p = mage(level, o);
		face(p, o, 0, 0);
		Spell s = spell("reality_slash");
		Vec3 hand = Cast.handOf(p);
		Zombie in = dummy(level, EntityType.ZOMBIE, o.offset(0, 0, 10));
		in.snapTo(hand.x, o.getY(), o.getZ() + 10.5, 0, 0);
		Zombie off = dummy(level, EntityType.ZOMBIE, o.offset(4, 0, 10));
		boolean[] ok = new boolean[1];
		griefing(false, () -> {
			ok[0] = Magic.castNow(p, s, 1.0F);
			run(8);
		});
		r.check(ok[0] && lost(in) >= 50, "Рассечение Сущего: цель в плоскости разреза — 64 урона сквозь броню (" + hp(in) + ")");
		r.check(lost(off) < 0.01, "Рассечение Сущего: цель в 4 блоках сбоку не задета (" + hp(off) + ")");

		for (boolean on : new boolean[] {true, false}) {
			BlockPos f = far(o, on ? 4 : 5);
			FakePlayer q = mage(level, f);
			face(q, f, 0, 0);
			BlockPos wall = f.offset(0, 1, 12);
			fill(level, wall, 2);
			int before = solid(level, wall, 2);
			griefing(on, () -> {
				Magic.castNow(q, s, 1.0F);
				run(8);
			});
			int after = solid(level, wall, 2);
			r.check(on ? after <= before - 10 : after == before, "Рассечение Сущего: " + (on ? "блоки в плоскости разреза исчезают"
					: "без разрушения блоки целы") + " (" + before + " → " + after + ")");
		}
	}

	// --- Божественный ---

	private static void planetary(ServerLevel level, BlockPos o, DevTests.Report r) {
		Spell s = spell("planetary_devastation");
		for (boolean on : new boolean[] {true, false}) {
			BlockPos f = far(o, on ? 6 : 7);
			FakePlayer p = mage(level, f);
			face(p, f, 0, 20);
			Vec3 ground = Vec3.atBottomCenterOf(f).add(0, 0, 1.62 / Math.tan(Math.toRadians(20)));
			BlockPos floor = BlockPos.containing(ground).below();
			int before = solid(level, floor, 2);
			Zombie near = dummy(level, EntityType.ZOMBIE, BlockPos.containing(ground).above(21));
			Zombie field = dummy(level, EntityType.ZOMBIE, BlockPos.containing(ground).offset(12, 0, 0));
			int temps = Terrain.tempCount();
			boolean[] ok = new boolean[1];
			boolean[] pulled = new boolean[1];
			griefing(on, () -> {
				ok[0] = Magic.castNow(p, s, 1.0F);
				run(16);
				pulled[0] = field.getDeltaMovement().y > 0.05 && field.getDeltaMovement().x < -0.05;
				run(120);
			});
			int after = solid(level, floor, 2);
			if (on) {
				r.check(ok[0] && pulled[0], "Опустошение Планеты: поле тяжести тянет врага в 12 блоках к ядру в небе");
				r.check(lost(near) >= 100, "Опустошение Планеты: у ядра — урон каждые 0,5 с и сжатие (" + hp(near) + ")");
				int moon = Terrain.tempCount() - temps;
				r.check(moon > 100, "Опустошение Планеты: вокруг ядра выросла каменная луна (" + moon + " временных блоков)");
				Terrain.restoreAll();
				r.check(Terrain.tempCount() == 0 && level.getBlockState(BlockPos.containing(ground.add(0, 24 + 7, 0))).isAir(),
						"Опустошение Планеты: луна осыпается — временные блоки возвращаются");
			}
			r.check(on ? after < before : after == before, "Опустошение Планеты: " + (on ? "земля под ядром вырвана кратером"
					: "без разрушения земля цела") + " (" + before + " → " + after + ")");
			Terrain.restoreAll();
		}
	}
}
