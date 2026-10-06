package com.suchki.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Галерея интерфейса магии: фразы заклинаний, колесо, мультикаст, полоса маны. */
public class HudGalleryTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!TestFilter.enabled("hud")) return;
	}
}
