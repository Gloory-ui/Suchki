package com.suchki.test;

import com.suchki.client.magic.MagicClient;
import com.suchki.magic.Magic;
import com.suchki.magic.MagicItems;
import com.suchki.magic.School;
import com.suchki.magic.Spell;
import com.suchki.magic.Spells;
import com.suchki.skill.PlayerSkills;
import com.suchki.skill.Skills;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Клиентская проверка магии: маг колдует снаряд, поток, «на себя», ритуал и призыв — скриншоты фаз
 * (формирование, пик, выпуск) от первого и третьего лица; гримуар со свободной рукой; колесо заклинаний; HUD.
 */
final class MagicUiTest {
	private static int[] side = new int[1];

	private MagicUiTest() {}

	/** Снимок в профиль через невидимую стойку сбоку, затем камера возвращается. */
	private static void sideShot(ClientGameTestContext context, String name) {
		CameraType before = context.computeOnClient(mc -> mc.options.getCameraType());
		context.runOnClient(mc -> {
			var e = mc.level.getEntity(side[0]);
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

	static void run(ClientGameTestContext context, TestSingleplayerContext sp, List<String> failures) {
		int[] target = new int[1];
		side = new int[1];
		sp.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			ServerLevel level = p.level();
			BlockPos o = p.blockPosition();
			// Чистая площадка и мишень в 10 блоках к югу.
			for (int dx = -14; dx <= 14; dx++) {
				for (int dz = -14; dz <= 14; dz++) {
					level.setBlock(o.offset(dx, -1, dz), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
					for (int dy = 0; dy <= 9; dy++) level.setBlock(o.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
				}
			}
			level.setDayTime(6000);
			p.setAttached(Skills.DATA, new PlayerSkills(Optional.of(Magic.ROLE),
					Map.of(Magic.ROLE, new PlayerSkills.Progress(50, 0, List.of())), 0L));
			Skills.applyAttributes(p);
			Magic.onRoleChosen(p, Magic.ROLE);
			for (Spell s : Spells.all()) Magic.teach(p, s);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MagicItems.ARCHMAGE_STAFF));
			p.setAttached(Magic.MANA, Magic.maxMana(p));
			p.teleportTo(level, o.getX() + 0.5, o.getY(), o.getZ() + 0.5, java.util.Set.of(), 0, 0, true);
			Zombie z = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
			z.snapTo(o.getX() + 0.5, o.getY(), o.getZ() + 10.5, 180, 0);
			z.setNoAi(true);
			z.setPersistenceRequired();
			z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(400);
			z.setHealth(400);
			level.addFreshEntity(z);
			target[0] = z.getId();
			// Невидимая стойка сбоку — камера для съёмки поз рук в профиль.
			net.minecraft.world.entity.decoration.ArmorStand stand = new net.minecraft.world.entity.decoration.ArmorStand(level,
					o.getX() + 4.5, o.getY() - 0.3, o.getZ() + 1.0);
			stand.setYRot(90);
			stand.setYHeadRot(90);
			stand.setXRot(4);
			stand.setInvisible(true);
			stand.setNoGravity(true);
			level.addFreshEntity(stand);
			side[0] = stand.getId();
		});
		context.waitTicks(20);
		look(context, 0, 0);

		boolean starter = sp.getServer().computeOnServer(server -> Magic.data(player(server)).starter());
		if (!starter) failures.add("Магия: стартовый набор не выдан");

		// HUD: полоса маны, значок заклинания.
		select(sp, "firebolt");
		camera(context, CameraType.FIRST_PERSON);
		context.waitTicks(15);
		context.takeScreenshot("magic_hud");

		// Снаряд (Огненная стрела): формирование, пик, выпуск — со спины и от первого лица.
		camera(context, CameraType.THIRD_PERSON_BACK);
		float hpBefore = health(sp, target[0]);
		float manaBefore = mana(sp);
		context.getInput().holdKey(o -> o.keyUse);
		context.waitTicks(5);
		context.takeScreenshot("magic_bolt_form_3p");
		sideShot(context, "magic_bolt_form_side");
		context.waitTicks(31);
		context.takeScreenshot("magic_bolt_peak_3p");
		sideShot(context, "magic_bolt_peak_side");
		context.getInput().releaseKey(o -> o.keyUse);
		context.waitTicks(1);
		sideShot(context, "magic_bolt_release_side");
		context.takeScreenshot("magic_bolt_release_3p");
		context.waitTicks(18);
		context.takeScreenshot("magic_bolt_after");
		if (!(health(sp, target[0]) < hpBefore)) failures.add("Магия: Огненная стрела не попала в мишень в 10 блоках");
		if (!(mana(sp) < manaBefore - 10)) failures.add("Магия: накопленная стрела не потратила ману");
		resetCooldowns(sp);

		camera(context, CameraType.FIRST_PERSON);
		context.getInput().holdKey(o -> o.keyUse);
		context.waitTicks(5);
		context.takeScreenshot("magic_bolt_form_1p");
		context.waitTicks(31);
		context.takeScreenshot("magic_bolt_peak_1p");
		context.getInput().releaseKey(o -> o.keyUse);
		context.waitTicks(2);
		context.takeScreenshot("magic_bolt_release_1p");
		context.waitTicks(10);
		resetCooldowns(sp);

		camera(context, CameraType.THIRD_PERSON_FRONT);
		context.getInput().holdKey(o -> o.keyUse);
		context.waitTicks(36);
		context.takeScreenshot("magic_bolt_peak_front");
		context.getInput().releaseKey(o -> o.keyUse);
		context.waitTicks(3);
		context.takeScreenshot("magic_bolt_release_front");
		context.waitTicks(15);
		resetCooldowns(sp);

		// Поток с лучом (Солнечный луч).
		select(sp, "sunbeam");
		camera(context, CameraType.THIRD_PERSON_BACK);
		context.getInput().holdKey(o -> o.keyUse);
		context.waitTicks(30);
		context.takeScreenshot("magic_beam_3p");
		sideShot(context, "magic_beam_side");
		camera(context, CameraType.FIRST_PERSON);
		context.waitTicks(10);
		context.takeScreenshot("magic_beam_1p");
		context.getInput().releaseKey(o -> o.keyUse);
		context.waitTicks(10);
		resetCooldowns(sp);

		// Оберег (щит перед магом).
		select(sp, "ward");
		camera(context, CameraType.THIRD_PERSON_FRONT);
		context.getInput().holdKey(o -> o.keyUse);
		context.waitTicks(20);
		context.takeScreenshot("magic_ward_front");
		sideShot(context, "magic_ward_side");
		camera(context, CameraType.FIRST_PERSON);
		context.waitTicks(5);
		context.takeScreenshot("magic_ward_1p");
		context.getInput().releaseKey(o -> o.keyUse);
		context.waitTicks(10);

		// На себя (Ледяная волна).
		select(sp, "frost_nova");
		camera(context, CameraType.THIRD_PERSON_FRONT);
		context.getInput().holdKey(o -> o.keyUse);
		context.waitTicks(22);
		context.takeScreenshot("magic_self_charge");
		sideShot(context, "magic_self_charge_side");
		context.getInput().releaseKey(o -> o.keyUse);
		context.waitTicks(2);
		sideShot(context, "magic_self_release_side");
		context.takeScreenshot("magic_self_release");
		context.waitTicks(15);
		resetCooldowns(sp);

		// Ритуал (Метеоритный дождь) — по земле в 8 блоках.
		select(sp, "meteor_shower");
		look(context, 0, 12);
		camera(context, CameraType.THIRD_PERSON_FRONT);
		context.getInput().holdKey(o -> o.keyUse);
		context.waitTicks(30);
		context.takeScreenshot("magic_ritual_charge");
		sideShot(context, "magic_ritual_charge_side");
		context.waitTicks(50);
		context.takeScreenshot("magic_ritual_peak");
		sideShot(context, "magic_ritual_peak_side");
		context.getInput().releaseKey(o -> o.keyUse);
		context.waitTicks(1);
		sideShot(context, "magic_ritual_release_side");
		context.waitTicks(1);
		context.takeScreenshot("magic_ritual_release");
		camera(context, CameraType.THIRD_PERSON_BACK);
		context.waitTicks(30);
		context.takeScreenshot("magic_meteors");
		context.waitTicks(60);
		look(context, 0, 0);
		resetCooldowns(sp);

		// Призыв (Призрачный волк).
		select(sp, "familiar");
		camera(context, CameraType.THIRD_PERSON_FRONT);
		context.getInput().holdKey(o -> o.keyUse);
		context.waitTicks(16);
		context.takeScreenshot("magic_summon_charge");
		sideShot(context, "magic_summon_charge_side");
		context.getInput().releaseKey(o -> o.keyUse);
		context.waitTicks(12);
		context.takeScreenshot("magic_summon_done");
		resetCooldowns(sp);

		// Гримуар: книга у груди, колдует свободная рука.
		sp.getServer().runOnServer(server -> player(server).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MagicItems.GRIMOIRE)));
		select(sp, "firebolt");
		camera(context, CameraType.FIRST_PERSON);
		context.waitTicks(5);
		context.getInput().holdKey(o -> o.keyUse);
		context.waitTicks(20);
		context.takeScreenshot("magic_grimoire_1p");
		camera(context, CameraType.THIRD_PERSON_FRONT);
		context.waitTicks(4);
		context.takeScreenshot("magic_grimoire_front");
		sideShot(context, "magic_grimoire_side");
		context.getInput().releaseKey(o -> o.keyUse);
		context.waitTicks(10);
		camera(context, CameraType.FIRST_PERSON);

