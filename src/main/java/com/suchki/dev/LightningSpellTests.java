package com.suchki.dev;

import static com.suchki.dev.SpellTestKit.arena;
import static com.suchki.dev.SpellTestKit.clean;
import static com.suchki.dev.SpellTestKit.dummy;
import static com.suchki.dev.SpellTestKit.face;
import static com.suchki.dev.SpellTestKit.hp;
import static com.suchki.dev.SpellTestKit.mage;
import static com.suchki.dev.SpellTestKit.run;
import static com.suchki.dev.SpellTestKit.spell;

import com.suchki.magic.Anchor;
import com.suchki.magic.Magic;
import com.suchki.magic.MagicEffects;
import com.suchki.magic.School;
import com.suchki.magic.Spell;
import com.suchki.magic.Terrain;
import com.suchki.magic.Zones;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Проверки заклинаний Молнии: каждое заклинание творится на мишенях-зомби (200 здоровья, без ИИ) и проверяется его
 * настоящий эффект — урон, паралич, прыжки цепей, рывок мага, тоннель и воронки с разрушением и без, мультикаст.
 */
final class LightningSpellTests {
	private LightningSpellTests() {}

	static void addTo(Map<String, DevTests.Test> tests) {
		tests.put("Магия: Молния — состав стихии", LightningSpellTests::roster);
		tests.put("Магия: Молния — Щелчок монеты", LightningSpellTests::coin);
		tests.put("Магия: Молния — Статические оковы", LightningSpellTests::shackles);
		tests.put("Магия: Молния — Громовой росчерк", LightningSpellTests::thunderclap);
		tests.put("Магия: Молния — Цепная молния", LightningSpellTests::chain);
		tests.put("Магия: Молния — Удар небесного грома", LightningSpellTests::strike);
		tests.put("Магия: Молния — Рельсотрон", LightningSpellTests::railgun);
		tests.put("Магия: Молния — Громовой дракон цепей", LightningSpellTests::dragon);
		tests.put("Магия: Молния — Небесный цилинь", LightningSpellTests::qilin);
		tests.put("Магия: Молния — Тысяча громов Индры", LightningSpellTests::indra);
	}

	// --- Помощники ---

	/** Свои дальние площадки (100+), чтобы не пересекаться с другими стихиями. */
	private static BlockPos far(BlockPos o, int i) {
		return SpellTestKit.far(o, 100 + i);
	}

	private static Zombie zombie(ServerLevel level, BlockPos at, int dx, int dz) {
		return dummy(level, EntityType.ZOMBIE, at.offset(dx, 0, dz));
	}

	/** Сколько здоровья потеряла мишень. */
	private static float lost(LivingEntity e) {
		return e.getMaxHealth() - e.getHealth();
	}

	private static int paralysis(LivingEntity e) {
		MobEffectInstance i = e.getEffect(MagicEffects.PARALYSIS);
		return i == null ? 0 : i.getDuration();
	}

	private static boolean paralyzed(LivingEntity e) {
		return e.hasEffect(MagicEffects.PARALYSIS);
	}

	/** Снять зоны и временные блоки, убрать мишени вокруг площадки. */
	private static void done(ServerLevel level, BlockPos at, int r) {
		clean();
		level.getEntitiesOfClass(Entity.class, new AABB(at).inflate(r), e -> !(e instanceof Player)).forEach(Entity::discard);
	}

	private static void wall(ServerLevel level, BlockPos at, int dz, BlockState s) {
		for (int x = -1; x <= 1; x++) {
			for (int y = 0; y <= 2; y++) level.setBlock(at.offset(x, y, dz), s, 2);
		}
	}

	private static String tag(boolean grief) {
		return grief ? " (разрушение вкл.)" : " (разрушение выкл.)";
	}

	// --- Состав ---

