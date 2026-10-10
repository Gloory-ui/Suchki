package com.suchki.dev;

import static com.suchki.dev.SpellTestKit.arena;
import static com.suchki.dev.SpellTestKit.clean;
import static com.suchki.dev.SpellTestKit.dummy;
import static com.suchki.dev.SpellTestKit.face;
import static com.suchki.dev.SpellTestKit.mage;
import static com.suchki.dev.SpellTestKit.run;

import com.suchki.magic.Anchor;
import com.suchki.magic.Cast;
import com.suchki.magic.CastStyle;
import com.suchki.magic.CircleLayout;
import com.suchki.magic.FocusItem;
import com.suchki.magic.Magic;
import com.suchki.magic.MagicItems;
import com.suchki.magic.MagicSounds;
import com.suchki.magic.School;
import com.suchki.magic.Spell;
import com.suchki.magic.SpellProjectile;
import com.suchki.magic.Spells;
import com.suchki.magic.Targeting;
import com.suchki.magic.Terrain;
import com.suchki.magic.Zones;
import com.suchki.skill.PlayerSkills;
import com.suchki.skill.Skills;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Самопроверка магии: роль и стартовый набор, изучение (ранги, слияние), мана и накопление силы, фокусы,
 * мультикаст, выбор и переключение заклинаний, прицел на 128 блоков, разрушение и временные блоки, зоны;
 * общие проверки каталога (тексты, фразы, иконки, рецепты, баланс по рангу) и «дымовая» проверка — каждое
 * заклинание творится без ошибок. Проверки эффектов каждого заклинания — в классах стихий ({@code *SpellTests}).
 */
final class MagicTests {
	private MagicTests() {}

	static void addTo(Map<String, DevTests.Test> tests) {
		tests.put("Магия: роль, книги, мана, ранги", MagicTests::basics);
		tests.put("Магия: накопление силы", MagicTests::charging);
		tests.put("Магия: мультикаст", MagicTests::multicast);
		tests.put("Магия: слияние стихий", MagicTests::fusion);
		tests.put("Магия: выбор и переключение заклинаний", MagicTests::selection);
		tests.put("Магия: прицел на 128 блоков", MagicTests::targeting);
		tests.put("Магия: разрушение и временные блоки", MagicTests::terrain);
		tests.put("Магия: зоны и отложенные удары", MagicTests::zones);
		tests.put("Магия: каталог заклинаний", MagicTests::catalog);
		tests.put("Магия: каждое заклинание творится", MagicTests::smoke);
		FireSpellTests.addTo(tests);
		WaterSpellTests.addTo(tests);
		WindSpellTests.addTo(tests);
		EarthSpellTests.addTo(tests);
		LightningSpellTests.addTo(tests);
		LightSpellTests.addTo(tests);
		DarkSpellTests.addTo(tests);
		SpaceSpellTests.addTo(tests);
		LifeSpellTests.addTo(tests);
		MindSpellTests.addTo(tests);
		SummonSpellTests.addTo(tests);
		FusionSpellTests.addTo(tests);
	}

	private static Optional<Spell> find(java.util.function.Predicate<Spell> p) {
		return Spells.all().stream().filter(p).findFirst();
	}

	// --- Основа ---

	private static void basics(ServerLevel level, BlockPos o, DevTests.Report r) {
		arena(level, o);
		FakePlayer p = DevTests.player(level, o, ItemStack.EMPTY, 0);
		p.removeAttached(Magic.DATA);
		p.setAttached(Skills.DATA, new PlayerSkills(Optional.of(Magic.ROLE), Map.of(Magic.ROLE, PlayerSkills.Progress.START), 0L));
		Magic.onRoleChosen(p, Magic.ROLE);
		boolean staff = p.getInventory().contains(new ItemStack(MagicItems.APPRENTICE_STAFF));
		r.check(staff && Magic.STARTER.stream().allMatch(id -> Magic.data(p).knows(id)) && Magic.STARTER.get(0).equals(Magic.data(p).selected()),
				"Стартовый набор Мага: посох ученика и " + Magic.STARTER);
		r.check(Magic.STARTER.stream().allMatch(id -> Spells.get(id) != null), "Стартовые заклинания существуют: " + Magic.STARTER);
		Magic.onRoleChosen(p, Magic.ROLE);
		int staffs = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(MagicItems.APPRENTICE_STAFF)) staffs++;
		r.check(staffs == 1, "Стартовый набор выдаётся один раз (посохов " + staffs + ")");

