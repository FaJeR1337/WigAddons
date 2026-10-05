package com.wiggox.wigaddons;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;

class FollowPlannerTest {
	private static final double WALK = 0.215;
	private static final double SPRINT = 0.28;

	private static FollowPlanner.Params params() {
		return FollowPlanner.Params.of(new WigConfig());
	}

	private static FollowPlanner.Target at(double x, double z) {
		return new FollowPlanner.Target(x, -60, z, -58.4);
	}

	@Test
	void keysSnapToTheEightDirections() {
		assertArrayEquals(new int[] {1, 0}, FollowPlanner.keysFor(0));
		assertArrayEquals(new int[] {1, 0}, FollowPlanner.keysFor(20));
		assertArrayEquals(new int[] {1, -1}, FollowPlanner.keysFor(45)); // ahead and to the right: W and D
		assertArrayEquals(new int[] {0, -1}, FollowPlanner.keysFor(90));
		assertArrayEquals(new int[] {-1, -1}, FollowPlanner.keysFor(135));
		assertArrayEquals(new int[] {-1, 0}, FollowPlanner.keysFor(180));
		assertArrayEquals(new int[] {-1, 0}, FollowPlanner.keysFor(-180));
		assertArrayEquals(new int[] {1, 1}, FollowPlanner.keysFor(-45)); // ahead and to the left: W and A
		assertArrayEquals(new int[] {0, 1}, FollowPlanner.keysFor(-90));
		assertArrayEquals(new int[] {-1, 1}, FollowPlanner.keysFor(-135));
	}

	@Test
	void standsStillWhenAlreadyClose() {
		FollowPlanner f = new FollowPlanner(params(), new Random(1));
		FollowPlanner.Output out = f.tick(0, -60, 0, 0, at(0, 2), true, false);
		assertEquals(FollowPlanner.Status.CLOSE, out.status());
		assertEquals(0, out.forward());
		assertEquals(0, out.strafe());
		assertFalse(out.sprint());
	}

	@Test
	void walksTowardAPlayerThatIsFarAndSprintsWhenVeryFar() {
		FollowPlanner f = new FollowPlanner(params(), new Random(1));
		FollowPlanner.Output far = f.tick(0, -60, 0, 0, at(0, 12), true, false);
		assertEquals(FollowPlanner.Status.FOLLOWING, far.status());
		assertEquals(1, far.forward());
		assertTrue(far.sprint());
		FollowPlanner g = new FollowPlanner(params(), new Random(1));
		FollowPlanner.Output nearish = g.tick(0, -60, 0, 0, at(0, 5.5), true, false);
		assertEquals(FollowPlanner.Status.FOLLOWING, nearish.status());
		assertFalse(nearish.sprint()); // closer than the sprint distance: walk
	}

	@Test
	void hysteresisStopsAtStopDistanceAndOnlyResumesBeyondResumeDistance() {
		FollowPlanner f = new FollowPlanner(params(), new Random(1));
		assertEquals(FollowPlanner.Status.FOLLOWING, f.tick(0, -60, 0, 0, at(0, 8), true, false).status());
		assertEquals(FollowPlanner.Status.FOLLOWING, f.tick(0, -60, 2, 0, at(0, 8), true, false).status()); // 6 away
		assertEquals(FollowPlanner.Status.FOLLOWING, f.tick(0, -60, 4, 0, at(0, 8), true, false).status()); // 4 away: between, still walking
		assertEquals(FollowPlanner.Status.CLOSE, f.tick(0, -60, 5.2, 0, at(0, 8), true, false).status()); // 2.8 away: stop
		assertEquals(FollowPlanner.Status.CLOSE, f.tick(0, -60, 4.2, 0, at(0, 8), true, false).status()); // 3.8 away: stays stopped
		assertEquals(FollowPlanner.Status.FOLLOWING, f.tick(0, -60, 2.8, 0, at(0, 8), true, false).status()); // 5.2 away: walks again
	}

	@Test
	void jumpsWhenBlockedOrTheyAreHigherUnlessNeverJump() {
		FollowPlanner f = new FollowPlanner(params(), new Random(1));
		assertTrue(f.tick(0, -60, 0, 0, at(0, 12), true, true).jump());
		FollowPlanner g = new FollowPlanner(params(), new Random(1));
		assertFalse(g.tick(0, -60, 0, 0, at(0, 12), false, true).jump()); // in the air
		FollowPlanner h = new FollowPlanner(params(), new Random(1));
		assertTrue(h.tick(0, -60, 0, 0, new FollowPlanner.Target(0, -59, 12, -57.4), true, false).jump()); // they are a block up
		WigConfig cfg = new WigConfig();
		cfg.followNeverJump = true;
		FollowPlanner n = new FollowPlanner(FollowPlanner.Params.of(cfg), new Random(1));
		assertFalse(n.tick(0, -60, 0, 0, at(0, 12), true, true).jump());
	}