	private static void roster(ServerLevel level, BlockPos o, DevTests.Report r) {
		Object[][] want = {
				{"flicked_coin", 1, Anchor.HAND, false}, {"paralyze", 2, Anchor.TARGET, false}, {"thunderclap_flash", 2, Anchor.HAND, false},
				{"chain_lightning", 3, Anchor.HAND, false}, {"lightning_strike", 3, Anchor.SKY, true}, {"railgun", 4, Anchor.LINE, true},
				{"thunder_dragon", 5, Anchor.HAND, false}, {"heavenly_qilin", 6, Anchor.SKY, true},
				{"indra_thousand_thunders", 7, Anchor.SKY, true}};
		for (Object[] w : want) {
			Spell s = spell((String) w[0]);
			r.check(s.school == School.LIGHTNING && s.tier == (int) w[1] && s.anchor == w[2] && s.destroys == (boolean) w[3],
					s.id + ": молния, ранг " + s.tier + ", круг " + s.anchor + (s.destroys ? ", ломает блоки" : ""));
		}
		r.check(spell("railgun").layers == 5 && spell("indra_thousand_thunders").layers == 6, "Рельсотрон — 5 кругов, Индра — 6");
		r.check(spell("indra_thousand_thunders").maxCopies == 1, "Божественный — без мультикаста");
	}

	// --- Ранг 1 ---

	private static void coin(ServerLevel level, BlockPos o, DevTests.Report r) {
		FakePlayer p = mage(level, o);
		face(p, o, 0);
		Spell s = spell("flicked_coin");
		Zombie a = zombie(level, o, 0, 6);
		Zombie b = zombie(level, o, 0, 10);
		Zombie c = zombie(level, o, 0, 13);
		r.check(Magic.castNow(p, s, 1.0F), "Монета выпущена");
		r.check(lost(a) >= 6.0F && lost(a) <= 10.0F, "Первая цель: 7 урона (" + hp(a) + ")");
		r.check(lost(b) >= 3.5F && lost(b) < lost(a), "Монета прошивает: вторая цель ×0,6 (" + hp(b) + ")");
		r.check(lost(c) == 0, "Третью цель монета уже не задевает (" + hp(c) + ")");
		a.discard();
		b.discard();
		c.discard();

		Zombie hidden = zombie(level, o, 0, 8);
		wall(level, o, 5, Blocks.STONE.defaultBlockState());
		Magic.castNow(p, s, 1.0F);
		r.check(lost(hidden) == 0, "Камень останавливает монету (" + hp(hidden) + ")");
		r.check(level.getBlockState(o.offset(0, 1, 5)).is(Blocks.STONE), "Блоки монета не ломает");
		wall(level, o, 5, Blocks.AIR.defaultBlockState());
		hidden.discard();

		List<Zombie> three = List.of(zombie(level, o, -3, 9), zombie(level, o, 0, 9), zombie(level, o, 3, 9));
		Magic.castNow(p, s, 1.0F, 3);
		run(6);
		long hit = three.stream().filter(SpellTestKit::hurt).count();
		r.check(hit == 3, "Мультикаст ×3: монеты в трёх разных врагов (задето " + hit + ")");
		done(level, o, 20);
	}

	// --- Ранг 2 ---

	private static void shackles(ServerLevel level, BlockPos o, DevTests.Report r) {
		FakePlayer p = mage(level, o);
		Spell s = spell("paralyze");
		face(p, o, -80);
		r.check(!Magic.castNow(p, s, 1.0F), "Без цели оковы срываются");

		face(p, o, 0);
		Zombie z = zombie(level, o, 0, 10);
		r.check(Magic.castNow(p, s, 1.0F), "Оковы наложены");
		run(10);
		r.check(lost(z) >= 3.0F && lost(z) <= 6.0F, "4 урона разрядами (" + hp(z) + ")");
		r.check(paralysis(z) >= 60 && paralysis(z) <= 80, "Паралич на 4 с (осталось " + paralysis(z) + " тиков)");
		done(level, o, 20);

		Warden w = dummy(level, EntityType.WARDEN, o.offset(0, 0, 10));
		Magic.castNow(p, s, 1.0F);
		r.check(paralyzed(w) && paralysis(w) <= 20, "Босса сковывает лишь на 1 с (" + paralysis(w) + " тиков)");
		done(level, o, 20);

		Zombie a = zombie(level, o, 0, 10);
		Zombie b = zombie(level, o, 3, 11);
		Magic.castNow(p, s, 1.0F, 2);
		r.check(paralyzed(a) && paralyzed(b), "Мультикаст ×2: скованы оба врага");
		done(level, o, 20);
	}