		r.check(Spell.TIER_LEVEL.length == Spell.MAX_TIER + 1 && Spell.MAX_TIER == 7, "Семь рангов");
		Optional<Spell> god = find(s -> s.tier == 7 && !s.fusion());
		Optional<Spell> low = find(s -> s.tier == 1 && !Magic.data(p).knows(s.id));
		if (god.isPresent()) r.check(Magic.learn(p, god.get()) == Magic.LearnResult.LOW_LEVEL, "Божественное заклинание не изучить Магу 1 уровня");
		if (low.isPresent()) {
			r.check(Magic.learn(p, low.get()) == Magic.LearnResult.LEARNED && Magic.data(p).knows(low.get().id), "Книга начального ранга изучается");
			r.check(Magic.learn(p, low.get()) == Magic.LearnResult.KNOWN, "Повторно ту же книгу не изучить");
		}
		p.setAttached(Skills.DATA, new PlayerSkills(Optional.of("miner"), Map.of("miner", PlayerSkills.Progress.START), 0L));
		if (low.isPresent()) {
			Magic.forgetAll(p);
			r.check(Magic.learn(p, low.get()) == Magic.LearnResult.NOT_MAGE, "Не-Маг книги не изучает");
		}
		r.check(Magic.startProblem(p, MagicItems.APPRENTICE_STAFF) != null, "Не-Маг не может колдовать");

		p.setAttached(Skills.DATA, new PlayerSkills(Optional.of(Magic.ROLE),
				Map.of(Magic.ROLE, new PlayerSkills.Progress(30, 0, List.of("mage.1.1", "mage.1.2"))), 0L));
		r.check(Math.abs(Magic.maxMana(p) - (100 + 4 * 30 + 20)) < 0.01, "Запас маны: 100 + 4×30 (уровень) + 20 (навык) = " + Magic.maxMana(p));
		r.check(Math.abs(Magic.regenPerSecond(p) - (2 + 0.12 * 30) * 1.2) < 0.01, "Восстановление маны " + Magic.regenPerSecond(p) + "/с");

