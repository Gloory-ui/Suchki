package com.suchki.dev;

import static com.suchki.dev.SpellTestKit.clean;
import static com.suchki.dev.SpellTestKit.dummy;
import static com.suchki.dev.SpellTestKit.face;
import static com.suchki.dev.SpellTestKit.far;
import static com.suchki.dev.SpellTestKit.hp;
import static com.suchki.dev.SpellTestKit.mage;
import static com.suchki.dev.SpellTestKit.run;
import static com.suchki.dev.SpellTestKit.spell;

import com.suchki.magic.Magic;
import com.suchki.magic.MagicEffects;
import com.suchki.magic.Minds;
import com.suchki.magic.Minions;
import com.suchki.magic.spells.MindSpells;
import java.util.ArrayList;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Проверки заклинаний стихии разума: взор мудреца, страх, колыбельная, невидимость, безумие, власть над душами,
 * зеркальные двойники, истерия, лунный кошмар и совершенный гипноз.
 */
final class MindSpellTests {
	private MindSpellTests() {}

	/** Площадки, чьи чанки держим загруженными, пока идёт проверка. */
	private static final List<BlockPos> FORCED = new ArrayList<>();

	static void addTo(Map<String, DevTests.Test> tests) {
		add(tests, "Взор Мудреца", MindSpellTests::sageEye);
		add(tests, "Жажда Убийства", MindSpellTests::fear);
		add(tests, "Колыбельная", MindSpellTests::lullaby);
		add(tests, "Пустое Место", MindSpellTests::emptySpace);
		add(tests, "Безумие", MindSpellTests::frenzy);
		add(tests, "Глас Владыки Душ", MindSpellTests::dominate);
		add(tests, "Зеркальная Луна", MindSpellTests::mirrorMoon);
		add(tests, "Массовая Истерия", MindSpellTests::hysteria);
		add(tests, "Лунный Кошмар", MindSpellTests::nightmare);
		add(tests, "Совершенный Гипноз", MindSpellTests::hypnosis);
	}

	private static void add(Map<String, DevTests.Test> tests, String name, DevTests.Test test) {
		tests.put("Магия: Разум — " + name, (level, o, r) -> {
			try {
				test.run(level, o, r);
			} finally {
				for (BlockPos c : FORCED) load(level, c, false);
				FORCED.clear();
			}
		});
	}

	/** Держать (или отпустить) чанки вокруг точки на 24 блока и дождаться их загрузки. */
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

	private static BlockPos spot(ServerLevel level, BlockPos o, int i) {
		BlockPos c = far(o, 140 + i);
		hold(level, c);
		return c;
	}

	private static void hold(ServerLevel level, BlockPos c) {
		load(level, c, true);
		FORCED.add(c);
	}

	private static float lost(LivingEntity e) {
		return e.getMaxHealth() - e.getHealth();
	}

	/** Убрать мишени, двойников, следы заклинаний и состояние мага. */
	private static void tidy(ServerLevel level, BlockPos at, FakePlayer p) {
		clean();
		p.removeAttached(Magic.CASTING);
		p.removeAllEffects();
		level.getEntitiesOfClass(Entity.class, new AABB(at).inflate(60), e -> !(e instanceof Player)).forEach(Entity::discard);
	}

	/** Обычный моб без ИИ (у зомби — 20 здоровья, в отличие от мишени на 200). */
	private static <T extends Mob> T weak(ServerLevel level, EntityType<T> type, BlockPos at) {
		T e = DevTests.spawn(level, type, at);
		e.setPersistenceRequired();
		return e;
	}

	private static List<Mannequin> mirrors(ServerLevel level, BlockPos at, Player p) {
		return level.getEntitiesOfClass(Mannequin.class, new AABB(at).inflate(16), m -> m.isAlive() && Minions.isOwnedBy(m, p));
	}

	// --- Ранг 1 ---