	private static void thunderclap(ServerLevel level, BlockPos o, DevTests.Report r) {
		Spell s = spell("thunderclap_flash");
		BlockPos at = far(o, 0);
		FakePlayer p = mage(level, at);
		arena(level, at.offset(0, 0, 14), 14, 6);
		face(p, at, 0);
		Zombie a = zombie(level, at, 0, 5);
		Zombie b = zombie(level, at, 1, 9);
		Zombie side = zombie(level, at, 6, 7);
		r.check(Magic.castNow(p, s, 1.0F), "Рывок");
		double moved = p.getZ() - (at.getZ() + 0.5);
		r.check(moved >= 12.5 && moved <= 14.6, "Маг пролетел ~14 блоков вперёд (" + String.format("%.1f", moved) + ")");
		r.check(lost(a) >= 8.0F && lost(b) >= 8.0F && paralyzed(a) && paralyzed(b), "Все на пути: 9 урона и паралич (" + hp(a) + ", " + hp(b) + ")");
		r.check(lost(side) == 0, "В стороне от пути — не задет");
		MobEffectInstance res = p.getEffect(MobEffects.RESISTANCE);
		r.check(res != null && res.getAmplifier() == 1, "После рывка — Сопротивление II");
		done(level, at, 30);

		// Стена: рывок обрывается перед ней.
		face(p, at, 0);
		wall(level, at, 7, Blocks.STONE.defaultBlockState());
		Magic.castNow(p, s, 1.0F);
		double before = p.getZ() - at.getZ();
		r.check(before > 3.0 && before < 7.0, "Перед стеной маг останавливается (" + String.format("%.1f", before) + ")");
		r.check(level.getBlockState(at.offset(0, 1, 7)).is(Blocks.STONE), "Стена цела");
		wall(level, at, 7, Blocks.AIR.defaultBlockState());
		done(level, at, 30);

		// Мультикаст: второй рывок — к следующему врагу у прицела.
		face(p, at, 0);
		Zombie first = zombie(level, at, 0, 6);
		Zombie second = zombie(level, at, 4, 11);
		Magic.castNow(p, s, 1.0F, 2);
		r.check(lost(first) >= 8.0F && lost(second) >= 8.0F, "Мультикаст ×2: второй рывок задевает второго врага (" + hp(first) + ", " + hp(second) + ")");
		done(level, at, 30);
	}

	// --- Ранг 3 ---

	private static void chain(ServerLevel level, BlockPos o, DevTests.Report r) {
		FakePlayer p = mage(level, o);
		Spell s = spell("chain_lightning");
		face(p, o, -80);
		r.check(!Magic.castNow(p, s, 1.0F), "Без цели молния срывается");

		face(p, o, 0);
		// Тесная кучка: все в 8 блоках друг от друга, предел — только число прыжков.
		List<Zombie> row = List.of(zombie(level, o, 0, 10), zombie(level, o, 3, 10), zombie(level, o, -3, 10), zombie(level, o, 0, 13),
				zombie(level, o, 3, 13));
		r.check(Magic.castNow(p, s, 1.0F), "Молния выпущена");
		run(12);
		long hit = row.stream().filter(SpellTestKit::hurt).count();
		r.check(hit == 4, "Сила ×1: первая цель и 3 прыжка (задето " + hit + " из 5)");
		r.check(lost(row.get(0)) >= 11.0F && lost(row.get(0)) <= 16.0F && paralyzed(row.get(0)), "Первая цель: 12 урона и паралич (" + hp(row.get(0)) + ")");
		float weakest = row.stream().filter(SpellTestKit::hurt).map(LightningSpellTests::lost).min(Float::compare).orElse(0.0F);
		r.check(weakest < lost(row.get(0)) * 0.7F && weakest > 4.0F, "Каждый прыжок слабее на 20% (последний " + String.format("%.1f", weakest) + ")");
		row.forEach(Entity::discard);

		List<Zombie> again = List.of(zombie(level, o, 0, 10), zombie(level, o, 3, 10), zombie(level, o, -3, 10), zombie(level, o, 0, 13),
				zombie(level, o, 3, 13), zombie(level, o, -3, 13), zombie(level, o, 0, 7));
		Magic.castNow(p, s, 1.4F);
		run(14);
		long hit2 = again.stream().filter(SpellTestKit::hurt).count();
		r.check(hit2 == 6, "Сила ×1,4: 5 прыжков (задето " + hit2 + " из 7)");
		again.forEach(Entity::discard);

		Zombie a = zombie(level, o, 0, 10);
		Zombie b = zombie(level, o, 6, 10);
		Magic.castNow(p, s, 1.0F, 2);
		run(10);
		r.check(lost(a) >= 18.0F && lost(b) >= 18.0F, "Мультикаст ×2: две цепи — каждый враг задет дважды (" + hp(a) + ", " + hp(b) + ")");
		done(level, o, 20);
	}

