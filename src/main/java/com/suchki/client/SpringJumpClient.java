package com.suchki.client;

import com.suchki.enchant.Ench;
import com.suchki.enchant.SpringJump;
import com.suchki.enchant.SuchkiEnchantments;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;

/** Клиентская часть Пружины: повторное нажатие прыжка в воздухе подбрасывает игрока. */
final class SpringJumpClient {
	/** Сила прыжка в воздухе — чуть выше обычного прыжка (0.42). */
	private static final double JUMP_POWER = 0.5;

	private static boolean wasDown;
	private static int used;

	private SpringJumpClient() {}

	static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(SpringJumpClient::tick);
	}

	private static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null) return;
		boolean down = mc.options.keyJump.isDown();
		if (player.onGround() || player.isInWater() || player.onClimbable()) used = 0;

		// Только новое нажатие: удержание пробела после обычного прыжка не считается.
		if (down && !wasDown && !player.onGround() && !player.getAbilities().flying && !player.isInWater()
				&& !player.onClimbable() && !player.isFallFlying() && !player.isPassenger()) {
			int level = Ench.level(player, SuchkiEnchantments.SPRING, player.getItemBySlot(EquipmentSlot.FEET));
			if (used < level) {
				used++;
				Vec3 v = player.getDeltaMovement();
				player.setDeltaMovement(v.x, JUMP_POWER, v.z);
				player.resetFallDistance();
				ClientPlayNetworking.send(SpringJump.JumpPayload.INSTANCE);
			}
		}
		wasDown = down;
	}
}
