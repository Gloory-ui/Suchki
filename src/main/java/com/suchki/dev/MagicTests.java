package com.suchki.dev;

import com.suchki.magic.Cast;
import com.suchki.magic.FocusItem;
import com.suchki.magic.Magic;
import com.suchki.magic.MagicBlocks;
import com.suchki.magic.MagicDamage;
import com.suchki.magic.MagicEffects;
import com.suchki.magic.MagicItems;
import com.suchki.magic.Minds;
import com.suchki.magic.Minions;
import com.suchki.magic.SpectralItem;
import com.suchki.magic.Spell;
import com.suchki.magic.SpellProjectile;
import com.suchki.magic.Spells;
import com.suchki.magic.Zones;
import com.suchki.skill.PlayerSkills;
import com.suchki.skill.Skills;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Самопроверка магии: роль и стартовый набор, изучение, мана и накопление силы, фокусы, перезарядки и все
 * 49 заклинаний по одному (эффект на мишенях), призванные, иллюзии, призрачные предметы.
 */
final class MagicTests {
	private MagicTests() {}

	static void addTo(Map<String, DevTests.Test> tests) {
		tests.put("Магия: роль, книги, мана", MagicTests::basics);
		tests.put("Магия: накопление силы", MagicTests::charging);
		tests.put("Магия: Разрушение", MagicTests::destruction);
		tests.put("Магия: Восстановление", MagicTests::restoration);
		tests.put("Магия: Свет", MagicTests::light);
		tests.put("Магия: Тьма", MagicTests::dark);
		tests.put("Магия: Изменение", MagicTests::alteration);
		tests.put("Магия: Иллюзия", MagicTests::illusion);
		tests.put("Магия: Колдовство", MagicTests::conjuration);
	}

	// --- Подготовка ---