		r.check(MagicItems.APPRENTICE_STAFF.maxTier == 3 && MagicItems.GRIMOIRE.maxTier == 5 && MagicItems.ARCHMAGE_STAFF.maxTier == 6
				&& MagicItems.PRIMORDIAL_STAFF.maxTier == 7, "Фокусы: ученик — до III, гримуар — до V, архимаг — до VI, первоначал — до VII");
		for (School s : School.values()) {
			r.check(MagicItems.TOMES.containsKey(s), "Книга стихии " + s.id);
		}
		Optional<Spell> craft = find(s -> s.tier <= 4);
		if (craft.isPresent()) {
			ItemStack tome = MagicItems.tome(craft.get());
			r.check(tome.is(MagicItems.TOMES.get(craft.get().school)) && craft.get().id.equals(tome.get(MagicItems.SPELL)),
					"Книга заклинаний несёт своё заклинание");
		}
		r.check(level.getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE,
				Identifier.fromNamespaceAndPath("suchki", "primordial_staff"))).isPresent(), "Рецепт посоха первоначал загружен");
	}

	private static void charging(ServerLevel level, BlockPos o, DevTests.Report r) {
		FakePlayer p = mage(level, o);
		FocusItem staff = MagicItems.ARCHMAGE_STAFF;
		Optional<Spell> pick = find(s -> s.charged() && s.tier <= 3 && s.anchor == Anchor.HAND && s.maxCopies > 1);
		if (pick.isEmpty()) {
			r.lines.add("SKIP Магия: нет заряжаемого заклинания для проверки накопления");
			return;
		}
		Spell bolt = pick.get();
		Magic.select(p, bolt.id);
		float cost = Magic.cost(p, bolt, staff);
		int form = Magic.formTicks(bolt, Magic.castSpeed(p, staff));
		int over = Magic.overTicks(bolt, form);
		float before = Magic.mana(p);
		float power = Magic.chargeNow(p, staff, form);
		r.check(Math.abs(power - 1.0F) < 0.01 && Math.abs(before - Magic.mana(p) - cost) < 0.05,
				bolt.id + ": готово ровно за время формирования: сила " + power + ", потрачено " + (before - Magic.mana(p)) + " из " + cost);
		r.check(Magic.data(p).readyAt(bolt.id) > level.getGameTime() || bolt.cooldownTicks <= 0, "После выпуска заклинание на перезарядке");

		Magic.resetCooldowns(p);
		p.setAttached(Magic.MANA, Magic.maxMana(p));
		before = Magic.mana(p);
		power = Magic.chargeNow(p, staff, form + over);
		r.check(Math.abs(power - staff.cap) < 0.02 && Math.abs(before - Magic.mana(p) - cost * staff.cap) < 0.1,
				"Полное накопление: сила ×" + power + " (предел ×" + staff.cap + "), мана " + (before - Magic.mana(p)));

		Magic.resetCooldowns(p);
		p.setAttached(Magic.MANA, Magic.maxMana(p));
		before = Magic.mana(p);
		power = Magic.chargeNow(p, staff, form / 2);
		float lost = before - Magic.mana(p);
		r.check(power == 0 && Math.abs(lost - cost / 4) < 0.15, "Срыв до готовности: половина вложенного сгорает (потеряно " + lost + ")");

		Magic.resetCooldowns(p);
		p.setAttached(Magic.MANA, Magic.maxMana(p));
		power = Magic.chargeNow(p, staff, form + over + Magic.HOLD_TICKS + 40);
		r.check(Math.abs(power - staff.cap) < 0.02, "На пределе заклинание срывается само (сила ×" + power + ")");

		Magic.resetCooldowns(p);
		p.setAttached(Magic.MANA, cost * 1.2F);
		power = Magic.chargeNow(p, staff, form + over);
		r.check(power > 1.0F && power < 1.25F, "Мана кончилась на накоплении — сила не растёт дальше (×" + power + ")");
		p.setAttached(Magic.MANA, 0.5F);
		r.check(Magic.startProblem(p, staff) != null, "Без маны на цену заклинание не начать");

		p.setAttached(Magic.MANA, Magic.maxMana(p));
		for (int tier = 4; tier <= 7; tier++) {
			int t = tier;
			Optional<Spell> high = find(s -> s.tier == t);
			if (high.isEmpty()) continue;
			Magic.select(p, high.get().id);
			r.check(Magic.startProblem(p, MagicItems.APPRENTICE_STAFF) != null, "Посох ученика не тянет ранг " + tier);
		}
		find(s -> s.tier == 7).ifPresent(s -> {
			Magic.select(p, s.id);
			r.check(Magic.startProblem(p, MagicItems.ARCHMAGE_STAFF) != null && Magic.startProblem(p, MagicItems.PRIMORDIAL_STAFF) == null,
					"Божественный ранг тянет только посох первоначал");
		});
		Magic.select(p, bolt.id);
		r.check(Magic.formTicks(bolt, Magic.castSpeed(p, MagicItems.GRIMOIRE)) < form, "Гримуар колдует быстрее посоха");
		p.setAttached(Skills.DATA, new PlayerSkills(Optional.of(Magic.ROLE),
				Map.of(Magic.ROLE, new PlayerSkills.Progress(50, 0, List.of("mage.2.1", "mage.2.2"))), 0L));
		r.check(Magic.cost(p, bolt, staff) < cost && Magic.formTicks(bolt, Magic.castSpeed(p, staff)) < form,
				"Навыки Мастерства удешевляют и ускоряют каст");
	}

	// --- Мультикаст ---

	private static void multicast(ServerLevel level, BlockPos o, DevTests.Report r) {
		FakePlayer p = mage(level, o);
		p.setAttached(Skills.DATA, new PlayerSkills(Optional.of(Magic.ROLE), Map.of(Magic.ROLE, new PlayerSkills.Progress(10, 0, List.of())), 0L));
		r.check(Magic.maxMulticast(p) == 1, "Мультикаст недоступен до 14 уровня");
		Magic.setMulticast(p, 3);
		r.check(Magic.data(p).multicast() == 1, "Выше доступного мультикаст не поднять (×" + Magic.data(p).multicast() + ")");
		p.setAttached(Skills.DATA, new PlayerSkills(Optional.of(Magic.ROLE), Map.of(Magic.ROLE, new PlayerSkills.Progress(26, 0, List.of())), 0L));
		r.check(Magic.maxMulticast(p) == 3, "С 26 уровня — ×3");
		p.setAttached(Skills.DATA, new PlayerSkills(Optional.of(Magic.ROLE), Map.of(Magic.ROLE, new PlayerSkills.Progress(50, 0, List.of())), 0L));
		r.check(Magic.maxMulticast(p) == 4, "С 40 уровня — ×4");
		Magic.setMulticast(p, 3);
		r.check(Magic.data(p).multicast() == 3, "Мультикаст ×3 выбран");

		Optional<Spell> pick = find(s -> s.charged() && s.anchor == Anchor.HAND && s.maxCopies >= 3 && s.projectile());
		if (pick.isPresent()) {
			Spell s = pick.get();
			Magic.select(p, s.id);
			FocusItem f = MagicItems.PRIMORDIAL_STAFF;
			r.check(Magic.copiesFor(p, s) == 3, s.id + ": копий " + Magic.copiesFor(p, s));
			r.check(Magic.formTicks(s, Magic.castSpeed(p, f), 3) > Magic.formTicks(s, Magic.castSpeed(p, f), 1), "С копиями круг строится дольше");
			for (int i = 0; i < 3; i++) dummy(level, EntityType.ZOMBIE, o.offset(-3 + 3 * i, 0, 9));
			face(p, o, 0);
			int before = level.getEntitiesOfClass(SpellProjectile.class, new AABB(o).inflate(20)).size();
			List<Cast> casts = Magic.casts(p, s, 1.0F, 3);
			Set<Vec3> origins = new HashSet<>();
			Set<Integer> targets = new HashSet<>();
			for (Cast c : casts) {
				origins.add(c.hand());
				if (c.target().entity() != null) targets.add(c.target().entity().getId());
			}
			r.check(casts.size() == 3 && origins.size() == 3, "Три копии вылетают из трёх разных кругов");
			r.check(targets.size() >= 2, "Копии целятся в разных врагов (целей " + targets.size() + ")");
			Magic.castNow(p, s, 1.0F, 3);
			int after = level.getEntitiesOfClass(SpellProjectile.class, new AABB(o).inflate(20)).size();
			r.check(after - before >= 3, s.id + " ×3: снарядов " + (after - before));
		} else {
			r.lines.add("SKIP Магия: нет снаряда для проверки мультикаста");
		}
		find(s -> s.tier == 7).ifPresent(s -> r.check(s.maxCopies == 1, "Божественный ранг без мультикаста (" + s.id + ")"));
		Spell any = Spells.all().isEmpty() ? null : Spells.all().get(0);
		if (any != null) {
			CircleLayout.Placement main = new CircleLayout.Placement(Vec3.ZERO, new Vec3(0, 0, 1), 1);
			Set<Vec3> spots = new HashSet<>();
			for (int i = 0; i < 4; i++) spots.add(CircleLayout.copy(any, main, new Vec3(0, 0, 1), i, 4).center());
			r.check(spots.size() == 4, "Раскладка: 4 копии — 4 разных круга");
		}
		Magic.setMulticast(p, 1);
	}

	// --- Слияние ---

	private static void fusion(ServerLevel level, BlockPos o, DevTests.Report r) {
		FakePlayer p = mage(level, o);
		Optional<Spell> pick = find(Spell::fusion);
		if (pick.isEmpty()) {
			r.lines.add("SKIP Магия: нет заклинаний слияния");
			return;
		}
		Spell f = pick.get();
		r.check(f.school == School.FUSION && f.combo.size() >= 2, f.id + ": слияние из " + f.combo);
		Magic.forgetAll(p);
		r.check(Magic.learn(p, f) == Magic.LearnResult.MISSING_ELEMENTS, "Слияние не изучить без стихий-составляющих");
		r.check(Magic.missingElements(p, f).size() == f.combo.size(), "Не хватает всех стихий: " + Magic.missingElements(p, f));
		for (School part : f.combo) {
			find(s -> s.school == part && s.tier >= f.tier - 1).ifPresent(s -> Magic.teach(p, s));
		}
		r.check(Magic.missingElements(p, f).isEmpty(), "После изучения составляющих стихий слияние доступно");
		r.check(Magic.learn(p, f) == Magic.LearnResult.LEARNED, "Слияние изучено");
		r.check(f.color() != School.FUSION.color || f.combo.stream().allMatch(s -> s == School.FUSION), "Цвет слияния — смесь стихий");
		for (Spell s : Spells.all()) {
			if (s.fusion()) r.check(s.combo.size() >= 2 && s.combo.stream().noneMatch(c -> c == School.FUSION), s.id + ": стихии слияния " + s.combo);
		}
	}

	// --- Выбор ---

	private static void selection(ServerLevel level, BlockPos o, DevTests.Report r) {
		FakePlayer p = mage(level, o);
		List<Spell> all = Spells.all();
		if (all.size() < 3) {
			r.lines.add("SKIP Магия: мало заклинаний для проверки выбора");
			return;
		}
		int switched = 0;
		for (Spell s : all) {
			Magic.select(p, s.id);
			if (s.id.equals(Magic.data(p).selected()) && Magic.data(p).selectedSpell() == s) switched++;
		}
		r.check(switched == all.size(), "Переключение на каждое заклинание по очереди (" + switched + "/" + all.size() + ")");
		Magic.select(p, "no_such_spell");
		r.check(!"no_such_spell".equals(Magic.data(p).selected()), "Несуществующее заклинание не выбрать");
		Spell a = all.get(0), b = all.get(1);
		Magic.forgetAll(p);
		Magic.teach(p, a);
		Magic.select(p, b.id);
		r.check(a.id.equals(Magic.data(p).selected()), "Неизученное заклинание не выбрать");
		Magic.teach(p, b);
		Optional<Spell> charged = find(s -> s.charged() && s.tier <= 3);
		if (charged.isPresent()) {
			Magic.teach(p, charged.get());
			Magic.select(p, charged.get().id);
			Magic.chargeNow(p, MagicItems.PRIMORDIAL_STAFF, 1);
			r.check(charged.get().id.equals(Magic.data(p).selected()), "После каста выбор сохраняется");
		}
		Magic.select(p, b.id);
		r.check(b.id.equals(Magic.data(p).selected()), "Переключение между двумя заклинаниями туда-обратно");
		Magic.select(p, a.id);
		r.check(a.id.equals(Magic.data(p).selected()), "…и обратно");
	}

	// --- Прицел ---

	private static void targeting(ServerLevel level, BlockPos o, DevTests.Report r) {
		FakePlayer p = mage(level, o);
		face(p, o, 0);
		// Стена в 100 блоках к югу.
		BlockPos wall = o.offset(0, 0, 100);
		for (int dx = -2; dx <= 2; dx++) for (int dy = -1; dy <= 3; dy++) level.setBlock(wall.offset(dx, dy, 0), Blocks.STONE.defaultBlockState(), 2);
		ChunkAccess ignored = level.getChunk(wall);
		Targeting.Aim aim = Targeting.aim(level, p, Magic.MAX_RANGE);
		r.check(!aim.miss() && Math.abs(aim.point().z - wall.getZ()) < 1.5, "Прицел достаёт стену в 100 блоках (z=" + aim.point().z + ")");
		// В игре чанки вокруг игрока загружены; у ненастоящего игрока — грузим чанк мишени сами.
		BlockPos farPos = o.offset(0, 0, 90);
		SpellTestKit.hold(level, farPos, 0);
		Zombie far = dummy(level, EntityType.ZOMBIE, farPos);
		aim = Targeting.aim(level, p, Magic.MAX_RANGE);
		r.check(aim.entity() == far, "Прицел ловит существо в 90 блоках");
		far.discard();
		level.setChunkForced(farPos.getX() >> 4, farPos.getZ() >> 4, false);
		for (int dx = -2; dx <= 2; dx++) for (int dy = -1; dy <= 3; dy++) level.setBlock(wall.offset(dx, dy, 0), Blocks.AIR.defaultBlockState(), 2);
		aim = Targeting.aim(level, p, Magic.MAX_RANGE);
		r.check(aim.miss() && aim.point().distanceTo(p.getEyePosition()) > 127, "В пустоту — точка на 128 блоках");
		face(p, o, 30);
		aim = Targeting.aim(level, p, Magic.MAX_RANGE);
		r.check(Math.abs(aim.ground().y - o.getY()) < 0.6, "Земля под прицелом — пол площадки (y=" + aim.ground().y + ")");
		for (Spell s : Spells.all()) {
			if (s.anchor.targeted()) r.check(s.range >= 32, s.id + ": дальность прицела " + s.range);
		}
		r.check(Spells.all().stream().filter(s -> s.anchor.targeted()).anyMatch(s -> s.range >= 100), "Есть заклинания с прицелом до 128 блоков");
	}

	// --- Местность ---

	private static void terrain(ServerLevel level, BlockPos o, DevTests.Report r) {
		FakePlayer p = mage(level, o);
		Optional<Spell> any = find(s -> true);
		if (any.isEmpty()) {
			r.lines.add("SKIP Магия: нет заклинаний");
			return;
		}
		BlockPos c = SpellTestKit.far(o, 0);
		SpellTestKit.fill(level, c, 5);
		Cast cast = new Cast(level, p, any.get(), 1, 1);
		int[] n = new int[1];
		SpellTestKit.griefing(false, () -> n[0] = Terrain.crater(cast, Vec3.atCenterOf(c), 4, 1, Terrain.Rim.NONE));
		r.check(n[0] == 0 && SpellTestKit.solid(level, c, 5) == 11 * 11 * 11, "Разрушение выключено — блоки целы");
		SpellTestKit.griefing(true, () -> n[0] = Terrain.crater(cast, Vec3.atCenterOf(c), 4, 1, Terrain.Rim.NONE));
		Terrain.flushNow();
		int left = SpellTestKit.solid(level, c, 5);
		r.check(n[0] > 150 && left < 11 * 11 * 11 - 150, "Воронка радиуса 4: сломано " + n[0] + " блоков");
		level.setBlock(c, Blocks.CHEST.defaultBlockState(), 2);
		level.setBlock(c.above(), Blocks.BEDROCK.defaultBlockState(), 2);
		SpellTestKit.griefing(true, () -> Terrain.crater(cast, Vec3.atCenterOf(c), 3, 1, Terrain.Rim.NONE));
		Terrain.flushNow();
		r.check(level.getBlockState(c).is(Blocks.CHEST) && level.getBlockState(c.above()).is(Blocks.BEDROCK), "Сундук и бедрок не ломаются");
		level.setBlock(c, Blocks.AIR.defaultBlockState(), 2);
		level.setBlock(c.above(), Blocks.AIR.defaultBlockState(), 2);

		BlockPos t = c.offset(0, 8, 0);
		level.setBlock(t, Blocks.AIR.defaultBlockState(), 2);
		boolean placed = Terrain.temp(cast, t, Blocks.ICE.defaultBlockState(), 40, false);
		r.check(placed && level.getBlockState(t).is(Blocks.ICE), "Временный лёд поставлен");
		SpellTestKit.griefing(false, () -> r.check(Terrain.temp(cast, t.above(), Blocks.STONE.defaultBlockState(), 40, false),
				"Временные блоки работают и без разрушения"));
		Terrain.restoreAll();
		r.check(level.getBlockState(t).isAir() && level.getBlockState(t.above()).isAir(), "Временные блоки исчезли");
		BlockPos tun = c.offset(0, -2, 0);
		SpellTestKit.fill(level, tun.offset(0, 0, 6), 2);
		int[] tn = new int[1];
		SpellTestKit.griefing(true, () -> tn[0] = Terrain.tunnel(cast, Vec3.atCenterOf(tun), Vec3.atCenterOf(tun.offset(0, 0, 12)), 1.5, Terrain.Rim.NONE));
		Terrain.flushNow();
		r.check(tn[0] > 20 && level.getBlockState(tun.offset(0, 0, 6)).isAir(), "Тоннель пробит (" + tn[0] + " блоков)");
	}

	// --- Зоны ---

	private static void zones(ServerLevel level, BlockPos o, DevTests.Report r) {
		FakePlayer p = mage(level, o);
		Optional<Spell> any = find(s -> true);
		if (any.isEmpty()) return;
		Cast cast = new Cast(level, p, any.get(), 1, 1);
		clean();
		int[] ticks = new int[1];
		boolean[] fired = new boolean[1];
		Zones.add(cast, Vec3.atCenterOf(o), 3, 20, (z, age) -> ticks[0]++);
		Zones.later(cast, 15, () -> fired[0] = true);
		Zones.add(cast, Vec3.atCenterOf(o), 3, 20, (z, age) -> {
			throw new IllegalStateException("проверка: ошибка в зоне");
		});
		run(25);
		r.check(ticks[0] == 20 && fired[0], "Зона прожила 20 тиков, отложенный удар сработал (тиков " + ticks[0] + ")");
		r.check(Zones.active() == 0, "Ошибка в зоне не ломает сервер, зона снята");
	}

	// --- Каталог ---

	private static void catalog(ServerLevel level, BlockPos o, DevTests.Report r) {
		List<Spell> all = Spells.all();
		r.check(all.size() >= 100, "Заклинаний " + all.size());
		for (School s : School.values()) {
			long n = all.stream().filter(x -> x.school == s).count();
			Set<Integer> tiers = new HashSet<>();
			all.stream().filter(x -> x.school == s).forEach(x -> tiers.add(x.tier));
			r.check(n >= 6, s.id + ": заклинаний " + n + ", ранги " + tiers);
		}
		long destroys = all.stream().filter(s -> s.destroys).count();
		r.check(destroys >= 15, "Разрушительных заклинаний (ломают блоки): " + destroys);
		long targeted = all.stream().filter(s -> s.anchor.targeted()).count();
		r.check(targeted >= 15, "Заклинаний с кругом у цели (земля, небо, линия): " + targeted);
		long multi = all.stream().filter(s -> s.layers >= 3 || s.anchor == Anchor.BACK || s.anchor == Anchor.ORBIT || s.fusion()).count();
		r.check(multi >= 10, "Заклинаний с несколькими кругами: " + multi);
		for (String id : Magic.STARTER) r.check(Spells.get(id) != null, "Стартовое заклинание " + id);

		List<String> problems = new ArrayList<>();
		for (Spell s : all) {
			String p = "spell.suchki." + s.id;
			for (String code : new String[] {"ru_ru", "en_us"}) {
				String name = SpellTestKit.text(code, p);
				String desc = SpellTestKit.text(code, p + ".desc");
				String chant = SpellTestKit.text(code, s.chantKey());
				if (name == null || name.isBlank()) problems.add(s.id + ": нет названия " + code);
				if (desc == null || desc.isBlank()) problems.add(s.id + ": нет описания " + code);
				if (s.tier >= 2 && (chant == null || chant.isBlank())) problems.add(s.id + ": нет фразы " + code);
				if (chant != null && s.tier >= 3 && chant.split("\\|").length < Math.min(4, s.tier - 1)) {
					problems.add(s.id + ": короткая фраза " + code + " (" + chant.split("\\|").length + " строк)");
				}
			}
			if (!SpellTestKit.resource("/assets/suchki/textures/gui/spell/" + s.id + ".png")) problems.add(s.id + ": нет иконки");
			boolean recipe = level.getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE,
					Identifier.fromNamespaceAndPath("suchki", "tome_" + s.id))).isPresent();
			if (s.tier >= 5 && recipe) problems.add(s.id + ": ранг " + s.tier + " не должен крафтиться");
			if (s.tier <= 3 && !recipe) problems.add(s.id + ": нет рецепта книги");
			int[][] manaRange = {{}, {5, 20}, {12, 35}, {20, 60}, {35, 95}, {60, 150}, {100, 230}, {150, 400}};
			int lo = manaRange[s.tier][0], hi = manaRange[s.tier][1];
			if (s.charged() && (s.mana < lo || s.mana > hi)) problems.add(s.id + ": мана " + s.mana + " вне " + lo + "…" + hi);
			if (s.charged() && s.chargeTicks < 4 + 2 * s.tier) problems.add(s.id + ": слишком быстрая зарядка " + s.chargeTicks);
			if (s.style == CastStyle.CHANNEL && s.loop == null) problems.add(s.id + ": у потока нет своего звука");
			if (s.fusion() && s.combo.size() < 2) problems.add(s.id + ": слияние без стихий");
		}
		for (School s : School.values()) {
			if (!BuiltInRegistries.SOUND_EVENT.containsKey(MagicSounds.CHARGE.get(s).location())) problems.add("нет звука зарядки " + s.id);
		}
		for (String pr : problems) r.check(false, pr);
		r.check(problems.isEmpty(), "Каталог без замечаний (" + problems.size() + ")");
	}

	// --- Каждое заклинание творится без ошибок ---

	private static void smoke(ServerLevel level, BlockPos o, DevTests.Report r) {
		int ok = 0;
		List<String> failed = new ArrayList<>();
		int i = 0;
		for (Spell s : Spells.all()) {
			BlockPos at = SpellTestKit.far(o, 300 + i++);
			FakePlayer p = mage(level, at);
			face(p, at, 10);
			Zombie z = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 8));
			try {
				if (s.charged()) {
					Magic.castNow(p, s, 1.0F);
				} else {
					Magic.channelNow(p, s, 30, 1.0F);
				}
				run(220);
				ok++;
			} catch (Throwable t) {
				failed.add(s.id + ": " + t);
				com.suchki.Suchki.LOGGER.error("suchki_test: заклинание {} упало", s.id, t);
			} finally {
				clean();
				level.getEntitiesOfClass(LivingEntity.class, new AABB(at).inflate(40), e -> !(e instanceof net.minecraft.world.entity.player.Player))
						.forEach(net.minecraft.world.entity.Entity::discard);
				z.discard();
			}
		}
		for (String f : failed) r.check(false, f);
		r.check(failed.isEmpty(), "Все заклинания творятся без ошибок (" + ok + "/" + Spells.all().size() + ")");
	}
}