	@Test
	void walksToWhereTheyWereLastSeenThenWaitsThenGivesUp() {
		WigConfig cfg = new WigConfig();
		cfg.followLostSeconds = 1; // 20 ticks
		FollowPlanner f = new FollowPlanner(FollowPlanner.Params.of(cfg), new Random(1));
		f.tick(0, -60, 0, 0, at(0, 20), true, false); // seen 20 blocks away
		FollowPlanner.Output out = f.tick(0, -60, 0, 0, null, true, false); // then gone
		assertEquals(FollowPlanner.Status.LOST_WAITING, out.status());
		assertEquals(1, out.forward()); // still heading for where they were
		// arrive at the last place they were seen
		out = f.tick(0, -60, 19.5, 0, null, true, false);
		assertEquals(FollowPlanner.Status.LOST_WAITING, out.status());
		assertEquals(0, out.forward()); // standing and waiting
		for (int i = 0; i < 30; i++) {
			out = f.tick(0, -60, 19.5, 0, null, true, false);
		}
		assertEquals(FollowPlanner.Status.LOST, out.status());
	}

	@Test
	void theWaitOnlyStartsOnceTheLastSeenPlaceIsReached() {
		WigConfig cfg = new WigConfig();
		cfg.followLostSeconds = 1; // 20 ticks
		FollowPlanner f = new FollowPlanner(FollowPlanner.Params.of(cfg), new Random(1));
		f.tick(0, -60, 0, 0, at(0, 30), true, false);
		// 60 ticks of walking toward the place, far longer than the wait: still not given up
		for (int i = 0; i < 60; i++) {
			assertEquals(FollowPlanner.Status.LOST_WAITING, f.tick(0, -60, i * 0.28, 0, null, true, false).status());
		}
		FollowPlanner.Output out = null;
		for (int i = 0; i < 30; i++) {
			out = f.tick(0, -60, 29.5, 0, null, true, false); // arrived
		}
		assertEquals(FollowPlanner.Status.LOST, out.status());
	}

	@Test
	void theSnapshotShowsTheTrailTheNextPointAndWhereTheyWereLastSeen() {
		FollowPlanner f = new FollowPlanner(params(), new Random(1));
		for (int i = 0; i <= 10; i++) {
			f.tick(0, -60, 0, 0, at(0, 10 + i * 0.5), true, false); // they walk 5 blocks away while I stand
		}
		FollowPlanner.Snapshot snap = f.snapshot();
		assertTrue(snap.trail().size() >= 10);
		assertEquals(10.0, snap.trail().get(0)[2], 1e-9);
		assertEquals(15.0, snap.lastSeen()[2], 1e-9);
		assertTrue(snap.next() != null); // far away, so walking
		FollowPlanner close = new FollowPlanner(params(), new Random(1));
		close.tick(0, -60, 0, 0, at(0, 2), true, false);
		assertEquals(null, close.snapshot().next()); // close enough: standing, nothing to walk to
	}

	@Test
	void resumesIfTheySoonReappear() {
		WigConfig cfg = new WigConfig();
		cfg.followLostSeconds = 5;
		FollowPlanner f = new FollowPlanner(FollowPlanner.Params.of(cfg), new Random(1));
		f.tick(0, -60, 0, 0, at(0, 20), true, false);
		for (int i = 0; i < 10; i++) {
			f.tick(0, -60, 1, 0, null, true, false);
		}
		FollowPlanner.Output back = f.tick(0, -60, 1, 0, at(0, 25), true, false);
		assertEquals(FollowPlanner.Status.FOLLOWING, back.status());
	}

	@Test
	void reportsStuckWhenWalkingButNotMoving() {
		WigConfig cfg = new WigConfig();
		cfg.stuckTimeoutSeconds = 1;
		FollowPlanner f = new FollowPlanner(FollowPlanner.Params.of(cfg), new Random(1));
		FollowPlanner.Output out = null;
		for (int i = 0; i < 25; i++) {
			out = f.tick(0, -60, 0, 0, at(0, 20), true, false);
			if (out.status() == FollowPlanner.Status.STUCK) {
				break;
			}
		}
		assertEquals(FollowPlanner.Status.STUCK, out.status());
	}

	@Test
	void theLookSpotStaysWithinTheRadiusOfTheHeadAndChangesOverTime() {
		WigConfig cfg = new WigConfig();
		cfg.followLookRadius = 0.5;
		FollowPlanner f = new FollowPlanner(FollowPlanner.Params.of(cfg), new Random(3));
		FollowPlanner.Target t = at(0, 20);
		double headY = t.eyeY() - 0.2;
		java.util.Set<Double> seen = new java.util.HashSet<>();
		for (int i = 0; i < 600; i++) {
			FollowPlanner.Output out = f.tick(0, -60, 0, 0, t, true, false);
			double dx = out.lookX() - t.x();
			double dy = out.lookY() - headY;
			double dz = out.lookZ() - t.z();
			assertTrue(Math.sqrt(dx * dx + dy * dy + dz * dz) <= 0.5 + 1e-9);
			seen.add(dx);
		}
		assertTrue(seen.size() > 3, "the look spot should move around");
		cfg.followLookRadius = 0;
		FollowPlanner exact = new FollowPlanner(FollowPlanner.Params.of(cfg), new Random(3));
		FollowPlanner.Output out = exact.tick(0, -60, 0, 0, t, true, false);
		assertEquals(t.x(), out.lookX(), 1e-12);
		assertEquals(headY, out.lookY(), 1e-12);
	}

