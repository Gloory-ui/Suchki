package com.suchki.magic.terrain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.suchki.magic.terrain.MagicTerrain.Policy;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class MagicTerrainPolicyTest {
	@Test
	void naturalOnlyNeverCreatesSpreadingFire() {
		Policy safe = new Policy(true, true, true, 256, 1_200.0F, 0.0F);
		BlockPos pos = new BlockPos(17, 64, -9);
		for (long seed = 0; seed < 10_000; seed++) {
			assertFalse(MagicTerrain.shouldPlaceFire(safe, seed, pos));
		}
	}

	@Test
	void explicitlyDestructivePolicyCanStillCreateFire() {
		Policy full = new Policy(true, true, false, 256, 1_200.0F, 0.0F);
		BlockPos pos = new BlockPos(17, 64, -9);
		boolean foundIgnitingSeed = false;
		for (long seed = 0; seed < 10_000 && !foundIgnitingSeed; seed++) {
			foundIgnitingSeed = MagicTerrain.shouldPlaceFire(full, seed, pos);
		}
		assertTrue(foundIgnitingSeed);
	}
}
