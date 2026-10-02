package com.suchki;

import com.suchki.enchant.Perks;
import com.suchki.potion.SuchkiPotions;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Suchki implements ModInitializer {
	public static final String MOD_ID = "suchki";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		SuchkiConfig.load();
		SuchkiPotions.init();
		Perks.register();
		LOGGER.info("Suchki загружен");
	}
}