	private static void sageEye(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = spot(level, o, 0);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			Zombie seen = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 6));
			Zombie hidden = dummy(level, EntityType.ZOMBIE, at.offset(10, 0, -10));
			hidden.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 2000, 0));
			Zombie farOne = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 45));
			r.check(Magic.castNow(p, spell("night_eye"), 1.0F), "Взор: творится");
			r.check(p.hasEffect(MobEffects.NIGHT_VISION) && p.getEffect(MobEffects.NIGHT_VISION).getDuration() >= 4000,
					"Взор: ночное зрение на 4 минуты");
			run(12);
			r.check(seen.hasEffect(MobEffects.GLOWING) && hidden.hasEffect(MobEffects.GLOWING), "Взор: волна подсветила существ в 32 блоках");
			r.check(!hidden.hasEffect(MobEffects.INVISIBILITY), "Взор: невидимый моб проявился");
			r.check(!farOne.hasEffect(MobEffects.GLOWING), "Взор: моб в 45 блоках не задет одиночным взором");
			Zombie late = dummy(level, EntityType.ZOMBIE, at.offset(-12, 0, 5));
			run(40);
			r.check(late.hasEffect(MobEffects.GLOWING), "Взор: импульс через 2 с отметил новичка");
			String panel = MindSpells.analysis(seen).getString();
			r.check(panel.contains("200") && panel.contains("⚔"), "Взор: разбор цели — " + panel);
			tidy(level, at, p);

			hold(level, at.offset(0, 0, 45));
			Zombie far2 = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 45));
			Magic.castNow(p, spell("night_eye"), 1.0F, 3);
			run(12);
			r.check(far2.hasEffect(MobEffects.GLOWING), "Взор ×3: обзор 64 блока — моб в 45 блоках подсвечен");
		} finally {
			tidy(level, at, p);
		}
	}

	private static void fear(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = spot(level, o, 1);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			Zombie a = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 6));
			Zombie b = dummy(level, EntityType.ZOMBIE, at.offset(2, 0, 7));
			Zombie c = dummy(level, EntityType.ZOMBIE, at.offset(6, 0, 6));
			r.check(Magic.castNow(p, spell("fear"), 1.0F), "Страх: творится");
			r.check(Minds.modeOf(a) == Minds.Mode.FEAR && Minds.modeOf(b) == Minds.Mode.FEAR, "Страх: мобы в радиусе 4 бегут");
			r.check(Minds.modeOf(c) == null, "Страх: моб в 6 блоках не задет");
			for (Zombie z : List.of(a, b, c)) Minds.clear(z);
			Magic.castNow(p, spell("fear"), 1.0F, 2);
			r.check(Minds.modeOf(c) == Minds.Mode.FEAR && Minds.modeOf(a) == Minds.Mode.FEAR, "Страх ×2: вторая волна у соседнего врага");
		} finally {
			tidy(level, at, p);
		}
	}

	// --- Ранг 2 ---

	private static void lullaby(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = spot(level, o, 2);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			Zombie small = weak(level, EntityType.ZOMBIE, at.offset(0, 0, 6));
			Zombie big = dummy(level, EntityType.ZOMBIE, at.offset(2, 0, 6));
			EnderMan ender = weak(level, EntityType.ENDERMAN, at.offset(-2, 0, 6));
			r.check(Magic.castNow(p, spell("calm"), 1.0F), "Колыбельная: творится");
			r.check(Minds.modeOf(small) == Minds.Mode.CALM && Minds.modeOf(big) == Minds.Mode.CALM, "Колыбельная: мобы умиротворены");
			r.check(small.hasEffect(MagicEffects.PARALYSIS) && !big.hasEffect(MagicEffects.PARALYSIS),
					"Колыбельная: слабый (20 здоровья) уснул, сильный (200) — нет");
			r.check(Minds.modeOf(ender) == null, "Колыбельная: эндермен не поддаётся");
			run(2);
			small.hurtServer(level, level.damageSources().playerAttack(p), 4.0F);
			run(1);
			r.check(!small.hasEffect(MagicEffects.PARALYSIS) && small.getHealth() <= 14.5F,
					"Колыбельная: удар по спящему +50% разумом и будит (" + hp(small) + ")");
		} finally {
			tidy(level, at, p);
		}
	}

	private static void emptySpace(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = spot(level, o, 3);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			Zombie hunter = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 10));
			hunter.setTarget(p);
			r.check(Magic.castNow(p, spell("invisibility"), 1.0F), "Пустое Место: творится");
			r.check(p.hasEffect(MobEffects.INVISIBILITY) && p.getEffect(MobEffects.INVISIBILITY).getDuration() >= 590,
					"Пустое Место: невидимость 30 с");
			r.check(hunter.getTarget() == null && Minds.modeOf(hunter) == Minds.Mode.CALM, "Пустое Место: преследователь потерял цель");
			run(2);
			p.setAttached(Magic.CASTING, "frenzy");
			run(1);
			r.check(!p.hasEffect(MobEffects.INVISIBILITY), "Пустое Место: зарядка заклинания 3 ранга выдаёт мага");
			p.removeAttached(Magic.CASTING);
			run(42);
			r.check(p.hasEffect(MobEffects.INVISIBILITY), "Пустое Место: через 2 с маг снова невидим");
			p.setLastHurtMob(hunter);
			run(1);
			r.check(!p.hasEffect(MobEffects.INVISIBILITY), "Пустое Место: удар выдаёт мага");
			tidy(level, at, p);

			Villager friend = weak(level, EntityType.VILLAGER, at.offset(1, 0, 1));
			Magic.castNow(p, spell("invisibility"), 1.0F, 2);
			r.check(friend.hasEffect(MobEffects.INVISIBILITY) && p.hasEffect(MobEffects.INVISIBILITY), "Пустое Место ×2: спрятан и союзник рядом");
		} finally {
			tidy(level, at, p);
		}
	}

	// --- Ранг 3 ---

	private static void frenzy(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = spot(level, o, 4);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			Zombie a = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 8));
			Zombie b = dummy(level, EntityType.ZOMBIE, at.offset(2, 0, 9));
			Zombie c = dummy(level, EntityType.ZOMBIE, at.offset(9, 0, 8));
			r.check(Magic.castNow(p, spell("frenzy"), 1.0F), "Безумие: творится");
			r.check(Minds.modeOf(a) == Minds.Mode.FRENZY && Minds.modeOf(b) == Minds.Mode.FRENZY, "Безумие: мобы в радиусе 5 в бешенстве");
			r.check(Minds.modeOf(c) == null, "Безумие: моб в 9 блоках не задет");
			double base = a.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).getBaseValue();
			double now = a.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
			r.check(MindSpells.enraged(a) && now > base * 1.15, "Безумие: урон +20% (" + base + " → " + now + ")");
			run(301);
			r.check(!MindSpells.enraged(a), "Безумие: через 15 с ярость снята");
		} finally {
			tidy(level, at, p);
		}
	}

	// --- Ранг 4 ---

	private static void dominate(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = spot(level, o, 5);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			Zombie a = weak(level, EntityType.ZOMBIE, at.offset(0, 0, 8));
			Zombie b = weak(level, EntityType.ZOMBIE, at.offset(2, 0, 9));
			Zombie strong = dummy(level, EntityType.ZOMBIE, at.offset(-2, 0, 9));
			r.check(Magic.castNow(p, spell("dominate"), 1.0F), "Глас Владыки: творится");
			r.check(Minions.isOwnedBy(a, p) && Minions.isOwnedBy(b, p), "Глас Владыки: два слабых моба стали слугами");
			r.check(a.hasCustomName() && a.hasEffect(MobEffects.GLOWING) && MindSpells.enraged(a), "Глас Владыки: имя слуги, свечение, +20% урона");
			r.check(MindSpells.servants(p) == 2, "Глас Владыки: у мага 2 слуги (" + MindSpells.servants(p) + ")");
			r.check(!Minions.isSummon(strong) && lost(strong) >= 9.5F && strong.hasEffect(MobEffects.NAUSEA),
					"Глас Владыки: сильный (200 здоровья) не подчинился — 10 урона и тошнота (" + hp(strong) + ")");
			a.hurtServer(level, level.damageSources().playerAttack(p), 1.0F);
			run(1);
			r.check(!Minions.isSummon(a) && !a.hasCustomName() && !MindSpells.enraged(a), "Глас Владыки: удар мага по слуге снимает чары");
			run(1200);
			r.check(!Minions.isSummon(b) && !b.hasCustomName() && b.isAlive(), "Глас Владыки: через 60 с слуга снова сам по себе");
			r.check(MindSpells.servants(p) == 0, "Глас Владыки: слуг не осталось");
			face(p, at, 0, -60);
			r.check(!Magic.castNow(p, spell("dominate"), 1.0F), "Глас Владыки: без цели не творится");
		} finally {
			tidy(level, at, p);
		}
	}

	private static void mirrorMoon(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = spot(level, o, 6);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			Zombie z = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 8));
			r.check(Magic.castNow(p, spell("mirror_image"), 1.0F), "Зеркальная Луна: творится");
			List<Mannequin> twins = mirrors(level, at, p);
			r.check(twins.size() == 2, "Зеркальная Луна: два двойника (" + twins.size() + ")");
			r.check(p.hasEffect(MobEffects.INVISIBILITY), "Зеркальная Луна: маг невидим");
			r.check(Minds.modeOf(z) == Minds.Mode.DECOY && z.getTarget() instanceof Mannequin, "Зеркальная Луна: враг целится в двойника");
			if (!twins.isEmpty()) {
				Mannequin d = twins.get(0);
				r.check(Math.abs(d.getMaxHealth() - 20.0F) < 0.01F, "Зеркальная Луна: у двойника 20 здоровья (" + hp(d) + ")");
				Zombie near = dummy(level, EntityType.ZOMBIE, d.blockPosition().offset(1, 0, 0));
				d.hurtServer(level, level.damageSources().mobAttack(z), 100.0F);
				run(1);
				r.check(lost(near) >= 3.8F && near.hasEffect(MobEffects.BLINDNESS), "Зеркальная Луна: осколки — 4 урона и слепота (" + hp(near) + ")");
				r.check(d.isRemoved() || !d.isAlive(), "Зеркальная Луна: двойник разбит");
			}
			tidy(level, at, p);

			Magic.castNow(p, spell("mirror_image"), 1.0F, 3);
			r.check(mirrors(level, at, p).size() == 4, "Зеркальная Луна ×3: четыре двойника (" + mirrors(level, at, p).size() + ")");
		} finally {
			tidy(level, at, p);
		}
	}

	// --- Ранг 5 ---

	private static void hysteria(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = spot(level, o, 7);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			List<Zombie> in = List.of(dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 6)), dummy(level, EntityType.ZOMBIE, at.offset(5, 0, -8)),
					dummy(level, EntityType.ZOMBIE, at.offset(12, 0, 0)));
			Zombie out = dummy(level, EntityType.ZOMBIE, at.offset(-20, 0, 0));
			r.check(Magic.castNow(p, spell("mass_hysteria"), 1.0F), "Истерия: творится");
			run(16);
			boolean all = true;
			for (Zombie z : in) {
				Minds.Mode m = Minds.modeOf(z);
				all &= lost(z) >= 11.5F && (m == Minds.Mode.FEAR && z.hasEffect(MobEffects.WEAKNESS) || m == Minds.Mode.FRENZY);
			}
			r.check(all, "Истерия: враги в 16 блоках — 12 урона, бегство со слабостью или безумие");
			r.check(lost(out) < 0.01F && Minds.modeOf(out) == null, "Истерия: моб в 20 блоках не задет");
			tidy(level, at, p);

			Zombie out2 = dummy(level, EntityType.ZOMBIE, at.offset(-20, 0, 0));
			Magic.castNow(p, spell("mass_hysteria"), 1.0F, 2);
			run(16);
			r.check(lost(out2) >= 11.5F, "Истерия ×2: радиус 22 — моб в 20 блоках задет (" + hp(out2) + ")");
		} finally {
			tidy(level, at, p);
		}
	}

	// --- Ранг 6 ---

	private static void nightmare(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = spot(level, o, 8);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			Zombie main = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 8));
			Zombie s1 = dummy(level, EntityType.ZOMBIE, at.offset(2, 0, 9));
			Zombie s2 = dummy(level, EntityType.ZOMBIE, at.offset(-2, 0, 9));
			Zombie farOne = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 17));
			r.check(Magic.castNow(p, spell("moonlit_nightmare"), 1.0F), "Лунный Кошмар: творится");
			r.check(main.hasEffect(MagicEffects.PARALYSIS) && main.getEffect(MagicEffects.PARALYSIS).getDuration() >= 110,
					"Лунный Кошмар: цель оцепенела на 6 с");
			r.check(s1.hasEffect(MagicEffects.PARALYSIS) && lost(s1) >= 14.5F && lost(s2) >= 14.5F,
					"Лунный Кошмар: соседи — оцепенение и 15 урона (" + hp(s1) + ")");
			r.check(lost(farOne) < 0.01F, "Лунный Кошмар: моб в 9 блоках от цели не задет");
			run(121);
			r.check(lost(main) >= 38.5F, "Лунный Кошмар: цель получила 40 урона разуму (" + hp(main) + ")");
			MobEffectInstance weak = main.getEffect(MobEffects.WEAKNESS);
			r.check(weak != null && weak.getAmplifier() == 1 && Minds.modeOf(main) == Minds.Mode.FEAR, "Лунный Кошмар: затем бегство и слабость II");
			tidy(level, at, p);
			face(p, at, 0, -60);
			r.check(!Magic.castNow(p, spell("moonlit_nightmare"), 1.0F), "Лунный Кошмар: без цели не творится");
		} finally {
			tidy(level, at, p);
		}
	}

	// --- Ранг 7 ---

	private static void hypnosis(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = spot(level, o, 9);
		hold(level, at.offset(32, 0, 32));
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			List<Zombie> in = List.of(dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 6)), dummy(level, EntityType.ZOMBIE, at.offset(8, 0, 8)),
					dummy(level, EntityType.ZOMBIE, at.offset(-15, 0, -15)), dummy(level, EntityType.ZOMBIE, at.offset(20, 0, -25)));
			Zombie out = dummy(level, EntityType.ZOMBIE, at.offset(32, 0, 32));
			r.check(Magic.castNow(p, spell("perfect_hypnosis"), 1.0F), "Гипноз: творится");
			run(31);
			boolean caught = true;
			int frenzy = 0, decoy = 0;
			for (Zombie z : in) {
				Minds.Mode m = Minds.modeOf(z);
				caught &= z.hasEffect(MobEffects.GLOWING) && MindSpells.enraged(z) && (m == Minds.Mode.FRENZY || m == Minds.Mode.DECOY);
				if (m == Minds.Mode.FRENZY) frenzy++;
				if (m == Minds.Mode.DECOY) decoy++;
			}
			r.check(caught, "Гипноз: враги в 40 блоках в иллюзии (бешенство " + frenzy + ", охота на двойников " + decoy + ")");
			r.check(frenzy > 0 && decoy > 0, "Гипноз: одни дерутся друг с другом, другие — с двойниками");
			r.check(mirrors(level, at, p).size() == 6, "Гипноз: шесть двойников мага (" + mirrors(level, at, p).size() + ")");
			r.check(Minds.modeOf(out) == null && !out.hasEffect(MobEffects.GLOWING), "Гипноз: моб в 45 блоках вне купола");
			List<Mannequin> twins = mirrors(level, at, p);
			if (!twins.isEmpty()) twins.get(0).hurtServer(level, level.damageSources().mobAttack(in.get(0)), 5.0F);
			run(20);
			r.check(mirrors(level, at, p).size() == 6, "Гипноз: разбитый двойник возродился");
			run(200);
			boolean done = true;
			for (Zombie z : in) {
				MobEffectInstance slow = z.getEffect(MobEffects.SLOWNESS);
				done &= lost(z) >= 106.0F && slow != null && slow.getAmplifier() == 2;
			}
			r.check(done, "Гипноз: 80 урона за 10 с и 30 в финале, замедление III (" + hp(in.get(0)) + ")");
			r.check(mirrors(level, at, p).isEmpty(), "Гипноз: двойники разбиты");
			r.check(lost(out) < 0.01F, "Гипноз: вне купола урона нет");
		} finally {
			tidy(level, at, p);
		}
	}
}
