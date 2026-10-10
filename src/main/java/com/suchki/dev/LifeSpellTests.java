package com.suchki.dev;

import static com.suchki.dev.SpellTestKit.clean;
import static com.suchki.dev.SpellTestKit.dummy;
import static com.suchki.dev.SpellTestKit.face;
import static com.suchki.dev.SpellTestKit.far;
import static com.suchki.dev.SpellTestKit.griefing;
import static com.suchki.dev.SpellTestKit.hp;
import static com.suchki.dev.SpellTestKit.mage;
import static com.suchki.dev.SpellTestKit.run;
import static com.suchki.dev.SpellTestKit.spell;

import com.suchki.magic.Magic;
import com.suchki.magic.MagicEffects;
import com.suchki.magic.Spell;
import com.suchki.magic.Terrain;
import java.util.Map;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Проверки заклинаний Жизни: лечение, очищение, баффы и печать на жителях-союзниках, корни и святой урон на
 * зомби-мишенях, временные деревья/родник/Мировое Древо (появляются без разрушения и исчезают после уборки).
 */
final class LifeSpellTests {
	private LifeSpellTests() {}

	static void addTo(Map<String, DevTests.Test> tests) {
		tests.put("Магия: Жизнь — Мистическая ладонь", LifeSpellTests::palm);
		tests.put("Магия: Жизнь — Луг цветущих воспоминаний", LifeSpellTests::meadow);
		tests.put("Магия: Жизнь — Исцеление союзника", LifeSpellTests::healOther);
		tests.put("Магия: Жизнь — Очищение", LifeSpellTests::cure);
		tests.put("Магия: Жизнь — Обновление", LifeSpellTests::rejuvenation);
		tests.put("Магия: Жизнь — Священный родник", LifeSpellTests::wellspring);
		tests.put("Магия: Жизнь — Печать бессмертия", LifeSpellTests::undying);
		tests.put("Магия: Жизнь — Явление дремучего леса", LifeSpellTests::deepForest);
		tests.put("Магия: Жизнь — Цветение Иггдрасиля", LifeSpellTests::yggdrasil);
	}

	// --- Ранг 1 ---

	private static void palm(ServerLevel level, BlockPos o, DevTests.Report r) {
		clean();
		load(level, o, 24, true);
		FakePlayer p = mage(level, o);
		Spell s = spell("healing");
		float bonus = Magic.spellBonus(p);
		r.check(s.tier == 1 && Magic.STARTER.contains(s.id), "healing — стартовое заклинание 1 ранга");
		face(p, o, 0);
		p.setHealth(10);
		Magic.channelNow(p, s, 20, 1.0F);
		r.check(Math.abs(p.getHealth() - (10 + 2 * bonus)) < 0.05, "Ладонь без союзника лечит мага: 2 × 1 за 1 с (" + p.getHealth() + ")");

		Villager v = ally(level, o.offset(0, 0, 3), 100);
		v.addEffect(new MobEffectInstance(MobEffects.POISON, 600, 0));
		float self = p.getHealth();
		Magic.channelNow(p, s, 61, 1.0F);
		r.check(v.getHealth() >= 100 + 6 * 1.2F * bonus - 0.05 && Math.abs(p.getHealth() - self) < 0.01,
				"Союзник под взглядом лечится ×1,2 вместо мага (" + hp(v) + ")");
		r.check(!v.hasEffect(MobEffects.POISON), "3 с на одном пациенте снимают дебафф");

		Villager second = ally(level, o.offset(2, 0, 1), 100);
		Magic.channelNow(p, s, 10, 1.0F, 2);
		r.check(second.getHealth() > 100.5F, "Мультикаст ×2: копия лечит ещё одного раненого союзника (" + hp(second) + ")");
		clean();
	}

