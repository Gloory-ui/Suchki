package com.suchki.client;

import com.suchki.skill.SkillEffects;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.entity.Entity;

/**
 * Охотник «Чуткий слух»: мобов из списка сервера подсвечиваем только у себя (миксин HunterGlowMixin).
 * Сервер шлёт список раз в 2 с; без обновлений (навык снят, роль сменена) подсветка гаснет через 3 с.
 */
public final class HunterSenseClient {
	private static final int TTL_TICKS = 60;
	private static final IntSet SENSED = new IntOpenHashSet();
	private static int ttl;

	private HunterSenseClient() {}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(SkillEffects.HunterSense.TYPE, (payload, context) -> {
			SENSED.clear();
			SENSED.addAll(payload.ids());
			ttl = TTL_TICKS;
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (ttl > 0) ttl--;
			if (ttl == 0 || mc.level == null) SENSED.clear();
		});
	}

	public static boolean isSensed(Entity entity) {
		return !SENSED.isEmpty() && SENSED.contains(entity.getId());
	}
}
