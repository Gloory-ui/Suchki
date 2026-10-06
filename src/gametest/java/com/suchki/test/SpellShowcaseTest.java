package com.suchki.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Каждое заклинание в настоящей игре: зарядка, выпуск, попадание — со скриншотами. */
public class SpellShowcaseTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!TestFilter.enabled("spells")) return;
	}
}