	private static void meadow(ServerLevel level, BlockPos o, DevTests.Report r) {
		clean();
		load(level, o, 24, true);
		FakePlayer p = mage(level, o);
		for (int dx = -12; dx <= 12; dx++) {
			for (int dz = -12; dz <= 12; dz++) level.setBlock(o.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
		}
		face(p, o, 0);
		Villager v = ally(level, o.offset(2, 0, 2), 100);
		v.addEffect(new MobEffectInstance(MobEffects.POISON, 600, 1));
		Zombie z = foe(level, o.offset(-2, 0, 2));
		r.check(Magic.castNow(p, spell("flower_field"), 1.0F), "Луг сотворён");
		run(12);
		int flowers = count(level, o, 6, 1, b -> b.defaultBlockState().is(BlockTags.SMALL_FLOWERS));
		r.check(flowers >= 10, "На траве распустились цветы (" + flowers + ")");
		MobEffectInstance regen = v.getEffect(MobEffects.REGENERATION);
		MobEffectInstance poison = v.getEffect(MobEffects.POISON);
		r.check(regen != null && regen.getAmplifier() == 0 && regen.getDuration() >= 150, "Союзнику — Регенерация I на 8 с");
		r.check(poison != null && poison.getAmplifier() == 0, "Яд ослаблен на уровень");
		r.check(z.hasEffect(MobEffects.SLOWNESS), "Нежить на лугу замедлена");
		Magic.castNow(p, spell("flower_field"), 1.0F, 2);
		run(12);
		regen = v.getEffect(MobEffects.REGENERATION);
		r.check(regen != null && regen.getDuration() >= 190, "Мультикаст ×2: регенерация длиннее (" + (regen == null ? 0 : regen.getDuration()) + " т)");
		clean();
	}

	// --- Ранг 2 ---

	private static void healOther(ServerLevel level, BlockPos o, DevTests.Report r) {
		clean();
		load(level, o, 24, true);
		FakePlayer p = mage(level, o);
		Spell s = spell("heal_other");
		float bonus = Magic.spellBonus(p);
		face(p, o, 0);
		Villager v = ally(level, o.offset(0, 0, 6), 100);
		v.addEffect(new MobEffectInstance(MobEffects.POISON, 600, 0));
		v.setRemainingFireTicks(100);
		r.check(Magic.castNow(p, s, 1.0F), "Исцеление союзника сотворено");
		r.check(Math.abs(v.getHealth() - (100 + 9 * bonus)) < 0.05 && !v.hasEffect(MobEffects.POISON) && v.getRemainingFireTicks() <= 0,
				"Союзник: +9 здоровья, дебафф и огонь сняты (" + hp(v) + ")");
		v.discard();

		Zombie z = foe(level, o.offset(0, 0, 6));
		Magic.castNow(p, s, 1.0F);
		r.check(Math.abs(200 - z.getHealth() - 7 * bonus) < 0.5, "Нежить под прицелом — 7 урона святостью (" + hp(z) + ")");
		z.discard();
		r.check(!Magic.castNow(p, s, 1.0F), "Без цели заклинание не срабатывает");

		Villager a = ally(level, o.offset(0, 0, 8), 100);
		Villager b = ally(level, o.offset(2, 0, 8), 100);
		Magic.castNow(p, s, 1.0F, 2);
		r.check(a.getHealth() > 105 && b.getHealth() > 105, "Мультикаст ×2: лечатся двое у прицела (" + hp(a) + ", " + hp(b) + ")");
		clean();
	}

	private static void cure(ServerLevel level, BlockPos o, DevTests.Report r) {
		clean();
		load(level, o, 24, true);
		FakePlayer p = mage(level, o);
		Spell s = spell("cure");
		float bonus = Magic.spellBonus(p);
		p.setHealth(10);
		p.addEffect(new MobEffectInstance(MobEffects.POISON, 600, 1));
		p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 0));
		p.addEffect(new MobEffectInstance(MobEffects.SPEED, 600, 0));
		p.setRemainingFireTicks(100);
		p.setTicksFrozen(100);
		r.check(Magic.castNow(p, s, 1.0F), "Очищение сотворено");
		r.check(harmful(p) == 0 && p.hasEffect(MobEffects.SPEED), "Все дебаффы сняты, полезные эффекты остались");
		r.check(p.getRemainingFireTicks() <= 0 && p.getTicksFrozen() == 0, "Огонь и холод сняты");
		r.check(Math.abs(p.getHealth() - Math.min(20, 10 + 4 * bonus)) < 0.05, "Лечит 4 (" + p.getHealth() + ")");
		run(40);
		p.addEffect(new MobEffectInstance(MobEffects.POISON, 600, 0));
		p.addEffect(new MobEffectInstance(MobEffects.HUNGER, 600, 0));
		run(1);
		r.check(!p.hasEffect(MobEffects.POISON) && !p.hasEffect(MobEffects.HUNGER), "5 с защиты от яда и голода");
		run(80);
		p.addEffect(new MobEffectInstance(MobEffects.POISON, 600, 0));
		run(1);
		r.check(p.hasEffect(MobEffects.POISON), "Защита кончается через 5 с");