	private static void strike(ServerLevel level, BlockPos o, DevTests.Report r) {
		Spell s = spell("lightning_strike");
		for (boolean grief : new boolean[] {true, false}) {
			BlockPos at = far(o, grief ? 1 : 2);
			FakePlayer p = mage(level, at);
			face(p, at, 0);
			Zombie a = zombie(level, at, 0, 10);
			Zombie near = zombie(level, at, 3, 10);
			Zombie fork = zombie(level, at, 7, 10);
			Zombie behind = zombie(level, at, 0, -8);
			level.setBlock(at.offset(1, -1, 11), Blocks.SAND.defaultBlockState(), 2);
			level.setBlock(at.offset(-1, -1, 9), Blocks.SAND.defaultBlockState(), 2);
			int broken = Terrain.brokenTotal();
			SpellTestKit.griefing(grief, () -> {
				r.check(Magic.castNow(p, s, 1.0F), "Круг над прицелом" + tag(grief));
				run(4);
				r.check(lost(a) == 0, "Сначала — предупреждение, удар ещё не упал");
				run(12);
			});
			r.check(lost(a) >= 16.0F && lost(a) <= 26.0F && paralyzed(a), "В центре: 18 урона и паралич (" + hp(a) + ")");
			r.check(lost(near) >= 8.0F && lost(near) < lost(a), "До 4 блоков — 9 урона (" + hp(near) + ")");
			r.check(lost(fork) >= 6.0F && lost(fork) <= 10.0F, "Разветвление бьёт врага в 8 блоках на 7 (" + hp(fork) + ")");
			r.check(lost(behind) == 0, "Далёкий враг не задет");
			boolean glass = level.getBlockState(at.offset(1, -1, 11)).is(Blocks.GLASS) || level.getBlockState(at.offset(-1, -1, 9)).is(Blocks.GLASS);
			if (grief) {
				r.check(Terrain.brokenTotal() > broken, "Воронка от удара" + tag(true) + " (" + (Terrain.brokenTotal() - broken) + " блоков)");
				r.check(glass, "Песок под молнией сплавился в стекло");
			} else {
				r.check(Terrain.brokenTotal() == broken && level.getBlockState(at.offset(0, -1, 10)).is(Blocks.STONE), "Без разрушения земля цела");
				r.check(!glass, "Без разрушения песок остаётся песком");
			}
			done(level, at, 20);
		}

		BlockPos at = far(o, 2);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		Zombie a = zombie(level, at, 0, 10);
		Zombie b = zombie(level, at, 7, 10);
		SpellTestKit.griefing(false, () -> {
			Magic.castNow(p, s, 1.0F, 2);
			run(16);
		});
		r.check(lost(a) >= 16.0F && lost(b) >= 16.0F, "Мультикаст ×2: второй круг бьёт второго врага (" + hp(a) + ", " + hp(b) + ")");
		done(level, at, 20);
	}

	// --- Ранг 4 ---

