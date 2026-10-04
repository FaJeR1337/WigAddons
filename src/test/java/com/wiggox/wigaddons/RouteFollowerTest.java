package com.wiggox.wigaddons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RouteFollowerTest {
	/** Sprint speed in blocks per tick. */
	private static final double SPEED = 0.28;

	/** Exact block centres unless a test asks for the random offset. */
	private static WigConfig config() {
		WigConfig cfg = new WigConfig();
		cfg.humanizeOffset = 0;
		return cfg;
	}

	private static RouteFollower follower(List<Waypoint> route, boolean loop, double radius) {
		return new RouteFollower(route, loop, radius, config());
	}

	@Test
	void yawConventionMatchesMinecraft() {
		assertEquals(0, RouteFollower.yawTo(0, 1), 1e-9);
		assertEquals(90, RouteFollower.yawTo(-1, 0), 1e-9);
		assertEquals(-90, RouteFollower.yawTo(1, 0), 1e-9);
		assertEquals(180, Math.abs(RouteFollower.yawTo(0, -1)), 1e-9);
	}

	@Test
	void stepYawTurnsTheShortWayAndNoFasterThanTheLimit() {
		assertEquals(10, RouteFollower.stepYaw(0, 90, 10), 1e-9);
		assertEquals(5, RouteFollower.stepYaw(0, 5, 10), 1e-9);
		// from 350 the short way to 90 is +100, so it wraps up through 360 rather than back down
		assertEquals(360, RouteFollower.stepYaw(350, 90, 10), 1e-9);
		assertEquals(-10, RouteFollower.stepYaw(0, -90, 10), 1e-9);
	}

	@Test
	void aimsStraightAtTheWaypointUntilWithinTheRadius() {
		// waypoint 0 is straight ahead (south), waypoint 1 is far to the west of it
		RouteFollower f = follower(List.of(new Waypoint(0, 64, 10), new Waypoint(-30, 64, 10)), false, 2);
		assertEquals(0, f.tick(0.5, 0.5, true, false).aimYaw(), 1e-9);
		// 2.5 blocks away is outside the radius of 2: still aiming at waypoint 0
		RouteFollower.Output out = f.tick(0.5, 8.0, true, false);
		assertEquals(0, out.aimYaw(), 1e-9);
		assertEquals(0, out.blend(), 1e-9);
	}

	@Test
	void theAimSlidesTowardTheNextWaypointInsideTheRadius() {
		RouteFollower f = follower(List.of(new Waypoint(0, 64, 10), new Waypoint(-30, 64, 10)), false, 2);
		// 1.5 blocks short of waypoint 0: a quarter of the way along
		RouteFollower.Output a = f.tick(0.5, 9.0, true, false);
		assertEquals(0.25, a.blend(), 1e-9);
		// 0.5 blocks short: three quarters of the way along, so aiming further toward the west
		RouteFollower.Output b = f.tick(0.5, 10.0, true, false);
		assertEquals(0.75, b.blend(), 1e-9);
		assertTrue(a.aimYaw() > 0 && b.aimYaw() > a.aimYaw() && b.aimYaw() < 90);
	}

	@Test
	void radiusZeroSwitchesOnlyAtTheMiddleOfTheBlock() {
		RouteFollower f = follower(List.of(new Waypoint(0, 64, 10), new Waypoint(30, 64, 10)), false, 0);
		f.tick(0.5, 9.0, true, false); // 1.5 blocks away
		assertEquals(0, f.targetIndex());
		f.tick(0.5, 9.4, true, false); // 1.1 blocks away
		assertEquals(0, f.targetIndex());
		f.tick(0.5, 10.4, true, false); // 0.1 blocks away
		assertEquals(1, f.targetIndex());
	}

	@Test
	void movesOnOncePastTheWaypointEvenIfNotAtItsMiddle() {
		RouteFollower f = follower(List.of(new Waypoint(0, 64, 10), new Waypoint(20, 64, 10)), false, 2);
		f.tick(0.5, 0.5, true, false);
		// 1.5 blocks to the side of the waypoint, but already level with it along the way it was approached
		f.tick(2.0, 10.6, true, false);
		assertEquals(1, f.targetIndex());
	}

	@Test
	void keepsRunningThroughAWaypointWithoutStopping() {
		RouteFollower f = follower(List.of(new Waypoint(0, 64, 5), new Waypoint(0, 64, 20)), false, 1);
		f.tick(0.5, 0.5, true, false);
		RouteFollower.Output out = f.tick(0.5, 5.4, true, false);
		assertEquals(RouteFollower.Status.RUNNING, out.status());
		assertEquals(1, f.targetIndex());
	}

	@Test
	void finishesAtTheLastWaypointWhenNotLooping() {
		RouteFollower f = follower(List.of(new Waypoint(0, 64, 10)), false, 1);
		f.tick(0.5, 0.5, true, false);
		assertEquals(RouteFollower.Status.FINISHED, f.tick(0.5, 9.5, true, false).status());
	}

	@Test
	void loopGoesBackToTheFirstWaypoint() {
		RouteFollower f = follower(List.of(new Waypoint(0, 64, 10), new Waypoint(0, 64, 20)), true, 1);
		f.tick(0.5, 0.5, true, false);
		f.tick(0.5, 10.4, true, false);
		assertEquals(RouteFollower.Status.RUNNING, f.tick(0.5, 20.4, true, false).status());
		assertEquals(0, f.targetIndex());
	}

	@Test
	void jumpsOnlyWhenBlockedOnTheGround() {
		RouteFollower f = follower(List.of(new Waypoint(0, 64, 30)), false, 1);
		assertTrue(f.tick(0.5, 0.5, true, true).jump());
		assertEquals(false, f.tick(0.5, 0.5, false, true).jump());
		assertEquals(false, f.tick(0.5, 0.5, true, false).jump());
	}

	@Test
	void reportsStuckWhenItBarelyMoves() {
		WigConfig cfg = config();
		cfg.stuckTimeoutSeconds = 1; // 20 ticks
		RouteFollower f = new RouteFollower(List.of(new Waypoint(0, 64, 30)), false, 1, cfg);
		RouteFollower.Output out = null;
		for (int i = 0; i < 25; i++) {
			out = f.tick(0.5, 0.5, true, false);
			if (out.status() == RouteFollower.Status.STUCK) {
				break;
			}
		}
		assertEquals(RouteFollower.Status.STUCK, out.status());
	}

	@Test
	void notStuckWhileMakingProgress() {
		WigConfig cfg = config();
		cfg.stuckTimeoutSeconds = 1;
		RouteFollower f = new RouteFollower(List.of(new Waypoint(0, 64, 100)), false, 1, cfg);
		for (int i = 0; i < 100; i++) {
			assertEquals(RouteFollower.Status.RUNNING, f.tick(0.5, 0.5 + i * 0.3, true, false).status());
		}
	}

	@Test
	void anEmptyRouteIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> follower(List.of(), false, 1));
	}

	/** Runs the follower against simple kinematics (constant speed, limited turning). Returns ticks used, or -1 if it never finished. */
	private static int simulate(List<Waypoint> route, double radius, double startX, double startZ, double startYaw, int maxTicks) {
		RouteFollower f = follower(route, false, radius);
		double x = startX;
		double z = startZ;
		double yaw = startYaw;
		for (int tick = 0; tick < maxTicks; tick++) {
			RouteFollower.Output out = f.tick(x, z, true, false);
			if (out.status() == RouteFollower.Status.FINISHED) {
				return tick;
			}
			yaw = RouteFollower.stepYaw(yaw, out.aimYaw(), config().turnSpeedDegPerTick);
			x += -Math.sin(Math.toRadians(yaw)) * SPEED;
			z += Math.cos(Math.toRadians(yaw)) * SPEED;
		}
		return -1;
	}

	@Test
	void doesNotOrbitAWaypointThatIsBesideThePlayer() {
		// the waypoint is 1.5 blocks to the side: inside the turning circle, so it can't be hit by turning toward it
		List<Waypoint> route = List.of(new Waypoint(-2, 64, 0), new Waypoint(-2, 64, 30));
		for (double radius : new double[] {0, 1, 2, 4}) {
			assertTrue(simulate(route, radius, 0.5, 0.5, 0, 400) > 0, "radius " + radius);
		}
	}

	@Test
	void doesNotOrbitWhenTheNextWaypointIsBehind() {
		List<Waypoint> route = List.of(new Waypoint(0, 64, 10), new Waypoint(0, 64, -10), new Waypoint(15, 64, -10));
		for (double radius : new double[] {0, 1, 2, 4}) {
			assertTrue(simulate(route, radius, 0.5, 0.5, 0, 800) > 0, "radius " + radius);
		}
	}

	@Test
	void runsASquareWithTightCornersWithoutCircling() {
		List<Waypoint> route = List.of(new Waypoint(10, 64, 0), new Waypoint(10, 64, 10), new Waypoint(0, 64, 10), new Waypoint(0, 64, 0));
		for (double radius : new double[] {0, 1, 2, 4}) {
			int ticks = simulate(route, radius, 0.5, 0.5, -90, 600);
			// the square is 40 blocks of walking, about 145 ticks; circling would blow far past this
			assertTrue(ticks > 0 && ticks < 300, "radius " + radius + " took " + ticks);
		}
	}

	@Test
	void closeTogetherWaypointsAreStillRunInOrder() {
		List<Waypoint> route = List.of(new Waypoint(0, 64, 4), new Waypoint(2, 64, 8), new Waypoint(0, 64, 12), new Waypoint(2, 64, 16));
		for (double radius : new double[] {0, 1, 2}) {
			int ticks = simulate(route, radius, 0.5, 0.5, 0, 600);
			assertTrue(ticks > 0 && ticks < 250, "radius " + radius + " took " + ticks);
		}
	}

	@Test
	void eachWaypointIsAimedAtARandomSpotInsideItsBlock() {
		WigConfig cfg = new WigConfig(); // default offset 0.4
		List<Waypoint> route = List.of(new Waypoint(0, 64, 10), new Waypoint(5, 64, 20), new Waypoint(9, 64, 30), new Waypoint(2, 64, 40));
		RouteFollower f = new RouteFollower(route, false, 1, cfg, new Random(7));
		boolean anyOffCentre = false;
		for (int i = 0; i < route.size(); i++) {
			double dx = f.aimX(i) - route.get(i).centerX();
			double dz = f.aimZ(i) - route.get(i).centerZ();
			assertTrue(Math.abs(dx) <= cfg.humanizeOffset && Math.abs(dz) <= cfg.humanizeOffset);
			anyOffCentre |= dx != 0 || dz != 0;
		}
		assertTrue(anyOffCentre);
	}

	@Test
	void radiusZeroAndOffsetZeroAimAtTheExactMiddle() {
		List<Waypoint> route = List.of(new Waypoint(3, 64, 10), new Waypoint(5, 64, 20));
		RouteFollower noRadius = new RouteFollower(route, false, 0, new WigConfig(), new Random(1));
		RouteFollower noOffset = new RouteFollower(route, false, 2, config(), new Random(1));
		for (RouteFollower f : List.of(noRadius, noOffset)) {
			for (int i = 0; i < route.size(); i++) {
				assertEquals(route.get(i).centerX(), f.aimX(i), 1e-12);
				assertEquals(route.get(i).centerZ(), f.aimZ(i), 1e-12);
			}
		}
	}

	@Test
	void aPassedWaypointGetsANewSpotForTheNextLap() {
		List<Waypoint> route = List.of(new Waypoint(0, 64, 10), new Waypoint(0, 64, 20));
		RouteFollower f = new RouteFollower(route, true, 1, new WigConfig(), new Random(3));
		double before = f.aimX(0);
		f.tick(0.5, 0.5, true, false);
		// walk up to waypoint 0 and through it
		f.tick(f.aimX(0), f.aimZ(0), true, false);
		assertEquals(1, f.targetIndex());
		assertTrue(f.aimX(0) != before);
	}

	@Test
	void stillFinishesRoutesWithTheRandomOffset() {
		WigConfig cfg = new WigConfig();
		List<Waypoint> square = List.of(new Waypoint(10, 64, 0), new Waypoint(10, 64, 10), new Waypoint(0, 64, 10), new Waypoint(0, 64, 0));
		for (long seed = 0; seed < 25; seed++) {
			for (double radius : new double[] {0.5, 1, 2}) {
				RouteFollower f = new RouteFollower(square, false, radius, cfg, new Random(seed));
				double x = 0.5;
				double z = 0.5;
				double yaw = -90;
				int ticks = -1;
				for (int t = 0; t < 600; t++) {
					RouteFollower.Output out = f.tick(x, z, true, false);
					if (out.status() == RouteFollower.Status.FINISHED) {
						ticks = t;
						break;
					}
					yaw = RouteFollower.stepYaw(yaw, out.aimYaw(), cfg.turnSpeedDegPerTick);
					x += -Math.sin(Math.toRadians(yaw)) * SPEED;
					z += Math.cos(Math.toRadians(yaw)) * SPEED;
				}
				assertTrue(ticks > 0 && ticks < 300, "seed " + seed + " radius " + radius + " took " + ticks);
			}
		}
	}

	@Test
	void stepTowardMovesNoFasterThanTheLimitAndStopsOnTarget() {
		assertEquals(10, RouteFollower.stepToward(0, 80, 10), 1e-9);
		assertEquals(-10, RouteFollower.stepToward(0, -80, 10), 1e-9);
		assertEquals(35, RouteFollower.stepToward(30, 35, 10), 1e-9);
		assertEquals(35, RouteFollower.stepToward(40, 35, 10), 1e-9);
	}

	@Test
	void pitchTargetIsExactWithoutRandomAndWithinTwoDegreesWithIt() {
		Random random = new Random(5);
		assertEquals(30, RouteFollower.pitchTarget(30, false, random), 0);
		boolean varied = false;
		for (int i = 0; i < 200; i++) {
			double t = RouteFollower.pitchTarget(30, true, random);
			assertTrue(t >= 28 && t <= 32, "got " + t);
			varied |= t != 30;
		}
		assertTrue(varied);
	}

	@Test
	void randomPitchStaysInsideTheValidRange() {
		Random random = new Random(9);
		for (int i = 0; i < 200; i++) {
			assertTrue(RouteFollower.pitchTarget(90, true, random) <= 90);
			assertTrue(RouteFollower.pitchTarget(-90, true, random) >= -90);
		}
	}
}