		// Колесо заклинаний: школа Свет, первое заклинание дуги.
		select(sp, "firebolt");
		context.waitTicks(3);
		context.getInput().holdKey(MagicClient.wheelKey);
		context.waitTicks(6);
		double[] gui = context.computeOnClient(mc -> new double[] {mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
				mc.getWindow().getGuiScale()});
		double cx = gui[0] / 2, cy = gui[1] / 2, scale = gui[2];
		int li = School.LIGHT.ordinal();
		double schoolAng = -Math.PI / 2 + Math.PI * 2 * li / School.values().length;
		context.getInput().setCursorPos((cx + Math.cos(schoolAng) * 56) * scale, (cy + Math.sin(schoolAng) * 56) * scale);
		context.waitTicks(4);
		context.takeScreenshot("magic_wheel_school");
		int count = Spells.of(School.LIGHT).size();
		double first = schoolAng - (count - 1) / 2.0 * Math.toRadians(24);
		context.getInput().setCursorPos((cx + Math.cos(first) * 112) * scale, (cy + Math.sin(first) * 112) * scale);
		context.waitTicks(4);
		context.takeScreenshot("magic_wheel_spell");
		context.getInput().releaseKey(MagicClient.wheelKey);
		context.waitTicks(5);
		String chosen = sp.getServer().computeOnServer(server -> Magic.data(player(server)).selected());
		String expect = Spells.of(School.LIGHT).get(0).id;
		if (!expect.equals(chosen)) failures.add("Магия: колесо выбрало «" + chosen + "», ожидалось «" + expect + "»");
		boolean closed = context.computeOnClient(mc -> mc.screen == null);
		if (!closed) failures.add("Магия: колесо не закрылось после отпускания R");

		sp.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			var e = p.level().getEntity(target[0]);
			if (e != null) e.discard();
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		});
		camera(context, CameraType.FIRST_PERSON);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void select(TestSingleplayerContext sp, String spell) {
		sp.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			Magic.select(p, spell);
			p.setAttached(Magic.MANA, Magic.maxMana(p));
		});
	}

	private static void resetCooldowns(TestSingleplayerContext sp) {
		sp.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			String sel = Magic.data(p).selected();
			Magic.forgetAll(p);
			for (Spell s : Spells.all()) Magic.teach(p, s);
			Magic.select(p, sel);
			p.setAttached(Magic.MANA, Magic.maxMana(p));
		});
	}

	private static void camera(ClientGameTestContext context, CameraType type) {
		context.runOnClient(mc -> mc.options.setCameraType(type));
		context.waitTicks(2);
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

	private static float health(TestSingleplayerContext sp, int id) {
		return sp.getServer().computeOnServer(server -> player(server).level().getEntity(id) instanceof Zombie z ? z.getHealth() : -1F);
	}

	private static float mana(TestSingleplayerContext sp) {
		return sp.getServer().computeOnServer(server -> Magic.mana(player(server)));
	}
}