	/** Ровная площадка 25×25 с воздухом над ней, чтобы рывки и призыв работали одинаково везде. */
	private static void arena(ServerLevel level, BlockPos o) {
		for (int dx = -12; dx <= 12; dx++) {
			for (int dz = -12; dz <= 12; dz++) {
				level.setBlock(o.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 2);
				for (int dy = 0; dy <= 6; dy++) level.setBlock(o.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
			}
		}
	}

	/** Маг 50 уровня со всеми заклинаниями, полной маной и посохом архимага; смотрит на юг (+Z). */
	private static FakePlayer mage(ServerLevel level, BlockPos o) {
		arena(level, o);
		FakePlayer p = DevTests.player(level, o, new ItemStack(MagicItems.ARCHMAGE_STAFF), 0);
		p.setAttached(Skills.DATA, new PlayerSkills(Optional.of(Magic.ROLE), Map.of(Magic.ROLE, new PlayerSkills.Progress(50, 0, List.of())), 0L));
		Skills.applyAttributes(p);
		Magic.forgetAll(p);
		for (Spell s : Spells.all()) Magic.teach(p, s);
		p.setAttached(Magic.MANA, Magic.maxMana(p));
		return p;
	}

	private static Spell spell(String id) {
		Spell s = Spells.get(id);
		if (s == null) throw new IllegalStateException("нет заклинания " + id);
		return s;
	}

	private static <T extends Mob> T dummy(ServerLevel level, EntityType<T> type, BlockPos at) {
		return DevTests.spawn(level, type, at);
	}

	private static EntityHitResult hitOn(Entity e) {
		return new EntityHitResult(e, Cast.center(e));
	}

	private static boolean hurt(LivingEntity e) {
		return e.getHealth() < e.getMaxHealth();
	}

	private static String hp(LivingEntity e) {
		return String.format("%.1f/%.1f", e.getHealth(), e.getMaxHealth());
	}

	// --- Основа ---

	private static void basics(ServerLevel level, BlockPos o, DevTests.Report r) {
		arena(level, o);
		FakePlayer p = DevTests.player(level, o, ItemStack.EMPTY, 0);
		p.removeAttached(Magic.DATA);
		p.setAttached(Skills.DATA, new PlayerSkills(Optional.of(Magic.ROLE), Map.of(Magic.ROLE, PlayerSkills.Progress.START), 0L));
		Magic.onRoleChosen(p, Magic.ROLE);
		boolean staff = p.getInventory().contains(new ItemStack(MagicItems.APPRENTICE_STAFF));
		r.check(staff && Magic.data(p).knows("firebolt") && Magic.data(p).knows("healing") && Magic.data(p).knows("candlelight")
				&& "firebolt".equals(Magic.data(p).selected()), "Стартовый набор Мага: посох ученика и 3 заклинания, выбрана Огненная стрела");
		Magic.onRoleChosen(p, Magic.ROLE);
		int staffs = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(MagicItems.APPRENTICE_STAFF)) staffs++;
		r.check(staffs == 1, "Стартовый набор выдаётся один раз (посохов " + staffs + ")");

		r.check(Magic.learn(p, spell("judgement")) == Magic.LearnResult.LOW_LEVEL, "Книгу V круга нельзя изучить Магу 1 уровня");
		r.check(Magic.learn(p, spell("smite")) == Magic.LearnResult.LEARNED && Magic.data(p).knows("smite"), "Книга I круга изучается");
		r.check(Magic.learn(p, spell("smite")) == Magic.LearnResult.KNOWN, "Повторно ту же книгу не изучить");
		p.setAttached(Skills.DATA, new PlayerSkills(Optional.of("miner"), Map.of("miner", PlayerSkills.Progress.START), 0L));
		r.check(Magic.learn(p, spell("flames")) == Magic.LearnResult.NOT_MAGE, "Не-Маг книги не изучает");
		r.check(Magic.startProblem(p, MagicItems.APPRENTICE_STAFF) != null, "Не-Маг не может колдовать");

		p.setAttached(Skills.DATA, new PlayerSkills(Optional.of(Magic.ROLE),
				Map.of(Magic.ROLE, new PlayerSkills.Progress(30, 0, List.of("mage.1.1", "mage.1.2"))), 0L));
		r.check(Math.abs(Magic.maxMana(p) - 150) < 0.01, "Запас маны: 100 + 30 (уровень) + 20 (навык) = " + Magic.maxMana(p));
		r.check(Math.abs(Magic.regenPerSecond(p) - (2 + 0.04 * 30) * 1.2) < 0.01, "Восстановление маны " + Magic.regenPerSecond(p) + "/с");

		// Книга из рецепта: книга + огненный порошок + кремень → Огненная стрела.
		ItemStack tome = MagicItems.tome(spell("firebolt"));
		r.check(tome.is(MagicItems.TOMES.get(spell("firebolt").school)) && "firebolt".equals(tome.get(MagicItems.SPELL)),
				"Книга заклинаний несёт своё заклинание");
		r.check(level.getServer().getRecipeManager().byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE,
				net.minecraft.resources.Identifier.fromNamespaceAndPath("suchki", "tome_firebolt"))).isPresent(), "Рецепт книги Огненной стрелы загружен");
	}

	private static void charging(ServerLevel level, BlockPos o, DevTests.Report r) {
		FakePlayer p = mage(level, o);
		FocusItem staff = MagicItems.ARCHMAGE_STAFF;
		Spell bolt = spell("firebolt");
		Magic.select(p, bolt.id);
		float cost = Magic.cost(p, bolt, staff);
		int form = Magic.formTicks(bolt, Magic.castSpeed(p, staff));
		int over = Magic.overTicks(bolt, form);

		float before = Magic.mana(p);
		float power = Magic.chargeNow(p, staff, form);
		r.check(Math.abs(power - 1.0F) < 0.01 && Math.abs(before - Magic.mana(p) - cost) < 0.05,
				"Готово ровно за время формирования: сила " + power + ", потрачено " + (before - Magic.mana(p)) + " из " + cost);
		r.check(Magic.data(p).readyAt(bolt.id) > level.getGameTime(), "После выпуска заклинание на перезарядке");
		r.check(Magic.startProblem(p, staff) != null, "Во время перезарядки снова не начать");

		Magic.forgetAll(p);
		Magic.teach(p, bolt);
		p.setAttached(Magic.MANA, Magic.maxMana(p));
		before = Magic.mana(p);
		power = Magic.chargeNow(p, staff, form + over);
		r.check(Math.abs(power - staff.cap) < 0.02 && Math.abs(before - Magic.mana(p) - cost * staff.cap) < 0.1,
				"Полное накопление: сила ×" + power + " (предел ×" + staff.cap + "), мана " + (before - Magic.mana(p)));

		Magic.forgetAll(p);
		Magic.teach(p, bolt);
		p.setAttached(Magic.MANA, Magic.maxMana(p));
		before = Magic.mana(p);
		power = Magic.chargeNow(p, staff, form / 2);
		float lost = before - Magic.mana(p);
		r.check(power == 0 && Math.abs(lost - cost / 4) < 0.1, "Срыв до готовности: половина вложенного сгорает (потеряно " + lost + ")");

		Magic.forgetAll(p);
		Magic.teach(p, bolt);
		p.setAttached(Magic.MANA, Magic.maxMana(p));
		power = Magic.chargeNow(p, staff, form + over + Magic.HOLD_TICKS + 40);
		r.check(Math.abs(power - staff.cap) < 0.02, "На пределе заклинание срывается само (сила ×" + power + ")");

		Magic.forgetAll(p);
		Magic.teach(p, bolt);
		p.setAttached(Magic.MANA, cost * 1.2F);
		power = Magic.chargeNow(p, staff, form + over);
		r.check(power > 1.0F && power < 1.25F, "Мана кончилась на накоплении — сила не растёт дальше (×" + power + ")");
		p.setAttached(Magic.MANA, 1.0F);
		r.check(Magic.startProblem(p, staff) != null, "Без маны на цену заклинание не начать");

		Magic.teach(p, spell("fireball"));
		Magic.select(p, "fireball");
		p.setAttached(Magic.MANA, Magic.maxMana(p));
		r.check(Magic.startProblem(p, MagicItems.APPRENTICE_STAFF) != null && Magic.startProblem(p, staff) == null,
				"Посох ученика не тянет IV круг, посох архимага — тянет");

		r.check(Magic.formTicks(bolt, Magic.castSpeed(p, MagicItems.GRIMOIRE)) < form, "Гримуар колдует быстрее посоха");
		p.setAttached(Skills.DATA, new PlayerSkills(Optional.of(Magic.ROLE),
				Map.of(Magic.ROLE, new PlayerSkills.Progress(50, 0, List.of("mage.2.1", "mage.2.2"))), 0L));
		r.check(Magic.cost(p, bolt, staff) < cost && Magic.formTicks(bolt, Magic.castSpeed(p, staff)) < form,
				"Навыки Мастерства удешевляют и ускоряют каст");
	}

	// --- Разрушение ---

	private static void destruction(ServerLevel level, BlockPos o, DevTests.Report r) {
		FakePlayer p = mage(level, o);
		Zombie z = dummy(level, EntityType.ZOMBIE, o.offset(0, 0, 4));
		int before = level.getEntitiesOfClass(SpellProjectile.class, new AABB(o).inflate(8)).size();
		Magic.castNow(p, spell("firebolt"), 1);
		r.check(level.getEntitiesOfClass(SpellProjectile.class, new AABB(o).inflate(8)).size() > before, "Огненная стрела: вылетает снаряд");
		Magic.hitNow(p, spell("firebolt"), hitOn(z), 1);
		r.check(hurt(z) && z.isOnFire(), "Огненная стрела: урон и поджог (" + hp(z) + ")");

		Zombie f = dummy(level, EntityType.ZOMBIE, o.offset(0, 0, 3));
		Magic.channelNow(p, spell("flames"), 10, 1);
		r.check(hurt(f) && f.isOnFire(), "Пламя: конус жжёт цель впереди (" + hp(f) + ")");

		Zombie i = dummy(level, EntityType.ZOMBIE, o.offset(3, 0, 0));
		Magic.hitNow(p, spell("ice_shard"), hitOn(i), 1);
		r.check(hurt(i) && i.hasEffect(MobEffects.SLOWNESS) && i.getTicksFrozen() > 140, "Ледяной осколок: урон, замедление, обледенение");

		level.getEntitiesOfClass(Zombie.class, new AABB(o).inflate(16)).forEach(Entity::discard);
		Zombie c1 = dummy(level, EntityType.ZOMBIE, o.offset(0, 0, 5));
		Zombie c2 = dummy(level, EntityType.ZOMBIE, o.offset(3, 0, 6));
		Zombie c3 = dummy(level, EntityType.ZOMBIE, o.offset(6, 0, 6));
		r.check(Magic.castNow(p, spell("chain_lightning"), 1) && hurt(c1) && hurt(c2) && hurt(c3),
				"Цепная молния: цель и две следующие (" + hp(c1) + ", " + hp(c2) + ", " + hp(c3) + ")");

		level.getEntitiesOfClass(Zombie.class, new AABB(o).inflate(16)).forEach(Entity::discard);
		Zombie n1 = dummy(level, EntityType.ZOMBIE, o.offset(3, 0, 0));
		Zombie n2 = dummy(level, EntityType.ZOMBIE, o.offset(-3, 0, 2));
		Villager v = dummy(level, EntityType.VILLAGER, o.offset(0, 0, -3));
		Magic.castNow(p, spell("frost_nova"), 1);
		r.check(hurt(n1) && hurt(n2) && n1.hasEffect(MobEffects.SLOWNESS) && !hurt(v), "Ледяная волна: враги вокруг, житель цел");

		Zombie e1 = dummy(level, EntityType.ZOMBIE, o.offset(0, 0, 8));
		Zombie e2 = dummy(level, EntityType.ZOMBIE, o.offset(2, 0, 8));
		Villager v2 = dummy(level, EntityType.VILLAGER, o.offset(-2, 0, 8));
		Magic.hitNow(p, spell("fireball"), hitOn(e1), 1);
		r.check(hurt(e1) && hurt(e2) && e2.isOnFire() && !hurt(v2) && level.getBlockState(o.offset(0, -1, 8)).is(Blocks.STONE),
				"Огненный шар: взрыв задевает врагов рядом, не трогает жителя и блоки");

		int zones = Zones.active();
		Magic.castNow(p, spell("meteor_shower"), 1);
		r.check(Zones.active() >= zones + 8, "Метеоритный дождь: запланировано 8 метеоров (" + (Zones.active() - zones) + ")");
	}

	// --- Восстановление ---

	private static void restoration(ServerLevel level, BlockPos o, DevTests.Report r) {
		FakePlayer p = mage(level, o);
		p.setHealth(8);
		Magic.channelNow(p, spell("healing"), 40, 1);
		r.check(p.getHealth() > 9, "Исцеление: поток лечит мага (" + p.getHealth() + ")");

		Zombie front = dummy(level, EntityType.ZOMBIE, o.offset(0, 0, 3));
		Zombie back = dummy(level, EntityType.ZOMBIE, o.offset(0, 0, -3));
		p.setAttached(Magic.CASTING, "ward");
		float fromFront = Magic.ward(p, level.damageSources().mobAttack(front), 10);
		float fromBack = Magic.ward(p, level.damageSources().mobAttack(back), 10);
		p.removeAttached(Magic.CASTING);
		r.check(Math.abs(fromFront - 3) < 0.01 && fromBack == 10, "Оберег: спереди 10 → " + fromFront + ", сзади " + fromBack);

		Villager v = dummy(level, EntityType.VILLAGER, o.offset(0, 0, 4));
		front.discard();
		v.setHealth(5);
		r.check(Magic.castNow(p, spell("heal_other"), 1) && v.getHealth() > 5, "Лечение ближнего лечит цель (" + hp(v) + ")");
		v.discard();
		Zombie undead = dummy(level, EntityType.ZOMBIE, o.offset(0, 0, 4));
		Magic.castNow(p, spell("heal_other"), 1);
		r.check(hurt(undead), "Лечение ближнего: нежить получает урон (" + hp(undead) + ")");
		undead.discard();

		p.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
		p.addEffect(new MobEffectInstance(MobEffects.SPEED, 200));
		p.igniteForTicks(100);
		Magic.castNow(p, spell("cure"), 1);
		r.check(!p.hasEffect(MobEffects.POISON) && p.hasEffect(MobEffects.SPEED) && !p.isOnFire(), "Очищение: снимает яд и огонь, полезное оставляет");

		Villager ally = dummy(level, EntityType.VILLAGER, o.offset(2, 0, 2));
		Magic.castNow(p, spell("rejuvenation"), 1);
		r.check(p.hasEffect(MobEffects.REGENERATION) && ally.hasEffect(MobEffects.REGENERATION) && !back.hasEffect(MobEffects.REGENERATION),
				"Восстановление сил: регенерация себе и союзникам, не врагам");

		int zones = Zones.active();
		Magic.castNow(p, spell("healing_circle"), 1);
		r.check(Zones.active() == zones + 1, "Круг исцеления: зона создана");

		Magic.castNow(p, spell("undying"), 1);
		r.check(p.hasEffect(MagicEffects.UNDYING), "Неугасимая жизнь: эффект на маге");
		// Сам эффект проверяем на зомби: «ненастоящий» игрок урона не получает.
		Zombie saved = dummy(level, EntityType.ZOMBIE, o.offset(-3, 0, 0));
		saved.addEffect(new MobEffectInstance(MagicEffects.UNDYING, 200));
		saved.hurtServer(level, level.damageSources().generic(), 1000);
		r.check(saved.isAlive() && saved.getHealth() >= saved.getMaxHealth() * 0.4F && !saved.hasEffect(MagicEffects.UNDYING),
				"Неугасимая жизнь: смертельный удар отменён (здоровье " + saved.getHealth() + ")");
	}

	// --- Свет ---

	private static void light(ServerLevel level, BlockPos o, DevTests.Report r) {
		FakePlayer p = mage(level, o);
		Magic.castNow(p, spell("candlelight"), 1);
		boolean lit = false;
		for (BlockPos b : BlockPos.betweenClosed(p.blockPosition().offset(-1, 0, -1), p.blockPosition().offset(1, 3, 1))) {
			if (level.getBlockState(b).is(MagicBlocks.MAGIC_LIGHT)) lit = true;
		}
		r.check(lit && MagicBlocks.candleUntil(p) > level.getGameTime(), "Светоч: свет у головы мага");

		Zombie z = dummy(level, EntityType.ZOMBIE, o.offset(0, 0, 4));
		Magic.hitNow(p, spell("smite"), hitOn(z), 1);
		r.check(z.getMaxHealth() - z.getHealth() >= 10 && z.isOnFire(), "Кара: нежить получает ×2,5 и горит (" + hp(z) + ")");

		Zombie blind = dummy(level, EntityType.ZOMBIE, o.offset(3, 0, 0));
		Magic.castNow(p, spell("flash"), 1);
		r.check(blind.hasEffect(MobEffects.BLINDNESS) && Minds.modeOf(blind) == Minds.Mode.CALM, "Вспышка: ослепление и потеря цели");

		level.getEntitiesOfClass(Zombie.class, new AABB(o).inflate(16)).forEach(Entity::discard);
		Zombie beam = dummy(level, EntityType.ZOMBIE, o.offset(0, 0, 6));
		Magic.channelNow(p, spell("sunbeam"), 10, 1);
		r.check(hurt(beam), "Солнечный луч: урон цели по прицелу (" + hp(beam) + ")");

		Magic.castNow(p, spell("blessing"), 1);
		r.check(p.hasEffect(MobEffects.STRENGTH) && p.hasEffect(MobEffects.RESISTANCE), "Благословение: сила и сопротивление");

		int zones = Zones.active();
		Magic.castNow(p, spell("sanctuary"), 1);
		Magic.castNow(p, spell("judgement"), 1);
		r.check(Zones.active() == zones + 2, "Святая земля и Небесный суд: зона и удар запланированы");
	}

	// --- Тьма ---

	private static void dark(ServerLevel level, BlockPos o, DevTests.Report r) {
		FakePlayer p = mage(level, o);
		Zombie z = dummy(level, EntityType.ZOMBIE, o.offset(0, 0, 4));
		Magic.hitNow(p, spell("shadow_bolt"), hitOn(z), 1);
		r.check(hurt(z) && z.hasEffect(MobEffects.WITHER), "Теневая стрела: урон и иссушение");

		z.setHealth(z.getMaxHealth());
		p.setHealth(10);
		z.invulnerableTime = 0;
		Magic.channelNow(p, spell("drain_life"), 10, 1);
		r.check(hurt(z) && p.getHealth() > 10, "Похищение жизни: урон цели и лечение мага (" + hp(z) + ", маг " + p.getHealth() + ")");

		Magic.castNow(p, spell("curse"), 1);
		float hexed = z.hasEffect(MagicEffects.HEX) ? MagicEffects.hexBonus(z) : 0;
		r.check(hexed >= 0.25F && z.hasEffect(MobEffects.WEAKNESS), "Проклятие: +" + Math.round(hexed * 100) + "% урона и слабость");

		z.discard();
		for (int dy = 0; dy <= 3; dy++) level.setBlock(o.offset(0, dy, 10), Blocks.STONE.defaultBlockState(), 3);
		Vec3 from = p.position();
		p.snapTo(o.getX() + 0.5, o.getY(), o.getZ() + 0.5, 0, -40);
		r.check(!Magic.castNow(p, spell("shadow_step"), 1), "Теневой шаг: в небо не переносит");
		p.snapTo(o.getX() + 0.5, o.getY(), o.getZ() + 0.5, 0, 0);
		boolean stepped = Magic.castNow(p, spell("shadow_step"), 1);
		r.check(stepped && p.position().distanceTo(from) > 5 && p.hasEffect(MobEffects.INVISIBILITY), "Теневой шаг (сработал: " + stepped + "): перенос на "
				+ String.format("%.1f", p.position().distanceTo(from)) + " блока и невидимость");

		p.snapTo(o.getX() + 0.5, o.getY(), o.getZ() + 0.5, 0, 0);
		Magic.castNow(p, spell("raise_dead"), 1);
		List<Husk> husks = level.getEntitiesOfClass(Husk.class, new AABB(o).inflate(8), h -> Minions.isOwnedBy(h, p));
		boolean peaceful = true;
		for (Husk h : husks) {
			h.setTarget(p);
			if (h.getTarget() == p) peaceful = false;
		}
		r.check(husks.size() == 2 && peaceful, "Поднять мертвецов: 2 союзника, на хозяина не нападают (" + husks.size() + ")");

		int zones = Zones.active();
		Magic.castNow(p, spell("darkness"), 1);
		r.check(Zones.active() == zones + 1, "Покров тьмы: облако создано");

		Zombie a = dummy(level, EntityType.ZOMBIE, o.offset(5, 0, 0));
		Zombie b = dummy(level, EntityType.ZOMBIE, o.offset(-5, 0, 3));
		Magic.castNow(p, spell("soul_harvest"), 1);
		r.check(hurt(a) && hurt(b) && a.hasEffect(MobEffects.WITHER), "Жатва душ: урон и иссушение врагам вокруг");
	}

	// --- Изменение ---

	private static void alteration(ServerLevel level, BlockPos o, DevTests.Report r) {
		FakePlayer p = mage(level, o);
		double armor = p.getAttributeValue(Attributes.ARMOR);
		Magic.castNow(p, spell("stoneflesh"), 1);
		r.check(p.getAttributeValue(Attributes.ARMOR) >= armor + 4, "Каменная кожа: +" + (p.getAttributeValue(Attributes.ARMOR) - armor) + " брони");

		ItemEntity item = new ItemEntity(level, o.getX() + 0.5, o.getY() + 1.0, o.getZ() + 10.5, new ItemStack(Items.DIAMOND));
		level.addFreshEntity(item);
		Magic.channelNow(p, spell("telekinesis"), 3, 1);
		r.check(item.getDeltaMovement().z < -0.2, "Телекинез: предмет летит к магу (скорость " + String.format("%.2f", item.getDeltaMovement().z) + ")");

		Magic.castNow(p, spell("featherfall"), 1);
		r.check(p.hasEffect(MobEffects.SLOW_FALLING), "Пёрышко: плавное падение");

		Vec3 from = p.position();
		Magic.castNow(p, spell("blink"), 1);
		r.check(p.position().z - from.z > 6, "Скачок: рывок вперёд на " + String.format("%.1f", p.position().z - from.z) + " блока");
		p.snapTo(o.getX() + 0.5, o.getY(), o.getZ() + 0.5, 0, 0);

		Magic.castNow(p, spell("deep_breath"), 1);
		r.check(p.hasEffect(MobEffects.WATER_BREATHING) && p.hasEffect(MobEffects.DOLPHINS_GRACE), "Дыхание глубин");

		Zombie z = dummy(level, EntityType.ZOMBIE, o.offset(0, 0, 4));
		Magic.hitNow(p, spell("paralyze"), hitOn(z), 1);
		r.check(z.hasEffect(MagicEffects.PARALYSIS) && z.getAttributeValue(Attributes.MOVEMENT_SPEED) < 0.001,
				"Паралич: скорость моба " + z.getAttributeValue(Attributes.MOVEMENT_SPEED));

		int zones = Zones.active();
		Magic.castNow(p, spell("earthquake"), 1);
		r.check(Zones.active() == zones + 3, "Землетрясение: 3 волны запланированы");
	}

	// --- Иллюзия ---

	private static void illusion(ServerLevel level, BlockPos o, DevTests.Report r) {
		FakePlayer p = mage(level, o);
		Magic.castNow(p, spell("night_eye"), 1);
		r.check(p.hasEffect(MobEffects.NIGHT_VISION), "Ночной глаз");

		Zombie z = dummy(level, EntityType.ZOMBIE, o.offset(0, 0, 6));
		Magic.castNow(p, spell("fear"), 1);
		z.setTarget(p);
		r.check(Minds.modeOf(z) == Minds.Mode.FEAR && z.getTarget() == null, "Страх: моб боится и не выбирает мага целью");

		Minds.clear(z);
		Magic.castNow(p, spell("calm"), 1);
		z.setTarget(p);
		boolean calm = Minds.modeOf(z) == Minds.Mode.CALM && z.getTarget() == null;
		z.hurtServer(level, level.damageSources().playerAttack(p), 1);
		r.check(calm && Minds.modeOf(z) == null, "Умиротворение: не нападает, пока его не ударить");

		Magic.castNow(p, spell("invisibility"), 1);
		r.check(p.hasEffect(MobEffects.INVISIBILITY), "Невидимость");

		Minds.clear(z);
		Zombie other = dummy(level, EntityType.ZOMBIE, o.offset(2, 0, 6));
		Magic.castNow(p, spell("frenzy"), 1);
		z.setTarget(p);
		r.check(Minds.modeOf(z) == Minds.Mode.FRENZY && z.getTarget() != p, "Бешенство: мобы не выбирают игрока целью");

		Minds.clear(z);
		Minds.clear(other);
		Magic.castNow(p, spell("mirror_image"), 1);
		List<Mannequin> decoys = level.getEntitiesOfClass(Mannequin.class, new AABB(o).inflate(6), m -> Minions.isOwnedBy(m, p));
		r.check(decoys.size() == 1 && Minds.modeOf(z) == Minds.Mode.DECOY && z.getTarget() == decoys.get(0), "Двойник: враги целятся в двойника");

		Minds.clear(z);
		Magic.castNow(p, spell("mass_hysteria"), 1);
		r.check(Minds.modeOf(z) == Minds.Mode.FEAR && Minds.modeOf(other) == Minds.Mode.FEAR && z.hasEffect(MobEffects.WEAKNESS),
				"Массовая паника: все враги бегут");
		Minds.clear(z);
		Minds.clear(other);
	}

	// --- Колдовство ---

	private static void conjuration(ServerLevel level, BlockPos o, DevTests.Report r) {
		FakePlayer p = mage(level, o);
		Magic.castNow(p, spell("bound_blade"), 2.0F);
		ItemStack blade = p.getOffhandItem();
		var sharp = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
		r.check(blade.is(MagicItems.SPECTRAL_BLADE) && blade.get(MagicItems.EXPIRES) != null && blade.getEnchantments().getLevel(sharp) == 5,
				"Призрачный клинок: в свободной руке, со сроком, при сильном накоплении — Острота V");

		// Призрачное не кладётся в сундук и тает, если выбросить.
		Slot chestSlot = new Slot(new SimpleContainer(1), 0, 0, 0);
		r.check(!chestSlot.mayPlace(blade), "Призрачный клинок не кладётся в сундук");
		ItemEntity dropped = new ItemEntity(level, o.getX() + 0.5, o.getY() + 1, o.getZ() + 0.5, blade.copy());
		level.addFreshEntity(dropped);
		r.check(dropped.isRemoved(), "Выброшенный призрачный клинок исчезает");

		level.setBlock(o.offset(0, 1, 3), Blocks.OAK_LOG.defaultBlockState(), 3);
		p.snapTo(o.getX() + 0.5, o.getY(), o.getZ() + 0.5, 0, 10);
		p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		Magic.castNow(p, spell("bound_tool"), 1);
		r.check(p.getOffhandItem().is(MagicItems.SPECTRAL_AXE), "Призрачный инструмент: у бревна — топор (" + p.getOffhandItem() + ")");
		level.setBlock(o.offset(0, 1, 3), Blocks.AIR.defaultBlockState(), 3);
		p.snapTo(o.getX() + 0.5, o.getY(), o.getZ() + 0.5, 0, 0);

		Magic.castNow(p, spell("familiar"), 1);
		List<Wolf> wolves = level.getEntitiesOfClass(Wolf.class, new AABB(o).inflate(6), w -> Minions.isOwnedBy(w, p));
		r.check(wolves.size() == 1 && wolves.get(0).isTame(), "Призрачный волк: приручен магом");

		Magic.castNow(p, spell("spirit_blades"), 1);
		r.check(level.getEntitiesOfClass(Vex.class, new AABB(o).inflate(6), v -> Minions.isOwnedBy(v, p)).size() == 3, "Духи-клинки: 3 духа");

		Magic.castNow(p, spell("spectral_steed"), 1);
		List<Horse> horses = level.getEntitiesOfClass(Horse.class, new AABB(o).inflate(6), h -> Minions.isOwnedBy(h, p));
		r.check(horses.size() == 1 && horses.get(0).isTamed() && horses.get(0).isSaddled(), "Призрачный конь: осёдлан и приручен");
		p.stopRiding();

		Magic.castNow(p, spell("iron_guardian"), 1);
		List<IronGolem> golems = level.getEntitiesOfClass(IronGolem.class, new AABB(o).inflate(8), g -> Minions.isOwnedBy(g, p));
		r.check(golems.size() == 1 && golems.get(0).isPlayerCreated(), "Железный страж: голем-союзник");
		if (!golems.isEmpty()) {
			IronGolem g = golems.get(0);
			g.hurtServer(level, level.damageSources().genericKill(), 1000);
			r.check(DevTests.count(level, g.blockPosition(), 4, Items.IRON_INGOT) == 0, "Убитый страж не роняет железо");
		}

		// Время призыва вышло — существо исчезает.
		wolves.forEach(w -> w.setAttached(Minions.SUMMON, new Minions.Summon(p.getUUID(), level.getGameTime() - 1, 0xFFFFFF)));
		Minions.processNow();
		r.check(wolves.stream().allMatch(Entity::isRemoved), "Призыв исчезает по истечении времени");

		BlockPos spawn = level.getRespawnData().pos();
		Magic.castNow(p, spell("recall"), 1);
		double flat = Math.hypot(p.getX() - spawn.getX(), p.getZ() - spawn.getZ());
		r.check(flat < 32, "Возврат: перенос к точке возрождения (" + p.blockPosition() + ", точка " + spawn + ")");
	}
}
