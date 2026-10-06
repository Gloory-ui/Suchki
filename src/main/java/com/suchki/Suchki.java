package com.suchki;

import com.suchki.enchant.Perks;
import com.suchki.potion.SuchkiPotions;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Suchki implements ModInitializer {
	public static final String MOD_ID = "suchki";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		SuchkiConfig.load();
		SuchkiPotions.init();
		com.suchki.potion.StrongPotions.init();
		Perks.register();
		com.suchki.skill.Skills.init();
		com.suchki.magic.Magic.init();
		com.suchki.enchant.AreaBreaking.register();
		com.suchki.enchant.SpringJump.register();
		com.suchki.enchant.LibrarianBooks.register();
		if (Boolean.getBoolean("suchki.audit")) {
			com.suchki.dev.DevTests.register();
			ServerLifecycleEvents.SERVER_STARTED.register(server -> {
				MixinEnvironment.getCurrentEnvironment().audit();
				LOGGER.info("Suchki: проверка миксинов пройдена");
			});
		}
		LOGGER.info("Suchki загружен");
	}
}