	private static void railgun(ServerLevel level, BlockPos o, DevTests.Report r) {
		Spell s = spell("railgun");
		for (boolean grief : new boolean[] {true, false}) {
			BlockPos at = far(o, grief ? 3 : 4);
			FakePlayer p = mage(level, at);
			arena(level, at.offset(0, 0, 26), 14, 8);
			face(p, at, 0);
			Zombie a = zombie(level, at, 0, 8);
			Zombie b = zombie(level, at, 0, 16);
			Zombie behindWall = zombie(level, at, 0, 28);
			Zombie behindObsidian = zombie(level, at, 0, 38);
			Zombie back = zombie(level, at, 0, -6);
			wall(level, at, 22, Blocks.STONE.defaultBlockState());
			for (int dz = 32; dz <= 34; dz++) wall(level, at, dz, Blocks.OBSIDIAN.defaultBlockState());
			SpellTestKit.griefing(grief, () -> {
				r.check(Magic.castNow(p, s, 1.0F), "Выстрел" + tag(grief));
				run(4);
			});
			r.check(lost(a) >= 22.0F && lost(a) <= 36.0F, "Первый на линии: 26 урона (" + hp(a) + ")");
			r.check(lost(b) >= 18.0F && lost(b) < lost(a) && b.isOnFire(), "Второй: ×0,85 и поджог (" + hp(b) + ")");
			r.check(lost(back) == 0, "За спиной мага никого не задевает");
			r.check(lost(behindObsidian) == 0, "Обсидиан монета не пробивает");
			if (grief) {
				r.check(level.getBlockState(at.offset(0, 1, 22)).isAir(), "Тоннель сквозь каменную стену" + tag(true));
				r.check(lost(behindWall) >= 10.0F, "Враг за стеной задет (" + hp(behindWall) + ")");
				r.check(level.getBlockState(at.offset(0, 1, 34)).is(Blocks.OBSIDIAN), "Дальний слой обсидиана цел");
			} else {
				r.check(level.getBlockState(at.offset(0, 1, 22)).is(Blocks.STONE), "Без разрушения стена цела");
				r.check(lost(behindWall) == 0, "Без разрушения стена закрывает врага за ней");
			}
			done(level, at, 40);
		}

		BlockPos at = far(o, 4);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		Zombie a = zombie(level, at, 0, 10);
		Zombie b = zombie(level, at, 4, 10);
		SpellTestKit.griefing(false, () -> {
			Magic.castNow(p, s, 1.0F, 2);
			run(4);
		});
		r.check(lost(a) >= 20.0F && lost(b) >= 20.0F, "Мультикаст ×2: второй ствол бьёт второго врага (" + hp(a) + ", " + hp(b) + ")");
		done(level, at, 40);
	}

	// --- Ранг 5 ---

	private static void dragon(ServerLevel level, BlockPos o, DevTests.Report r) {
		Spell s = spell("thunder_dragon");
		BlockPos at = far(o, 5);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		Zombie a = zombie(level, at, 0, 12);
		Zombie b = zombie(level, at, 5, 13);
		Zombie c = zombie(level, at, -4, 14);
		Zombie out = zombie(level, at, 12, -12);
		r.check(Magic.castNow(p, s, 1.0F), "Дракон выпущен");
		run(6);
		r.check(lost(a) == 0, "Дракон летит, а не бьёт мгновенно");
		run(80);
		r.check(lost(a) >= 26.0F && paralyzed(a), "Укус первой цели: 30 урона и паралич (" + hp(a) + ")");
		r.check(lost(b) >= 18.0F && lost(c) >= 18.0F, "Дракон перескочил на соседей (" + hp(b) + ", " + hp(c) + ")");
		r.check(Math.min(lost(b), lost(c)) < lost(a), "Каждый прыжок слабее");
		r.check(lost(out) == 0, "Далёкий враг (дальше 12 блоков от жертв) не задет");
		r.check(Zones.active() == 0, "Дракон рассеялся, когда жертвы кончились");
		done(level, at, 30);

		Zombie x = zombie(level, at, 0, 12);
		Zombie y = zombie(level, at, 5, 12);
		Magic.castNow(p, s, 1.0F, 2);
		run(90);
		r.check(lost(x) >= 45.0F && lost(y) >= 45.0F, "Мультикаст ×2: два дракона — каждый враг укушен дважды (" + hp(x) + ", " + hp(y) + ")");
		done(level, at, 30);
	}

	// --- Ранг 6 ---

