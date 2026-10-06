package com.suchki.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Галерея магических кругов: каждая стихия и ранг на разных стадиях рисования, эффекты в мире. */
public class CircleGalleryTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!TestFilter.enabled("circles")) return;
	}
}
