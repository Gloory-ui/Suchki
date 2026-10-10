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
import com.suchki.magic.Cast;
import com.suchki.magic.Magic;
import com.suchki.magic.MagicDamage;
import com.suchki.magic.MagicEffects;
import com.suchki.magic.Minions;
import com.suchki.magic.School;
import com.suchki.magic.Spell;
import com.suchki.magic.Terrain;
import com.suchki.magic.spells.DarkSpells;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Проверки заклинаний Тьмы: каждое заклинание творится на мишенях-зомби (200 здоровья, без ИИ) и проверяется его
 * настоящий эффект — урон, эффекты, лечение мага, перемещение, временные блоки, разрушение с настройкой и без,
 * мультикаст.
 */
final class DarkSpellTests {
	private DarkSpellTests() {}

	static void addTo(Map<String, DevTests.Test> tests) {
		tests.put("Магия: Тьма — состав стихии", DarkSpellTests::roster);
		tests.put("Магия: Тьма — Луч обыденной смерти", DarkSpellTests::deathRay);
		tests.put("Магия: Тьма — Похищение жизни", DarkSpellTests::drainLife);
		tests.put("Магия: Тьма — Проклятая печать", DarkSpellTests::curse);
		tests.put("Магия: Тьма — Теневой обмен", DarkSpellTests::shadowStep);
		tests.put("Магия: Тьма — Безлунная мгла", DarkSpellTests::haze);
		tests.put("Магия: Тьма — Жатва душ", DarkSpellTests::soulHarvest);
		tests.put("Магия: Тьма — Длань, сжимающая сердце", DarkSpellTests::graspHeart);
		tests.put("Магия: Тьма — Чёрный гроб", DarkSpellTests::blackCoffin);
		tests.put("Магия: Тьма — Чёрный лунный клык", DarkSpellTests::moonfang);
		tests.put("Магия: Тьма — Святилище злобы", DarkSpellTests::shrine);
	}

	// --- Помощники ---

	private static Zombie zombie(ServerLevel level, BlockPos at, int dx, int dz) {
		return dummy(level, EntityType.ZOMBIE, at.offset(dx, 0, dz));
	}

	/** Сколько здоровья потеряла мишень. */
	private static float lost(LivingEntity e) {
		return e.getMaxHealth() - e.getHealth();
	}

	private static int amp(LivingEntity e, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> fx) {
		MobEffectInstance i = e.getEffect(fx);
		return i == null ? -1 : i.getAmplifier();
	}

	/** Снять зоны и временные блоки, убрать мишени вокруг дальней площадки. */
	private static void done(ServerLevel level, BlockPos at) {
		clean();
		level.getEntitiesOfClass(Entity.class, new AABB(at).inflate(48), e -> !(e instanceof Player)).forEach(Entity::discard);
	}

