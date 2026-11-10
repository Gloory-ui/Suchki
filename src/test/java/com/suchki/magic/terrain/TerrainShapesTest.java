package com.suchki.magic.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class TerrainShapesTest {
	@Test
	void craterIsDeterministicUniqueCenteredAndBounded() {
		Vec3 center = new Vec3(10.4, 64.0, -3.2);
		List<BlockPos> first = TerrainShapes.crater(center, 8.0, 6.0, 42L, 700);
		List<BlockPos> second = TerrainShapes.crater(center, 8.0, 6.0, 42L, 700);

		assertEquals(first, second);
		assertEquals(first.size(), new HashSet<>(first).size());
		assertTrue(first.size() <= 700);
		assertTrue(first.contains(BlockPos.containing(center)));
	}

	@Test
	void everyShapeHonorsItsHardLimit() {
		assertEquals(37, TerrainShapes.tunnel(Vec3.ZERO, new Vec3(96, 0, 0), 6.0, 37).size());
		assertEquals(41, TerrainShapes.fissure(Vec3.ZERO, new Vec3(0, 0, 1), 96, 3, 14, 9L, 41).size());
		assertEquals(23, TerrainShapes.surfaceTrail(Vec3.ZERO, new Vec3(96, 0, 0), 4, 11L, 23).size());
	}

	@Test
	void zeroBudgetNeverProducesWork() {
		assertTrue(TerrainShapes.crater(Vec3.ZERO, 5, 5, 1L, 0).isEmpty());
		assertTrue(TerrainShapes.tunnel(Vec3.ZERO, new Vec3(5, 0, 0), 1, 0).isEmpty());
		assertTrue(TerrainShapes.fissure(Vec3.ZERO, new Vec3(1, 0, 0), 5, 1, 3, 1L, 0).isEmpty());
		assertTrue(TerrainShapes.surfaceTrail(Vec3.ZERO, new Vec3(5, 0, 0), 1, 1L, 0).isEmpty());
	}

	@Test
	void extremeInputsStayInsideGlobalGeometryCaps() {
		List<BlockPos> crater = TerrainShapes.crater(Vec3.ZERO, 10_000, 10_000, 5L, 500);
		List<BlockPos> tunnel = TerrainShapes.tunnel(Vec3.ZERO, new Vec3(10_000, 0, 0), 100, 500);
		assertFalse(crater.isEmpty());
		assertFalse(tunnel.isEmpty());
		assertTrue(crater.size() <= 500);
		assertTrue(tunnel.size() <= 500);
		assertTrue(tunnel.stream().allMatch(p -> p.getX() <= TerrainShapes.MAX_LENGTH + TerrainShapes.MAX_TUNNEL_RADIUS + 1));
	}

	@Test
	void fissureOnlyDescendsFromItsOriginPlane() {
		int originY = 80;
		List<BlockPos> fissure = TerrainShapes.fissure(new Vec3(0, originY, 0), new Vec3(1, 0, 1), 20, 2, 7, 123L, 2_000);
		assertFalse(fissure.isEmpty());
		assertTrue(fissure.stream().allMatch(p -> p.getY() <= originY && p.getY() >= originY - 6));
	}
}
