package com.suchki.dev;

import com.suchki.Suchki;
import com.suchki.SuchkiConfig;
import com.suchki.enchant.GrindstoneSplit;
import com.suchki.enchant.Perks;
import com.suchki.enchant.ProtectionCurve;
import com.suchki.enchant.SuchkiEnchantments;
import com.suchki.mixin.GrindstoneMenuAccessor;
import com.suchki.skill.PlayerSkills;
import com.suchki.skill.Skills;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Самопроверка всех механик Suchki на сервере. Доступна только в тестовых запусках:
 * команда /suchki_test (runServer с -Dsuchki.audit=true) и клиентский тест runClientGameTest.
 * Ставит блоки и мобов рядом с {@code origin}, действует «ненастоящим» игроком, пишет OK/FAIL по каждой проверке.
 */
public final class DevTests {
	private DevTests() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, ctx, env) -> dispatcher.register(
				Commands.literal("suchki_spells_dump").executes(c -> dumpSpells(c.getSource()))));
		CommandRegistrationCallback.EVENT.register((dispatcher, ctx, env) -> dispatcher.register(
				Commands.literal("suchki_test").executes(c -> runCommand(c.getSource(), ""))
						.then(Commands.argument("filter", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
								.executes(c -> runCommand(c.getSource(),
										com.mojang.brigadier.arguments.StringArgumentType.getString(c, "filter"))))));
	}

	/** /suchki_spells_dump — записать все заклинания (ранг, стихия, цена, якорь…) в magic_spells.json для документации. */
	private static int dumpSpells(net.minecraft.commands.CommandSourceStack source) {
		com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
		for (com.suchki.magic.Spell s : com.suchki.magic.Spells.all()) {
			com.google.gson.JsonObject o = new com.google.gson.JsonObject();
			o.addProperty("id", s.id);
			o.addProperty("school", s.school.id);
			o.addProperty("tier", s.tier);
			o.addProperty("style", s.style.name());
			o.addProperty("anchor", s.anchor.name());
			o.addProperty("layers", s.layers);
			o.addProperty("mana", s.mana);
			o.addProperty("charge", s.chargeTicks);
			o.addProperty("cooldown", s.cooldownTicks);
			o.addProperty("range", s.range);
			o.addProperty("destroys", s.destroys);
			o.addProperty("copies", s.maxCopies);
			com.google.gson.JsonArray combo = new com.google.gson.JsonArray();
			s.combo.forEach(c -> combo.add(c.id));
			o.add("combo", combo);
			arr.add(o);
		}
		try {
			java.nio.file.Files.writeString(java.nio.file.Path.of("magic_spells.json"),
					new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(arr), java.nio.charset.StandardCharsets.UTF_8);
		} catch (java.io.IOException e) {
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Записано заклинаний: " + arr.size()), false);
		return 1;
	}

	/** /suchki_test [фильтр] — фильтр: часть названия проверки (например «Огонь» или «Магия:»). */
	private static int runCommand(net.minecraft.commands.CommandSourceStack source, String filter) {
		// Фильтр можно передать файлом suchki_test_filter.txt (UTF-8) в папке сервера — консоль портит кириллицу.
		java.nio.file.Path file = java.nio.file.Path.of("suchki_test_filter.txt");
		if (filter.isBlank() && java.nio.file.Files.exists(file)) {
			try {
				filter = java.nio.file.Files.readString(file, java.nio.charset.StandardCharsets.UTF_8).trim();
			} catch (java.io.IOException ignored) {
				// без фильтра
			}
		}
		BlockPos origin = BlockPos.containing(source.getPosition()).above(30);
		List<String> report = run(source.getLevel(), origin, filter);
		report.forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
		return report.stream().anyMatch(l -> l.startsWith("FAIL")) ? 0 : 1;
	}

	/** Отчёт: строки «OK  …» и «FAIL …». */
	static final class Report {
		final List<String> lines = new ArrayList<>();

		void check(boolean ok, String what) {
			lines.add((ok ? "OK   " : "FAIL ") + what);
		}
	}

	interface Test {
		void run(ServerLevel level, BlockPos origin, Report r) throws Exception;
	}

	public static List<String> run(ServerLevel level, BlockPos origin) {
		return run(level, origin, "");
	}

	public static List<String> run(ServerLevel level, BlockPos origin, String filter) {
		Report r = new Report();
		Map<String, Test> tests = new java.util.LinkedHashMap<>();
		tests.put("Бур и Автоплавка", DevTests::drillAndSmelting);
		tests.put("Лесоруб", DevTests::timber);
		tests.put("Магнит", DevTests::magnet);
		tests.put("Наковальня", DevTests::anvil);
		tests.put("Защита", DevTests::protection);
		tests.put("Невесомость", DevTests::featherFalling);
		tests.put("Прочность", DevTests::unbreaking);
		tests.put("Урон чар", DevTests::damage);
		tests.put("Эффекты чар при ударе", DevTests::postAttack);
		tests.put("Совместимость и столы", DevTests::compatibility);
		tests.put("Удача, Добыча, Заговор огня", DevTests::lootPerks);
		tests.put("Спавнер Шёлковым касанием", DevTests::spawner);
		tests.put("Смесь зелий", DevTests::potionMix);
		tests.put("Сильные зелья", DevTests::strongPotions);
		tests.put("Отражение", DevTests::deflect);
		tests.put("Маяк", DevTests::beacon);
		tests.put("Точильный камень", DevTests::grindstone);
		tests.put("Библиотекарь", DevTests::librarian);
		tests.put("Роли", DevTests::roles);
		tests.put("Навыки: эффекты", DevTests::skillPerks);
		tests.put("Пружина: урон от падения", DevTests::springFall);
		tests.put("Баланс боевых ролей", DevTests::combatBalance);
		MagicTests.addTo(tests);
		Skills.testChanceOverride = -1;
		for (Map.Entry<String, Test> t : tests.entrySet()) {
			if (!filter.isBlank() && !t.getKey().toLowerCase(java.util.Locale.ROOT).contains(filter.toLowerCase(java.util.Locale.ROOT).trim())) {
				continue;
			}
			try {
				t.getValue().run(level, origin, r);
			} catch (Throwable e) {
				Suchki.LOGGER.error("suchki_test: {} упал", t.getKey(), e);
				r.check(false, t.getKey() + ": исключение " + e);
			} finally {
				cleanup(level, origin);
			}
		}
		long fails = r.lines.stream().filter(l -> l.startsWith("FAIL")).count();
		long oks = r.lines.stream().filter(l -> l.startsWith("OK")).count();
		long skips = r.lines.stream().filter(l -> l.startsWith("SKIP")).count();
		r.lines.add("Итого: " + oks + " OK, " + fails + " FAIL" + (skips > 0 ? ", " + skips + " SKIP" : "") + (filter.isBlank() ? "" : " (фильтр «" + filter + "»)"));
		return r.lines;
	}

	// --- Помощники ---

	private static ItemStack enchanted(ServerLevel level, Item item, Object... enchants) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.ENCHANTMENTS, enchantments(level, enchants));
		return stack;
	}

	private static ItemStack book(ServerLevel level, Object... enchants) {
		ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
		stack.set(DataComponents.STORED_ENCHANTMENTS, enchantments(level, enchants));
		return stack;
	}

	@SuppressWarnings("unchecked")
	private static ItemEnchantments enchantments(ServerLevel level, Object... enchants) {
		ItemEnchantments.Mutable m = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		for (int i = 0; i < enchants.length; i += 2) m.set(holder(level, (ResourceKey<Enchantment>) enchants[i]), (Integer) enchants[i + 1]);
		return m.toImmutable();
	}

	private static Holder<Enchantment> holder(ServerLevel level, ResourceKey<Enchantment> key) {
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	static FakePlayer player(ServerLevel level, BlockPos at, ItemStack held, float pitch) {
		FakePlayer p = FakePlayer.get(level);
		p.setGameMode(GameType.SURVIVAL);
		p.getInventory().clearContent();
		p.removeAllEffects();
		p.setHealth(p.getMaxHealth());
		p.experienceLevel = 0;
		p.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, pitch);
		// Взгляд живых существ идёт по голове — после прошлых проверок она могла остаться повёрнутой.
		p.setYHeadRot(0);
		p.yHeadRotO = 0;
		p.yBodyRot = 0;
		p.setItemInHand(InteractionHand.MAIN_HAND, held);
		return p;
	}

	/** Задать роль и навыки напрямую (минуя порядок покупки) — чтобы проверить эффект конкретного навыка. */
	static void skills(FakePlayer p, String role, String... nodes) {
		PlayerSkills s = role == null ? PlayerSkills.EMPTY : new PlayerSkills(Optional.of(role),
				Map.of(role, new PlayerSkills.Progress(50, 0, List.of(nodes))), 0L);
		p.setAttached(Skills.DATA, s);
		Skills.applyAttributes(p);
	}

	static <T extends Entity> T spawn(ServerLevel level, EntityType<T> type, BlockPos at) {
		T e = type.create(level, EntitySpawnReason.COMMAND);
		e.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		if (e instanceof Mob mob) mob.setNoAi(true);
		level.addFreshEntity(e);
		return e;
	}

	static int count(ServerLevel level, BlockPos center, int r, Item item) {
		int n = 0;
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(r))) {
			if (e.getItem().is(item)) n += e.getItem().getCount();
		}
		return n;
	}

	private static void cleanup(ServerLevel level, BlockPos origin) {
		// Магия: незавершённые зоны и временные блоки одной проверки не должны задевать следующую.
		com.suchki.magic.Zones.clear();
		com.suchki.magic.Terrain.restoreAll();
		SpellTestKit.releaseChunks(level);
		AABB area = new AABB(origin).inflate(48);
		level.getEntitiesOfClass(Entity.class, area, e -> !(e instanceof net.minecraft.world.entity.player.Player)).forEach(Entity::discard);
		Skills.testChanceOverride = -1;
		FakePlayer p = FakePlayer.get(level);
		skills(p, null);
		p.containerMenu = p.inventoryMenu;
	}

	private static float magicAbsorb(LivingEntity e, DamageSource s, float dmg) throws Exception {
		Method m = LivingEntity.class.getDeclaredMethod("getDamageAfterMagicAbsorb", DamageSource.class, float.class);
		m.setAccessible(true);
		return (float) m.invoke(e, s, dmg);
	}

	private static boolean near(double a, double b, double eps) {
		return Math.abs(a - b) <= eps;
	}

	// --- Добыча и инструменты ---

	private static void drillAndSmelting(ServerLevel level, BlockPos o, Report r) {
		BlockPos c = o.offset(0, 0, 0);
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) level.setBlock(c.offset(dx, 0, dz), Blocks.IRON_ORE.defaultBlockState(), 3);
		FakePlayer p = player(level, c.above(2), enchanted(level, Items.DIAMOND_PICKAXE, SuchkiEnchantments.DRILL, 1, SuchkiEnchantments.SMELTING, 1), 90);
		p.gameMode.destroyBlock(c);
		int left = 0;
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (!level.getBlockState(c.offset(dx, 0, dz)).isAir()) left++;
		r.check(left == 0, "Бур: копает 3×3 (осталось " + left + " из 9)");
		r.check(count(level, c, 4, Items.IRON_INGOT) >= 9 && count(level, c, 4, Items.RAW_IRON) == 0,
				"Автоплавка: слитков " + count(level, c, 4, Items.IRON_INGOT) + ", сырого железа " + count(level, c, 4, Items.RAW_IRON));

		// Присел — копает один блок.
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) level.setBlock(c.offset(dx, 0, dz), Blocks.STONE.defaultBlockState(), 3);
		p.setShiftKeyDown(true);
		p.gameMode.destroyBlock(c);
		p.setShiftKeyDown(false);
		int stoneLeft = 0;
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (level.getBlockState(c.offset(dx, 0, dz)).is(Blocks.STONE)) stoneLeft++;
		r.check(stoneLeft == 8, "Бур: присев — один блок (осталось " + stoneLeft + " из 9)");
	}

	private static void timber(ServerLevel level, BlockPos o, Report r) {
		BlockPos base = o.offset(10, 0, 0);
		for (int y = 0; y < 6; y++) level.setBlock(base.above(y), Blocks.OAK_LOG.defaultBlockState(), 3);
		level.setBlock(base.above(6), Blocks.OAK_LEAVES.defaultBlockState(), 3);
		FakePlayer p = player(level, base.offset(1, 0, 0), enchanted(level, Items.DIAMOND_AXE, SuchkiEnchantments.TIMBER, 1), 0);
		p.gameMode.destroyBlock(base);
		int left = 0;
		for (int y = 0; y < 6; y++) if (level.getBlockState(base.above(y)).is(Blocks.OAK_LOG)) left++;
		r.check(left == 0, "Лесоруб: рубит всё дерево (осталось брёвен " + left + " из 6)");

		// Постройка из брёвен без листвы — не трогаем.
		BlockPos wall = o.offset(14, 0, 0);
		for (int y = 0; y < 4; y++) level.setBlock(wall.above(y), Blocks.OAK_LOG.defaultBlockState(), 3);
		p.gameMode.destroyBlock(wall);
		int kept = 0;
		for (int y = 1; y < 4; y++) if (level.getBlockState(wall.above(y)).is(Blocks.OAK_LOG)) kept++;
		r.check(kept == 3, "Лесоруб: столб из брёвен без листвы не рубит (осталось " + kept + " из 3)");
	}

	private static void magnet(ServerLevel level, BlockPos o, Report r) {
		BlockPos c = o.offset(20, 0, 0);
		level.setBlock(c, Blocks.STONE.defaultBlockState(), 3);
		FakePlayer p = player(level, c.above(), enchanted(level, Items.IRON_PICKAXE, SuchkiEnchantments.MAGNET, 1), 90);
		p.gameMode.destroyBlock(c);
		r.check(p.getInventory().countItem(Items.COBBLESTONE) == 1 && count(level, c, 4, Items.COBBLESTONE) == 0,
				"Магнит: булыжник в инвентаре " + p.getInventory().countItem(Items.COBBLESTONE) + ", на земле " + count(level, c, 4, Items.COBBLESTONE));
	}

	// --- Наковальня ---

	private static void anvil(ServerLevel level, BlockPos o, Report r) {
		FakePlayer p = player(level, o, ItemStack.EMPTY, 0);
		AnvilMenu menu = new AnvilMenu(0, p.getInventory(), ContainerLevelAccess.create(level, o));
		menu.getSlot(0).set(book(level, Enchantments.SHARPNESS, 9));
		menu.getSlot(1).set(book(level, Enchantments.SHARPNESS, 9));
		ItemStack out = menu.getSlot(2).getItem();
		int lvl = out.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(holder(level, Enchantments.SHARPNESS));
		int cost = menu.getCost();
		r.check(lvl == 10, "Наковальня: Острота IX + IX = " + lvl + " (нужно X)");
		r.check(cost > 0 && cost <= SuchkiConfig.INSTANCE.anvilMaxCost, "Наковальня: цена " + cost + " (не больше " + SuchkiConfig.INSTANCE.anvilMaxCost + ")");

		ItemStack worn = new ItemStack(Items.DIAMOND_SWORD);
		worn.set(DataComponents.REPAIR_COST, 200);
		menu.getSlot(0).set(worn);
		menu.getSlot(1).set(book(level, Enchantments.SHARPNESS, 1));
		r.check(!menu.getSlot(2).getItem().isEmpty() && menu.getCost() <= SuchkiConfig.INSTANCE.anvilMaxCost,
				"Наковальня: нет «Слишком дорого!» при штрафе 200 (цена " + menu.getCost() + ")");
		r.check(AnvilMenu.calculateIncreasedRepairCost(7) == 7 + SuchkiConfig.INSTANCE.anvilPenaltyStep,
				"Наковальня: штраф растёт линейно (7 → " + AnvilMenu.calculateIncreasedRepairCost(7) + ")");

		skills(p, "blacksmith", "blacksmith.0.1", "blacksmith.0.2", "blacksmith.0.3", "blacksmith.0.4", "blacksmith.0.5");
		menu.getSlot(0).set(book(level, Enchantments.SHARPNESS, 9));
		menu.getSlot(1).set(book(level, Enchantments.SHARPNESS, 9));
		r.check(menu.getCost() < cost, "Кузнец: скидка на наковальне (" + cost + " → " + menu.getCost() + ")");
	}

	// --- Защита ---

	private static void protection(ServerLevel level, BlockPos o, Report r) throws Exception {
		r.check(near(CombatRules.getDamageAfterMagicAbsorb(10, 16), 3.6, 0.01),
				"Защита: до 20 очков как в ванили (10 урона, 16 очков → " + CombatRules.getDamageAfterMagicAbsorb(10, 16) + ")");
		float soft = CombatRules.getDamageAfterMagicAbsorb(10, 40);
		r.check(near(soft, 1.2, 0.1), "Защита: без потолка 80% (40 очков → " + soft + " урона, ≈88%)");
		r.check(ProtectionCurve.reduction(400) < 1.0, "Защита: никогда не 100% (400 очков → " + ProtectionCurve.reduction(400) + ")");

		Mob z = spawn(level, EntityType.ZOMBIE, o.offset(-6, 0, 0));
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			Item item = switch (slot) {
				case HEAD -> Items.IRON_HELMET;
				case CHEST -> Items.IRON_CHESTPLATE;
				case LEGS -> Items.IRON_LEGGINGS;
				default -> Items.IRON_BOOTS;
			};
			z.setItemSlot(slot, enchanted(level, item, Enchantments.PROTECTION, 10));
		}
		float general = EnchantmentHelper.getDamageProtection(level, z, level.damageSources().generic());
		float fire = EnchantmentHelper.getDamageProtection(level, z, level.damageSources().inFire());
		r.check(near(general, 40, 0.01), "Защита X ×4: от любого урона " + general + " очков (нужно 40)");
		r.check(near(fire, 80, 0.01), "Защита объединена: от огня " + fire + " очков (нужно 80)");
		float taken = magicAbsorb(z, level.damageSources().generic(), 10);
		r.check(near(taken, 10 * (1 - ProtectionCurve.reduction(40)) * 0.9, 0.05),
				"Перк Защиты X: −10% сверху (10 урона → " + String.format("%.2f", taken) + ")");
	}

	private static void featherFalling(ServerLevel level, BlockPos o, Report r) {
		Mob z = spawn(level, EntityType.ZOMBIE, o.offset(-10, 0, 0));
		z.setItemSlot(EquipmentSlot.FEET, enchanted(level, Items.IRON_BOOTS, Enchantments.FEATHER_FALLING, 8));
		float hp = z.getHealth();
		z.causeFallDamage(8.0, 1.0F, level.damageSources().fall());
		r.check(z.getHealth() == hp, "Невесомость VIII: падение с 8 блоков без урона (" + hp + " → " + z.getHealth() + ")");
		z.causeFallDamage(20.0, 1.0F, level.damageSources().fall());
		r.check(z.getHealth() < hp, "Невесомость VIII: падение с 20 блоков ранит (" + hp + " → " + z.getHealth() + ")");
	}

	private static void unbreaking(ServerLevel level, BlockPos o, Report r) {
		ItemStack x = enchanted(level, Items.IRON_SWORD, Enchantments.UNBREAKING, 10);
		x.setDamageValue(x.getMaxDamage() - 1);
		for (int i = 0; i < 50; i++) x.hurtAndBreak(5, level, null, item -> {});
		r.check(!x.isEmpty() && x.getDamageValue() == x.getMaxDamage() - 1, "Прочность X: предмет не ломается (прочность " +
				(x.getMaxDamage() - x.getDamageValue()) + ")");
		FakePlayer p = player(level, o, x, 0);
		r.check(Perks.wornOut(p, x), "Прочность X: изношенный предмет не бьёт и не копает");
		ItemStack plain = enchanted(level, Items.IRON_SWORD, Enchantments.UNBREAKING, 3);
		plain.setDamageValue(plain.getMaxDamage() - 1);
		for (int i = 0; i < 200 && !plain.isEmpty(); i++) plain.hurtAndBreak(5, level, null, item -> {});
		r.check(plain.isEmpty(), "Прочность III: обычный предмет ломается как в ванили");
	}

	// --- Урон и эффекты при ударе ---

	private static void damage(ServerLevel level, BlockPos o, Report r) {
		FakePlayer p = player(level, o, ItemStack.EMPTY, 0);
		Mob z = spawn(level, EntityType.ZOMBIE, o.offset(-3, 0, 0));
		DamageSource hit = level.damageSources().playerAttack(p);
		float s5 = EnchantmentHelper.modifyDamage(level, enchanted(level, Items.IRON_SWORD, Enchantments.SHARPNESS, 5), z, hit, 6);
		float s10 = EnchantmentHelper.modifyDamage(level, enchanted(level, Items.IRON_SWORD, Enchantments.SHARPNESS, 10), z, hit, 6);
		float smite = EnchantmentHelper.modifyDamage(level, enchanted(level, Items.IRON_SWORD, Enchantments.SMITE, 10), z, hit, 6);
		r.check(near(s5, 9, 0.01), "Острота V: +3 (6 → " + s5 + ")");
		r.check(near(s10, 14, 0.01), "Острота X: +8 (6 → " + s10 + ")");
		r.check(near(smite, 31, 0.01), "Небесная кара X по зомби: +25 (6 → " + smite + ")");
		float armorFx = EnchantmentHelper.modifyArmorEffectiveness(level, enchanted(level, Items.IRON_SWORD, Enchantments.SHARPNESS, 10), z, hit, 1.0F);
		r.check(near(armorFx, 0.75, 0.01), "Острота X: удар игнорирует 25% брони (эффективность брони " + armorFx + ")");
	}

	private static void postAttack(ServerLevel level, BlockPos o, Report r) {
		FakePlayer p = player(level, o, enchanted(level, Items.IRON_SWORD, Enchantments.SMITE, 10), 0);
		Mob z = spawn(level, EntityType.ZOMBIE, o.offset(-3, 0, 0));
		EnchantmentHelper.doPostAttackEffects(level, z, level.damageSources().playerAttack(p));
		r.check(z.getRemainingFireTicks() > 0, "Небесная кара X: поджигает нежить (огонь " + z.getRemainingFireTicks() + " тиков)");

		p.setItemInHand(InteractionHand.MAIN_HAND, enchanted(level, Items.IRON_SWORD, Enchantments.BANE_OF_ARTHROPODS, 10));
		Mob spider = spawn(level, EntityType.SPIDER, o.offset(-5, 0, 0));
		spider.hurtServer(level, level.damageSources().playerAttack(p), 1);
		EnchantmentHelper.doPostAttackEffects(level, spider, level.damageSources().playerAttack(p));
		MobEffectInstance weak = spider.getEffect(MobEffects.WEAKNESS);
		MobEffectInstance slow = spider.getEffect(MobEffects.SLOWNESS);
		r.check(weak != null && weak.getAmplifier() == 1 && slow != null && slow.getDuration() > 0,
				"Бич членистоногих X: Слабость II + замедление (слабость " + (weak != null) + ", замедление " + (slow != null) + ")");
		p.setItemInHand(InteractionHand.MAIN_HAND, enchanted(level, Items.IRON_SWORD, Enchantments.BANE_OF_ARTHROPODS, 5));
		Mob spider2 = spawn(level, EntityType.SPIDER, o.offset(-7, 0, 0));
		EnchantmentHelper.doPostAttackEffects(level, spider2, level.damageSources().playerAttack(p));
		r.check(spider2.hasEffect(MobEffects.SLOWNESS) && !spider2.hasEffect(MobEffects.POISON), "Бич членистоногих V: замедление как в ванили");

		p.setItemInHand(InteractionHand.MAIN_HAND, enchanted(level, Items.MACE, SuchkiEnchantments.STUN, 3));
		Mob z2 = spawn(level, EntityType.ZOMBIE, o.offset(-9, 0, 0));
		int stunned = 0;
		for (int i = 0; i < 60; i++) {
			z2.removeAllEffects();
			EnchantmentHelper.doPostAttackEffects(level, z2, level.damageSources().playerAttack(p));
			if (z2.hasEffect(MobEffects.SLOWNESS)) stunned++;
		}
		r.check(stunned > 3 && stunned < 30, "Оглушение III: сработало " + stunned + " раз из 60 (ожидается ≈24%)");

		var thorns = holder(level, Enchantments.THORNS).value().getEffects(EnchantmentEffectComponents.POST_ATTACK).toString();
		r.check(!thorns.toLowerCase().contains("itemdamage") && !thorns.contains("change_item_damage"),
				"Шипы: не тратят прочность брони");
	}

	private static void compatibility(ServerLevel level, BlockPos o, Report r) {
		r.check(Enchantment.areCompatible(holder(level, Enchantments.INFINITY), holder(level, Enchantments.MENDING)),
				"Бесконечность и Починка совместимы");
		r.check(!Enchantment.areCompatible(holder(level, SuchkiEnchantments.SMELTING), holder(level, Enchantments.SILK_TOUCH)),
				"Автоплавка несовместима с Шёлковым касанием");
		var table = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(EnchantmentTags.IN_ENCHANTING_TABLE);
		r.check(!table.contains(holder(level, Enchantments.FIRE_PROTECTION)) && table.contains(holder(level, Enchantments.PROTECTION)),
				"Огнестойкость убрана со стола, обычная Защита осталась");
		r.check(!table.contains(holder(level, SuchkiEnchantments.DRILL)) && table.contains(holder(level, SuchkiEnchantments.SPRING)),
				"Бур — сокровище (не на столе), Пружина — на столе");
	}

	private static void lootPerks(ServerLevel level, BlockPos o, Report r) {
		double gem = SuchkiConfig.INSTANCE.fortuneGemChance, head = SuchkiConfig.INSTANCE.lootingHeadChance;
		try {
			SuchkiConfig.INSTANCE.fortuneGemChance = 1;
			SuchkiConfig.INSTANCE.lootingHeadChance = 1;
			BlockPos c = o.offset(0, 0, 10);
			level.setBlock(c, Blocks.COAL_ORE.defaultBlockState(), 3);
			FakePlayer p = player(level, c.above(), enchanted(level, Items.DIAMOND_PICKAXE, Enchantments.FORTUNE, 5), 90);
			p.gameMode.destroyBlock(c);
			int gems = count(level, c, 3, Items.DIAMOND) + count(level, c, 3, Items.EMERALD);
			r.check(gems >= 1, "Удача V: самоцвет из руды (выпало " + gems + ")");

			p.setItemInHand(InteractionHand.MAIN_HAND, enchanted(level, Items.IRON_SWORD, Enchantments.LOOTING, 5));
			Mob z = spawn(level, EntityType.ZOMBIE, o.offset(4, 0, 10));
			z.hurtServer(level, level.damageSources().playerAttack(p), 1000);
			r.check(count(level, z.blockPosition(), 3, Items.ZOMBIE_HEAD) >= 1, "Добыча V: голова зомби выпала");

			p.setItemInHand(InteractionHand.MAIN_HAND, enchanted(level, Items.IRON_SWORD, Enchantments.FIRE_ASPECT, 5));
			Animal cow = spawn(level, EntityType.COW, o.offset(8, 0, 10));
			cow.hurtServer(level, level.damageSources().playerAttack(p), 1000);
			int cooked = count(level, cow.blockPosition(), 3, Items.COOKED_BEEF), raw = count(level, cow.blockPosition(), 3, Items.BEEF);
			r.check(cooked >= 1 && raw == 0, "Заговор огня V: дроп жареный (жареной " + cooked + ", сырой " + raw + ")");
		} finally {
			SuchkiConfig.INSTANCE.fortuneGemChance = gem;
			SuchkiConfig.INSTANCE.lootingHeadChance = head;
		}
	}

	private static void spawner(ServerLevel level, BlockPos o, Report r) throws Exception {
		BlockPos c = o.offset(0, 0, 14);
		level.setBlock(c, Blocks.SPAWNER.defaultBlockState(), 3);
		((SpawnerBlockEntity) level.getBlockEntity(c)).setEntityId(EntityType.ZOMBIE, level.random);
		FakePlayer p = player(level, c.above(), enchanted(level, Items.DIAMOND_PICKAXE, Enchantments.SILK_TOUCH, 1), 90);
		p.gameMode.destroyBlock(c);
		ItemStack drop = ItemStack.EMPTY;
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(c).inflate(3))) if (e.getItem().is(Items.SPAWNER)) drop = e.getItem();
		r.check(!drop.isEmpty(), "Шёлковое касание: спавнер выпал предметом");
		if (drop.isEmpty()) return;
		BlockPos place = c.offset(2, 0, 0);
		level.setBlock(place, Blocks.SPAWNER.defaultBlockState(), 3);
		Method m = BlockItem.class.getDeclaredMethod("updateCustomBlockEntityTag", BlockPos.class,
				net.minecraft.world.level.Level.class, net.minecraft.world.entity.player.Player.class, ItemStack.class, BlockState.class);
		m.setAccessible(true);
		m.invoke(Items.SPAWNER, place, level, p, drop, level.getBlockState(place));
		Entity shown = ((SpawnerBlockEntity) level.getBlockEntity(place)).getSpawner().getOrCreateDisplayEntity(level, place);
		r.check(shown != null && shown.getType() == EntityType.ZOMBIE, "Спавнер после установки призывает зомби (" +
				(shown == null ? "никого" : shown.getType().toShortString()) + ")");
	}

	// --- Зелья ---

	private static ItemStack potion(Holder<net.minecraft.world.item.alchemy.Potion> p) {
		return PotionContents.createItemStack(Items.POTION, p);
	}

	private static void potionMix(ServerLevel level, BlockPos o, Report r) {
		CraftingInput three = CraftingInput.of(3, 1, List.of(potion(Potions.SWIFTNESS), potion(Potions.STRENGTH), potion(Potions.REGENERATION)));
		var recipe = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, three, level);
		r.check(recipe.isPresent(), "Смесь зелий: 3 зелья смешиваются");
		if (recipe.isPresent()) {
			ItemStack mix = recipe.get().value().assemble(three, level.registryAccess());
			int effects = 0;
			for (MobEffectInstance ignored : mix.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).getAllEffects()) effects++;
			r.check(effects == 3, "Смесь зелий: в смеси " + effects + " эффекта (нужно 3)");
			int bottles = 0;
			for (ItemStack s : recipe.get().value().getRemainingItems(three)) if (s.is(Items.GLASS_BOTTLE)) bottles++;
			r.check(bottles == 2, "Смесь зелий: возвращает " + bottles + " пустых склянки (нужно 2)");
		}
		CraftingInput four = CraftingInput.of(2, 2, List.of(potion(Potions.SWIFTNESS), potion(Potions.STRENGTH),
				potion(Potions.REGENERATION), potion(Potions.FIRE_RESISTANCE)));
		r.check(level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, four, level).isEmpty(), "Смесь зелий: 4 зелья — нельзя");
		CraftingInput mixed = CraftingInput.of(2, 1, List.of(potion(Potions.SWIFTNESS), new ItemStack(Items.SPLASH_POTION)));
		r.check(level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, mixed, level).isEmpty(), "Смесь зелий: питьевое + взрывное — нельзя");
	}

	private static void strongPotions(ServerLevel level, BlockPos o, Report r) {
		var brewing = level.potionBrewing();
		ItemStack third = brewing.mix(new ItemStack(Items.ECHO_SHARD), potion(Potions.STRONG_STRENGTH));
		ItemStack longer = brewing.mix(new ItemStack(Items.AMETHYST_SHARD), potion(Potions.STRONG_STRENGTH));
		var e3 = third.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).getAllEffects().iterator();
		var eL = longer.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).getAllEffects().iterator();
		int amp = e3.hasNext() ? e3.next().getAmplifier() : -1;
		int dur = eL.hasNext() ? eL.next().getDuration() : -1;
		int base = Potions.STRONG_STRENGTH.value().getEffects().getFirst().getDuration();
		r.check(amp == 2, "Эхо-осколок: Сила II → Сила " + (amp + 1));
		r.check(dur == base * 2, "Аметист: Сила II " + base / 20 + " с → " + dur / 20 + " с");
		r.check(brewing.mix(new ItemStack(Items.AMETHYST_SHARD), potion(Potions.HEALING)).is(Items.POTION)
				&& !brewing.hasMix(potion(Potions.HEALING), new ItemStack(Items.AMETHYST_SHARD)), "Аметист не действует на мгновенные зелья");
	}

	// --- Отражение ---

	private static void deflect(ServerLevel level, BlockPos o, Report r) {
		double chance = SuchkiConfig.INSTANCE.deflectChancePerLevel;
		try {
			SuchkiConfig.INSTANCE.deflectChancePerLevel = 1;
			FakePlayer p = player(level, o.offset(0, 0, -10), ItemStack.EMPTY, 0);
			Mob z = spawn(level, EntityType.ZOMBIE, o.offset(0, 0, -4));
			z.setItemSlot(EquipmentSlot.CHEST, enchanted(level, Items.IRON_CHESTPLATE, SuchkiEnchantments.DEFLECT, 5));
			Arrow arrow = new Arrow(level, p, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
			arrow.setPos(z.getX(), z.getEyeY(), z.getZ() - 1);
			arrow.setDeltaMovement(0, 0, 2);
			ProjectileDeflection d = z.deflection(arrow);
			r.check(d != ProjectileDeflection.NONE, "Отражение V: снаряд отбит");
			d.deflect(arrow, z, level.random);
			Vec3 toShooter = p.getEyePosition().subtract(arrow.position()).normalize();
			r.check(arrow.getDeltaMovement().normalize().dot(toShooter) > 0.9, "Отражение: стрела летит обратно в стрелка");
			Mob plain = spawn(level, EntityType.ZOMBIE, o.offset(3, 0, -4));
			r.check(plain.deflection(arrow) == ProjectileDeflection.NONE, "Без Отражения снаряды не отбиваются");
		} finally {
			SuchkiConfig.INSTANCE.deflectChancePerLevel = chance;
		}
	}

	// --- Маяк, точильный камень, жители ---

	private static void beacon(ServerLevel level, BlockPos o, Report r) throws Exception {
		BlockPos top = o.offset(40, 12, 0);
		for (int layer = 1; layer <= 5; layer++) {
			for (int dx = -layer; dx <= layer; dx++) for (int dz = -layer; dz <= layer; dz++) {
				level.setBlock(top.offset(dx, -layer, dz), Blocks.IRON_BLOCK.defaultBlockState(), 2);
			}
		}
		Method update = BeaconBlockEntity.class.getDeclaredMethod("updateBase", net.minecraft.world.level.Level.class, int.class, int.class, int.class);
		update.setAccessible(true);
		int tiers = (int) update.invoke(null, level, top.getX(), top.getY(), top.getZ());
		r.check(tiers == 5, "Маяк: пирамида из 5 слоёв даёт ярус " + tiers);

		// Маяк действует только на настоящих игроков в мире — проверяем, если такой есть (клиентский тест).
		net.minecraft.server.level.ServerPlayer p = level.players().stream().filter(pl -> !(pl instanceof FakePlayer)).findFirst().orElse(null);
		if (p == null) {
			r.lines.add("SKIP Маяк 5 яруса: эффекты проверяются в клиентском тесте (нужен настоящий игрок)");
			return;
		}
		Vec3 back = p.position();
		p.teleportTo(top.getX() + 0.5, top.getY() + 2, top.getZ() + 0.5);
		p.removeAllEffects();
		Method apply = BeaconBlockEntity.class.getDeclaredMethod("applyEffects", net.minecraft.world.level.Level.class, BlockPos.class,
				int.class, Holder.class, Holder.class);
		apply.setAccessible(true);
		apply.invoke(null, level, top, 5, MobEffects.SPEED, MobEffects.REGENERATION);
		MobEffectInstance speed = p.getEffect(MobEffects.SPEED);
		r.check(speed != null && speed.getAmplifier() == 1 && p.hasEffect(MobEffects.REGENERATION),
				"Маяк 5 яруса: Скорость II + Регенерация одновременно");
		p.removeAllEffects();
		apply.invoke(null, level, top, 4, MobEffects.SPEED, MobEffects.REGENERATION);
		speed = p.getEffect(MobEffects.SPEED);
		r.check(speed != null && speed.getAmplifier() == 0, "Маяк 4 яруса работает как в ванили");
		p.removeAllEffects();
		p.teleportTo(back.x, back.y, back.z);
	}

	private static void grindstone(ServerLevel level, BlockPos o, Report r) {
		ItemStack sword = enchanted(level, Items.IRON_SWORD, Enchantments.SHARPNESS, 5, Enchantments.BINDING_CURSE, 1);
		FakePlayer p = player(level, o, ItemStack.EMPTY, 0);
		GrindstoneMenu menu = new GrindstoneMenu(0, p.getInventory(), ContainerLevelAccess.create(level, o));
		p.containerMenu = menu;
		Container in = ((GrindstoneMenuAccessor) menu).suchki$repairSlots();
		r.check(menu.getSlot(1).mayPlace(new ItemStack(Items.BOOK)), "Точильный камень: принимает обычную книгу");
		in.setItem(0, sword);
		in.setItem(1, new ItemStack(Items.BOOK, 3));
		Slot result = menu.getSlot(2);
		var stored = result.getItem().getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
		r.check(result.getItem().is(Items.ENCHANTED_BOOK) && stored.size() == 1, "Точильный камень: на книге " + stored.size()
				+ " чара (проклятие осталось на мече)");
		r.check(!result.mayPickup(p), "Точильный камень: без опыта забрать нельзя");
		p.experienceLevel = 30;
		r.check(result.mayPickup(p), "Точильный камень: с опытом можно");
		int cost = GrindstoneSplit.cost(sword);
		result.onTake(p, result.getItem());
		ItemEnchantments left = in.getItem(0).getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		r.check(p.experienceLevel == 30 - cost && left.size() == 1 && in.getItem(1).getCount() == 2,
				"Точильный камень: списано " + (30 - p.experienceLevel) + " ур. (нужно " + cost + "), на мече " + left.size()
						+ " чара, книг осталось " + in.getItem(1).getCount());
	}

	private static void librarian(ServerLevel level, BlockPos o, Report r) {
		double chance = SuchkiConfig.INSTANCE.librarianHighBookChance;
		try {
			SuchkiConfig.INSTANCE.librarianHighBookChance = 1;
			boolean found = false;
			for (int attempt = 0; attempt < 5 && !found; attempt++) {
				Villager v = spawn(level, EntityType.VILLAGER, o.offset(-14, 0, attempt * 2));
				v.setVillagerData(v.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.LIBRARIAN).withLevel(5));
				for (MerchantOffer offer : v.getOffers()) {
					ItemStack res = offer.getResult();
					for (var e : res.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet()) {
						if (e.getIntValue() > e.getKey().value().getMaxLevel()) found = true;
					}
				}
			}
			r.check(found, "Мастер-библиотекарь продаёт книгу выше ванильного максимума");
		} finally {
			SuchkiConfig.INSTANCE.librarianHighBookChance = chance;
		}
	}

	// --- Роли и навыки ---

	private static void roles(ServerLevel level, BlockPos o, Report r) {
		FakePlayer p = player(level, o, ItemStack.EMPTY, 0);
		skills(p, null);
		var speed = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_BREAK_SPEED);
		double base = speed.getValue();

		Skills.chooseRole(p, "miner");
		r.check(Skills.get(p).active().filter("miner"::equals).isPresent(), "Роль: выбран Шахтёр");
		Skills.addXp(p, "miner", 500);
		Skills.addXp(p, "warrior", 500);
		var miner = Skills.get(p).progress("miner");
		r.check(miner.level() > 5 && Skills.get(p).progress("warrior").level() == 1,
				"Опыт идёт только активной роли (Шахтёр ур. " + miner.level() + ", Воин ур. " + Skills.get(p).progress("warrior").level() + ")");
		Skills.unlock(p, "miner.0.3");
		r.check(!Skills.get(p).progress("miner").has("miner.0.3"), "Навыки: ступень 3 без 1–2 не покупается");
		Skills.unlock(p, "miner.0.1");
		Skills.unlock(p, "miner.0.2");
		double boosted = speed.getValue();
		r.check(boosted > base * 1.15, "Навыки: скорость копания " + String.format("%.2f → %.2f", base, boosted));

		Skills.chooseRole(p, "warrior");
		r.check(Skills.get(p).active().filter("miner"::equals).isPresent(), "Смена роли: перезарядка не даёт сменить сразу");
		int cd = SuchkiConfig.INSTANCE.roleSwitchCooldownMinutes;
		SuchkiConfig.INSTANCE.roleSwitchCooldownMinutes = 0;
		Skills.chooseRole(p, "warrior");
		boolean warrior = Skills.get(p).active().filter("warrior"::equals).isPresent();
		double afterSwitch = speed.getValue();
		Skills.chooseRole(p, "miner");
		SuchkiConfig.INSTANCE.roleSwitchCooldownMinutes = cd;
		var back = Skills.get(p).progress("miner");
		r.check(warrior && near(afterSwitch, base, 1e-6), "Смена роли: бонусы прошлой роли снимаются");
		r.check(back.level() == miner.level() && back.has("miner.0.2") && near(speed.getValue(), boosted, 1e-6),
				"Смена роли: прогресс каждой роли сохраняется");

		p.experienceLevel = 0;
		Skills.reset(p);
		r.check(back.nodes().size() == Skills.get(p).progress("miner").nodes().size(), "Сброс навыков: без опыта не сбрасывает");
		p.experienceLevel = 20;
		Skills.reset(p);
		r.check(Skills.get(p).progress("miner").nodes().isEmpty() && p.experienceLevel == 20 - SuchkiConfig.INSTANCE.skillResetLevels,
				"Сброс навыков: очки вернулись, списано " + (20 - p.experienceLevel) + " ур.");

		BlockPos c = o.offset(3, 0, 3);
		level.setBlock(c, Blocks.STONE.defaultBlockState(), 3);
		int before = Skills.get(p).progress("miner").xp() + Skills.get(p).progress("miner").level() * 100000;
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
		p.gameMode.destroyBlock(c);
		int after = Skills.get(p).progress("miner").xp() + Skills.get(p).progress("miner").level() * 100000;
		r.check(after > before, "Опыт Шахтёра за добытый камень");
	}

	private static void skillPerks(ServerLevel level, BlockPos o, Report r) throws Exception {
		FakePlayer p = player(level, o, ItemStack.EMPTY, 0);
		Mob z = spawn(level, EntityType.ZOMBIE, o.offset(-3, 0, -3));
		DamageSource hit = level.damageSources().playerAttack(p);

		skills(p, "warrior", "warrior.2.2");
		r.check(near(magicAbsorb(z, hit, 10), 11.0, 0.01), "Воин «Точный удар»: +10% урона (" + magicAbsorb(z, hit, 10) + ")");
		skills(p, "warrior", "warrior.0.1");
		p.setHealth(5);
		r.check(near(magicAbsorb(z, hit, 10), 11, 0.01), "Воин «Ярость»: +10% при низком здоровье (" + magicAbsorb(z, hit, 10) + ")");
		p.setHealth(p.getMaxHealth());
		r.check(near(magicAbsorb(z, hit, 10), 10, 0.01), "Воин «Ярость»: без бонуса при полном здоровье");
		skills(p, "warrior", "warrior.1.4");
		r.check(near(magicAbsorb(p, level.damageSources().generic(), 10), 9.0, 0.01), "Воин «Бывалый»: −10% получаемого урона");
		skills(p, "hunter", "hunter.2.1");
		r.check(near(magicAbsorb(z, hit, 10), 10.5, 0.01), "Охотник: +5% по враждебным мобам");

		skills(p, "miner", "miner.2.4");
		Field ex = FoodData.class.getDeclaredField("exhaustionLevel");
		ex.setAccessible(true);
		ex.setFloat(p.getFoodData(), 0);
		p.causeFoodExhaustion(1.0F);
		r.check(near(ex.getFloat(p.getFoodData()), 0.8, 0.01), "Шахтёр: голод −20% (истощение " + ex.getFloat(p.getFoodData()) + ")");
		skills(p, "miner", "miner.2.5");
		r.check(near(p.getMaxHealth(), 24, 0.01), "Шахтёр «Хозяин глубин»: +2 сердца (здоровье " + p.getMaxHealth() + ")");

		skills(p, "alchemist", "alchemist.0.1", "alchemist.0.2", "alchemist.0.3", "alchemist.0.4", "alchemist.0.5");
		new PotionContents(Potions.SWIFTNESS).applyToLivingEntity(p, 1.0F);
		MobEffectInstance sp = p.getEffect(MobEffects.SPEED);
		int baseDur = Potions.SWIFTNESS.value().getEffects().getFirst().getDuration();
		r.check(sp != null && near(sp.getDuration(), baseDur * 1.6, 2), "Алхимик: зелья дольше на 60% (" + (sp == null ? 0 : sp.getDuration() / 20) + " с)");
		skills(p, "alchemist", "alchemist.1.1");
		p.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
		MobEffectInstance poison = p.getEffect(MobEffects.POISON);
		r.check(poison != null && poison.getDuration() == 180, "Алхимик: яд на 10% короче (" + (poison == null ? 0 : poison.getDuration()) + " тиков)");

		Skills.testChanceOverride = 1;
		skills(p, "archer", "archer.1.1");
		Method useAmmo = ProjectileWeaponItem.class.getDeclaredMethod("useAmmo", ItemStack.class, ItemStack.class, LivingEntity.class, boolean.class);
		useAmmo.setAccessible(true);
		ItemStack arrows = new ItemStack(Items.ARROW, 5);
		useAmmo.invoke(null, new ItemStack(Items.BOW), arrows, p, false);
		r.check(arrows.getCount() == 5, "Лучник «Бережливый»: стрела не потрачена (" + arrows.getCount() + " из 5)");

		skills(p, "farmer", "farmer.1.1");
		Animal a = spawn(level, EntityType.COW, o.offset(6, 0, 6));
		Animal b2 = spawn(level, EntityType.COW, o.offset(7, 0, 6));
		a.setInLove(p);
		b2.setInLove(p);
		a.spawnChildFromBreeding(level, b2);
		long babies = level.getEntitiesOfClass(Animal.class, new AABB(o.offset(6, 0, 6)).inflate(4), Animal::isBaby).size();
		r.check(babies == 2, "Фермер «Пастух»: двойняшки (" + babies + " телёнка)");

		skills(p, "miner", "miner.1.1");
		BlockPos ore = o.offset(0, 0, -14);
		level.setBlock(ore, Blocks.IRON_ORE.defaultBlockState(), 3);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
		p.gameMode.destroyBlock(ore);
		r.check(count(level, ore, 3, Items.RAW_IRON) == 2, "Шахтёр «Глазомер»: двойная руда (" + count(level, ore, 3, Items.RAW_IRON) + ")");

		skills(p, "lumberjack", "lumberjack.1.1");
		BlockPos log = o.offset(4, 0, -14);
		level.setBlock(log, Blocks.OAK_LOG.defaultBlockState(), 3);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
		p.gameMode.destroyBlock(log);
		r.check(count(level, log, 3, Items.OAK_LOG) == 2, "Лесоруб «Сборщик»: двойное бревно (" + count(level, log, 3, Items.OAK_LOG) + ")");

		skills(p, "farmer", "farmer.0.1");
		BlockPos crop = o.offset(8, 0, -14);
		level.setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState(), 3);
		level.setBlock(crop, ((CropBlock) Blocks.WHEAT).getStateForAge(7), 3);
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		p.gameMode.destroyBlock(crop);
		r.check(count(level, crop, 3, Items.WHEAT) == 2, "Фермер «Огородник»: двойной урожай (" + count(level, crop, 3, Items.WHEAT) + " пшеницы)");

		skills(p, "hunter", "hunter.1.1");
		Mob chicken = spawn(level, EntityType.CHICKEN, o.offset(12, 0, -14));
		chicken.hurtServer(level, level.damageSources().playerAttack(p), 1000);
		r.check(count(level, chicken.blockPosition(), 3, Items.CHICKEN) == 2, "Охотник «Добытчик»: двойная добыча с моба ("
				+ count(level, chicken.blockPosition(), 3, Items.CHICKEN) + " курицы)");
		Skills.testChanceOverride = -1;

		skills(p, "blacksmith", "blacksmith.1.1", "blacksmith.1.2", "blacksmith.1.3", "blacksmith.1.4", "blacksmith.1.5");
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		sword.onCraftedBy(p, 1);
		int once = sword.getMaxDamage();
		sword.onCraftedBy(p, 1);
		r.check(once == Math.round(1561 * 1.5) && sword.getMaxDamage() == once,
				"Кузнец «Закалка»: +50% прочности без накопления (" + once + ", повторно " + sword.getMaxDamage() + ")");

		skills(p, null);
		float plain = p.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState());
		skills(p, "lumberjack", "lumberjack.0.1");
		float fast = p.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState());
		r.check(near(fast / plain, 1.15, 0.01), "Лесоруб «Острый топор»: рубит быстрее (×" + String.format("%.2f", fast / plain) + ")");

		// Ночное зрение Шахтёра: решение «включить/держать/выключить» и снятие без мерцания.
		Method tick = com.suchki.skill.SkillEffects.class.getDeclaredMethod("tick", net.minecraft.server.level.ServerPlayer.class);
		tick.setAccessible(true);
		boolean logic = com.suchki.skill.SkillEffects.wantsNightVision(10, 3, false)
				&& !com.suchki.skill.SkillEffects.wantsNightVision(10, 9, false)
				&& com.suchki.skill.SkillEffects.wantsNightVision(10, 9, true)
				&& !com.suchki.skill.SkillEffects.wantsNightVision(10, 12, true)
				&& !com.suchki.skill.SkillEffects.wantsNightVision(60, 0, false);
		r.check(logic, "Шахтёр «Кошачий глаз»: включается в темноте ниже Y=40, держится до света 11 (без дёрганья)");
		skills(p, "miner", "miner.2.1");
		p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 15 * 20, 0, true, false, true));
		p.snapTo(o.getX() + 0.5, Math.max(o.getY(), 100), o.getZ() + 0.5, 0, 0);
		tick.invoke(null, p);
		r.check(!p.hasEffect(MobEffects.NIGHT_VISION), "Шахтёр «Кошачий глаз»: на поверхности снимается сразу, без мерцания");
		p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 3600, 0, false, true, true));
		tick.invoke(null, p);
		r.check(p.hasEffect(MobEffects.NIGHT_VISION), "Зелье ночного зрения навык не снимает");
		p.removeAllEffects();
		BlockPos cave = o.offset(20, 0, -20);

		skills(p, "hunter", "hunter.0.1");
		Mob far = spawn(level, EntityType.ZOMBIE, cave.offset(5, 0, 0));
		Mob tooFar = spawn(level, EntityType.ZOMBIE, cave.offset(20, 0, 0));
		p.snapTo(cave.getX() + 0.5, cave.getY(), cave.getZ() + 0.5, 0, 0);
		tick.invoke(null, p);
		List<Integer> sensed = com.suchki.skill.SkillEffects.hunterTargets(p);
		r.check(sensed.contains(far.getId()) && !sensed.contains(tooFar.getId()),
				"Охотник «Чуткий слух»: моб в 5 блоках подсвечен, в 20 — нет");
		r.check(!far.hasEffect(MobEffects.GLOWING), "Охотник «Чуткий слух»: подсветка только у охотника (без эффекта свечения)");

		skills(p, "farmer", "farmer.2.1", "farmer.2.2", "farmer.2.3", "farmer.2.4", "farmer.2.5");
		BlockPos field = o.offset(-20, 0, 20);
		int planted = 0;
		for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
			level.setBlock(field.offset(dx, -1, dz), Blocks.FARMLAND.defaultBlockState(), 3);
			level.setBlock(field.offset(dx, 0, dz), Blocks.WHEAT.defaultBlockState(), 3);
			planted++;
		}
		p.snapTo(field.getX() + 0.5, field.getY(), field.getZ() + 0.5, 0, 0);
		for (int i = 0; i < 5; i++) tick.invoke(null, p);
		int grown = 0;
		for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
			BlockState s = level.getBlockState(field.offset(dx, 0, dz));
			if (s.getBlock() instanceof CropBlock cb && cb.getAge(s) > 0) grown++;
		}
		r.check(grown > 0, "Фермер «Садовник»: посевы рядом подрастают (" + grown + " из " + planted + ")");
	}

	// --- Баланс ---

	/** Все узлы роли (как на 50 уровне). */
	private static String[] allNodes(String role) {
		return com.suchki.skill.SkillTree.role(role).nodes().stream().map(com.suchki.skill.SkillTree.Node::id).toArray(String[]::new);
	}

	/** Итоги полностью прокачанных боевых ролей: урон, защита, здоровье — как в таблице баланса. */
	private static void combatBalance(ServerLevel level, BlockPos o, Report r) throws Exception {
		FakePlayer p = player(level, o, ItemStack.EMPTY, 0);
		Mob z = spawn(level, EntityType.ZOMBIE, o.offset(-3, 0, -3));
		DamageSource melee = level.damageSources().playerAttack(p);
		DamageSource arrow = level.damageSources().arrow(EntityType.ARROW.create(level, EntitySpawnReason.COMMAND), p);

		skills(p, "warrior", allNodes("warrior"));
		p.setHealth(p.getMaxHealth());
		float full = magicAbsorb(z, melee, 10);
		p.setHealth(p.getMaxHealth() / 4);
		float low = magicAbsorb(z, melee, 10);
		p.setHealth(p.getMaxHealth());
		r.check(near(full, 14.0, 0.01) && near(low, 19.0, 0.01) && near(magicAbsorb(p, level.damageSources().generic(), 10), 8.0, 0.01)
				&& near(p.getMaxHealth(), 26.0, 0.01),
				"Воин 50: урон вблизи +40% (" + full + "), при ранении +90% (" + low + "), получаемый −20%, 13 сердец ("
						+ p.getMaxHealth() + ")");

		skills(p, "archer", allNodes("archer"));
		r.check(near(magicAbsorb(z, arrow, 10), 15.5, 0.01) && near(magicAbsorb(p, level.damageSources().generic(), 10), 9.0, 0.01)
				&& near(p.getMaxHealth(), 24.0, 0.01),
				"Лучник 50: снаряды +55% (" + magicAbsorb(z, arrow, 10) + "), получаемый −10%, 12 сердец");

		skills(p, "hunter", allNodes("hunter"));
		r.check(near(magicAbsorb(z, melee, 10), 16.0, 0.01) && near(magicAbsorb(z, arrow, 10), 16.0, 0.01)
				&& near(magicAbsorb(p, level.damageSources().generic(), 10), 9.0, 0.01) && near(p.getMaxHealth(), 24.0, 0.01)
				&& near(Skills.get(p).perk(com.suchki.skill.Perk.HUNTER_SENSE), 32, 0.01),
				"Охотник 50: по монстрам +60% вблизи и снарядами (" + magicAbsorb(z, melee, 10) + "), получаемый −10%, 12 сердец, подсветка 32");
		skills(p, null);
		p.setHealth(p.getMaxHealth());
	}

	// --- Пружина ---

	/** Высота, набранная прыжками в воздухе, не ранит; падение ниже точки отрыва от земли — как в ванили. */
	private static void springFall(ServerLevel level, BlockPos o, Report r) {
		FakePlayer p = player(level, o, ItemStack.EMPTY, 0);
		p.setItemSlot(EquipmentSlot.FEET, enchanted(level, Items.IRON_BOOTS, SuchkiEnchantments.SPRING, 3));
		p.resetCurrentImpulseContext();
		double ground = o.getY();

		// Ровная земля: три прыжка в воздухе (4-й не даётся), вершина ≈5,5 блока, приземление туда же.
		p.setOnGround(true);
		com.suchki.enchant.SpringJump.track(p);
		p.setOnGround(false);
		boolean jumps = true;
		for (int i = 0; i < 3; i++) {
			p.snapTo(o.getX() + 0.5, ground + 1.2 + i * 1.4, o.getZ() + 0.5, 0, 0);
			jumps &= com.suchki.enchant.SpringJump.onJump(p);
		}
		r.check(jumps && !com.suchki.enchant.SpringJump.onJump(p), "Пружина III: три прыжка в воздухе, четвёртого нет");
		p.snapTo(o.getX() + 0.5, ground, o.getZ() + 0.5, 0, 0);
		r.check(!p.causeFallDamage(5.5, 1.0F, level.damageSources().fall()),
				"Пружина III: падение с вершины прыжка (5,5 блока) на ту же высоту без урона");

		// Обрыв: оторвался от земли на 10 блоков выше, прыгнул в воздухе и упал вниз — урон за 10 блоков.
		p.resetCurrentImpulseContext();
		p.snapTo(o.getX() + 0.5, ground + 10, o.getZ() + 0.5, 0, 0);
		p.setOnGround(true);
		com.suchki.enchant.SpringJump.track(p);
		p.setOnGround(false);
		p.snapTo(o.getX() + 0.5, ground + 11, o.getZ() + 0.5, 0, 0);
		com.suchki.enchant.SpringJump.onJump(p);
		p.snapTo(o.getX() + 0.5, ground, o.getZ() + 0.5, 0, 0);
		r.check(p.causeFallDamage(12.4, 1.0F, level.damageSources().fall()), "Пружина III: падение с обрыва ниже точки прыжка ранит");
		p.resetCurrentImpulseContext();
	}
}
