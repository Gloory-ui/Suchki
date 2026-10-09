package com.suchki.test;

import com.suchki.client.magic.CastVisuals;
import com.suchki.client.magic.CircleRenderer;
import com.suchki.client.magic.WorldFxRenderer;
import com.suchki.magic.Anchor;
import com.suchki.magic.CastStyle;
import com.suchki.magic.FxNet;
import com.suchki.magic.Magic;
import com.suchki.magic.MagicFx;
import com.suchki.magic.MagicItems;
import com.suchki.magic.School;
import com.suchki.magic.Spell;
import com.suchki.magic.Spells;
import com.suchki.skill.PlayerSkills;
import com.suchki.skill.Skills;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Галерея магических кругов: каждая стихия на рангах 1, 4, 7 на стадиях прорисовки 25%, 60% и 100%; кольца
 * силы, дрожь на пределе и вспышка «замка»; круг у посоха, «ствол» из кругов, небесный круг снизу, слияние,
 * круги вдали и днём; все эффекты мира; круги и эффекты, пришедшие с сервера; настоящий каст с каждым якорем
 * (если такие заклинания есть). Ночь и чёрный пол — чтобы свечение было видно. Скриншоты — circles_*.
 */
public class CircleGalleryTest implements FabricClientGameTest {
	private static final float[] PROGRESS = {0.25F, 0.6F, 1.0F};
	private static final int[] RANKS = {1, 4, 7};

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!TestFilter.enabled("circles")) return;
		List<String> failures = new ArrayList<>();
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			context.getInput().resizeWindow(1920, 1080);
			context.runOnClient(mc -> {
				mc.options.hideGui = true;
				// Дальность прорисовки 8 чанков — как у обычного игрока (круги до 128 блоков не тонут в тумане).
				mc.options.renderDistance().set(8);
				mc.resizeDisplay();
			});
			BlockPos o = sp.getServer().computeOnServer(server -> {
				ServerPlayer p = player(server);
				ServerLevel level = p.level();
				BlockPos at = p.blockPosition();
				// Чёрный пол 101×101 (за ним — трава для дневного снимка), ночь.
				for (int dx = -50; dx <= 50; dx++) {
					for (int dz = -50; dz <= 50; dz++) {
						level.setBlock(at.offset(dx, -1, dz), Blocks.BLACK_CONCRETE.defaultBlockState(), 2);
					}
				}
				level.setDayTime(18000);
				// Роль мага сразу — иначе всплывёт обязательный выбор роли.
				p.setAttached(Skills.DATA, new PlayerSkills(Optional.of(Magic.ROLE), Map.of(Magic.ROLE, new PlayerSkills.Progress(50, 0, List.of())),
						0L));
				Skills.applyAttributes(p);
				Magic.onRoleChosen(p, Magic.ROLE);
				p.setGameMode(GameType.SPECTATOR);
				return at;
			});
			Vec3 g = new Vec3(o.getX() + 0.5, o.getY() + 0.02, o.getZ() + 0.5);
			context.waitTicks(50);
			context.setScreen(() -> null);
			context.runOnClient(mc -> mc.gui.getChat().clearMessages(false));
			context.waitTicks(5);

			schoolGrids(context, sp, g, failures);
			powerAndLock(context, sp, g, failures);
			perspective(context, sp, g);
			staffAndLine(context, sp, g, failures);
			sky(context, sp, g);
			fusion(context, sp, g, failures);
			worldFx(context, sp, g, failures);
			network(context, sp, g, failures);
			distanceAndDay(context, sp, g, failures);
			realCasts(context, sp, g, failures);
		}
		if (!failures.isEmpty()) throw new AssertionError("Галерея кругов:\n" + String.join("\n", failures));
	}

	// ---------------------------------------------------------------- разделы

	/** Сетка 3×3 на каждую стихию: строки — ранги 1/4/7, столбцы — прорисовка 25/60/100%. Вид сверху. */
	private static void schoolGrids(ClientGameTestContext context, TestSingleplayerContext sp, Vec3 g, List<String> failures) {
		camera(context, sp, g.add(0, 23, 0), 0, 90);
		for (School s : School.values()) {
			context.runOnClient(mc -> {
				CircleRenderer.clear();
				for (int row = 0; row < 3; row++) {
					for (int col = 0; col < 3; col++) {
						Vec3 at = g.add(-(col - 1) * 10.5, 0, (1 - row) * 10.5);
						float since = PROGRESS[col] >= 1 ? 40 : -1;
						fixed(at, new Vec3(0, 1, 0), 4.6F, s, RANKS[row], PROGRESS[col], since, 0.3F * col).underlay = 1;
					}
				}
			});
			context.waitTicks(2);
			int active = context.computeOnClient(mc -> CircleRenderer.activeCount());
			int drawn = context.computeOnClient(mc -> CircleRenderer.drawnLastFrame());
			int strokes = context.computeOnClient(mc -> CircleRenderer.strokesLastFrame());
			if (active != 9) failures.add(s.id + ": кругов в мире " + active + " вместо 9");
			if (drawn < 9) failures.add(s.id + ": нарисовано кругов " + drawn + " (ждали 9)");
			if (strokes < 200) failures.add(s.id + ": слишком мало штрихов в кадре: " + strokes);
			shot(context, "circles_school_" + s.id);
			int verts = context.computeOnClient(mc -> CastVisuals.lastFrameVertices());
			if (verts > 400_000) failures.add(s.id + ": слишком много вершин за кадр (" + verts + ")");
			System.out.println("[circles] " + s.id + ": кругов " + drawn + ", штрихов " + strokes + ", вершин " + verts);
		}
		// Подробность кругов из настроек: 0 (простые) и 2 (подробные) — седьмой ранг.
		for (int detail : new int[] {0, 2}) {
			context.runOnClient(mc -> {
				com.suchki.SuchkiConfig.INSTANCE.circleDetail = detail;
				CircleRenderer.clear();
				fixed(g, new Vec3(0, 1, 0), 9, School.SUMMON, 7, 1, 40, 0.2F).underlay = 1;
			});
			shot(context, "circles_detail_" + detail);
			int strokes = context.computeOnClient(mc -> CircleRenderer.strokesLastFrame());
			System.out.println("[circles] подробность " + detail + ": штрихов " + strokes);
		}
		context.runOnClient(mc -> com.suchki.SuchkiConfig.INSTANCE.circleDetail = 1);
		context.runOnClient(mc -> CircleRenderer.clear());
	}

	/** Кольца силы (накопление), дрожь на пределе (двоение), вспышка «замка» и выпуск с призраком. */
	private static void powerAndLock(ClientGameTestContext context, TestSingleplayerContext sp, Vec3 g, List<String> failures) {
		camera(context, sp, g.add(0, 13, 0), 0, 90);
		CircleRenderer.Inst[] in = new CircleRenderer.Inst[1];
		context.runOnClient(mc -> {
			CircleRenderer.clear();
			in[0] = fixed(g, new Vec3(0, 1, 0), 3.6F, School.FIRE, 6, 1, 40, 0.4F);
			in[0].underlay = 1;
			in[0].power = 0.5F;
		});
		shot(context, "circles_power_half");
		context.runOnClient(mc -> in[0].power = 1);
		shot(context, "circles_power_full");
		context.runOnClient(mc -> in[0].tremble = 0.9F);
		shot(context, "circles_hold_tremble");
		context.runOnClient(mc -> {
			in[0].power = 0;
			in[0].tremble = 0;
			in[0].fixedSince = 1;
		});
		shot(context, "circles_lock_flash");
		// Живой круг: рисуется 30 тиков сам (не заморожен), потом вспышка и вращение.
		context.runOnClient(mc -> {
			CircleRenderer.clear();
			CircleRenderer.Inst live = new CircleRenderer.Inst();
			live.pos = g;
			live.radius = 3.6F;
			live.school = School.LIGHTNING;
			live.tier = 5;
			live.color = School.LIGHTNING.color;
			live.light = School.LIGHTNING.light;
			live.draw = 30;
			live.hold = 40;
			live.fade = 10;
			live.underlay = 1;
			live.start = CircleRenderer.now(0);
			CircleRenderer.add(live);
		});
		context.waitTicks(8);
		shot(context, "circles_live_draw_a");
		context.waitTicks(10);
		shot(context, "circles_live_draw_b");
		context.waitTicks(13);
		shot(context, "circles_live_locked");
		context.waitTicks(60);
		int left = context.computeOnClient(mc -> CircleRenderer.activeCount());
		if (left != 0) failures.add("живой круг не исчез после draw+hold+fade (осталось " + left + ")");
		// Призрак выпуска: вспыхивает и гаснет.
		context.runOnClient(mc -> {
			CircleRenderer.Inst gh = new CircleRenderer.Inst();
			gh.pos = g;
			gh.radius = 3.6F;
			gh.school = School.WATER;
			gh.tier = 5;
			gh.color = School.WATER.color;
			gh.light = School.WATER.light;
			gh.ghost = true;
			gh.draw = 0;
			gh.hold = 3;
			gh.fade = 8;
			gh.underlay = 1;
			gh.start = CircleRenderer.now(0);
			CircleRenderer.add(gh);
		});
		context.waitTicks(1);
		shot(context, "circles_release_ghost");
		context.waitTicks(15);
		left = context.computeOnClient(mc -> CircleRenderer.activeCount());
		if (left != 0) failures.add("призрак выпуска не исчез (осталось " + left + ")");
	}

	/** Круги седьмого ранга на земле в перспективе (как их видит игрок). */
	private static void perspective(ClientGameTestContext context, TestSingleplayerContext sp, Vec3 g) {
		camera(context, sp, g.add(0, 7, -8), 0, 32);
		School[] show = {School.LIGHT, School.DARK, School.SPACE};
		context.runOnClient(mc -> {
			CircleRenderer.clear();
			for (int i = 0; i < 3; i++) fixed(g.add((1 - i) * 11, 0, 6), new Vec3(0, 1, 0), 5, show[i], 7, 1, 40, 0.5F * i).underlay = 1;
		});
		shot(context, "circles_perspective_rank7");
		context.runOnClient(mc -> {
			CircleRenderer.clear();
			School[] more = {School.WATER, School.LIFE, School.MIND};
			for (int i = 0; i < 3; i++) {
				CircleRenderer.Inst c = fixed(g.add((1 - i) * 11, 0, 6), new Vec3(0, 1, 0), 5, more[i], 6, 1, 40, 0.5F * i);
				c.underlay = 1;
				c.layers = 3;
				c.gap = 1.1F;
			}
		});
		shot(context, "circles_perspective_layers");
	}

	/** Круг у посоха (анфас и сбоку) с копиями мультикаста и «ствол» из кругов от посоха к цели. */
	private static void staffAndLine(ClientGameTestContext context, TestSingleplayerContext sp, Vec3 g, List<String> failures) {
		Vec3 eye = g.add(0, 1.8, -4.5);
		camera(context, sp, eye, 0, 0);
		context.runOnClient(mc -> {
			CircleRenderer.clear();
			Vec3 c = g.add(0, 1.8, 0);
			fixed(c, new Vec3(0, 0, -1), 1.3F, School.FIRE, 5, 1, 40, 0.2F);
			for (int i = 0; i < 3; i++) {
				double a = Math.PI * 2 * i / 3 + Math.PI / 2;
				fixed(c.add(Math.cos(a) * 2.4, Math.sin(a) * 2.4, 0.3), new Vec3(0, 0, -1), 0.9F, School.FIRE, 5, 0.7F, -1, 0.5F * i);
			}
		});
		shot(context, "circles_staff_multicast_front");
		camera(context, sp, g.add(3.6, 2.2, -2.6), 52, 6);
		shot(context, "circles_staff_multicast_side");
		// «Ствол»: пять кругов от посоха к цели, растут к цели.
		context.runOnClient(mc -> {
			CircleRenderer.clear();
			Vec3 from = g.add(0, 1.6, -2);
			for (int k = 0; k < 5; k++) {
				Vec3 c = from.add(0, 0.15 * k, 2.2 + 3.2 * k);
				fixed(c, new Vec3(0, 0.05, 1), 0.9F + 0.45F * k, School.LIGHTNING, 6, Math.min(1, 1.2F - 0.15F * k), 40, 0.7F * k);
			}
		});
		camera(context, sp, g.add(-6, 2.8, 1), -42, 8);
		shot(context, "circles_line_stack_side");
		camera(context, sp, g.add(0.6, 2.2, -5), 0, 3);
		shot(context, "circles_line_stack_tunnel");
		int n = context.computeOnClient(mc -> CircleRenderer.drawnLastFrame());
		if (n < 5) failures.add("ствол из кругов: нарисовано " + n + " из 5");
	}

	/** Огромный круг в небе над целью — снизу и издалека. */
	private static void sky(ClientGameTestContext context, TestSingleplayerContext sp, Vec3 g) {
		context.runOnClient(mc -> {
			CircleRenderer.clear();
			CircleRenderer.Inst c = fixed(g.add(0, 16, 6), new Vec3(0, -1, 0), 13, School.LIGHTNING, 6, 1, 40, 0.3F);
			c.layers = 2;
			c.gap = 2.5F;
			c.power = 0.7F;
			fixed(g.add(0, 0, 6), new Vec3(0, 1, 0), 4, School.LIGHTNING, 4, 1, 40, 0).underlay = 1;
		});
		camera(context, sp, g.add(0, 1.6, -6), 0, -48);
		shot(context, "circles_sky_from_below");
		camera(context, sp, g.add(0, 9, -40), 0, 8);
		shot(context, "circles_sky_far");
		context.runOnClient(mc -> {
			CircleRenderer.clear();
			fixed(g.add(0, 16, 6), new Vec3(0, -1, 0), 13, School.FIRE, 7, 0.55F, -1, 0.3F);
		});
		camera(context, sp, g.add(0, 1.6, -6), 0, -48);
		shot(context, "circles_sky_drawing");
	}

	/** Слияние: круги стихий кружат, сходятся и сливаются (25/55/80/100%). */
	private static void fusion(ClientGameTestContext context, TestSingleplayerContext sp, Vec3 g, List<String> failures) {
		camera(context, sp, g.add(0, 24, 0), 0, 90);
		float[] ps = {0.3F, 0.6F, 0.85F, 1.0F};
		context.runOnClient(mc -> {
			CircleRenderer.clear();
			for (int i = 0; i < 4; i++) {
				CircleRenderer.Inst a = fixed(g.add(15 - 10 * i, 0, 6), new Vec3(0, 1, 0), 4.2F, School.FUSION, 5, ps[i], ps[i] >= 1 ? 40 : -1, 0.4F);
				a.combo = List.of(School.FIRE, School.WATER);
				a.color = mixColor(School.FIRE.color, School.WATER.color);
				a.light = mixColor(School.FIRE.light, School.WATER.light);
				a.underlay = 1;
				CircleRenderer.Inst b = fixed(g.add(15 - 10 * i, 0, -6), new Vec3(0, 1, 0), 4.2F, School.FUSION, 7, ps[i], ps[i] >= 1 ? 40 : -1, 0.4F);
				b.combo = List.of(School.SPACE, School.LIGHT, School.DARK);
				b.color = mixColor(School.SPACE.color, mixColor(School.LIGHT.color, School.DARK.color));
				b.light = 0xF0E8FF;
				b.underlay = 1;
			}
		});
		shot(context, "circles_fusion");
		camera(context, sp, g.add(0, 9, -12), 0, 30);
		shot(context, "circles_fusion_perspective");
		int n = context.computeOnClient(mc -> CircleRenderer.drawnLastFrame());
		if (n < 8) failures.add("слияние: нарисовано " + n + " кругов из 8");
	}

	/** Все эффекты мира (заморожены на середине жизни) и проверка, что эффекты умирают сами. */
	private static void worldFx(ClientGameTestContext context, TestSingleplayerContext sp, Vec3 g, List<String> failures) {
		context.runOnClient(mc -> CircleRenderer.clear());
		Vec3 eye = g.add(0, 3.5, -7);
		camera(context, sp, eye, 0, 6);
		Vec3 c = g.add(0, 0, 6);
		int fire = School.FIRE.color, fireL = School.FIRE.light;
		Object[][] kinds = {
				{"beam", FxNet.WorldFx.BEAM, c.add(-8, 2, -6), c.add(6, 1, 6), 0.45F, 0F, School.LIGHT.color, School.LIGHT.light, 40, 10},
				{"beam_taper", FxNet.WorldFx.BEAM, c.add(-6, 6, -4), c.add(4, 0.5, 4), 0.7F, 1F, School.SPACE.color, School.SPACE.light, 40, 12},
				{"lightning", FxNet.WorldFx.LIGHTNING, c.add(2, 22, 4), c, 0.22F, 0F, School.LIGHTNING.color, 0xFFFFFF, 12, 2},
				{"sphere", FxNet.WorldFx.SPHERE, c.add(0, 2, 0), c.add(0, 2, 0), 0.5F, 7F, fire, fireL, 24, 7},
				{"dome", FxNet.WorldFx.DOME, c, c, 6F, 6F, School.DARK.color, School.DARK.light, 80, 25},
				{"core", FxNet.WorldFx.CORE, c.add(0, 4, 0), c.add(0, 4, 0), 1.8F, 5.5F, School.SPACE.color, School.SPACE.light, 100, 40},
				{"ring", FxNet.WorldFx.RING, c.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.5F, 9F, School.WIND.color, School.WIND.light, 20, 7},
				{"pillar", FxNet.WorldFx.PILLAR, c, c, 1.4F, 30F, School.LIGHT.color, School.LIGHT.light, 50, 14},
				{"tornado", FxNet.WorldFx.TORNADO, c, c, 4.5F, 14F, School.WIND.color, School.WIND.light, 100, 40},
				{"slash", FxNet.WorldFx.SLASH, c.add(-1, 1.5, -6), new Vec3(0.1, 0, 1), 3.2F, 14F, School.WATER.color, School.WATER.light, 14, 2},
				{"helix", FxNet.WorldFx.HELIX, c.add(-8, 2, -6), c.add(6, 2, 8), 0.3F, 0F, School.MIND.color, School.MIND.light, 40, 14},
				{"barrier", FxNet.WorldFx.BARRIER, c.add(0, 1, 0), c.add(0, 1, 0), 5F, 5F, School.SUMMON.color, School.SUMMON.light, 100, 30},
				{"comet", FxNet.WorldFx.COMET, c.add(-14, 26, 10), c.add(2, 0, 4), 0.9F, 0F, fire, fireL, 24, 15},
		};
		for (Object[] k : kinds) {
			String name = (String) k[0];
			int kind = (int) k[1];
			context.runOnClient(mc -> {
				WorldFxRenderer.clear();
				// Серп с seed 58 летит наискось (наклон серпа берётся из seed).
				WorldFxRenderer.Fx fx = WorldFxRenderer.add(new FxNet.WorldFx(kind, (Vec3) k[2], (Vec3) k[3], (float) k[4], (float) k[5], (int) k[6],
						(int) k[7], (int) k[8], kind == FxNet.WorldFx.SLASH ? 58 : 12345));
				fx.fixedAge = (int) k[9];
			});
			context.waitTicks(1);
			int n = context.computeOnClient(mc -> WorldFxRenderer.activeCount(kind));
			if (n != 1) failures.add("эффект " + name + ": живых " + n + " вместо 1");
			shot(context, "circles_fx_" + name);
		}
		// Сочетание: круг у цели, из него столп, по земле волна.
		context.runOnClient(mc -> {
			WorldFxRenderer.clear();
			fixed(c, new Vec3(0, 1, 0), 5, School.LIGHT, 6, 1, 3, 0.2F).underlay = 1;
			WorldFxRenderer.add(new FxNet.WorldFx(FxNet.WorldFx.PILLAR, c, c, 2.2F, 40, School.LIGHT.color, School.LIGHT.light, 50, 1)).fixedAge = 6;
			WorldFxRenderer.add(new FxNet.WorldFx(FxNet.WorldFx.RING, c.add(0, 0.1, 0), new Vec3(0, 1, 0), 1, 12, School.LIGHT.color,
					0xFFFFFF, 20, 2)).fixedAge = 5;
		});
		shot(context, "circles_fx_combo_judgement");
		// Вспышка экрана — элемент интерфейса, поэтому на время снимка интерфейс включён.
		context.runOnClient(mc -> {
			CircleRenderer.clear();
			WorldFxRenderer.clear();
			mc.options.hideGui = false;
			WorldFxRenderer.add(new FxNet.WorldFx(FxNet.WorldFx.FLASH, eye, eye, 40, 1, 0xFFE0A0, 0xFFE0A0, 12, 0)).fixedAge = 1;
		});
		context.waitTicks(1);
		int flashes = context.computeOnClient(mc -> WorldFxRenderer.activeCount(FxNet.WorldFx.FLASH));
		if (flashes != 1) failures.add("вспышка экрана не принята");
		shot(context, "circles_fx_flash");
		context.runOnClient(mc -> {
			mc.options.hideGui = true;
			WorldFxRenderer.clear();
			// Короткоживущие эффекты (не замороженные) должны исчезнуть сами.
			WorldFxRenderer.add(new FxNet.WorldFx(FxNet.WorldFx.RING, c, new Vec3(0, 1, 0), 1, 4, fire, fireL, 5, 0));
			WorldFxRenderer.add(new FxNet.WorldFx(FxNet.WorldFx.FLASH, c, c, 10, 0.5F, fire, fire, 5, 0));
		});
		context.waitTicks(12);
		int left = context.computeOnClient(mc -> WorldFxRenderer.activeCount());
		if (left != 0) failures.add("короткие эффекты не исчезли: " + left);
	}

	/** Круг и эффекты, отправленные сервером, доходят до клиента и рисуются. */
	private static void network(ClientGameTestContext context, TestSingleplayerContext sp, Vec3 g, List<String> failures) {
		context.runOnClient(mc -> {
			CircleRenderer.clear();
			WorldFxRenderer.clear();
		});
		Vec3 c = g.add(0, 0.06, 6);
		camera(context, sp, g.add(0, 7, -5), 0, 38);
		sp.getServer().runOnServer(server -> {
			ServerLevel level = player(server).level();
			MagicFx.circle(level, School.EARTH, 6, School.EARTH.color, School.EARTH.light, c, new Vec3(0, 1, 0), 5, 2, 1.2F, 40, 40, 20, 0.15F);
			MagicFx.pillar(level, c, 1.2F, 20, School.EARTH.color, School.EARTH.light, 60);
		});
		context.waitTicks(20);
		int circles = context.computeOnClient(mc -> CircleRenderer.activeCount());
		int fx = context.computeOnClient(mc -> WorldFxRenderer.activeCount(FxNet.WorldFx.PILLAR));
		if (circles != 1) failures.add("круг с сервера не дошёл (кругов " + circles + ")");
		if (fx != 1) failures.add("столп с сервера не дошёл (" + fx + ")");
		shot(context, "circles_net_drawing");
		context.waitTicks(25);
		shot(context, "circles_net_locked");
		context.waitTicks(90);
		circles = context.computeOnClient(mc -> CircleRenderer.activeCount());
		if (circles != 0) failures.add("круг с сервера не исчез после draw+hold+fade (" + circles + ")");
	}

	/** Круги вдали (60 и 120 блоков, упрощение) и днём на траве. */
	private static void distanceAndDay(ClientGameTestContext context, TestSingleplayerContext sp, Vec3 g, List<String> failures) {
		context.runOnClient(mc -> {
			CircleRenderer.clear();
			WorldFxRenderer.clear();
			fixed(g.add(-10, 0, 40), new Vec3(0, 1, 0), 9, School.FIRE, 6, 1, 40, 0).underlay = 1;
			fixed(g.add(15, 0, 110), new Vec3(0, 1, 0), 14, School.WATER, 7, 1, 40, 0).underlay = 1;
			fixed(g.add(-30, 26, 120), new Vec3(0, -1, 0), 20, School.DARK, 7, 1, 40, 0);
		});
		camera(context, sp, g.add(0, 12, -10), 0, 12);
		context.waitTicks(40);
		shot(context, "circles_distance_lod");
		int drawn = context.computeOnClient(mc -> CircleRenderer.drawnLastFrame());
		if (drawn < 3) failures.add("дальние круги не нарисованы (" + drawn + " из 3)");
		// День, трава (за чёрным полом).
		sp.getServer().runOnServer(server -> player(server).level().setDayTime(6000));
		Vec3 grass = g.add(80, 0, 0);
		context.runOnClient(mc -> {
			CircleRenderer.clear();
			School[] s = {School.LIGHT, School.FIRE, School.WATER};
			for (int i = 0; i < 3; i++) fixed(grass.add((1 - i) * 10, 0, 6), new Vec3(0, 1, 0), 4.2F, s[i], 5, 1, 40, 0.3F * i).underlay = 1;
		});
		camera(context, sp, grass.add(0, 9, -9), 0, 38);
		context.waitTicks(40);
		shot(context, "circles_daylight");
		sp.getServer().runOnServer(server -> player(server).level().setDayTime(18000));
		context.runOnClient(mc -> CircleRenderer.clear());
	}

	/** Пробный каст: заклинание, копии мультикаста, наклон взгляда, откуда снимать сбоку. */
	private record Trial(Spell spell, int copies, float pitch, Vec3 side, float sideYaw, float sidePitch, boolean firstPerson) {}

	/**
	 * Заклинания только для этого теста (по одному на якорь, плюс слияние и поток с лучом). Регистрируются после
	 * загрузки, поэтому не попадают в {@link Spells#all()} — колесо, книги и другие тесты их не видят.
	 */
	private static Spell testSpell(String id, School school, int tier, CastStyle style, Anchor anchor, java.util.function.UnaryOperator<Spell.Builder> extra) {
		Spell existing = Spells.get(id);
		if (existing != null) return existing;
		Spell.Builder b = Spell.of(id, school, tier, style).mana(5).charge(24).cooldown(0).range(64).anchor(anchor);
		b = style == CastStyle.CHANNEL ? b.channel((c, t) -> {}) : b.action(c -> true);
		return extra.apply(b).register();
	}

	/**
	 * Настоящий каст: маг держит ПКМ — круг строится там, где велит якорь (у посоха, под ногами, у цели, в небе,
	 * рядом к цели, веером за спиной, по орбите; копии мультикаста; слияние; поток с лучом), после выпуска остаётся
	 * призрак. Проверяется, где и сколько кругов нарисовано.
	 */
	private static void realCasts(ClientGameTestContext context, TestSingleplayerContext sp, Vec3 g, List<String> failures) {
		Vec3 me = g.add(0, 0, -10);
		List<Trial> trials = List.of(
				new Trial(testSpell("circle_test_hand", School.FIRE, 4, CastStyle.BOLT, Anchor.HAND, b -> b), 1, 0, me.add(3.5, 1.8, 2.5), 125, 5, true),
				new Trial(testSpell("circle_test_hand", School.FIRE, 4, CastStyle.BOLT, Anchor.HAND, b -> b), 3, 0, me.add(4.5, 2.0, 4), 135, 8, true),
				new Trial(testSpell("circle_test_feet", School.LIFE, 5, CastStyle.SELF, Anchor.FEET, b -> b.layers(2)), 1, 10, me.add(5, 4, 5), 135, 30,
						false),
				new Trial(testSpell("circle_test_target", School.EARTH, 5, CastStyle.BOLT, Anchor.TARGET, b -> b), 1, 12, me.add(7, 6, 3), 70, 30, false),
				new Trial(testSpell("circle_test_target", School.EARTH, 5, CastStyle.BOLT, Anchor.TARGET, b -> b), 3, 12, me.add(9, 9, 4), 70, 38,
						false),
				new Trial(testSpell("circle_test_sky", School.LIGHTNING, 6, CastStyle.RITUAL, Anchor.SKY, b -> b.layers(2)), 1, 12, me.add(14, 3, 2), 60,
						-25, false),
				new Trial(testSpell("circle_test_line", School.FIRE, 6, CastStyle.BOLT, Anchor.LINE, b -> b.layers(5)), 1, 0, me.add(-6, 2.5, 6), -110,
						5, true),
				new Trial(testSpell("circle_test_back", School.SUMMON, 6, CastStyle.BOLT, Anchor.BACK, b -> b), 1, 0, me.add(0, 2.5, 6), 180, 5, false),
				new Trial(testSpell("circle_test_orbit", School.SPACE, 4, CastStyle.SELF, Anchor.ORBIT, b -> b), 1, 0, me.add(4, 3, 4), 135, 20, false),
				new Trial(testSpell("circle_test_fusion", School.FUSION, 6, CastStyle.BOLT, Anchor.TARGET, b -> b.combo(School.FIRE, School.WATER)), 1,
						12, me.add(7, 6, 3), 70, 30, false),
				new Trial(testSpell("circle_test_beam", School.LIGHT, 4, CastStyle.CHANNEL, Anchor.HAND, b -> b.beam()), 2, 4, me.add(4, 2.2, 3), 120, 5,
						true));
		int[] id = new int[2];
		sp.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			p.setGameMode(GameType.CREATIVE);
			for (Trial t : trials) Magic.teach(p, t.spell);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MagicItems.PRIMORDIAL_STAFF));
			p.getAbilities().flying = false;
			p.onUpdateAbilities();
			p.teleportTo(p.level(), me.x, me.y, me.z, Set.of(), 0, 0, true);
			id[0] = p.getId();
			// Невидимая стойка — вторая камера (сбоку).
			ArmorStand stand = new ArmorStand(p.level(), me.x + 4, me.y, me.z);
			stand.setInvisible(true);
			stand.setNoGravity(true);
			p.level().addFreshEntity(stand);
			id[1] = stand.getId();
		});
		context.waitTicks(10);
		for (Trial t : trials) {
			Spell s = t.spell;
			String tag = s.anchor.name().toLowerCase() + (t.copies > 1 ? "_x" + t.copies : "") + (s.fusion() ? "_fusion" : "")
					+ (s.beam ? "_beam" : "");
			sp.getServer().runOnServer(server -> {
				ServerPlayer p = player(server);
				Magic.resetCooldowns(p);
				Magic.setMulticast(p, t.copies);
				Magic.select(p, s.id);
				p.setAttached(Magic.MANA, Magic.maxMana(p));
				p.teleportTo(p.level(), me.x, me.y, me.z, Set.of(), 0, t.pitch, true);
				ArmorStand stand = (ArmorStand) p.level().getEntity(id[1]);
				stand.snapTo(t.side.x, t.side.y - 1.7, t.side.z, t.sideYaw, t.sidePitch);
				stand.setYHeadRot(t.sideYaw);
			});
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(4);
			context.getInput().holdKey(opt -> opt.keyUse);
			context.waitTicks(12);
			shot(context, "circles_cast_" + tag + "_forming");
			CastVisuals.Placement pl = context.computeOnClient(mc -> CastVisuals.lastPlacement(id[0]));
			List<CastVisuals.Drawn> all = context.computeOnClient(mc -> CastVisuals.lastCircles(id[0]));
			if (pl == null) {
				failures.add("каст " + tag + ": круг не нарисован");
			} else {
				int expect = switch (s.anchor) {
					case LINE -> s.layers * t.copies;
					case BACK -> (2 + s.tier) * t.copies;
					case ORBIT -> Math.max(t.copies, 2 + s.tier / 2);
					default -> t.copies;
				};
				if (all.size() != expect) failures.add("каст " + tag + ": кругов " + all.size() + ", ждали " + expect);
				Vec3 eye = me.add(0, 1.62, 0);
				double ahead = 1.62 / Math.tan(Math.toRadians(Math.max(1, t.pitch)));
				Vec3 ground = new Vec3(me.x, me.y, me.z + ahead);
				switch (s.anchor) {
					case TARGET -> {
						if (t.copies == 1 && pl.center().distanceTo(ground) > 1.5) failures.add("каст " + tag + ": круг у цели в " + pl.center() + ", ждали " + ground);
					}
					case SKY -> {
						double h = pl.center().y - me.y;
						if (Math.abs(h - s.skyHeight()) > 1.5 || Math.abs(pl.center().z - ground.z) > 1.5) {
							failures.add("каст " + tag + ": небесный круг в " + pl.center() + " (высота " + h + ", ждали " + s.skyHeight() + ")");
						}
					}
					case FEET -> {
						if (pl.center().distanceTo(me) > 0.5) failures.add("каст " + tag + ": круг под ногами в " + pl.center());
					}
					case HAND -> {
						if (pl.center().distanceTo(eye) > 2.5) failures.add("каст " + tag + ": круг у посоха далеко: " + pl.center());
					}
					default -> {
					}
				}
			}
			context.waitTicks(30);
			shot(context, "circles_cast_" + tag + "_ready");
			standShot(context, id[1], "circles_cast_" + tag + "_side");
			if (t.firstPerson) {
				context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
				context.waitTicks(2);
				shot(context, "circles_cast_" + tag + "_first_person");
				context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			}
			if (!s.charged()) context.waitTicks(20);
			context.getInput().releaseKey(opt -> opt.keyUse);
			context.waitTicks(2);
			int ghosts = context.computeOnClient(mc -> CircleRenderer.activeCount());
			if (ghosts == 0) failures.add("каст " + tag + ": после выпуска нет призрака круга");
			standShot(context, id[1], "circles_cast_" + tag + "_release");
			context.waitTicks(50);
			int left = context.computeOnClient(mc -> CircleRenderer.activeCount());
			if (left != 0) failures.add("каст " + tag + ": призраки не погасли (" + left + ")");
		}
		// Срыв: отпустить ПКМ, не дождавшись формирования, — круг рассыпается.
		Spell target = trials.get(3).spell;
		sp.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			Magic.setMulticast(p, 1);
			Magic.select(p, target.id);
			p.teleportTo(p.level(), me.x, me.y, me.z, Set.of(), 0, 12, true);
		});
		context.waitTicks(3);
		context.getInput().holdKey(opt -> opt.keyUse);
		context.waitTicks(8);
		context.getInput().releaseKey(opt -> opt.keyUse);
		context.waitTicks(2);
		shot(context, "circles_cast_fizzle");
		context.runOnClient(mc -> {
			mc.options.setCameraType(CameraType.FIRST_PERSON);
			mc.setCameraEntity(mc.player);
		});
	}

	/** Снимок с камеры-стойки, затем камера возвращается к игроку. */
	private static void standShot(ClientGameTestContext context, int stand, String name) {
		CameraType before = context.computeOnClient(mc -> mc.options.getCameraType());
		context.runOnClient(mc -> {
			var e = mc.level.getEntity(stand);
			if (e != null) {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.setCameraEntity(e);
			}
		});
		context.waitTicks(1);
		context.takeScreenshot(name);
		context.runOnClient(mc -> {
			mc.setCameraEntity(mc.player);
			mc.options.setCameraType(before);
		});
	}

	// ---------------------------------------------------------------- помощники

	private static CircleRenderer.Inst fixed(Vec3 at, Vec3 normal, float radius, School s, int tier, float progress, float since, float spin) {
		CircleRenderer.Inst in = new CircleRenderer.Inst();
		in.pos = at;
		in.normal = normal;
		in.radius = radius;
		in.school = s;
		in.tier = tier;
		in.color = s.color;
		in.light = s.light;
		in.fixedProgress = progress;
		in.fixedSince = since;
		in.fixedSpin = spin;
		in.forever = true;
		in.draw = 20;
		return CircleRenderer.add(in);
	}

	private static int mixColor(int a, int b) {
		return (((a >> 16 & 255) + (b >> 16 & 255)) / 2) << 16 | (((a >> 8 & 255) + (b >> 8 & 255)) / 2) << 8 | ((a & 255) + (b & 255)) / 2;
	}

	/** Камера наблюдателя: телепорт с поворотом. */
	private static void camera(ClientGameTestContext context, TestSingleplayerContext sp, Vec3 at, float yaw, float pitch) {
		sp.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			p.teleportTo(p.level(), at.x, at.y - p.getEyeHeight(), at.z, Set.of(), yaw, pitch, true);
		});
		context.waitTicks(3);
		context.runOnClient(mc -> {
			mc.player.setYRot(yaw);
			mc.player.setXRot(pitch);
			mc.player.yRotO = yaw;
			mc.player.xRotO = pitch;
		});
		context.waitTicks(1);
	}

	private static void shot(ClientGameTestContext context, String name) {
		context.waitTicks(1);
		context.takeScreenshot(name);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}
}