		Villager v = ally(level, o.offset(2, 0, 1), 100);
		v.addEffect(new MobEffectInstance(MobEffects.WITHER, 600, 0));
		Magic.castNow(p, s, 1.0F, 2);
		r.check(!v.hasEffect(MobEffects.WITHER) && v.getHealth() > 100, "Мультикаст ×2: очищен и союзник рядом");
		clean();
	}

	// --- Ранг 3 ---

	private static void rejuvenation(ServerLevel level, BlockPos o, DevTests.Report r) {
		clean();
		load(level, o, 24, true);
		FakePlayer p = mage(level, o);
		Spell s = spell("rejuvenation");
		float bonus = Magic.spellBonus(p);
		Villager near = ally(level, o.offset(0, 0, 5), 100);
		Villager distant = ally(level, o.offset(0, 0, 11), 100);
		Zombie z = foe(level, o.offset(3, 0, 0));
		r.check(Magic.castNow(p, s, 1.0F), "Обновление сотворено");
		MobEffectInstance regen = near.getEffect(MobEffects.REGENERATION);
		r.check(regen != null && regen.getAmplifier() == 1 && regen.getDuration() >= 190 && Math.abs(near.getHealth() - (100 + 3 * bonus)) < 0.05,
				"Союзник в 8 блоках: Регенерация II 10 с и +3 (" + hp(near) + ")");
		r.check(distant.getEffect(MobEffects.REGENERATION) == null, "Дальше 8 блоков не действует");
		r.check(!z.hasEffect(MobEffects.REGENERATION), "Врагов не лечит");
		Magic.castNow(p, s, 1.0F, 2);
		r.check(distant.hasEffect(MobEffects.REGENERATION), "Мультикаст ×2: радиус 12");
		clean();
	}

	// --- Ранг 4 ---

	private static void wellspring(ServerLevel level, BlockPos o, DevTests.Report r) {
		clean();
		load(level, o, 24, true);
		FakePlayer p = mage(level, o);
		Spell s = spell("healing_circle");
		float bonus = Magic.spellBonus(p);
		p.setShiftKeyDown(true);
		Villager v = ally(level, o.offset(3, 0, 0), 100);
		v.addEffect(new MobEffectInstance(MobEffects.POISON, 600, 0));
		Zombie z = foe(level, o.offset(-3, 0, 1));
		griefing(false, () -> r.check(Magic.castNow(p, s, 1.0F), "Родник сотворён (сидя — у ног, разрушение выключено)"));
		p.setShiftKeyDown(false);
		r.check(level.getBlockState(o.offset(1, 0, 0)).is(Blocks.MOSSY_STONE_BRICK_SLAB) && Terrain.tempCount() >= 8,
				"Чаша родника из временных плит (" + Terrain.tempCount() + " врем. блоков)");
		Arrow arrow = DevTests.spawn(level, EntityType.ARROW, o.offset(0, 1, 3));
		arrow.setOwner(z);
		run(1);
		r.check(arrow.isRemoved(), "Вражеская стрела в роднике сгорела");
		run(40);
		r.check(v.getHealth() >= 100 + 8 * bonus - 0.05, "Союзник: 6 при входе + 1/0,5 с (" + hp(v) + ")");
		r.check(!v.hasEffect(MobEffects.POISON), "Дебафф снят родником");
		r.check(z.getHealth() <= 200 - 8 * bonus + 0.5, "Нежить горит: 4 урона в секунду (" + hp(z) + ")");
		r.check(z.hasEffect(MobEffects.SLOWNESS), "Нежить в роднике замедлена");
		clean();
		r.check(level.getBlockState(o.offset(1, 0, 0)).isAir() && Terrain.tempCount() == 0, "Плиты родника исчезают");
	}

	// --- Ранг 5 ---

	private static void undying(ServerLevel level, BlockPos o, DevTests.Report r) {
		clean();
		load(level, o, 24, true);
		FakePlayer p = mage(level, o);
		Spell s = spell("undying");
		face(p, o, 0);
		Villager v = ally(level, o.offset(0, 0, 5), 200);
		Zombie z = foe(level, o.offset(2, 0, 5));
		r.check(Magic.castNow(p, s, 1.0F), "Печать сотворена");
		MobEffectInstance seal = v.getEffect(MagicEffects.UNDYING);
		r.check(seal != null && seal.getDuration() >= 1190 && !p.hasEffect(MagicEffects.UNDYING), "Печать на 60 с легла на союзника под прицелом");
		v.hurtServer(level, level.damageSources().generic(), 10000);
		r.check(v.isAlive() && Math.abs(v.getHealth() - 100) < 1 && !v.hasEffect(MagicEffects.UNDYING),
				"Смертельный удар оставил половину здоровья, печать израсходована (" + hp(v) + ")");
		r.check(v.hasEffect(MobEffects.ABSORPTION) && v.hasEffect(MobEffects.REGENERATION), "Спасённому — Регенерация и Поглощение");
		r.check(z.getDeltaMovement().horizontalDistance() > 0.3, "Золотой взрыв отбросил врага рядом");
		Magic.castNow(p, s, 1.0F, 2);
		r.check(v.hasEffect(MagicEffects.UNDYING) && p.hasEffect(MagicEffects.UNDYING), "Мультикаст ×2: печать и на мага");
		p.removeAllEffects();
		clean();
	}

	// --- Ранг 6 ---

	private static void deepForest(ServerLevel level, BlockPos o, DevTests.Report r) {
		clean();
		BlockPos f = far(o, 0);
		load(level, f, 24, true);
		FakePlayer p = mage(level, f);
		Spell s = spell("deep_forest_bloom");
		float bonus = Magic.spellBonus(p);
		face(p, f, 20);
		Zombie z = foe(level, f.offset(3, 0, 7));
		Villager v = ally(level, f.offset(-4, 0, 6), 100);
		griefing(false, () -> {
			r.check(Magic.castNow(p, s, 1.0F), "Лес сотворён (разрушение выключено)");
			MobEffectInstance slow = z.getEffect(MobEffects.SLOWNESS);
			r.check(slow != null && slow.getAmplifier() >= 6 && slow.getDuration() >= 95, "Корни держат врага 5 с");
			run(12);
			r.check(200 - z.getHealth() >= 12 * bonus * 0.95F, "Ствол бьёт на 12 (" + hp(z) + ")");
			r.check(v.getHealth() >= 100 + 4 * bonus - 0.05 && amp(v, MobEffects.REGENERATION) == 1, "Союзнику — +4 и Регенерация II");
			run(30);
			int logs = count(level, f.offset(0, 0, 4), 13, 9, b -> b == Blocks.OAK_LOG);
			int leaves = count(level, f.offset(0, 0, 4), 14, 11, b -> b == Blocks.OAK_LEAVES);
			r.check(logs >= 30 && leaves >= 30, "Выросли деревья из временных блоков (брёвен " + logs + ", листвы " + leaves + ")");
			run(20);
			r.check(200 - z.getHealth() >= 36 * bonus * 0.95F, "Через 3 с стволы давят: всего 36 (" + hp(z) + ")");
			clean();
			int left = count(level, f.offset(0, 0, 4), 14, 11, b -> b == Blocks.OAK_LOG || b == Blocks.OAK_LEAVES);
			r.check(left == 0, "Лес исчезает без следа (осталось " + left + ")");
		});
		z.discard();
		v.discard();
		load(level, f, 24, false);
	}

	// --- Ранг 7 ---

	private static void yggdrasil(ServerLevel level, BlockPos o, DevTests.Report r) {
		clean();
		BlockPos f = far(o, 1);
		load(level, f, 40, true);
		FakePlayer p = mage(level, f);
		Spell s = spell("yggdrasil_bloom");
		float bonus = Magic.spellBonus(p);
		r.check(s.tier == 7 && s.maxCopies == 1, "Божественный ранг без мультикаста");
		face(p, f, (float) Math.toDegrees(Math.atan2(1.62, 8.0)));
		Villager v = ally(level, f.offset(5, 0, 4), 40);
		v.addEffect(new MobEffectInstance(MobEffects.POISON, 600, 1));
		Zombie z = foe(level, f.offset(-6, 0, 16));
		Wolf w = DevTests.spawn(level, EntityType.WOLF, f.offset(-3, 0, 0));
		w.tame(p);
		w.hurtServer(level, level.damageSources().generic(), 1000);
		r.check(!w.isAlive(), "Питомец пал до заклинания");
		p.setAttached(Magic.MANA, 0.0F);
		griefing(false, () -> {
			r.check(Magic.castNow(p, s, 1.0F), "Иггдрасиль сотворён (разрушение выключено)");
			run(21);
			r.check(v.getHealth() >= v.getMaxHealth() - 0.01 && harmful(v) == 0, "Союзник полностью исцелён и очищен (" + hp(v) + ")");
			r.check(amp(v, MobEffects.REGENERATION) == 2 && amp(v, MobEffects.ABSORPTION) == 3 && v.hasEffect(MagicEffects.UNDYING),
					"Регенерация III, Поглощение IV и Печать бессмертия");
			r.check(Magic.mana(p) >= 0.29F * Magic.maxMana(p), "Магу вернулось 30% маны (" + Magic.mana(p) + ")");
			int pets = level.getEntitiesOfClass(Wolf.class, new AABB(f).inflate(24), Wolf::isAlive).size();
			r.check(pets == 1, "Павший питомец вернулся к стволу (" + pets + ")");
			r.check(amp(z, MobEffects.SLOWNESS) >= 6, "Корни схватили врага");
			run(40);
			int wood = count(level, f.offset(0, 0, 8), 4, 34, b -> b == Blocks.OAK_WOOD);
			int crown = count(level, f.offset(0, 20, 8), 16, 20, b -> b == Blocks.OAK_LEAVES || b == Blocks.FLOWERING_AZALEA_LEAVES || b == Blocks.SHROOMLIGHT);
			r.check(wood >= 200 && crown >= 200, "Выросло Мировое Древо (ствол " + wood + ", крона " + crown + ")");
			run(100);
			float dealt = z.isAlive() ? 200 - z.getHealth() : 200;
			r.check(dealt >= 98 * bonus * 0.95F, "Корни 7 раз по 14 (+ святость по нежити): " + dealt);
			clean();
			int left = count(level, f.offset(0, 16, 8), 16, 24, b -> b == Blocks.OAK_WOOD || b == Blocks.OAK_LOG || b == Blocks.OAK_LEAVES
					|| b == Blocks.SHROOMLIGHT);
			r.check(left == 0 && Terrain.tempCount() == 0, "Древо исчезает без следа (осталось " + left + ")");
		});
		for (Entity e : level.getEntitiesOfClass(Entity.class, new AABB(f).inflate(40), e -> !(e instanceof net.minecraft.world.entity.player.Player))) e.discard();
		p.removeAllEffects();
		load(level, f, 40, false);
	}

	// --- Помощники ---

	/**
	 * Держать чанки вокруг точки загруженными: у ненастоящего игрока чанки вокруг могут быть не готовы, и тогда
	 * поиск существ их не видит.
	 */
	private static void load(ServerLevel level, BlockPos c, int r, boolean on) {
		for (int cx = (c.getX() - r) >> 4; cx <= (c.getX() + r) >> 4; cx++) {
			for (int cz = (c.getZ() - r) >> 4; cz <= (c.getZ() + r) >> 4; cz++) {
				level.setChunkForced(cx, cz, on);
				if (on) level.getChunk(cx, cz);
			}
		}
		if (!on) return;
		// Чанк становится «видимым» для поиска существ отложенной задачей сервера — прогоняем их сразу.
		long until = System.nanoTime() + 40_000_000L;
		while (System.nanoTime() < until) {
			int n = 0;
			while (n < 10_000 && level.getChunkSource().pollTask()) n++;
			Thread.onSpinWait();
		}
	}

	/** Житель-союзник (200 здоровья) с заданным здоровьем. */
	private static Villager ally(ServerLevel level, BlockPos at, float health) {
		Villager v = dummy(level, EntityType.VILLAGER, at);
		v.setHealth(health);
		return v;
	}

	private static Zombie foe(ServerLevel level, BlockPos at) {
		return dummy(level, EntityType.ZOMBIE, at);
	}

	private static int harmful(LivingEntity e) {
		int n = 0;
		for (MobEffectInstance i : e.getActiveEffects()) if (i.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) n++;
		return n;
	}

	private static int amp(LivingEntity e, Holder<MobEffect> effect) {
		MobEffectInstance i = e.getEffect(effect);
		return i == null ? -1 : i.getAmplifier();
	}

	/** Сколько блоков, подходящих под условие, в квадрате радиуса {@code r} и высотой {@code h} над точкой. */
	private static int count(ServerLevel level, BlockPos c, int r, int h, java.util.function.Predicate<Block> what) {
		int n = 0;
		for (BlockPos q : BlockPos.betweenClosed(c.offset(-r, 0, -r), c.offset(r, h, r))) {
			if (what.test(level.getBlockState(q).getBlock())) n++;
		}
		return n;
	}
}
