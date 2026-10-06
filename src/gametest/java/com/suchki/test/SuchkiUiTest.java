package com.suchki.test;

import com.suchki.client.RoleSelectScreen;
import com.suchki.client.SkillTreeScreen;
import com.suchki.dev.DevTests;
import com.suchki.enchant.SuchkiEnchantments;
import com.suchki.mixin.GrindstoneMenuAccessor;
import com.suchki.skill.Skills;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * Клиентский тест Suchki: русский язык, три размера экрана, все окна мода, щелчки и прокрутка,
 * подсказки, вкладка творческого режима, двойной прыжок, плюс полная серверная самопроверка
 * с настоящим игроком. Скриншоты — build/run/clientGameTest/screenshots.
 */
public class SuchkiUiTest implements FabricClientGameTest {
	/** Размеры: 1920×1080 с масштабом 3 и авто (=4), маленькое окно 854×480 с масштабом 2. */
	private static final int[][] SIZES = {{1920, 1080, 3}, {1920, 1080, 0}, {854, 480, 2}};

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!TestFilter.enabled("ui")) return;
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			context.runOnClient(mc -> {
				mc.options.languageCode = "ru_ru";
				mc.getLanguageManager().setSelected("ru_ru");
				mc.reloadResourcePacks();
			});
			context.waitTicks(60);

			sp.getServer().runOnServer(server -> player(server).setGameMode(GameType.SURVIVAL));
			List<String> failures = new ArrayList<>();

			// 1. Выбор роли (обязательный, без роли) — в трёх размерах, плюс прокрутка и стрелки.
			for (int[] size : SIZES) {
				resize(context, size);
				cursorGui(context, 1, 1);
				context.setScreen(() -> new RoleSelectScreen(true, null));
				context.waitTicks(12);
				context.takeScreenshot("role_select_" + tag(size));
			}
			resize(context, SIZES[0]);
			context.setScreen(() -> new RoleSelectScreen(true, null));
			context.waitTicks(8);
			cursorGui(context, 150, 200);
			context.getInput().scroll(-3);
			context.waitTicks(12);
			context.takeScreenshot("role_select_scrolled");
			for (int i = 0; i < 4; i++) context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN);
			context.waitTicks(12);
			context.takeScreenshot("role_select_arrows");

			// 2. Выбор роли кнопкой «Выбрать» и дерево навыков.
			context.setScreen(() -> new RoleSelectScreen(true, null));
			context.waitTicks(5);
			context.clickScreenButton("Выбрать: Шахтёр");
			context.waitTicks(10);
			sp.getServer().runOnServer(server -> {
				ServerPlayer p = player(server);
				if (!Skills.get(p).active().filter("miner"::equals).isPresent()) throw new AssertionError("кнопка «Выбрать» не выбрала роль");
				Skills.addXp(p, "miner", 400);
				Skills.unlock(p, "miner.0.1");
				Skills.unlock(p, "miner.0.2");
				Skills.unlock(p, "miner.1.1");
			});
			context.waitTicks(5);
			for (int[] size : SIZES) {
				resize(context, size);
				cursorGui(context, 1, 1);
				context.setScreen(SkillTreeScreen::new);
				context.waitTicks(12);
				context.takeScreenshot("skill_tree_" + tag(size));
			}

			// 3. Наведение и покупка навыка щелчком (в размере 1920×1080, масштаб 3).
			resize(context, SIZES[0]);
			context.setScreen(SkillTreeScreen::new);
			context.waitTicks(12);
			int[] node = context.computeOnClient(mc -> ((SkillTreeScreen) mc.screen).nodeCenter("miner.2.1"));
			if (node == null) throw new AssertionError("навык miner.2.1 не нарисован");
			cursorGui(context, node[0], node[1]);
			context.waitTicks(4);
			context.takeScreenshot("skill_tree_hover");
			context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTicks(3);
			context.takeScreenshot("skill_tree_unlock_burst");
			context.waitTicks(20);
			context.takeScreenshot("skill_tree_after_unlock");
			sp.getServer().runOnServer(server -> {
				if (!Skills.get(player(server)).progress("miner").has("miner.2.1")) throw new AssertionError("щелчок по навыку не купил его");
			});
			cursorGui(context, 4, 4);

			// 4. Точильный камень: цена разделения чар.
			sp.getServer().runOnServer(server -> {
				ServerPlayer p = player(server);
				ServerLevel level = p.level();
				BlockPos at = p.blockPosition().relative(p.getDirection());
				level.setBlock(at, Blocks.GRINDSTONE.defaultBlockState(), 3);
				p.setExperienceLevels(30);
				ItemStack sword = enchanted(level, Items.DIAMOND_SWORD, Enchantments.SHARPNESS, 10, Enchantments.UNBREAKING, 3);
				p.openMenu(new SimpleMenuProvider((id, inv, pl) -> {
					GrindstoneMenu m = new GrindstoneMenu(id, inv, ContainerLevelAccess.create(level, at));
					((GrindstoneMenuAccessor) m).suchki$repairSlots().setItem(0, sword);
					((GrindstoneMenuAccessor) m).suchki$repairSlots().setItem(1, new ItemStack(Items.BOOK));
					return m;
				}, Component.translatable("container.grindstone_title")));
			});
			context.waitTicks(10);
			context.takeScreenshot("grindstone_split");

			// 5. Наковальня: две книги Остроты IX.
			sp.getServer().runOnServer(server -> {
				ServerPlayer p = player(server);
				ServerLevel level = p.level();
				p.closeContainer();
				BlockPos at = p.blockPosition().relative(p.getDirection());
				level.setBlock(at, Blocks.ANVIL.defaultBlockState(), 3);
				p.openMenu(new SimpleMenuProvider((id, inv, pl) -> {
					AnvilMenu m = new AnvilMenu(id, inv, ContainerLevelAccess.create(level, at));
					m.getSlot(0).set(book(level, Enchantments.SHARPNESS, 9));
					m.getSlot(1).set(book(level, Enchantments.SHARPNESS, 9));
					return m;
				}, Component.translatable("container.repair")));
			});
			context.waitTicks(10);
			context.takeScreenshot("anvil_sharpness_x");
			sp.getServer().runOnServer(server -> player(server).closeContainer());
			context.waitTicks(5);

			// 6. Подсказки предметов: перки и бонусы чар (проверка текста + скриншот инвентаря).
			sp.getServer().runOnServer(server -> {
				ServerPlayer p = player(server);
				ServerLevel level = p.level();
				p.getInventory().clearContent();
				p.getInventory().setItem(9, enchanted(level, Items.NETHERITE_SWORD, Enchantments.SHARPNESS, 10, Enchantments.UNBREAKING, 10,
						Enchantments.BANE_OF_ARTHROPODS, 7));
				p.getInventory().setItem(10, enchanted(level, Items.DIAMOND_PICKAXE, SuchkiEnchantments.DRILL, 1, SuchkiEnchantments.MAGNET, 1));
				p.getInventory().setItem(11, book(level, Enchantments.PROTECTION, 9));
			});
			context.waitTicks(5);
			List<String> tooltip = context.computeOnClient(mc -> {
				List<String> lines = new ArrayList<>();
				ItemStack sword = mc.player.getInventory().getItem(9);
				for (Component c : sword.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL)) lines.add(c.getString());
				return lines;
			});
			String all = String.join("\n", tooltip);
			for (String want : new String[] {"+8 урона", "Удар игнорирует 25% брони", "Не ломается", "На X: Добавляет Слабость II"}) {
				if (!all.contains(want)) failures.add("в подсказке меча нет «" + want + "»:\n" + all);
			}
			context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
			context.waitTicks(6);
			int[] slot = context.computeOnClient(mc -> {
				var screen = (InventoryScreen) mc.screen;
				int left = (screen.width - 176) / 2, top = (screen.height - 166) / 2;
				var s = screen.getMenu().getSlot(9);
				return new int[] {left + s.x + 8, top + s.y + 8};
			});
			cursorGui(context, slot[0], slot[1]);
			context.waitTicks(4);
			context.takeScreenshot("tooltip_sword");
			cursorGui(context, slot[0] + 18 * 2, slot[1]);
			context.waitTicks(4);
			context.takeScreenshot("tooltip_book");
			context.setScreen(() -> null);

			// 7. Вкладка «Ингредиенты»: книги новых уровней.
			boolean books = context.computeOnClient(mc -> {
				CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), true, mc.level.registryAccess());
				Holder<Enchantment> sharp = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
				for (ItemStack s : BuiltInRegistries.CREATIVE_MODE_TAB.getValueOrThrow(CreativeModeTabs.INGREDIENTS).getDisplayItems()) {
					if (s.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(sharp) == 10) return true;
				}
				return false;
			});
			if (!books) failures.add("во вкладке «Ингредиенты» нет книги Остроты X");

			// 8. Пружина: двойной прыжок выше обычного.
			double single = jumpHeight(context, sp, 0);
			double triple = jumpHeight(context, sp, 3);
			if (!(triple > single + 0.8)) failures.add("Пружина III: прыжок " + triple + " не выше обычного " + single);
			springAndHunter(context, sp, failures);

			// 9. Прочность X: изношенный предмет — удар отменён и есть надпись над хотбаром.
			boolean[] cancelled = new boolean[1];
			sp.getServer().runOnServer(server -> {
				ServerPlayer p = player(server);
				ItemStack worn = enchanted(p.level(), Items.IRON_SWORD, Enchantments.UNBREAKING, 10);
				worn.setDamageValue(worn.getMaxDamage() - 1);
				p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, worn);
				var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(p.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
				cancelled[0] = net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.invoker()
						.interact(p, p.level(), net.minecraft.world.InteractionHand.MAIN_HAND, zombie, null)
						== net.minecraft.world.InteractionResult.FAIL;
			});
			context.waitTicks(5);
			String overlay = context.computeOnClient(mc -> {
				try {
					var f = net.minecraft.client.gui.Gui.class.getDeclaredField("overlayMessageString");
					f.setAccessible(true);
					Object c = f.get(mc.gui);
					return c == null ? "" : ((Component) c).getString();
				} catch (ReflectiveOperationException e) {
					return "?" + e;
				}
			});
			context.takeScreenshot("worn_out_message");
			if (!cancelled[0] || !overlay.contains("изношен")) {
				failures.add("Прочность X: удар не отменён или нет надписи (отменён " + cancelled[0] + ", надпись «" + overlay + "»)");
			}

			// 10. Магия: каст всех типов от первого и третьего лица, колесо заклинаний, HUD.
			MagicUiTest.run(context, sp, failures);

			// 11. Полная серверная самопроверка — теперь с настоящим игроком (маяк и др.).
			List<String> report = new ArrayList<>();
			sp.getServer().runOnServer(server -> {
				ServerPlayer p = player(server);
				report.addAll(DevTests.run(p.level(), p.blockPosition().offset(0, 40, 0)));
			});
			report.forEach(line -> System.out.println("[suchki-test] " + line));
			report.stream().filter(l -> l.startsWith("FAIL")).forEach(failures::add);
			System.out.println("[suchki-test] Пружина: обычный прыжок " + String.format("%.2f", single) + ", с Пружиной III "
					+ String.format("%.2f", triple));

			if (!failures.isEmpty()) throw new AssertionError("Ошибки клиентского теста:\n" + String.join("\n", failures));
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static String tag(int[] size) {
		return size[0] + "x" + size[1] + "_gui" + (size[2] == 0 ? "auto" : size[2]);
	}

	private static void resize(ClientGameTestContext context, int[] size) {
		context.getInput().resizeWindow(size[0], size[1]);
		context.runOnClient(mc -> {
			mc.options.guiScale().set(size[2]);
			mc.resizeDisplay();
		});
		context.waitTicks(3);
	}

	/** Курсор в координатах интерфейса (GUI) — переводим в пиксели окна. */
	private static void cursorGui(ClientGameTestContext context, double x, double y) {
		double scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
		context.getInput().setCursorPos(x * scale, y * scale);
	}

	private static ItemStack enchanted(ServerLevel level, Item item, Object... enchants) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.ENCHANTMENTS, ench(level, enchants));
		return stack;
	}

	private static ItemStack book(ServerLevel level, Object... enchants) {
		ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
		stack.set(DataComponents.STORED_ENCHANTMENTS, ench(level, enchants));
		return stack;
	}

	@SuppressWarnings("unchecked")
	private static ItemEnchantments ench(ServerLevel level, Object... enchants) {
		ItemEnchantments.Mutable m = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		var lookup = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		for (int i = 0; i < enchants.length; i += 2) m.set(lookup.getOrThrow((ResourceKey<Enchantment>) enchants[i]), (Integer) enchants[i + 1]);
		return m.toImmutable();
	}

	/**
	 * Пружина III: после тройного прыжка на ту же землю — без урона (по статистике «получено урона»: её не прячет
	 * регенерация). Охотник: моб рядом светится контуром у охотника, но эффекта свечения (видного всем) у моба нет.
	 */
	private static void springAndHunter(ClientGameTestContext context, TestSingleplayerContext sp, List<String> failures) {
		var damageTaken = net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DAMAGE_TAKEN);
		int before = sp.getServer().computeOnServer(server -> player(server).getStats().getValue(damageTaken));
		double h = jumpHeight(context, sp, 3);
		int after = sp.getServer().computeOnServer(server -> player(server).getStats().getValue(damageTaken));
		if (after > before) failures.add("Пружина III: урон от падения после прыжка на " + String.format("%.2f", h) + " блока");
		System.out.println("[suchki-test] Пружина III: прыжок " + String.format("%.2f", h) + ", урон " + (after - before) / 10.0);

		int[] id = new int[1];
		var difficulty = sp.getServer().computeOnServer(server -> server.getWorldData().getDifficulty());
		var saved = sp.getServer().computeOnServer(server -> {
			ServerPlayer p = player(server);
			var old = p.getAttached(Skills.DATA);
			p.setAttached(Skills.DATA, new com.suchki.skill.PlayerSkills(java.util.Optional.of("hunter"),
					java.util.Map.of("hunter", new com.suchki.skill.PlayerSkills.Progress(50, 0, List.of("hunter.0.1"))), 0L));
			server.setDifficulty(net.minecraft.world.Difficulty.EASY, true);
			var husk = net.minecraft.world.entity.EntityType.HUSK.create(p.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			var ahead = p.position().add(p.getLookAngle().multiply(5, 0, 5));
			husk.snapTo(ahead.x, p.getY(), ahead.z, p.getYRot() + 180, 0);
			husk.setNoAi(true);
			p.level().addFreshEntity(husk);
			id[0] = husk.getId();
			return java.util.Optional.ofNullable(old);
		});
		context.waitTicks(50);
		boolean glows = context.computeOnClient(mc -> {
			var e = mc.level.getEntity(id[0]);
			return e != null && mc.shouldEntityAppearGlowing(e);
		});
		context.takeScreenshot("hunter_glow");
		boolean effect = sp.getServer().computeOnServer(server -> {
			var e = player(server).level().getEntity(id[0]);
			boolean has = e instanceof net.minecraft.world.entity.LivingEntity le && le.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING);
			if (e != null) e.discard();
			ServerPlayer p = player(server);
			saved.ifPresentOrElse(s -> p.setAttached(Skills.DATA, s), () -> p.removeAttached(Skills.DATA));
			server.setDifficulty(difficulty, true);
			return has;
		});
		if (!glows) failures.add("Охотник: моб рядом не подсвечен у охотника");
		if (effect) failures.add("Охотник: у моба эффект свечения — подсветку видят все игроки");
	}

	/** Высота прыжка (блоков над землёй): прыжок, затем повторные нажатия в воздухе. */
	private static double jumpHeight(ClientGameTestContext context, TestSingleplayerContext sp, int springLevel) {
		sp.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			ServerLevel level = p.level();
			p.setItemSlot(EquipmentSlot.FEET, springLevel == 0 ? ItemStack.EMPTY
					: enchanted(level, Items.IRON_BOOTS, SuchkiEnchantments.SPRING, springLevel));
		});
		context.waitTicks(10);
		double ground = context.computeOnClient(mc -> mc.player.getY());
		double max = ground;
		context.getInput().holdKeyFor(opt -> opt.keyJump, 2);
		max = Math.max(max, context.computeOnClient(mc -> mc.player.getY()));
		for (int t = 0; t < 40; t++) {
			if (t == 5 || t == 11 || t == 17) {
				context.getInput().holdKeyFor(opt -> opt.keyJump, 2);
				t += 2;
			} else {
				context.waitTicks(1);
			}
			max = Math.max(max, context.computeOnClient(mc -> mc.player.getY()));
		}
		context.waitTicks(30);
		return max - ground;
	}
}
