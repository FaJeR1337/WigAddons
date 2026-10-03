package com.fajer.wigaddons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RouteFollowerTest {
	/** Sprint speed in blocks per tick. */
	private static final double SPEED = 0.28;

	private static WigConfig config() {
		return new WigConfig();
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
	void turnsTheShortWayAndNoFasterThanTheLimit() {
		RouteFollower f = follower(List.of(new Waypoint(-20, 64, 0)), false, 1);
		// target is west (yaw 90); starting at 0 turns 10 degrees per tick
		assertEquals(10, f.tick(0.5, 0.5, 0, true, false).yaw(), 1e-9);
		// from 350 the short way to 90 is +100, so it wraps up through 360 rather than back down
		RouteFollower g = follower(List.of(new Waypoint(-20, 64, 0)), false, 1);
		assertEquals(360, g.tick(0.5, 0.5, 350, true, false).yaw(), 1e-9);
	}

	@Test
	void keepsFacingTheCurrentWaypointUntilWithinTheRadius() {
		// waypoint 0 is straight ahead (south), waypoint 1 is far to the west of it
		RouteFollower f = follower(List.of(new Waypoint(0, 64, 10), new Waypoint(-30, 64, 10)), false, 2);
		assertEquals(0, f.tick(0.5, 0.5, 0, true, false).yaw(), 1e-9);
		// 3 blocks away is outside the radius of 2: still heading for waypoint 0
		assertEquals(0, f.tick(0.5, 7.5, 0, true, false).yaw(), 1e-9);
		assertEquals(0, f.targetIndex());
		// within 2 blocks: waypoint 0 is reached and it turns toward waypoint 1 (west, positive yaw)
		assertTrue(f.tick(0.5, 8.5, 0, true, false).yaw() > 0);
		assertEquals(1, f.targetIndex());
	}

	@Test
	void aLargerRadiusStartsTheTurnEarlier() {
		List<Waypoint> route = List.of(new Waypoint(0, 64, 10), new Waypoint(-30, 64, 10));
		RouteFollower wide = follower(route, false, 5);
		RouteFollower tight = follower(route, false, 1);
		// 4 blocks from waypoint 0
		wide.tick(0.5, 6.5, 0, true, false);
		tight.tick(0.5, 6.5, 0, true, false);
		assertEquals(1, wide.targetIndex());
		assertEquals(0, tight.targetIndex());
	}

	@Test
	void radiusZeroWaitsUntilNearTheMiddleOfTheBlock() {
		RouteFollower f = follower(List.of(new Waypoint(0, 64, 10), new Waypoint(30, 64, 10)), false, 0);
		f.tick(0.5, 9.0, 0, true, false); // 1.5 blocks away
		assertEquals(0, f.targetIndex());
		f.tick(0.5, 9.4, 0, true, false); // 1.1 blocks away
		assertEquals(0, f.targetIndex());
		f.tick(0.5, 10.4, 0, true, false); // 0.1 blocks away
		assertEquals(1, f.targetIndex());
	}

	@Test
	void keepsRunningThroughAWaypointWithoutStopping() {
		RouteFollower f = follower(List.of(new Waypoint(0, 64, 5), new Waypoint(0, 64, 20)), false, 1);
		f.tick(0.5, 0.5, 0, true, false);
		RouteFollower.Output out = f.tick(0.5, 5.0, 0, true, false);
		assertEquals(RouteFollower.Status.RUNNING, out.status());
		assertEquals(1, f.targetIndex());
	}

	@Test
	void finishesAtTheLastWaypointWhenNotLooping() {
		RouteFollower f = follower(List.of(new Waypoint(0, 64, 10)), false, 1);
		f.tick(0.5, 0.5, 0, true, false);
		assertEquals(RouteFollower.Status.FINISHED, f.tick(0.5, 9.5, 0, true, false).status());
	}

	@Test
	void loopGoesBackToTheFirstWaypoint() {
		RouteFollower f = follower(List.of(new Waypoint(0, 64, 10), new Waypoint(0, 64, 20)), true, 1);
		f.tick(0.5, 0.5, 0, true, false);
		f.tick(0.5, 9.5, 0, true, false);
		assertEquals(RouteFollower.Status.RUNNING, f.tick(0.5, 19.5, 0, true, false).status());
		assertEquals(0, f.targetIndex());
	}

	@Test
	void jumpsOnlyWhenBlockedOnTheGround() {
		RouteFollower f = follower(List.of(new Waypoint(0, 64, 30)), false, 1);
		assertTrue(f.tick(0.5, 0.5, 0, true, true).jump());
		assertEquals(false, f.tick(0.5, 0.5, 0, false, true).jump());
		assertEquals(false, f.tick(0.5, 0.5, 0, true, false).jump());
	}

	@Test
	void reportsStuckWhenItBarelyMoves() {
		WigConfig cfg = config();
		cfg.stuckTimeoutSeconds = 1; // 20 ticks
		RouteFollower f = new RouteFollower(List.of(new Waypoint(0, 64, 30)), false, 1, cfg);
		RouteFollower.Output out = null;
		for (int i = 0; i < 25; i++) {
			out = f.tick(0.5, 0.5, 0, true, false);
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
			assertEquals(RouteFollower.Status.RUNNING, f.tick(0.5, 0.5 + i * 0.3, 0, true, false).status());
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
			RouteFollower.Output out = f.tick(x, z, yaw, true, false);
			if (out.status() == RouteFollower.Status.FINISHED) {
				return tick;
			}
			yaw = out.yaw();
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
}
