package com.suchki.client.magic;

import com.mojang.blaze3d.platform.InputConstants;
import com.suchki.magic.FxNet;
import com.suchki.magic.Magic;
import com.suchki.magic.MagicSounds;
import com.suchki.magic.Spell;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Клавиши магии, кроме колеса заклинаний. G — мультикаст: ×1 → ×2 → … → наибольший по уровню → ×1
 * (во время каста не переключается — сервер всё равно не даст).
 */
public final class MagicKeys {
	public static KeyMapping multicastKey;
	/** Выбранное, но ещё не подтверждённое сервером число копий (и когда выбрано) — чтобы быстрые нажатия шли по кругу. */
	private static int pending;
	private static long pendingAt = -100;

	private MagicKeys() {}

	public static void register(KeyMapping.Category category) {
		multicastKey = KeyBindingHelper.registerKeyBinding(
				new KeyMapping("key.suchki.multicast", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, category));
		ClientTickEvents.END_CLIENT_TICK.register(MagicKeys::tick);
	}

	private static void tick(Minecraft mc) {
		if (multicastKey == null) return;
		while (multicastKey.consumeClick()) {
			LocalPlayer p = mc.player;
			if (p != null && mc.level != null && mc.screen == null && Magic.isMage(p)) cycleMulticast(p);
		}
	}

	/** Текущее число копий у своего игрока (с учётом ещё не подтверждённого переключения). */
	public static int currentMulticast(LocalPlayer p) {
		int max = Magic.maxMulticast(p);
		int cur = pending > 0 && p.level().getGameTime() - pendingAt < 20 ? pending : Magic.data(p).multicast();
		return Math.max(1, Math.min(cur, max));
	}

	/** Следующее значение мультикаста по кругу. */
	public static int next(int current, int max) {
		return max <= 1 ? 1 : current % max + 1;
	}

	/** Переключить мультикаст: пакет серверу, всплывашка и звук. */
	public static void cycleMulticast(LocalPlayer p) {
		int max = Magic.maxMulticast(p);
		if (max <= 1) {
			MagicHud.toast(Component.translatable("suchki.multicast.toast", 1), Component.translatable("suchki.multicast.locked", 14), 0x9A9AB0, 1, 1);
			p.playSound(MagicSounds.MULTICAST, 0.35F, 0.7F);
			return;
		}
		if (CastAnim.compute(p, 0) instanceof CastAnim.Pose pose && pose.casting()) {
			// Посреди каста число копий уже зафиксировано.
			return;
		}
		int n = next(currentMulticast(p), max);
		pending = n;
		pendingAt = p.level().getGameTime();
		ClientPlayNetworking.send(new FxNet.SetMulticast(n));
		Spell spell = Magic.data(p).selectedSpell();
		Component sub = null;
		if (spell != null && spell.maxCopies < n) {
			sub = Component.translatable("suchki.multicast.spell_limit", spell.displayName(), spell.maxCopies);
		}
		int color = spell != null ? spell.lightColor() : 0xFFD0F0;
		MagicHud.toast(Component.translatable("suchki.multicast.toast", n), sub, color, n, max);
		p.playSound(MagicSounds.MULTICAST, 0.6F, 0.85F + 0.12F * (n - 1));
	}
}
