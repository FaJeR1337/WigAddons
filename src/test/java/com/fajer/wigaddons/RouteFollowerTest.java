package com.fajer.wigaddons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RouteFollowerTest {
	private static WigConfig config() {
		return new WigConfig();
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
		RouteFollower f = new RouteFollower(List.of(new Waypoint(-20, 64, 0)), false, config());
		// target is west (yaw 90); starting at 0 turns 10 degrees per tick
		RouteFollower.Output out = f.tick(0.5, 0.5, 0, true, false);
		assertEquals(10, out.yaw(), 1e-9);
		// from 350 the short way to 90 is +100, so it wraps up through 360 rather than back down
		RouteFollower g = new RouteFollower(List.of(new Waypoint(-20, 64, 0)), false, config());
		assertEquals(360, g.tick(0.5, 0.5, 350, true, false).yaw(), 1e-9);
	}

	@Test
	void startsTurningTowardTheNextWaypointWhenNearTheCurrentOne() {
		// waypoint 0 is straight ahead (south), waypoint 1 is far to the west of it
		RouteFollower f = new RouteFollower(List.of(new Waypoint(0, 64, 10), new Waypoint(-30, 64, 10)), false, config());
		// far from waypoint 0: keeps facing it
		assertEquals(0, f.tick(0.5, 0.5, 0, true, false).yaw(), 1e-9);
		// within 4 blocks of waypoint 0: turns toward waypoint 1 (west, positive yaw)
		assertTrue(f.tick(0.5, 7.5, 0, true, false).yaw() > 0);
	}

	@Test
	void keepsRunningThroughAWaypointWithoutStopping() {
		RouteFollower f = new RouteFollower(List.of(new Waypoint(0, 64, 5), new Waypoint(0, 64, 20)), false, config());
		f.tick(0.5, 0.5, 0, true, false);
		RouteFollower.Output out = f.tick(0.5, 5.5, 0, true, false);
		assertEquals(RouteFollower.Status.RUNNING, out.status());
		assertEquals(1, f.targetIndex());
	}

	@Test
	void advancesWhenPassedEvenIfTurningEarlyMissedTheRadius() {
		RouteFollower f = new RouteFollower(List.of(new Waypoint(0, 64, 10), new Waypoint(30, 64, 10)), false, config());
		f.tick(0.5, 0.5, 0, true, false);
		// 3 blocks to the side of the waypoint but already beyond it along the segment
		f.tick(3.5, 11.5, 0, true, false);
		assertEquals(1, f.targetIndex());
	}

	@Test
	void finishesAtTheLastWaypointWhenNotLooping() {
		RouteFollower f = new RouteFollower(List.of(new Waypoint(0, 64, 10)), false, config());
		f.tick(0.5, 0.5, 0, true, false);
		assertEquals(RouteFollower.Status.FINISHED, f.tick(0.5, 9.5, 0, true, false).status());
	}

	@Test
	void loopGoesBackToTheFirstWaypoint() {
		RouteFollower f = new RouteFollower(List.of(new Waypoint(0, 64, 10), new Waypoint(0, 64, 20)), true, config());
		f.tick(0.5, 0.5, 0, true, false);
		f.tick(0.5, 9.5, 0, true, false);
		assertEquals(RouteFollower.Status.RUNNING, f.tick(0.5, 19.5, 0, true, false).status());
		assertEquals(0, f.targetIndex());
	}

	@Test
	void jumpsOnlyWhenBlockedOnTheGround() {
		RouteFollower f = new RouteFollower(List.of(new Waypoint(0, 64, 30)), false, config());
		assertTrue(f.tick(0.5, 0.5, 0, true, true).jump());
		assertEquals(false, f.tick(0.5, 0.5, 0, false, true).jump());
		assertEquals(false, f.tick(0.5, 0.5, 0, true, false).jump());
	}

	@Test
	void reportsStuckWhenItBarelyMoves() {
		WigConfig cfg = config();
		cfg.stuckTimeoutSeconds = 1; // 20 ticks
		RouteFollower f = new RouteFollower(List.of(new Waypoint(0, 64, 30)), false, cfg);
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
		RouteFollower f = new RouteFollower(List.of(new Waypoint(0, 64, 100)), false, cfg);
		for (int i = 0; i < 100; i++) {
			assertEquals(RouteFollower.Status.RUNNING, f.tick(0.5, 0.5 + i * 0.3, 0, true, false).status());
		}
	}

	@Test
	void anEmptyRouteIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> new RouteFollower(List.of(), false, config()));
	}
}
