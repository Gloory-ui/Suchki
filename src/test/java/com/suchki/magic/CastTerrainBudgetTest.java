package com.suchki.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CastTerrainBudgetTest {
	@Test
	void delayedWorkAndProjectilesShareOneCastBudget() {
		Cast original = new Cast(null, null, null, 1.0F, 1.0F);
		Cast delayed = new Cast(null, null, null, 1.0F, 1.0F, original.terrainBudget());

		assertEquals(100, original.terrainBlocksRemaining(100));
		original.accountTerrainBlocks(37);
		assertEquals(63, delayed.terrainBlocksRemaining(100));
		delayed.accountTerrainBlocks(80);
		assertEquals(0, original.terrainBlocksRemaining(100));
	}
}
