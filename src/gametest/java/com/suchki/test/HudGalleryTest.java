package com.suchki.test;

import com.suchki.client.magic.CastAnim;
import com.suchki.client.magic.IncantationHud;
import com.suchki.client.magic.MagicClient;
import com.suchki.client.magic.MagicHud;
import com.suchki.client.magic.MagicKeys;
import com.suchki.client.magic.SpellWheelScreen;
import com.suchki.magic.Magic;
import com.suchki.magic.MagicItems;
import com.suchki.magic.School;
import com.suchki.magic.Spell;
import com.suchki.magic.Spells;
import com.suchki.skill.PlayerSkills;
import com.suchki.skill.Skills;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * Галерея интерфейса магии: заклинательные фразы (стихии, ранги, прогресс, накопление, предел, вспышка «готово»,
 * глитчи, распад и срыв), выкрик названия, всплывашка и значок мультикаста, колесо заклинаний (12 стихий),
 * разные масштабы интерфейса. Плюс проверки механик: печать фразы (последняя буква — ровно на замыкании круга),
 * переключение мультикаста клавишей по кругу ×1→…→×N→×1, наведение в колесе и выбор, формирование с копиями.
 * Если заклинания уже есть — ещё и живой каст с фразой и выкриком.
 * Скриншоты — build/run/clientGameTest/screenshots (или папка из переменной SUCHKI_GALLERY).
 */
public class HudGalleryTest implements FabricClientGameTest {
	/** Примеры фраз (свои тексты) — по стихиям и рангам. */
	private record Sample(String tag, School school, int tier, String chant, String name) {}

	private static final List<Sample> SAMPLES = List.of(
			new Sample("life_r2", School.LIFE, 2, "Ростки жизни, услышьте мой зов и затяните раны!", "Целебный Росток"),
			new Sample("fire_r3", School.FIRE, 3,
					"Пламя первородное, что спит в сердце земли,|пробудись и обрати врага моего в пепел!", "Огненный Шар"),
			new Sample("lightning_r4", School.LIGHTNING, 4,
					"Гром, что рвёт небеса на части,|копьё небесного владыки,|пронзи этот мир ослепительной вспышкой!", "Небесное Копьё"),
			new Sample("water_r5", School.WATER, 5,
					"Воды бездонные, слёзы забытых морей,|склонитесь пред волей моей,|скуйте небо льдом безмолвия,|и да застынет само время!",
					"Ледяная Темница"),
			new Sample("fusion_r6", School.FUSION, 6,
					"Пламя преисподней и мрак забвения,|сплетитесь в единый смертный узор,|огонь, не знающий пощады,|"
							+ "тень, не знающая рассвета,|выжгите этот мир до самого дна!", "Пламя Преисподней"),
			new Sample("dark_r7", School.DARK, 7,
					"Тьма, что чернее самой чёрной ночи,|бездна, где тонет даже свет,|я, принявший клятву пустоты,|"
							+ "взываю к имени, что нельзя произносить:|разверзнись, пасть небытия,|и поглоти всё сущее без остатка!", "Взрыв"));

