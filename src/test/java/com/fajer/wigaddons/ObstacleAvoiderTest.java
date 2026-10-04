package com.fajer.wigaddons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class ObstacleAvoiderTest {
	private static final double SPEED = 0.28;
	private static final double HALF = 0.3;
	private static final int FEET = 64;

	/** A cell in the test world. */
	private record Cell(int x, int y, int z) {
	}

	private static Predicate<Cell> wall(int x0, int x1, int z, int y0, int y1) {
		return c -> c.x() >= x0 && c.x() <= x1 && c.z() == z && c.y() >= y0 && c.y() <= y1;
	}

	private static Predicate<Cell> any(List<Predicate<Cell>> parts) {
		return c -> parts.stream().anyMatch(p -> p.test(c));
	}

	private static boolean walkable(Predicate<Cell> solid, double x, double z) {
		for (double dx : new double[] {-HALF, HALF}) {
			for (double dz : new double[] {-HALF, HALF}) {
				int cx = (int) Math.floor(x + dx);
				int cz = (int) Math.floor(z + dz);
				boolean feet = solid.test(new Cell(cx, FEET, cz));
				boolean head = solid.test(new Cell(cx, FEET + 1, cz));
				boolean ceiling = solid.test(new Cell(cx, FEET + 2, cz));
				boolean free = !feet && !head;
				boolean step = feet && !head && !ceiling; // jumped onto
				if (!free && !step) {
					return false;
				}
			}
		}
		return true;
	}

	private record Result(boolean finished, boolean gaveUp, boolean everStrafed, int firstStrafe, double maxSideways, int ticks) {
	}

	/** Walks the route against the given solid cells, with the avoider steering. */
	private static Result walk(Predicate<Cell> solid, List<Waypoint> route, double startX, double startZ, long seed) {
		return walk(solid, route, startX, startZ, seed, true);
	}

	private static Result walk(Predicate<Cell> solid, List<Waypoint> route, double startX, double startZ, long seed, boolean avoid) {
		WigConfig cfg = new WigConfig();
		cfg.humanizeOffset = 0;
		cfg.stuckTimeoutSeconds = 60; // this test is about the avoider, not the stuck stop
		RouteFollower follower = new RouteFollower(route, false, 1, cfg, new Random(seed));
		ObstacleAvoider avoider = new ObstacleAvoider((x, y, z) -> solid.test(new Cell(x, y, z)), new Random(seed));
		double x = startX;
		double z = startZ;
		double yaw = 0;
		boolean strafed = false;
		int firstStrafe = 0;
		double maxSideways = 0;
		for (int tick = 0; tick < 1500; tick++) {
			RouteFollower.Output out = follower.tick(x, z, true, false);
			if (out.status() == RouteFollower.Status.FINISHED) {
				return new Result(true, false, strafed, firstStrafe, maxSideways, tick);
			}
			yaw = RouteFollower.stepYaw(yaw, out.aimYaw(), cfg.turnSpeedDegPerTick);
			double fx = -Math.sin(Math.toRadians(yaw));
			double fz = Math.cos(Math.toRadians(yaw));
			double lx = Math.cos(Math.toRadians(yaw));
			double lz = Math.sin(Math.toRadians(yaw));
			ObstacleAvoider.Output around = avoid
				? avoider.tick(x, FEET, z, yaw, out.aimX(), out.aimZ(), pressed(solid, x, z, fx, fz))
				: new ObstacleAvoider.Output(0, false);
			if (around.gaveUp()) {
				return new Result(false, true, strafed, firstStrafe, maxSideways, tick);
			}
			int strafe = around.strafe();
			if (strafe != 0 && !strafed) {
				strafed = true;
				firstStrafe = strafe;
			}
			// A/D plus forward, with the move vector normalised like the game does
			double mx = fx + lx * strafe;
			double mz = fz + lz * strafe;
			double len = Math.hypot(mx, mz);
			double stepX = mx / len * SPEED;
			double stepZ = mz / len * SPEED;
			if (walkable(solid, x + stepX, z)) {
				x += stepX;
			}
			if (walkable(solid, x, z + stepZ)) {
				z += stepZ;
			}
			maxSideways = Math.max(maxSideways, Math.abs(x - startX));
		}
		return new Result(false, false, strafed, firstStrafe, maxSideways, 1500);
	}

	/** True when the next step straight ahead would be blocked. */
	private static boolean pressed(Predicate<Cell> solid, double x, double z, double fx, double fz) {
		return !walkable(solid, x + fx * SPEED, z + fz * SPEED);
	}

	private static final List<Waypoint> AHEAD = List.of(new Waypoint(0, FEET, 24));

	@Test
	void goesRoundAOneBlockPillarWithOnlyASmallSidestep() {
		Result r = walk(wall(0, 0, 8, FEET, FEET + 1), AHEAD, 0.5, 0.5, 1);
		assertTrue(r.finished(), "did not finish");
		assertTrue(r.everStrafed());
		// one block wide: out of the way of that block, not a long detour
		assertTrue(r.maxSideways() < 2.5, "went " + r.maxSideways() + " blocks sideways");
	}

	@Test
	void withoutTheAvoiderThePillarStopsTheWalkerDead() {
		Result r = walk(wall(0, 0, 8, FEET, FEET + 1), AHEAD, 0.5, 0.5, 1, false);
		assertFalse(r.finished());
	}

	@Test
	void goesRoundAWiderWall() {
		Result r = walk(wall(-2, 2, 8, FEET, FEET + 2), AHEAD, 0.5, 0.5, 2);
		assertTrue(r.finished());
		assertTrue(r.everStrafed());
	}

	@Test
	void worksFromManyStartsAndRandomTimings() {
		for (long seed = 0; seed < 40; seed++) {
			Result r = walk(wall(-1, 1, 8, FEET, FEET + 1), AHEAD, 0.5 + (seed % 5) * 0.1, 0.5, seed);
			assertTrue(r.finished(), "seed " + seed);
		}
	}

	@Test
	void jumpsOverAOneBlockStepWithoutSidestepping() {
		Result r = walk(wall(0, 0, 8, FEET, FEET), AHEAD, 0.5, 0.5, 3);
		assertTrue(r.finished());
		assertFalse(r.everStrafed());
	}

	@Test
	void sidestepsAStepThatHasALowCeiling() {
		Predicate<Cell> world = any(List.of(wall(0, 0, 8, FEET, FEET), wall(0, 0, 8, FEET + 2, FEET + 2)));
		Result r = walk(world, AHEAD, 0.5, 0.5, 4);
		assertTrue(r.finished());
		assertTrue(r.everStrafed());
	}

	@Test
	void givesUpWhenTheWallIsFarTooWide() {
		Result r = walk(wall(-40, 40, 8, FEET, FEET + 2), AHEAD, 0.5, 0.5, 5);
		assertTrue(r.gaveUp());
		assertFalse(r.finished());
	}

	@Test
	void usesTheOpenSideWhenOnlyOneIs() {
		// the player faces south, so their left (A) is +x: a long wall there leaves only the right (D) open
		Predicate<Cell> world = any(List.of(wall(0, 0, 8, FEET, FEET + 1), wall(1, 1, 0, FEET, FEET + 1), wall(1, 1, 1, FEET, FEET + 1)));
		// blocks beside the start in +x: a column of cells at x=1 for z=0..6
		Predicate<Cell> column = c -> c.x() == 1 && c.z() >= 0 && c.z() <= 6 && c.y() >= FEET && c.y() <= FEET + 1;
		Result r = walk(any(List.of(world, column)), AHEAD, 0.5, 0.5, 6);
		assertTrue(r.finished());
		assertEquals(-1, r.firstStrafe());
	}

	@Test
	void leansTowardTheSideOfTheWaypoint() {
		// the waypoint is well to the left of the line through the pillar: go round on the left (A)
		List<Waypoint> route = List.of(new Waypoint(6, FEET, 24));
		Result r = walk(wall(0, 1, 6, FEET, FEET + 1), route, 0.5, 0.5, 7);
		assertTrue(r.finished());
		assertEquals(1, r.firstStrafe());
	}

	@Test
	void ignoresAnObstacleBeyondTheWaypoint() {
		Result r = walk(wall(-3, 3, 8, FEET, FEET + 2), List.of(new Waypoint(0, FEET, 5)), 0.5, 0.5, 8);
		assertTrue(r.finished());
		assertFalse(r.everStrafed());
	}

	@Test
	void doesNothingInOpenGround() {
		Result r = walk(c -> false, AHEAD, 0.5, 0.5, 9);
		assertTrue(r.finished());
		assertFalse(r.everStrafed());
	}
}
