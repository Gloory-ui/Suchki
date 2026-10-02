package com.suchki.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.suchki.Suchki;
import com.suchki.skill.Skills;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** Клавиша K — окно навыков; без роли — обязательное окно выбора роли. */
final class SkillsClient {
	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Suchki.MOD_ID, "main"));
	private static KeyMapping openSkills;

	private SkillsClient() {}

	static void register() {
		openSkills = KeyBindingHelper.registerKeyBinding(
				new KeyMapping("key.suchki.skills", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(SkillsClient::tick);
	}

	private static void tick(Minecraft mc) {
		if (mc.player == null) return;
		// Даём пару секунд, чтобы данные о роли успели прийти с сервера.
		if (mc.screen == null && mc.player.tickCount > 40 && Skills.get(mc.player).active().isEmpty()) {
			mc.setScreen(new RoleSelectScreen(true, null));
			return;
		}
		while (openSkills.consumeClick()) {
			if (mc.screen == null) mc.setScreen(new SkillTreeScreen());
		}
	}
}
