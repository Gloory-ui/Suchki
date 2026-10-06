package com.suchki.test;

import com.suchki.magic.Anchor;
import com.suchki.magic.CastStyle;
import com.suchki.magic.Magic;
import com.suchki.magic.MagicItems;
import com.suchki.magic.Spell;
import com.suchki.magic.Spells;
import com.suchki.magic.Terrain;
import com.suchki.magic.Zones;
import com.suchki.skill.PlayerSkills;
import com.suchki.skill.Skills;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Каждое заклинание в настоящей игре: игрок держит ПКМ посохом первоначал, круг рисуется, заклинание выпускается,
 * бьёт по мишеням. Для каждого — снимки: рисование круга, готовность (сбоку), выпуск, удар (общий план), итог.
 * Каждое заклинание — на своей площадке (разрушение не мешает следующим). В лог пишется, что сделало заклинание:
 * урон мишеням, потраченная мана, сломанные блоки, призванные существа. Ошибка — только если заклинание упало
 * или заряженное заклинание не потратило ману (значит, не сработало).
 */
public class SpellShowcaseTest implements FabricClientGameTest {
	private static final int SHOT_W = 960, SHOT_H = 540;
	private static final int[] target = new int[3];
	private static final int[] cams = new int[2];

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!TestFilter.enabled("spells")) return;
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			context.runOnClient(mc -> {
				mc.options.languageCode = "ru_ru";
				mc.getLanguageManager().setSelected("ru_ru");
				mc.reloadResourcePacks();
			});
			context.waitTicks(40);
			context.getInput().resizeWindow(1920, 1080);
			context.runOnClient(mc -> {
				mc.options.guiScale().set(3);
				mc.resizeDisplay();
			});
			sp.getServer().runOnServer(server -> {
				ServerPlayer p = player(server);
				p.setGameMode(GameType.SURVIVAL);
				p.setAttached(Skills.DATA, new PlayerSkills(Optional.of(Magic.ROLE), Map.of(Magic.ROLE, new PlayerSkills.Progress(50, 0, List.of())), 0L));
				Skills.applyAttributes(p);
				Magic.onRoleChosen(p, Magic.ROLE);
				for (Spell s : Spells.all()) Magic.teach(p, s);
				p.getInventory().clearContent();
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MagicItems.PRIMORDIAL_STAFF));
				p.getAbilities().invulnerable = true;
				p.level().setDayTime(13000);
			});
			context.waitTicks(10);

			List<String> failures = new ArrayList<>();
			List<String> report = new ArrayList<>();
			List<Spell> all = Spells.all();
			for (int i = 0; i < all.size(); i++) {
				Spell s = all.get(i);
				try {
					showcase(context, sp, s, i, 1, report, failures);
				} catch (Throwable t) {
					failures.add(s.id + ": исключение " + t);
				}
			}
			// Мультикаст и слияние крупным планом.
			int extra = all.size();
			for (Spell s : all) {
				if (s.maxCopies >= 3 && s.charged() && s.tier <= 5 && (s.anchor == Anchor.HAND || s.anchor == Anchor.TARGET || s.anchor == Anchor.SKY)) {
					try {
						showcase(context, sp, s, extra++, Math.min(4, s.maxCopies), report, failures);
					} catch (Throwable t) {
						failures.add(s.id + " ×N: исключение " + t);
					}
					if (extra - all.size() >= 8) break;
				}
			}
			report.forEach(l -> System.out.println("[showcase] " + l));
			failures.forEach(l -> System.out.println("[showcase] FAIL " + l));
			System.out.println("[showcase] Итого: заклинаний " + all.size() + ", ошибок " + failures.size());
			if (!failures.isEmpty()) throw new AssertionError("Ошибки витрины заклинаний:\n" + String.join("\n", failures));
		}
	}

	/** Одно заклинание на своей площадке: зарядка, снимки, выпуск, итог. */
	private static void showcase(ClientGameTestContext context, TestSingleplayerContext sp, Spell s, int index, int copies,
			List<String> report, List<String> failures) {
		String tag = String.format("%03d_%s%s", index, s.id, copies > 1 ? "_x" + copies : "");
		BlockPos o = new BlockPos(2000 + index * 160, 100, 0);
		sp.getServer().runOnServer(server -> setup(player(server), o, s, copies));
		context.waitTicks(4);
		sp.getClientWorld().waitForChunksRender();
		float pitch = switch (s.anchor) {
			case TARGET, SKY -> 9;
			case LINE -> 4;
			default -> s.style == CastStyle.SELF || s.anchor == Anchor.FEET ? 6 : 2;
		};
		look(context, 0, pitch);
		camera(context, CameraType.THIRD_PERSON_BACK);
		context.waitTicks(6);

		int form = sp.getServer().computeOnServer(server -> {
			ServerPlayer p = player(server);
			return Magic.formTicks(s, Magic.castSpeed(p, MagicItems.PRIMORDIAL_STAFF), Magic.copiesFor(p, s));
		});
		float[] before = state(sp);
		int brokenBefore = Terrain.brokenTotal();

		context.getInput().holdKey(opt -> opt.keyUse);
		if (s.charged()) {
			int a = Math.max(1, Math.round(form * 0.35F));
			context.waitTicks(a);
			shot(context, tag + "_1_draw");
			int b = Math.max(1, Math.round(form * 0.8F)) - a;
			camera(context, CameraType.FIRST_PERSON);
			context.waitTicks(Math.max(1, b));
			shot(context, tag + "_2_chant_1p");
			camera(context, CameraType.THIRD_PERSON_BACK);
			context.waitTicks(Math.max(1, form - a - b) + 4);
			sideShot(context, tag + "_3_ready_side", cams[0]);
			context.getInput().releaseKey(opt -> opt.keyUse);
			context.waitTicks(3);
			shot(context, tag + "_4_release");
			context.waitTicks(s.tier >= 5 ? 30 : 14);
			sideShot(context, tag + "_5_impact_wide", cams[1]);
			context.waitTicks(s.tier >= 6 ? 120 : 50);
			sideShot(context, tag + "_6_after_wide", cams[1]);
		} else {
			context.waitTicks(30);
			shot(context, tag + "_1_channel");
			sideShot(context, tag + "_3_channel_side", cams[0]);
			camera(context, CameraType.FIRST_PERSON);
			context.waitTicks(4);
			shot(context, tag + "_2_channel_1p");
			camera(context, CameraType.THIRD_PERSON_BACK);
			context.waitTicks(10);
			context.getInput().releaseKey(opt -> opt.keyUse);
			context.waitTicks(10);
		}
		float[] after = state(sp);
		int broken = Terrain.brokenTotal() - brokenBefore;
		String line = String.format("%s [%s, ранг %d, %s, %s%s]: урон мишеням %.1f, мана −%.1f, сломано блоков %d, существ %+d",
				s.id, s.school.id, s.tier, s.style, s.anchor, copies > 1 ? ", ×" + copies : "",
				before[0] - after[0], before[1] - after[1], broken, Math.round(after[2] - before[2]));
		report.add(line);
		if (s.charged() && before[1] - after[1] < 0.5F) failures.add(s.id + ": заклинание не сработало (мана не потрачена)");
		sp.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			Magic.resetCooldowns(p);
			Magic.setMulticast(p, 1);
			p.setHealth(p.getMaxHealth());
			p.removeAllEffects();
			Zones.clear();
			Terrain.restoreAll();
		});
	}

	/** Площадка: пол, «домик» для разрушения, три мишени, две камеры (сбоку и общий план). */
	private static void setup(ServerPlayer p, BlockPos o, Spell s, int copies) {
		ServerLevel level = p.level();
		for (int dx = -26; dx <= 26; dx++) {
			for (int dz = -12; dz <= 40; dz++) {
				level.setBlock(o.offset(dx, -1, dz), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
				level.setBlock(o.offset(dx, -2, dz), Blocks.STONE.defaultBlockState(), 2);
				for (int dy = 0; dy <= 24; dy++) level.setBlock(o.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
			}
		}
		// Каменный «домик» за мишенями — видно, ломает ли заклинание блоки.
		for (int dx = -4; dx <= 4; dx++) {
			for (int dy = 0; dy <= 4; dy++) {
				for (int dz = 22; dz <= 26; dz++) {
					boolean wall = dx == -4 || dx == 4 || dz == 22 || dz == 26 || dy == 4;
					if (wall) level.setBlock(o.offset(dx, dy, dz), (dy + dx) % 3 == 0 ? Blocks.STONE_BRICKS.defaultBlockState()
							: Blocks.COBBLESTONE.defaultBlockState(), 2);
				}
			}
		}
		p.teleportTo(level, o.getX() + 0.5, o.getY(), o.getZ() + 0.5, java.util.Set.of(), 0, 0, true);
		p.setAttached(Magic.MANA, Magic.maxMana(p));
		Magic.resetCooldowns(p);
		Magic.select(p, s.id);
		Magic.setMulticast(p, copies);
		int[][] spots = {{0, 12}, {-4, 15}, {4, 15}};
		for (int i = 0; i < 3; i++) {
			Mob z = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
			z.snapTo(o.getX() + 0.5 + spots[i][0], o.getY(), o.getZ() + 0.5 + spots[i][1], 180, 0);
			z.setNoAi(true);
			z.setPersistenceRequired();
			z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400);
			z.setHealth(400);
			level.addFreshEntity(z);
			target[i] = z.getId();
		}
		cams[0] = stand(level, o.getX() + 7.5, o.getY() + 1.0, o.getZ() + 3.0, 90, 8);
		boolean high = s.anchor == Anchor.SKY || s.tier >= 6;
		cams[1] = stand(level, o.getX() + 20.5, o.getY() + (high ? 14 : 6), o.getZ() - 6.0, 45, high ? 22 : 12);
	}

	private static int stand(ServerLevel level, double x, double y, double z, float yaw, float pitch) {
		ArmorStand stand = new ArmorStand(level, x, y, z);
		stand.setYRot(yaw);
		stand.setYHeadRot(yaw);
		stand.setXRot(pitch);
		stand.setInvisible(true);
		stand.setNoGravity(true);
		level.addFreshEntity(stand);
		return stand.getId();
	}

	/** Здоровье мишеней (сумма), мана, число существ вокруг. */
	private static float[] state(TestSingleplayerContext sp) {
		return sp.getServer().computeOnServer(server -> {
			ServerPlayer p = player(server);
			float hp = 0;
			for (int id : target) if (p.level().getEntity(id) instanceof LivingEntity l) hp += l.getHealth();
			int mobs = p.level().getEntitiesOfClass(LivingEntity.class, new AABB(p.blockPosition()).inflate(40),
					e -> !(e instanceof Player) && !(e instanceof ArmorStand)).size();
			return new float[] {hp, Magic.mana(p), mobs};
		});
	}

	private static void shot(ClientGameTestContext context, String name) {
		context.takeScreenshot(TestScreenshotOptions.of(name).withSize(SHOT_W, SHOT_H));
	}

	/** Снимок с камеры-стойки, затем камера возвращается к игроку. */
	private static void sideShot(ClientGameTestContext context, String name, int stand) {
		CameraType before = context.computeOnClient(mc -> mc.options.getCameraType());
		context.runOnClient(mc -> {
			Entity e = mc.level.getEntity(stand);
			if (e != null) {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.setCameraEntity(e);
			}
		});
		context.waitTicks(1);
		shot(context, name);
		context.runOnClient(mc -> {
			mc.setCameraEntity(mc.player);
			mc.options.setCameraType(before);
		});
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void camera(ClientGameTestContext context, CameraType type) {
		context.runOnClient(mc -> mc.options.setCameraType(type));
		context.waitTicks(1);
	}

	private static void look(ClientGameTestContext context, float yaw, float pitch) {
		context.runOnClient(mc -> {
			mc.player.setYRot(yaw);
			mc.player.setXRot(pitch);
			mc.player.setYHeadRot(yaw);
			mc.player.yBodyRot = yaw;
		});
		context.waitTicks(2);
	}
}