	/** Сколько воздуха в слое {@code y} квадрата радиуса {@code r}. */
	private static int air(ServerLevel level, BlockPos c, int r) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, 0, -r), c.offset(r, 0, r))) {
			if (level.getBlockState(p).isAir()) n++;
		}
		return n;
	}

	// --- Состав ---

	private static void roster(ServerLevel level, BlockPos o, DevTests.Report r) {
		Object[][] want = {
				{"shadow_bolt", 1, Anchor.HAND, false}, {"drain_life", 2, Anchor.HAND, false}, {"curse", 2, Anchor.TARGET, false},
				{"shadow_step", 3, Anchor.TARGET, false}, {"darkness", 3, Anchor.TARGET, false}, {"soul_harvest", 4, Anchor.FEET, false},
				{"grasp_heart", 5, Anchor.HAND, false}, {"black_coffin", 5, Anchor.TARGET, true}, {"black_moonfang", 6, Anchor.HAND, true},
				{"malevolent_shrine", 7, Anchor.FEET, true}};
		for (Object[] w : want) {
			Spell s = spell((String) w[0]);
			r.check(s.school == School.DARK && s.tier == (int) w[1] && s.anchor == w[2] && s.destroys == (boolean) w[3],
					s.id + ": тьма, ранг " + s.tier + ", круг " + s.anchor + (s.destroys ? ", ломает блоки" : ""));
		}
		r.check(spell("drain_life").loop != null && spell("malevolent_shrine").maxCopies == 1, "Поток со своим звуком, божественный — без мультикаста");
	}

	// --- Ранг 1 ---

	private static void deathRay(ServerLevel level, BlockPos o, DevTests.Report r) {
		FakePlayer p = mage(level, o);
		face(p, o, 0);
		Spell s = spell("shadow_bolt");
		Zombie a = zombie(level, o, 0, 6);
		Zombie b = zombie(level, o, 0, 10);
		Zombie c = zombie(level, o, 0, 13);
		Magic.castNow(p, s, 1.0F);
		r.check(lost(a) >= 3.5F && lost(a) <= 6.0F && a.hasEffect(MobEffects.WITHER), "Луч: первая цель 5 урона и Иссушение (" + hp(a) + ")");
		r.check(lost(b) >= 2.0F && lost(b) < lost(a), "Луч пронзает: вторая цель 3 урона (" + hp(b) + ")");
		r.check(lost(c) == 0, "Третью цель луч уже не задевает (" + hp(c) + ")");
		a.discard();
		b.discard();
		c.discard();

		Zombie hidden = zombie(level, o, 0, 8);
		for (int y = 0; y <= 2; y++) level.setBlock(o.offset(0, y, 5), Blocks.STONE.defaultBlockState(), 2);
		Magic.castNow(p, s, 1.0F);
		r.check(lost(hidden) == 0, "Стена останавливает луч (" + hp(hidden) + ")");
		for (int y = 0; y <= 2; y++) level.setBlock(o.offset(0, y, 5), Blocks.AIR.defaultBlockState(), 2);
		hidden.discard();

		List<Zombie> three = List.of(zombie(level, o, -3, 9), zombie(level, o, 0, 9), zombie(level, o, 3, 9));
		Magic.castNow(p, s, 1.0F, 3);
		run(12);
		long hit = three.stream().filter(SpellTestKit::hurt).count();
		r.check(hit == 3, "Мультикаст ×3: залп по трём разным врагам (задето " + hit + ")");
		clean();
	}

	// --- Ранг 2 ---

	private static void drainLife(ServerLevel level, BlockPos o, DevTests.Report r) {
		FakePlayer p = mage(level, o);
		face(p, o, 0);
		Zombie a = zombie(level, o, 0, 6);
		Zombie b = zombie(level, o, 1, 8);
		Zombie c = zombie(level, o, -1, 8);
		Zombie far = zombie(level, o, 0, 26);
		a.addEffect(new MobEffectInstance(MobEffects.SPEED, 1200, 1));
		p.setHealth(p.getMaxHealth() - 10.0F);
		float before = p.getHealth();
		Magic.channelNow(p, spell("drain_life"), 60, 1.0F);
		r.check(lost(a) >= 8.0F && lost(b) >= 8.0F && lost(c) >= 8.0F,
				"Нити тянут из трёх врагов по 2 за полсекунды (" + hp(a) + ", " + hp(b) + ", " + hp(c) + ")");
		r.check(lost(far) == 0, "Дальше 20 блоков нить не достаёт (" + hp(far) + ")");
		r.check(p.getHealth() > before + 5.0F, "Маг лечится отнятым (" + before + " → " + p.getHealth() + ")");
		r.check(p.hasEffect(MobEffects.SPEED) && !a.hasEffect(MobEffects.SPEED), "Через 3 с нить крадёт полезный эффект цели");
		clean();
	}

	private static void curse(ServerLevel level, BlockPos o, DevTests.Report r) {
		FakePlayer p = mage(level, o);
		face(p, o, 0);
		Spell s = spell("curse");
		Zombie a = zombie(level, o, 0, 8);
		Zombie b = zombie(level, o, 3, 11);
		Zombie plain = zombie(level, o, -6, 4);
		r.check(Magic.castNow(p, s, 1.0F), "Печать наложена");
		r.check(a.hasEffect(MagicEffects.HEX) && a.hasEffect(MobEffects.WEAKNESS) && a.hasEffect(MobEffects.SLOWNESS)
				&& a.getEffect(MagicEffects.HEX).getDuration() >= 280, "Цель проклята на 15 с: печать, Слабость, Замедление");
		r.check(!b.hasEffect(MagicEffects.HEX), "Соседа печать пока не трогает");
		Cast probe = new Cast(level, p, s, 1.0F, 1.0F);
		probe.hurt(a, 10.0F, MagicDamage.DARK);
		probe.hurt(plain, 10.0F, MagicDamage.DARK);
		r.check(lost(a) > lost(plain) * 1.15F, "Проклятый получает больше урона (" + lost(a) + " против " + lost(plain) + ")");
		a.kill(level);
		run(3);
		r.check(b.hasEffect(MagicEffects.HEX), "Проклятый умер — печать перескочила на соседа");
		b.discard();
		plain.discard();
		clean();

		Zombie c = zombie(level, o, 0, 12);
		Zombie d = zombie(level, o, 3, 12);
		Magic.castNow(p, s, 1.0F, 2);
		r.check(c.hasEffect(MagicEffects.HEX) && d.hasEffect(MagicEffects.HEX), "Мультикаст ×2: прокляты два врага");
		face(p, o, -80);
		r.check(!Magic.castNow(p, s, 1.0F), "Без врага у прицела печать не тратится");
		clean();
	}

	// --- Ранг 3 ---

	private static void shadowStep(ServerLevel level, BlockPos o, DevTests.Report r) {
		FakePlayer p = mage(level, o);
		Spell s = spell("shadow_step");
		face(p, o, 0);
		Zombie foe = zombie(level, o, 0, 12);
		Vec3 start = p.position();
		r.check(Magic.castNow(p, s, 1.0F), "Шаг к врагу");
		r.check(p.position().distanceTo(start) > 8 && p.position().distanceTo(foe.position()) < 2.5 && p.hasEffect(MobEffects.INVISIBILITY),
				"Маг вышел из тени у врага и стал невидим (до врага " + String.format("%.1f", p.position().distanceTo(foe.position())) + ")");
		float hpBefore = foe.getHealth();
		p.attack(foe);
		r.check(hpBefore - foe.getHealth() >= 5.0F, "Удар в спину: ещё 6 тьмой (" + hpBefore + " → " + foe.getHealth() + ")");
		hpBefore = foe.getHealth();
		foe.invulnerableTime = 0;
		p.attack(foe);
		r.check(hpBefore - foe.getHealth() < 5.0F, "Бонус к удару — только один раз");
		foe.discard();
		clean();

		face(p, o, 0);
		Cast cast = new Cast(level, p, s, 1.0F, 1.0F);
		Zombie mine = EntityType.ZOMBIE.create(level, EntitySpawnReason.MOB_SUMMONED);
		mine.snapTo(o.getX() + 0.5, o.getY(), o.getZ() + 20.5, 0, 0);
		mine.setNoAi(true);
		Minions.summon(cast, mine, 400);
		Vec3 home = p.position();
		Vec3 there = mine.position();
		r.check(Magic.castNow(p, s, 1.0F), "Обмен со своим призывом");
		r.check(p.position().distanceTo(there) < 0.6 && mine.position().distanceTo(home) < 0.6, "Маг и призыв поменялись местами");
		mine.discard();
		clean();

		face(p, o, 0);
		for (int x = -1; x <= 1; x++) for (int y = 0; y <= 3; y++) level.setBlock(o.offset(x, y, 12), Blocks.STONE.defaultBlockState(), 2);
		Magic.castNow(p, s, 1.0F);
		r.check(Math.abs(p.getZ() - (o.getZ() + 11.5)) < 0.8 && Math.abs(p.getX() - (o.getX() + 0.5)) < 0.8,
				"Шаг к стене: маг у её грани (z " + String.format("%.1f", p.getZ() - o.getZ()) + ")");
		for (int x = -1; x <= 1; x++) for (int y = 0; y <= 3; y++) level.setBlock(o.offset(x, y, 12), Blocks.AIR.defaultBlockState(), 2);
		clean();

		face(p, o, -80);
		Vec3 sky = p.position();
		r.check(!Magic.castNow(p, s, 1.0F) && p.position().equals(sky), "В небо шаг не делается");

		face(p, o, 0);
		Zombie foe2 = zombie(level, o, 0, 10);
		Vec3 st = p.position();
		Magic.castNow(p, s, 1.0F, 3);
		List<Mannequin> decoys = level.getEntitiesOfClass(Mannequin.class, new AABB(BlockPos.containing(st)).inflate(4),
				m -> Minions.isOwnedBy(m, p));
		r.check(decoys.size() == 2, "Мультикаст ×3: на месте старта два теневых двойника (" + decoys.size() + ")");
		decoys.forEach(Entity::discard);
		foe2.discard();
		clean();
	}

	private static void haze(ServerLevel level, BlockPos o, DevTests.Report r) {
		FakePlayer p = mage(level, o);
		face(p, o, 0);
		Zombie in = zombie(level, o, 0, 8);
		Zombie out = zombie(level, o, 12, 8);
		in.setTarget(p);
		Magic.castNow(p, spell("darkness"), 1.0F);
		run(11);
		r.check(in.hasEffect(MobEffects.BLINDNESS) && in.hasEffect(MobEffects.DARKNESS) && in.hasEffect(MobEffects.WITHER)
				&& in.hasEffect(MobEffects.SLOWNESS), "Во мгле враг слепнет, иссыхает и замедляется");
		r.check(in.getTarget() == null, "Враг во мгле теряет цель");
		r.check(!out.hasEffect(MobEffects.BLINDNESS), "Вне облака (12 блоков) — без эффектов");
		in.teleportTo(o.getX() + 0.5, o.getY(), o.getZ() + 24.5);
		run(10);
		r.check(in.hasEffect(MobEffects.NAUSEA), "Вышедший из мглы бродит в растерянности");
		clean();
	}

	// --- Ранг 4 ---

	private static void soulHarvest(ServerLevel level, BlockPos o, DevTests.Report r) {
		FakePlayer p = mage(level, o);
		face(p, o, 0);
		Spell s = spell("soul_harvest");
		List<Zombie> near = List.of(zombie(level, o, 4, 0), zombie(level, o, -5, 3), zombie(level, o, 0, 9));
		Zombie far = zombie(level, o, 0, 14);
		p.setHealth(p.getMaxHealth() - 10.0F);
		float before = p.getHealth();
		Magic.castNow(p, s, 1.0F);
		r.check(near.stream().allMatch(z -> lost(z) >= 15.0F && amp(z, MobEffects.WITHER) == 1),
				"Враги в радиусе 12: 18 урона и Иссушение II (" + hp(near.get(0)) + ")");
		r.check(lost(far) == 0, "Дальше 12 блоков — нетронут");
		r.check(p.getHealth() >= before + 5.9F && p.hasEffect(MobEffects.ABSORPTION),
				"Три души лечат мага на 6 и дают поглощение (" + before + " → " + p.getHealth() + ")");
		near.forEach(Entity::discard);
		far.discard();
		clean();

		Zombie z = zombie(level, o, 3, 3);
		Magic.castNow(p, s, 1.0F, 2);
		float first = lost(z);
		run(12);
		r.check(first >= 15.0F && lost(z) - first >= 7.0F, "Мультикаст ×2: второй всплеск вполсилы (" + first + " + " + (lost(z) - first) + ")");
		clean();
	}

	// --- Ранг 5 ---

	private static void graspHeart(ServerLevel level, BlockPos o, DevTests.Report r) {
		FakePlayer p = mage(level, o);
		face(p, o, 0);
		Spell s = spell("grasp_heart");
		Zombie weak = zombie(level, o, 0, 10);
		weak.setHealth(40.0F);
		r.check(Magic.castNow(p, s, 1.0F), "Сердце схвачено");
		run(8);
		r.check(!weak.isAlive(), "Моб с 40 здоровья умирает от сжатого сердца");
		clean();

		Zombie tough = zombie(level, o, 0, 10);
		Magic.castNow(p, s, 1.0F);
		run(8);
		r.check(lost(tough) >= 30.0F && tough.hasEffect(MagicEffects.PARALYSIS), "Крепкий: 38 урона и оцепенение (" + hp(tough) + ")");
		tough.discard();
		clean();

		Warden boss = dummy(level, EntityType.WARDEN, o.offset(0, 0, 10));
		Magic.castNow(p, s, 1.0F);
		run(8);
		r.check(boss.isAlive() && lost(boss) >= 20.0F && !boss.hasEffect(MagicEffects.PARALYSIS),
				"Босс: урон, но ни смерти, ни оцепенения (" + hp(boss) + ")");
		boss.discard();
		clean();

		face(p, o, -80);
		r.check(!Magic.castNow(p, s, 1.0F), "Без цели — срыв");
	}

	private static void blackCoffin(ServerLevel level, BlockPos o, DevTests.Report r) {
		Spell s = spell("black_coffin");
		for (boolean grief : new boolean[] {true, false}) {
			BlockPos at = SpellTestKit.far(o, grief ? 20 : 21);
			FakePlayer p = mage(level, at);
			face(p, at, 0);
			Zombie z = zombie(level, at, 0, 10);
			BlockPos box = at.offset(0, 0, 10);
			int airBefore = air(level, box.below(), 1);
			String tag = grief ? " (разрушение вкл.)" : " (разрушение выкл.)";
			SpellTestKit.griefing(grief, () -> {
				Magic.castNow(p, s, 1.0F);
				run(10);
				r.check(level.getBlockState(box.offset(2, 1, 0)).is(Blocks.BLACK_STAINED_GLASS)
						&& level.getBlockState(box.offset(2, 0, 2)).is(Blocks.BLACK_CONCRETE)
						&& level.getBlockState(box.offset(0, 4, 0)).is(Blocks.BLACK_STAINED_GLASS), "Гроб 5×5×5 из временных блоков встал" + tag);
				r.check(z.hasEffect(MagicEffects.PARALYSIS), "Враг в гробу скован" + tag);
				run(32);
			});
			r.check(lost(z) >= 25.0F && amp(z, MobEffects.WITHER) == 1 && amp(z, MobEffects.SLOWNESS) == 3,
					"Копья тьмы: 34 урона, Иссушение II, Замедление IV (" + hp(z) + ")");
			int airAfter = air(level, box.below(), 1);
			if (grief) r.check(airAfter > airBefore + 3, "Земля в гробу раздавлена (пустот " + airBefore + " → " + airAfter + ")");
			else r.check(airAfter == airBefore, "Без разрушения земля цела");
			Terrain.restoreAll();
			r.check(level.getBlockState(box.offset(2, 1, 0)).isAir(), "Гроб исчез, блоки вернулись" + tag);
			done(level, at);
		}

		BlockPos at = SpellTestKit.far(o, 22);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		Zombie a = zombie(level, at, 0, 12);
		Zombie b = zombie(level, at, 6, 12);
		SpellTestKit.griefing(false, () -> {
			Magic.castNow(p, s, 1.0F, 2);
			run(42);
		});
		r.check(lost(a) >= 20.0F && lost(b) >= 20.0F, "Мультикаст ×2: два гроба на двух врагах (" + hp(a) + ", " + hp(b) + ")");
		done(level, at);
	}

	// --- Ранг 6 ---

	private static void moonfang(ServerLevel level, BlockPos o, DevTests.Report r) {
		Spell s = spell("black_moonfang");
		for (boolean grief : new boolean[] {true, false}) {
			BlockPos at = SpellTestKit.far(o, grief ? 23 : 24);
			FakePlayer p = mage(level, at);
			arena(level, at.offset(0, 0, 26), 14, 8);
			face(p, at, 0);
			Zombie a = zombie(level, at, 0, 10);
			Zombie b = zombie(level, at, 4, 30);
			Zombie behind = zombie(level, at, 0, -6);
			BlockPos floor = at.offset(0, -1, 26);
			int airBefore = air(level, floor, 1);
			SpellTestKit.griefing(grief, () -> {
				Magic.castNow(p, s, 1.0F);
				run(34);
			});
			String tag = grief ? " (разрушение вкл.)" : " (разрушение выкл.)";
			r.check(lost(a) >= 40.0F && lost(b) >= 40.0F && DarkSpells.bleeding(a), "Серп: 48 урона и кровотечение по всей полосе (" + hp(a) + ", " + hp(b) + ")");
			r.check(lost(behind) == 0, "За спиной мага серп не режет");
			int airAfter = air(level, floor, 1);
			if (grief) r.check(airAfter >= airBefore + 3, "Траншея по земле" + tag + " (пустот " + airBefore + " → " + airAfter + ")");
			else r.check(airAfter == airBefore, "Без разрушения траншеи нет");
			done(level, at);
		}

		BlockPos at = SpellTestKit.far(o, 25);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		Zombie z = zombie(level, at, 0, 12);
		SpellTestKit.griefing(false, () -> {
			Magic.castNow(p, s, 1.0F, 2);
			run(34);
		});
		r.check(lost(z) >= 90.0F, "Мультикаст ×2: крест из двух серпов и взрыв пересечения (" + hp(z) + ")");
		done(level, at);
	}

	// --- Ранг 7 ---

	private static void shrine(ServerLevel level, BlockPos o, DevTests.Report r) {
		Spell s = spell("malevolent_shrine");
		BlockPos at = SpellTestKit.far(o, 26);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		List<Zombie> in = List.of(zombie(level, at, 5, 5), zombie(level, at, -10, 8), zombie(level, at, 0, 20));
		Zombie out = zombie(level, at, 0, 30);
		int broken = Terrain.brokenTotal();
		SpellTestKit.griefing(true, () -> {
			r.check(Magic.castNow(p, s, 1.0F), "Территория раскрыта");
			run(20);
			r.check(level.getBlockState(at.offset(2, 1, -4)).is(Blocks.BONE_BLOCK) && level.getBlockState(at.offset(0, 4, -6)).is(Blocks.RED_NETHER_BRICKS),
					"За спиной встало костяное святилище");
			r.check(amp(p, MobEffects.RESISTANCE) == 1, "Маг под Сопротивлением II");
			r.check(in.stream().allMatch(z -> SpellTestKit.hurt(z) && DarkSpells.bleeding(z)), "Разрезы по всем внутри, кровотечение");
			run(150);
		});
		r.check(in.stream().allMatch(z -> !z.isAlive() || lost(z) >= 50.0F),
				"За 8 с — десятки разрезов (" + hp(in.get(0)) + ", " + hp(in.get(1)) + ", " + hp(in.get(2)) + ")");
		r.check(lost(out) == 0, "Вне радиуса 24 — нетронут");
		r.check(Terrain.brokenTotal() > broken, "Местность нарезана (" + (Terrain.brokenTotal() - broken) + " блоков)");
		Terrain.restoreAll();
		r.check(!level.getBlockState(at.offset(2, 1, -4)).is(Blocks.BONE_BLOCK), "Святилище исчезло");
		done(level, at);

		BlockPos at2 = SpellTestKit.far(o, 27);
		FakePlayer p2 = mage(level, at2);
		face(p2, at2, 0);
		int broken2 = Terrain.brokenTotal();
		SpellTestKit.griefing(false, () -> {
			Magic.castNow(p2, s, 1.0F);
			run(40);
		});
		r.check(Terrain.brokenTotal() == broken2, "Без разрушения местность цела");
		done(level, at2);
	}
}