	/** Moves the follower from what the planner asks for, with the view turning toward the look spot. */
	private static double[] simulate(IntFunction<FollowPlanner.Target> targetAt, int ticks, FollowPlanner f, double[] startPos, double[] log) {
		double x = startPos[0];
		double z = startPos[1];
		double yaw = 0;
		double maxDist = 0;
		int starts = 0;
		boolean wasMoving = false;
		for (int t = 0; t < ticks; t++) {
			FollowPlanner.Target target = targetAt.apply(t);
			FollowPlanner.Output out = f.tick(x, -60, z, yaw, target, true, false);
			if (target != null) {
				maxDist = Math.max(maxDist, Math.hypot(target.x() - x, target.z() - z));
			}
			boolean moving = out.forward() != 0 || out.strafe() != 0;
			if (moving && !wasMoving) {
				starts++;
			}
			wasMoving = moving;
			double wanted = FollowPlanner.yawToward(x, z, out.lookX(), out.lookZ());
			yaw = RouteFollower.stepYaw(yaw, wanted, 10);
			double fx = -Math.sin(Math.toRadians(yaw));
			double fz = Math.cos(Math.toRadians(yaw));
			double lx = Math.cos(Math.toRadians(yaw));
			double lz = Math.sin(Math.toRadians(yaw));
			double mx = fx * out.forward() + lx * out.strafe();
			double mz = fz * out.forward() + lz * out.strafe();
			double len = Math.hypot(mx, mz);
			if (len > 0) {
				double speed = out.sprint() ? SPRINT : WALK;
				x += mx / len * speed;
				z += mz / len * speed;
			}
		}
		log[0] = maxDist;
		log[1] = starts;
		return new double[] {x, z};
	}

	@Test
	void keepsUpWithAPlayerWalkingAroundACorner() {
		// the player walks 30 blocks south, then 30 east, at walking pace
		IntFunction<FollowPlanner.Target> walker = t -> {
			double d = t * WALK;
			return d <= 30 ? at(0, d) : at(Math.min(30, d - 30), 30);
		};
		double[] log = new double[2];
		FollowPlanner f = new FollowPlanner(params(), new Random(2));
		double[] end = simulate(walker, 500, f, new double[] {0, -3}, log);
		FollowPlanner.Target last = walker.apply(499);
		double finalDist = Math.hypot(last.x() - end[0], last.z() - end[1]);
		assertTrue(finalDist <= 3.6, "ended " + finalDist + " from the player");
		assertTrue(log[0] < 9, "fell as far as " + log[0] + " behind");
	}

	@Test
	void doesNotKeepStartingAndStoppingWhenThePlayerStandsStill() {
		IntFunction<FollowPlanner.Target> still = t -> at(0, 10);
		double[] log = new double[2];
		FollowPlanner f = new FollowPlanner(params(), new Random(2));
		double[] end = simulate(still, 600, f, new double[] {0, 0}, log);
		assertTrue(Math.hypot(0 - end[0], 10 - end[1]) <= 3.3);
		assertEquals(1, (int) log[1], "started walking " + (int) log[1] + " times");
	}

	/** The followed player walks down z and then along x; where is the player told to head, with this terrain? */
	private static double[] aimAtCurve(ObstacleAvoider.Terrain terrain) {
		FollowPlanner f = new FollowPlanner(params(), new Random(1));
		f.setTerrain(terrain);
		for (double z = 8; z <= 20; z += 0.5) {
			f.tick(0, -60, 0, 0, at(0, z), true, false);
		}
		for (double x = 0.5; x <= 12; x += 0.5) {
			f.tick(0, -60, 0, 0, at(x, 20), true, false);
		}
		for (int i = 0; i < 8; i++) {
			f.tick(0, -60, 0, 0, at(12, 20), true, false);
		}
		return f.snapshot().next();
	}

	@Test
	void cutsAcrossACurveWhenTheGroundIsOpen() {
		double[] next = aimAtCurve((x, y, z) -> y <= -61);
		assertTrue(next[0] > 5, "heads across the curve, aiming at x=" + next[0]);
	}

	@Test
	void followsTheCurveWhenAWallBlocksTheShortcut() {
		double[] next = aimAtCurve((x, y, z) -> y <= -61 || (x >= 1 && z >= 5 && z <= 15 && y <= -59));
		assertTrue(next[0] < 2, "stays on the trail, aiming at x=" + next[0]);
	}
}