	private static void qilin(ServerLevel level, BlockPos o, DevTests.Report r) {
		Spell s = spell("heavenly_qilin");
		for (boolean grief : new boolean[] {true, false}) {
			BlockPos at = far(o, grief ? 6 : 7);
			FakePlayer p = mage(level, at);
			face(p, at, 0);
			Zombie a = zombie(level, at, 0, 12);
			Zombie b = zombie(level, at, 5, 12);
			Zombie out = zombie(level, at, 0, -12);
			int broken = Terrain.brokenTotal();
			SpellTestKit.griefing(grief, () -> {
				r.check(Magic.castNow(p, s, 1.0F), "Гроза собирается" + tag(grief));
				run(20);
				r.check(lost(a) >= 14.0F && lost(a) < 50.0F, "Гроза: молнии бьют ближайшего к центру врага (" + hp(a) + ")");
				r.check(b.hasEffect(MobEffects.SLOWNESS), "Под тучами враги замедлены");
				run(24);
				r.check(paralyzed(a) && paralyzed(b) && lost(b) >= 30.0F, "Зверь упал: удар по площади и паралич (" + hp(b) + ")");
				run(24);
			});
			r.check(!a.isAlive() || lost(a) >= 90.0F, "В центре — гроза, 55 удара и остаточные разряды (" + hp(a) + ")");
			r.check(lost(out) == 0, "Вне грозы (24 блока) — нетронут");
			if (grief) r.check(Terrain.brokenTotal() - broken >= 15, "Кратер" + tag(true) + " (" + (Terrain.brokenTotal() - broken) + " блоков)");
			else r.check(Terrain.brokenTotal() == broken, "Без разрушения кратера нет");
			done(level, at, 40);
		}

		BlockPos at = far(o, 7);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		Zombie a = zombie(level, at, 0, 12);
		Zombie e = zombie(level, at, 7, 14);
		SpellTestKit.griefing(false, () -> {
			Magic.castNow(p, s, 1.0F, 2);
			run(70);
		});
		r.check(lost(e) >= 28.0F, "Мультикаст ×2: малый удар зверя по второму врагу (" + hp(e) + ")");
		done(level, at, 40);
	}

	// --- Ранг 7 ---

	private static void indra(ServerLevel level, BlockPos o, DevTests.Report r) {
		Spell s = spell("indra_thousand_thunders");
		for (boolean grief : new boolean[] {true, false}) {
			BlockPos at = far(o, grief ? 8 : 9);
			FakePlayer p = mage(level, at);
			arena(level, at.offset(0, 0, 20), 24, 10);
			face(p, at, 0);
			Zombie a = zombie(level, at, 0, 20);
			Zombie b = zombie(level, at, 6, 24);
			Zombie c = zombie(level, at, -20, 20);
			int broken = Terrain.brokenTotal();
			SpellTestKit.griefing(grief, () -> {
				r.check(Magic.castNow(p, s, 1.0F), "Свод раскрыт" + tag(grief));
				run(60);
				r.check(SpellTestKit.hurt(a) && SpellTestKit.hurt(c), "Ливень молний бьёт по всему полю (" + hp(a) + ", " + hp(c) + ")");
				r.check(lost(b) < 100.0F, "До копья — только ливень (" + hp(b) + ")");
				run(90);
			});
			r.check(!a.isAlive() || lost(a) >= 150.0F, "В центре: ливень и копьё Индры (" + hp(a) + ")");
			r.check(!b.isAlive() || lost(b) >= 80.0F, "В 7 блоках от центра копьё бьёт сильно (" + hp(b) + ")");
			r.check(lost(c) >= 10.0F && lost(c) < 180.0F, "Вдали от центра — только ливень (" + hp(c) + ")");
			if (grief) r.check(Terrain.brokenTotal() - broken >= 100, "Оплавленный кратер" + tag(true) + " (" + (Terrain.brokenTotal() - broken) + " блоков)");
			else r.check(Terrain.brokenTotal() == broken, "Без разрушения земля цела");
			done(level, at, 50);
		}

		// Босс получает не больше 80 от копья.
		BlockPos at = far(o, 9);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		Warden w = dummy(level, EntityType.WARDEN, at.offset(0, 0, 10));
		SpellTestKit.griefing(false, () -> {
			Magic.castNow(p, s, 1.0F);
			run(125);
			float before = lost(w);
			run(8);
			r.check(lost(w) - before <= 81.0F && lost(w) - before >= 50.0F, "Копьё по боссу — не больше 80 (" + String.format("%.1f", lost(w) - before) + ")");
		});
		r.check(w.isAlive(), "Босс жив");
		done(level, at, 50);
	}
}
