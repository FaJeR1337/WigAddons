package com.wiggox.wigaddons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TeleportPathTest {
	private static final TeleportPath.Air OPEN = (x, y, z) -> true;
	/** Open air above a floor: everything below y 60 is solid, so a landing near y 62 has ground within three blocks under it. */
	private static final TeleportPath.Air FLOOR = (x, y, z) -> y >= 60;

	private static double sideways(double[] a, double[] b) {
		return Math.hypot(a[0] - b[0], a[2] - b[2]);
	}

	@Test
	void openAirIsFullHopsStraightAtTheGoalUntilWithinTheRadius() {
		double[] goal = {100, 60, 0};
		List<double[]> plan = TeleportPath.plan(new double[] {0, 60, 0}, goal, 12, 10, OPEN);
		assertNotNull(plan);
		assertTrue(plan.size() >= 7 && plan.size() <= 9, "hops: " + plan.size());
		double[] previous = {0, 60, 0};
		for (double[] point : plan) {
			double length = Math.sqrt(Math.pow(point[0] - previous[0], 2) + Math.pow(point[1] - previous[1], 2) + Math.pow(point[2] - previous[2], 2));
			assertEquals(12.0, length, 1.3); // the landing is the middle of a block, up to a block from where the full hop ends
			assertEquals(0.5, point[0] - Math.floor(point[0]), 1e-9);
			assertEquals(0.5, point[2] - Math.floor(point[2]), 1e-9);
			assertEquals(0.0, point[1] - Math.floor(point[1]), 1e-9);
			previous = point;
		}
		assertTrue(sideways(previous, goal) <= 10);
	}

	@Test
	void alreadyWithinTheRadiusNeedsNoHop() {
		assertTrue(TeleportPath.plan(new double[] {0, 60, 0}, new double[] {5, 60, 0}, 12, 10, OPEN).isEmpty());
	}

	@Test
	void tooNearForAHopToHelpIsLeftToWalking() {
		// 5 blocks away with a 12 block hop: a hop would land 7 blocks past it, further than the radius of 3
		assertTrue(TeleportPath.plan(new double[] {0, 60, 0}, new double[] {5, 60, 0}, 12, 3, OPEN).isEmpty());
	}

	@Test
	void aHopThatCarriesPastTheGoalButLandsWithinTheRadiusIsMade() {
		List<double[]> plan = TeleportPath.plan(new double[] {0, 60, 0}, new double[] {11, 60, 0}, 12, 10, OPEN);
		assertEquals(1, plan.size());
	}

	@Test
	void aWallIsGoneAroundOrOver() {
		// a wall across x = 30, from y 50 to 69 and 30 wide in z
		TeleportPath.Air wall = (x, y, z) -> !(x >= 30 && x <= 31 && y < 70 && y >= 50 && Math.abs(z) < 15);
		List<double[]> plan = TeleportPath.plan(new double[] {0, 60, 0}, new double[] {100, 60, 0}, 12, 10, wall);
		assertNotNull(plan);
		double[] previous = {0, 60, 0};
		for (double[] point : plan) {
			assertTrue(TeleportPath.clearLine(previous, point, wall));
			previous = point;
		}
		assertTrue(sideways(previous, new double[] {100, 60, 0}) <= 10);
	}

	@Test
	void aRouteAroundAWallBendsGently() {
		TeleportPath.Air wall = (x, y, z) -> !(x >= 30 && x <= 31 && y < 70 && y >= 50 && Math.abs(z) < 15);
		List<double[]> plan = TeleportPath.plan(new double[] {0, 60, 0}, new double[] {100, 60, 0}, 12, 10, wall);
		assertNotNull(plan);
		for (int i = 1; i < plan.size(); i++) {
			double[] a = plan.get(i - 1);
			double[] b = plan.get(i);
			double dot = a[3] * b[3] + a[4] * b[4] + a[5] * b[5];
			assertTrue(Math.toDegrees(Math.acos(Math.min(1, dot))) <= 30.5, "turn at hop " + i);
		}
	}

	@Test
	void hopsAlongAWalkingRouteLandOnIt() {
		// a walking route that is a straight line along x, one cell every block
		List<double[]> guide = new java.util.ArrayList<>();
		for (int x = 1; x <= 60; x++) {
			guide.add(new double[] {x + 0.5, 60, 0.5});
		}
		List<double[]> hops = TeleportPath.planAlong(new double[] {0.5, 60, 0.5}, guide, new double[] {60.5, 60, 0.5}, 12, 10, FLOOR);
		assertTrue(hops.size() >= 4, "hops: " + hops.size());
		for (double[] hop : hops) {
			assertEquals(62.0, hop[1], 1e-9); // exactly the cell two blocks above the route
			assertEquals(0.5, hop[2], 1e-9);
		}
		assertTrue(Math.hypot(60.5 - hops.get(hops.size() - 1)[0], 0) <= 10);
	}

	@Test
	void straightOnlyHopsSkipOnlyStraightStretches() {
		List<double[]> bent = new java.util.ArrayList<>();
		for (int x = 1; x <= 8; x++) {
			bent.add(new double[] {x + 0.5, 60, 0.5});
		}
		for (int z = 1; z <= 20; z++) {
			bent.add(new double[] {8.5, 60, z + 0.5}); // a right angle after eight blocks
		}
		double[] goal = {8.5, 60, 20.5};
		assertTrue(TeleportPath.planAlong(new double[] {0.5, 60, 0.5}, bent, goal, 12, 3, FLOOR, true).isEmpty());
		assertTrue(!TeleportPath.planAlong(new double[] {0.5, 60, 0.5}, bent, goal, 12, 3, FLOOR, false).isEmpty());
	}

	@Test
	void theFirstHopCanBeMadeFromAnyDistanceAlongTheRoute() {
		List<double[]> guide = new java.util.ArrayList<>();
		for (int x = 1; x <= 40; x++) {
			guide.add(new double[] {x + 0.5, 60, 0.5});
		}
		double[] goal = {40.5, 60, 0.5};
		// the route's cells are 1 to 40 blocks away, but the hop is 12: strictly one hop away needs a cell at about 12, and there is one
		assertTrue(!TeleportPath.planAlong(new double[] {0.5, 60, 0.5}, guide, goal, 12, 3, FLOOR, false, true).isEmpty());
		// a route that begins 8 blocks off (nearer than a hop): only the relaxed first hop gets going
		List<double[]> near = new java.util.ArrayList<>();
		for (int x = 1; x <= 8; x++) {
			near.add(new double[] {x + 0.5, 60, 0.5});
		}
		double[] nearGoal = {8.5, 60, 0.5};
		assertTrue(TeleportPath.planAlong(new double[] {0.5, 60, 0.5}, near, nearGoal, 12, 1, FLOOR, false, false).isEmpty());
		assertTrue(!TeleportPath.planAlong(new double[] {0.5, 60, 0.5}, near, nearGoal, 12, 1, FLOOR, false, true).isEmpty());
	}

	@Test
	void hopsAlongARouteLandOnlyWithGroundWithinThreeBlocksBelow() {
		List<double[]> guide = new java.util.ArrayList<>();
		for (int x = 1; x <= 40; x++) {
			guide.add(new double[] {x + 0.5, 60, 0.5});
		}
		double[] goal = {40.5, 60, 0.5};
		// solid ground only at and below y 55: the landing around y 62 has nothing within three blocks under it
		TeleportPath.Air highUp = (x, y, z) -> y > 55;
		assertTrue(TeleportPath.planAlong(new double[] {0.5, 60, 0.5}, guide, goal, 12, 3, highUp).isEmpty());
		TeleportPath.Air nearGround = (x, y, z) -> y > 59;
		assertTrue(!TeleportPath.planAlong(new double[] {0.5, 60, 0.5}, guide, goal, 12, 3, nearGround).isEmpty());
	}

	@Test
	void aHopIsOnlyClearWhereNothingGetsInTheWay() {
		TeleportPath.Air wall = (x, y, z) -> !(x == 6 && y >= 59 && y <= 62);
		assertTrue(TeleportPath.hopClear(new double[] {0.5, 60, 0.5}, new double[] {0, 0, 1}, 12, wall));
		assertTrue(!TeleportPath.hopClear(new double[] {0.5, 60, 0.5}, new double[] {1, 0, 0}, 12, wall));
	}

	@Test
	void aGentlePlanIsFoundInOpenAirButNotInASealedBox() {
		assertNotNull(TeleportPath.planGentle(new double[] {0, 60, 0}, new double[] {100, 60, 0}, 12, 10, OPEN));
		TeleportPath.Air box = (x, y, z) -> Math.abs(x) <= 3 && Math.abs(z) <= 3 && y >= 58 && y <= 64;
		assertNull(TeleportPath.planGentle(new double[] {0, 60, 0}, new double[] {100, 60, 0}, 12, 10, box));
	}

	@Test
	void aSealedBoxHasNoPath() {
		TeleportPath.Air box = (x, y, z) -> Math.abs(x) <= 3 && Math.abs(z) <= 3 && y >= 58 && y <= 64;
		assertNull(TeleportPath.plan(new double[] {0, 60, 0}, new double[] {100, 60, 0}, 12, 10, box));
	}
}