	private Path dest;
	private final List<String> failures = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!TestFilter.enabled("hud")) return;
		String env = System.getenv("SUCHKI_GALLERY");
		dest = env != null && !env.isBlank() ? Path.of(env) : null;
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			// Роль мага — сразу, иначе через пару секунд всплывёт обязательный выбор роли.
			setup(sp);
			context.runOnClient(mc -> {
				mc.options.languageCode = "ru_ru";
				mc.getLanguageManager().setSelected("ru_ru");
				mc.reloadResourcePacks();
			});
			context.waitTicks(40);
			resize(context, 1920, 1080, 3);
			context.setScreen(() -> null);
			context.waitTicks(10);
			// Чат не нужен на снимках.
			context.runOnClient(mc -> mc.gui.getChat().clearMessages(false));
			boolean free = context.computeOnClient(mc -> mc.screen == null && Magic.isMage(mc.player));
			check(free, "игрок — маг, никаких окон поверх мира");
			look(context, 0, -8);

			logic(context);
			hudIdle(context);
			chants(context);
			shouts(context);
			scales(context);
			multicast(context, sp);
			wheel(context, sp);
			liveCast(context, sp);

			context.runOnClient(mc -> {
				IncantationHud.debugClear();
				MagicHud.debugToast(1, 1, 0, -1);
			});
		}
		if (!failures.isEmpty()) throw new AssertionError("Ошибки галереи HUD:\n" + String.join("\n", failures));
	}

	// --- Подготовка ---

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void setup(TestSingleplayerContext sp) {
		sp.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			ServerLevel level = p.level();
			BlockPos o = p.blockPosition();
			for (int dx = -12; dx <= 12; dx++) {
				for (int dz = -12; dz <= 12; dz++) {
					level.setBlock(o.offset(dx, -1, dz), (dx + dz & 3) == 0 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
							: Blocks.GRASS_BLOCK.defaultBlockState(), 2);
					for (int dy = 0; dy <= 8; dy++) level.setBlock(o.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
				}
			}
			level.setDayTime(6000);
			p.setGameMode(GameType.SURVIVAL);
			p.setAttached(Skills.DATA, new PlayerSkills(Optional.of(Magic.ROLE),
					Map.of(Magic.ROLE, new PlayerSkills.Progress(50, 0, List.of())), 0L));
			Skills.applyAttributes(p);
			Magic.onRoleChosen(p, Magic.ROLE);
			for (Spell s : Spells.all()) Magic.teach(p, s);
			p.getInventory().clearContent();
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MagicItems.ARCHMAGE_STAFF));
			p.setAttached(Magic.MANA, Magic.maxMana(p) * 0.8F);
			p.teleportTo(level, o.getX() + 0.5, o.getY(), o.getZ() + 0.5, java.util.Set.of(), 0, 0, true);
		});
	}

	private void shot(ClientGameTestContext context, String name) {
		TestScreenshotOptions o = TestScreenshotOptions.of("hud_" + name);
		if (dest != null) o = o.withDestinationDir(dest).disableCounterPrefix();
		context.takeScreenshot(o);
	}

	private static void resize(ClientGameTestContext context, int w, int h, int scale) {
		context.getInput().resizeWindow(w, h);
		context.runOnClient(mc -> {
			mc.options.guiScale().set(scale);
			mc.resizeDisplay();
		});
		context.waitTicks(3);
	}

	private static void look(ClientGameTestContext context, float yaw, float pitch) {
		context.runOnClient(mc -> {
			mc.player.setYRot(yaw);
			mc.player.setXRot(pitch);
			mc.player.yRotO = yaw;
			mc.player.xRotO = pitch;
		});
		context.waitTicks(2);
	}

	private static void cursorGui(ClientGameTestContext context, double x, double y) {
		double scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
		context.getInput().setCursorPos(x * scale, y * scale);
	}

	private void check(boolean ok, String what) {
		if (!ok) failures.add("FAIL " + what);
		System.out.println("[suchki-test] " + (ok ? "OK   " : "FAIL ") + what);
	}

	private static String[] lines(Sample s) {
		return s.chant.split("\\|");
	}

	// --- Проверки без отрисовки ---

	private void logic(ClientGameTestContext context) {
		// Печать фразы: ничего при 0, последняя строка допечатывается ровно при 1, а не раньше.
		for (Sample s : SAMPLES) {
			String[] l = lines(s);
			float[] at0 = IncantationHud.reveal(l, 0);
			float[] before = IncantationHud.reveal(l, 0.985F);
			float[] at1 = IncantationHud.reveal(l, 1);
			int lastLen = l[l.length - 1].codePointCount(0, l[l.length - 1].length());
			check(at0[0] == 0, "фраза " + s.tag + ": при 0 ничего не напечатано");
			check(before[l.length - 1] < lastLen, "фраза " + s.tag + ": последняя строка не дописана до замыкания круга");
			check(at1[l.length - 1] == lastLen, "фраза " + s.tag + ": последняя строка дописана ровно при замыкании");
			boolean mono = true;
			float prevSum = -1;
			for (int i = 0; i <= 50; i++) {
				float sum = 0;
				for (float v : IncantationHud.reveal(l, i / 50.0F)) sum += v;
				if (sum < prevSum) mono = false;
				prevSum = sum;
			}
			check(mono, "фраза " + s.tag + ": печать идёт только вперёд");
			if (l.length > 2) {
				float[] mid = IncantationHud.reveal(l, 0.5F);
				check(mid[0] == l[0].codePointCount(0, l[0].length()) && mid[l.length - 1] == 0,
						"фраза " + s.tag + ": к середине первая строка готова, последняя ещё не начата");
			} else if (l.length == 2) {
				float[] mid = IncantationHud.reveal(l, 0.6F);
				check(mid[0] > 0 && mid[1] < l[1].codePointCount(0, l[1].length()),
						"фраза " + s.tag + ": на 60% вторая строка ещё печатается");
			}
		}
		// Мультикаст по кругу.
		check(MagicKeys.next(1, 4) == 2 && MagicKeys.next(4, 4) == 1 && MagicKeys.next(2, 2) == 1 && MagicKeys.next(1, 1) == 1,
				"мультикаст: ×1→×2…→×N→×1");
		// Геометрия колеса: значки стихий и заклинаний не налезают друг на друга ни при каком числе заклинаний.
		boolean apart = true;
		for (int[] size : new int[][] {{640, 360}, {480, 270}, {427, 240}, {960, 540}}) {
			float k = SpellWheelScreen.scale(size[0], size[1]);
			for (int i = 0; i < 12; i++) {
				float[] a = SpellWheelScreen.schoolCenter(i, size[0], size[1]);
				float[] b = SpellWheelScreen.schoolCenter((i + 1) % 12, size[0], size[1]);
				if (Math.hypot(a[0] - b[0], a[1] - b[1]) < 22 * k) apart = false;
			}
			for (int n = 1; n <= 18; n++) {
				for (School s : School.values()) {
					for (int i = 0; i < n; i++) {
						float[] a = SpellWheelScreen.spellCenter(s, i, n, size[0], size[1]);
						if (a[0] < 0 || a[1] < 0 || a[0] > size[0] || a[1] > size[1]) apart = false;
						for (int j = i + 1; j < n; j++) {
							float[] b = SpellWheelScreen.spellCenter(s, j, n, size[0], size[1]);
							if (Math.hypot(a[0] - b[0], a[1] - b[1]) < (SpellWheelScreen.ICON + 2) * k) apart = false;
						}
					}
				}
			}
		}
		check(apart, "колесо: значки не перекрываются (12 стихий, до 18 заклинаний, 4 размера экрана)");
		// Звуки, которые играет клиент, зарегистрированы.
		boolean sounds = context.computeOnClient(mc -> com.suchki.magic.MagicSounds.SELECT != null
				&& com.suchki.magic.MagicSounds.MULTICAST != null && com.suchki.magic.MagicSounds.CIRCLE_DRAW != null
				&& com.suchki.magic.MagicSounds.CIRCLE_COMPLETE != null && com.suchki.magic.MagicSounds.RELEASE_HEAVY != null
				&& com.suchki.magic.MagicSounds.RELEASE_ULTIMATE != null && com.suchki.magic.MagicSounds.GLITCH != null
				&& com.suchki.magic.MagicSounds.LOOP_CHANT != null);
		check(sounds, "звуки интерфейса и каста зарегистрированы");
	}

	// --- Скриншоты ---

	private void hudIdle(ClientGameTestContext context) {
		shot(context, "idle_mana_bar");
	}

	private void chant(ClientGameTestContext context, Sample s, float progress, float over, float hold, float flash, float burst, float time,
			String name) {
		context.runOnClient(mc -> {
			IncantationHud.debugClear();
			IncantationHud.debugTime(time);
			IncantationHud.debugShow(lines(s), s.school.color, s.school.light, s.tier, progress, over, hold, flash, burst);
		});
		context.waitTick();
		shot(context, name);
	}

	private void chants(ClientGameTestContext context) {
		float t = 100;
		for (Sample s : SAMPLES) {
			chant(context, s, 0.3F, 0, 0, -1, 0, t, "chant_" + s.tag + "_p30");
			chant(context, s, 0.72F, 0, 0, -1, 0, t + 7, "chant_" + s.tag + "_p72");
			chant(context, s, 1.0F, 0.4F, 0, 2, 0, t + 13, "chant_" + s.tag + "_ready_flash");
		}
		Sample dark = SAMPLES.get(5);
		Sample water = SAMPLES.get(3);
		Sample bolt = SAMPLES.get(2);
		// Предел без всплеска (дрожь, редкие руны) и всплеск глитча.
		float calm = 333;
		while (IncantationHud.debugBurstAt(calm, 7, 1.0F, 0.9F) > 0 || IncantationHud.debugBurstAt(calm - 1, 7, 1.0F, 0.9F) > 0) calm += 1;
		chant(context, dark, 1.0F, 1.0F, 0.9F, -1, 0, calm, "chant_dark_r7_hold");
		chant(context, dark, 1.0F, 1.0F, 0.5F, -1, 1.0F, 417, "chant_dark_r7_glitch_burst");
		chant(context, bolt, 1.0F, 0.6F, 0, -1, 0.8F, 211, "chant_lightning_r4_glitch");
		chant(context, water, 0.55F, 0, 0, -1, 0, 50, "chant_water_r5_typing");
		// Распад: выпуск (разлетается) и срыв (осыпается, краснеет).
		context.runOnClient(mc -> {
			IncantationHud.debugTime(120);
			IncantationHud.debugEnd(lines(water), water.school.color, water.school.light, water.tier, 3, false);
		});
		context.waitTick();
		shot(context, "chant_water_r5_release_dissolve");
		context.runOnClient(mc -> IncantationHud.debugEnd(lines(bolt), bolt.school.color, bolt.school.light, bolt.tier, 5, true));
		context.waitTick();
		shot(context, "chant_lightning_r4_fizzle");
		// Тот же текст на земле (светлый фон) — читаемость.
		look(context, 0, 40);
		chant(context, SAMPLES.get(1), 0.8F, 0, 0, -1, 0, 140, "chant_fire_r3_ground");
		look(context, 0, -35);
		chant(context, SAMPLES.get(4), 1.0F, 0.7F, 0, -1, 0, 160, "chant_fusion_r6_sky");
		look(context, 0, -8);
		context.runOnClient(mc -> IncantationHud.debugClear());
	}

	private void shout(ClientGameTestContext context, Sample s, float age, String name) {
		context.runOnClient(mc -> {
			IncantationHud.debugClear();
			IncantationHud.debugTime(200 + age);
			IncantationHud.debugShout(s.name, s.school.color, s.school.light, s.tier, age);
		});
		context.waitTick();
		shot(context, name);
	}

	private void shouts(ClientGameTestContext context) {
		Sample fire = SAMPLES.get(1);
		Sample water = SAMPLES.get(3);
		Sample fusion = SAMPLES.get(4);
		Sample dark = SAMPLES.get(5);
		shout(context, fire, 1, "shout_fire_r3_t01_slam");
		shout(context, fire, 9, "shout_fire_r3_t09");
		shout(context, water, 4, "shout_water_r5_t04_glitch");
		shout(context, water, 15, "shout_water_r5_t15");
		shout(context, fusion, 12, "shout_fusion_r6_t12");
		shout(context, dark, 2, "shout_dark_r7_t02");
		shout(context, dark, 18, "shout_dark_r7_t18");
		shout(context, dark, 31, "shout_dark_r7_t31_fade");
		// Выкрик поверх распадающейся фразы — как в игре на выпуске.
		context.runOnClient(mc -> {
			IncantationHud.debugTime(260);
			IncantationHud.debugEnd(lines(dark), dark.school.color, dark.school.light, dark.tier, 2, false);
			IncantationHud.debugShout(dark.name, dark.school.color, dark.school.light, dark.tier, 2);
		});
		context.waitTick();
		shot(context, "release_combo_dark_r7");
		context.runOnClient(mc -> IncantationHud.debugClear());
	}

	private void scales(ClientGameTestContext context) {
		Sample dark = SAMPLES.get(5);
		Sample water = SAMPLES.get(3);
		int[][] sizes = {{1920, 1080, 2}, {1920, 1080, 4}, {854, 480, 2}};
		for (int[] size : sizes) {
			resize(context, size[0], size[1], size[2]);
			String tag = size[0] + "x" + size[1] + "_gui" + size[2];
			chant(context, dark, 0.92F, 0, 0, -1, 0, 90, "scale_" + tag + "_chant_dark_r7");
			chant(context, water, 1.0F, 0.5F, 0, -1, 0, 95, "scale_" + tag + "_chant_water_r5");
			context.runOnClient(mc -> IncantationHud.debugClear());
			shout(context, dark, 12, "scale_" + tag + "_shout_dark_r7");
		}
		resize(context, 1920, 1080, 3);
		context.runOnClient(mc -> IncantationHud.debugClear());
	}

	private void multicast(ClientGameTestContext context, TestSingleplayerContext sp) {
		// Настоящая клавиша G: ×1 → ×2 → ×3 → ×4 → ×1 (маг 50 уровня), сервер получает каждое значение.
		int max = sp.getServer().computeOnServer(server -> Magic.maxMulticast(player(server)));
		check(max == 4, "мультикаст: у мага 50 уровня ×4 (есть " + max + ")");
		int expect = sp.getServer().computeOnServer(server -> Magic.data(player(server)).multicast());
		for (int i = 0; i < max; i++) {
			expect = MagicKeys.next(expect, max);
			context.getInput().pressKey(MagicKeys.multicastKey);
			context.waitTicks(4);
			if (i == 0 || i == max - 2) shot(context, "multicast_toast_x" + expect);
			int now = sp.getServer().computeOnServer(server -> Magic.data(player(server)).multicast());
			check(now == expect, "мультикаст: клавиша G переключила на ×" + expect + " (на сервере ×" + now + ")");
		}
		// Зафиксированная всплывашка для галереи и значок ×N у выбранного заклинания.
		context.runOnClient(mc -> MagicHud.debugToast(3, 4, School.MIND.light, 6));
		context.waitTick();
		shot(context, "multicast_toast_x3_fixed");
		context.runOnClient(mc -> MagicHud.debugToast(1, 1, 0, -1));
		Spell multi = Spells.all().stream().filter(s -> s.maxCopies >= 3 && s.tier <= 6).findFirst().orElse(null);
		if (multi != null) {
			sp.getServer().runOnServer(server -> {
				ServerPlayer p = player(server);
				Magic.select(p, multi.id);
				Magic.setMulticast(p, 3);
			});
			context.waitTicks(4);
			shot(context, "multicast_badge_x3_" + multi.id);
			int copies = context.computeOnClient(mc -> Magic.copiesFor(mc.player, multi));
			check(copies == 3, "мультикаст: клиент видит ×3 у " + multi.id + " (есть ×" + copies + ")");
		} else {
			System.out.println("[suchki-test] SKIP значок мультикаста: заклинаний ещё нет");
		}
		sp.getServer().runOnServer(server -> Magic.setMulticast(player(server), 1));
		context.waitTicks(2);
	}

	private void wheel(ClientGameTestContext context, TestSingleplayerContext sp) {
		context.getInput().holdKey(MagicClient.wheelKey);
		context.waitTicks(8);
		boolean open = context.computeOnClient(mc -> mc.screen instanceof SpellWheelScreen);
		check(open, "колесо: открывается по R");
		if (!open) {
			context.getInput().releaseKey(MagicClient.wheelKey);
			return;
		}
		int[] gui = context.computeOnClient(mc -> new int[] {mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight()});
		School[] schools = School.values();
		for (int i : new int[] {0, 3, 6, 11}) {
			float[] c = SpellWheelScreen.schoolCenter(i, gui[0], gui[1]);
			cursorGui(context, c[0], c[1]);
			context.waitTicks(3);
			School hov = context.computeOnClient(mc -> ((SpellWheelScreen) mc.screen).school());
			check(hov == schools[i], "колесо: наведение на стихию " + schools[i].id + " (наведена " + hov.id + ")");
			shot(context, "wheel_school_" + schools[i].id);
		}
		// Заклинания: наводим на каждое у стихии, где их больше всего; последнее выбираем, отпустив R.
		School best = null;
		int bestN = 0;
		for (School s : schools) {
			int n = Spells.of(s).size();
			if (n > bestN) {
				bestN = n;
				best = s;
			}
		}
		if (best != null && bestN > 0) {
			School s = best;
			List<Spell> list = Spells.of(s);
			float[] sc = SpellWheelScreen.schoolCenter(s.ordinal(), gui[0], gui[1]);
			cursorGui(context, sc[0], sc[1]);
			context.waitTicks(2);
			int ok = 0;
			for (int i = 0; i < list.size(); i++) {
				float[] c = SpellWheelScreen.spellCenter(s, i, list.size(), gui[0], gui[1]);
				cursorGui(context, c[0], c[1]);
				context.waitTicks(1);
				Spell hov = context.computeOnClient(mc -> mc.screen instanceof SpellWheelScreen w ? w.hovered() : null);
				if (hov == list.get(i)) ok++;
				if (i == 0 || i == list.size() - 1) shot(context, "wheel_spell_" + s.id + "_" + i);
			}
			check(ok == list.size(), "колесо: наведение на каждое заклинание стихии " + s.id + " (" + ok + " из " + list.size() + ")");
			Spell target = list.get(list.size() - 1);
			context.getInput().releaseKey(MagicClient.wheelKey);
			context.waitTicks(4);
			String sel = sp.getServer().computeOnServer(server -> Magic.data(player(server)).selected());
			check(target.id.equals(sel), "колесо: отпустил R — выбрано " + target.id + " (выбрано " + sel + ")");
		} else {
			System.out.println("[suchki-test] SKIP наведение на заклинания: заклинаний ещё нет");
			context.getInput().releaseKey(MagicClient.wheelKey);
			context.waitTicks(4);
		}
		boolean closed = context.computeOnClient(mc -> mc.screen == null);
		check(closed, "колесо: закрывается, когда отпустили R");
		// Маленький экран: колесо сжимается и не вылезает за край.
		resize(context, 854, 480, 2);
		context.getInput().holdKey(MagicClient.wheelKey);
		context.waitTicks(8);
		int[] small = context.computeOnClient(mc -> new int[] {mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight()});
		float[] c = SpellWheelScreen.schoolCenter(School.FUSION.ordinal(), small[0], small[1]);
		cursorGui(context, c[0], c[1]);
		context.waitTicks(3);
		shot(context, "wheel_small_854x480");
		context.getInput().releaseKey(MagicClient.wheelKey);
		context.waitTicks(4);
		resize(context, 1920, 1080, 3);
	}

	private void liveCast(ClientGameTestContext context, TestSingleplayerContext sp) {
		Spell spell = context.computeOnClient(mc -> Spells.all().stream()
				.filter(s -> s.charged() && s.tier <= 6 && IncantationHud.chantLines(s).length > 0)
				.max(Comparator.comparingInt(s -> s.tier)).orElse(null));
		if (spell == null) {
			System.out.println("[suchki-test] SKIP живой каст с фразой: заклинаний с фразами ещё нет");
			return;
		}
		sp.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			Magic.resetCooldowns(p);
			Magic.select(p, spell.id);
			Magic.setMulticast(p, spell.maxCopies >= 2 ? 2 : 1);
			p.setAttached(Magic.MANA, Magic.maxMana(p));
		});
		context.waitTicks(3);
		int copies = context.computeOnClient(mc -> Magic.copiesFor(mc.player, spell));
		int form = sp.getServer().computeOnServer(server -> {
			ServerPlayer p = player(server);
			return Magic.formTicks(spell, Magic.castSpeed(p, MagicItems.ARCHMAGE_STAFF), copies);
		});
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		look(context, 0, spell.anchor.targeted() ? 20 : -5);
		context.getInput().holdKey(o -> o.keyUse);
		int third = Math.max(1, form / 3);
		context.waitTicks(third);
		boolean shown = context.computeOnClient(mc -> IncantationHud.liveChantShown());
		check(shown, "живой каст " + spell.id + ": фраза на экране во время зарядки");
		float f = context.computeOnClient(mc -> {
			CastAnim.Pose pose = CastAnim.compute(mc.player, 0);
			return pose == null ? -1F : pose.form;
		});
		float expect = Math.min(1, (third) / (float) form);
		check(Math.abs(f - expect) < 1.5F / form + 0.05F, "живой каст " + spell.id + ": формирование с ×" + copies + " копиями " + f + " ≈ " + expect);
		shot(context, "live_" + spell.id + "_third");
		context.waitTicks(Math.max(1, form - 2 * third));
		shot(context, "live_" + spell.id + "_two_thirds");
		context.waitTicks(third + 3);
		shot(context, "live_" + spell.id + "_ready");
		context.getInput().releaseKey(o -> o.keyUse);
		context.waitTicks(3);
		String shout = context.computeOnClient(mc -> IncantationHud.liveShoutText());
		check(shout != null && !shout.isEmpty(), "живой каст " + spell.id + ": выкрик названия на выпуске (" + shout + ")");
		shot(context, "live_" + spell.id + "_shout");
		context.waitTicks(12);
		shot(context, "live_" + spell.id + "_shout_late");
		boolean cleared = context.computeOnClient(mc -> !IncantationHud.liveChantShown());
		check(cleared, "живой каст " + spell.id + ": фраза убрана после выпуска");
		sp.getServer().runOnServer(server -> Magic.setMulticast(player(server), 1));
	}
}
