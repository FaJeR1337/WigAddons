package com.wiggox.wigaddons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class WanderTest {
	@Test
	void picksOpenGroundWithinTheRadius() {
		ObstacleAvoider.Terrain flat = (x, y, z) -> y <= -61;
		Random random = new Random(4);
		for (int i = 0; i < 100; i++) {
			double[] spot = Wander.pick(flat, 0.5, -60, 0.5, 8, 3, random);
			assertNotNull(spot);
			double d = Math.hypot(spot[0] - 0.5, spot[2] - 0.5);
			assertTrue(d >= 2.0 && d <= 9.0, "distance " + d);
			assertEquals(-60, spot[1], 1e-9);
		}
	}

	@Test
	void givesUpWhereThereIsNothingToStandOn() {
		assertNull(Wander.pick((x, y, z) -> false, 0, 0, 0, 8, 3, new Random(1)));
		assertNull(Wander.pick((x, y, z) -> y <= -61, 0, -60, 0, 0, 3, new Random(1)));
	}

	@Test
	void doesNotPickInsideBlocks() {
		// a solid ring of pillars everywhere except one direction: whatever is picked must be standable
		ObstacleAvoider.Terrain pillars = (x, y, z) -> y <= -61 || (y <= -59 && x % 2 == 0);
		Random random = new Random(2);
		for (int i = 0; i < 50; i++) {
			double[] spot = Wander.pick(pillars, 0.5, -60, 0.5, 8, 3, random);
			if (spot != null) {
				assertTrue(((int) Math.floor(spot[0])) % 2 != 0);
			}
		}
	}
}
