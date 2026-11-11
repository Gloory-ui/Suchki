package com.suchki.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.UUID;
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

	@Test
	void independentlyRestoredProjectilesRejoinOneBudget() {
		TerrainBudgetLedger ledger = new TerrainBudgetLedger();
		Cast original = new Cast(null, null, null, 1.0F, 1.0F);
		Cast.TerrainBudget first = ledger.bind(original.terrainBudget(), 100);
		UUID id = first.id;
		Cast.TerrainBudget second = ledger.restore(id, 37, 101);
		assertSame(first, second);

		Cast fromFirstProjectile = new Cast(null, null, null, 1.0F, 1.0F, first);
		Cast fromSecondProjectile = new Cast(null, null, null, 1.0F, 1.0F, second);
		fromFirstProjectile.accountTerrainBlocks(20);
		assertEquals(43, fromSecondProjectile.terrainBlocksRemaining(100));

		ledger.record(first, 102);
		Cast.TerrainBudget laterChunk = ledger.restore(id, 80, 103);
		assertSame(first, laterChunk);
		assertEquals(20, fromFirstProjectile.terrainBlocksRemaining(100));
	}

	@Test
	void persistedMaximumSurvivesRestartAndStaleEntityNbt() {
		TerrainBudgetLedger beforeRestart = new TerrainBudgetLedger();
		Cast original = new Cast(null, null, null, 1.0F, 1.0F);
		Cast.TerrainBudget budget = beforeRestart.bind(original.terrainBudget(), 200);
		budget.used = 200;
		beforeRestart.record(budget, 201);

		TerrainBudgetLedger afterRestart = new TerrainBudgetLedger(beforeRestart.snapshot());
		Cast.TerrainBudget restoredFromStaleNbt = afterRestart.restore(budget.id, 100, 202);
		assertEquals(0, new Cast(null, null, null, 1.0F, 1.0F, restoredFromStaleNbt).terrainBlocksRemaining(200));
	}

	@Test
	void expiredLedgerEntryFailsClosedInsteadOfRestoringAllowance() {
		TerrainBudgetLedger ledger = new TerrainBudgetLedger();
		Cast original = new Cast(null, null, null, 1.0F, 1.0F);
		Cast.TerrainBudget budget = ledger.bind(original.terrainBudget(), 0);
		ledger.prune(TerrainBudgetLedger.RETENTION_TICKS + 1);

		Cast.TerrainBudget restored = ledger.restore(budget.id, 0, TerrainBudgetLedger.RETENTION_TICKS + 2);
		assertEquals(0, new Cast(null, null, null, 1.0F, 1.0F, restored).terrainBlocksRemaining(6_000));
	}

	@Test
	void legacyProjectileBudgetFailsClosed() {
		Cast restored = new Cast(null, null, null, 1.0F, 1.0F, Cast.exhaustedTerrainBudget());
		assertEquals(0, restored.terrainBlocksRemaining(6_000));
	}
}
