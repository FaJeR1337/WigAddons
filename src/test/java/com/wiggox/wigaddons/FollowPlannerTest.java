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
	void keysFollowTheTunedBands() {
		assertArrayEquals(new int[] {1, 0}, FollowPlanner.keysFor(0));
		assertArrayEquals(new int[] {1, 0}, FollowPlanner.keysFor(10)); // W alone to 10 degrees
		assertArrayEquals(new int[] {1, 0}, FollowPlanner.keysFor(-10));
		assertArrayEquals(new int[] {1, -1}, FollowPlanner.keysFor(10.5)); // W and D from there to 35
		assertArrayEquals(new int[] {1, -1}, FollowPlanner.keysFor(35));
		assertArrayEquals(new int[] {1, 1}, FollowPlanner.keysFor(-20)); // W and A on the left
		assertArrayEquals(new int[] {1, 1}, FollowPlanner.keysFor(-35));
		assertArrayEquals(new int[] {0, -1}, FollowPlanner.keysFor(36)); // D alone beyond that
		assertArrayEquals(new int[] {0, -1}, FollowPlanner.keysFor(90));
		assertArrayEquals(new int[] {0, 1}, FollowPlanner.keysFor(-90));
		assertArrayEquals(new int[] {-1, -1}, FollowPlanner.keysFor(135)); // backing up to the right
		assertArrayEquals(new int[] {-1, 1}, FollowPlanner.keysFor(-135));
		assertArrayEquals(new int[] {-1, 0}, FollowPlanner.keysFor(180));
		assertArrayEquals(new int[] {-1, 0}, FollowPlanner.keysFor(-180));
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
		assertFalse(h.tick(0, -60, 0, 0, new FollowPlanner.Target(0, -59, 12, -57.4), true, false).jump()); // a block up but far away: no hopping yet
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
		double headY = -60 + 1.62; // same level as us: the view stays level
		double levelSlack = Math.tan(Math.toRadians(2.0)) * 20; // the level view drifts up to two degrees
		java.util.Set<Double> seen = new java.util.HashSet<>();
		for (int i = 0; i < 600; i++) {
			FollowPlanner.Output out = f.tick(0, -60, 0, 0, t, true, false);
			double dx = out.lookX() - t.x();
			double dy = out.lookY() - headY;
			double dz = out.lookZ() - t.z();
			assertTrue(Math.sqrt(dx * dx + dy * dy + dz * dz) <= 0.5 + levelSlack + 1e-9);
			seen.add(dx);
		}
		assertTrue(seen.size() > 3, "the look spot should move around");
		cfg.followLookRadius = 0;
		FollowPlanner exact = new FollowPlanner(FollowPlanner.Params.of(cfg), new Random(3));
		FollowPlanner.Output out = exact.tick(0, -60, 0, 0, t, true, false);
		assertEquals(t.x(), out.lookX(), 1e-12);
		assertEquals(headY, out.lookY(), levelSlack);
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

	@Test
	void walksAroundAWallInsteadOfIntoIt() {
		// a wall across x -6..6 at z=5; the player is behind it at the origin, the followed player is on the far side
		ObstacleAvoider.Terrain wall = (x, y, z) -> y <= -61 || (z == 5 && x >= -6 && x <= 6 && y <= -59);
		FollowPlanner f = new FollowPlanner(params(), new Random(1));
		f.setTerrain(wall);
		f.tick(0, -60, 0, 0, at(0.5, 14), true, false);
		FollowPlanner.Output out = f.tick(0.5, -60, 0.5, 0, at(0.5, 14), true, false);
		double[] next = f.snapshot().next();
		assertTrue(Math.abs(next[0] - 0.5) > 1.0, "aims off to the side of the wall, at x=" + next[0]);
		assertTrue(out.forward() >= 0);
	}

	@Test
	void gridPathFindsTheWayAroundAndNullWhenSealedIn() {
		ObstacleAvoider.Terrain wall = (x, y, z) -> y <= -61 || (z == 5 && x >= -6 && x <= 6 && y <= -59);
		java.util.List<double[]> path = GridPath.find(wall, 0, -60, 0, 0, -60, 10, true);
		assertTrue(path != null && !path.isEmpty());
		for (double[] node : path) {
			assertFalse(Math.floor(node[2]) == 5 && Math.abs(node[0]) <= 6.5);
		}
		ObstacleAvoider.Terrain sealed = (x, y, z) -> y <= -61 || (Math.abs(x) == 2 && Math.abs(z) <= 2 && y <= -59) || (Math.abs(z) == 2 && Math.abs(x) <= 2 && y <= -59);
		assertTrue(GridPath.find(sealed, 0, -60, 0, 0, -60, 10, true) == null);
	}

	@Test
	void looksAtTheNextPointInsteadOfThePlayerBehindAWall() {
		ObstacleAvoider.Terrain wall = (x, y, z) -> y <= -61 || (z == 5 && x >= -6 && x <= 6 && y <= -57);
		WigConfig cfg = new WigConfig();
		cfg.followLookRadius = 0;
		cfg.followLookOnlyWhenVisible = true;
		FollowPlanner f = new FollowPlanner(FollowPlanner.Params.of(cfg), new Random(1));
		f.setTerrain(wall);
		FollowPlanner.Output out = null;
		for (int i = 0; i < 20; i++) {
			out = f.tick(0.5, -60, 0.5, 0, at(0.5, 14), true, false);
		}
		assertTrue(Math.abs(out.lookX() - 0.5) > 1.0, "looks along the detour, x=" + out.lookX());
		WigConfig always = new WigConfig();
		always.followLookRadius = 0;
		FollowPlanner g = new FollowPlanner(FollowPlanner.Params.of(always), new Random(1));
		g.setTerrain(wall);
		for (int i = 0; i < 20; i++) {
			out = g.tick(0.5, -60, 0.5, 0, at(0.5, 14), true, false);
		}
		assertEquals(0.5, out.lookX(), 1e-9);
	}

	@Test
	void slabsAreWalkedOverNotAroundOrJumped() {
		// a strip of bottom slabs across the way at z=5: they can be stood on but don't block
		ObstacleAvoider.Terrain slabs = new ObstacleAvoider.Terrain() {
			@Override
			public boolean solid(int x, int y, int z) {
				return y <= -61;
			}

			@Override
			public boolean supports(int x, int y, int z) {
				return y <= -61 || (z == 5 && y == -60);
			}
		};
		FollowPlanner f = new FollowPlanner(params(), new Random(1));
		f.setTerrain(slabs);
		FollowPlanner.Output out = null;
		for (int i = 0; i < 10; i++) {
			out = f.tick(0.5, -60, 0.5, 0, at(0.5, 14), true, false);
		}
		assertEquals(0.5, f.snapshot().next()[0], 1e-9); // straight on, no detour
		assertFalse(out.jump());
	}

	@Test
	void doesNotScrapeAlongTheCornerOfABlock() {
		ObstacleAvoider.Terrain pillar = (x, y, z) -> y <= -61 || (x == 1 && z == 5 && y <= -59);
		FollowPlanner f = new FollowPlanner(params(), new Random(1));
		f.setTerrain(pillar);
		f.tick(0.8, -60, 0.5, 0, at(0.8, 14), true, false);
		f.tick(0.8, -60, 0.5, 0, at(0.8, 14), true, false);
		// a point-wide line down x=0.8 just misses the block, but the body wouldn't: it steers clear
		assertTrue(Math.abs(f.snapshot().next()[0] - 0.8) > 0.1, "aims away from the corner, x=" + f.snapshot().next()[0]);
	}

	@Test
	void holdsTheLookBetweenRelooks() {
		WigConfig cfg = new WigConfig();
		cfg.followLookRadius = 0;
		cfg.followLookInterval = 2;
		FollowPlanner f = new FollowPlanner(FollowPlanner.Params.of(cfg), new Random(1));
		double first = f.tick(0, -60, 0, 0, at(0, 12), true, false).lookX();
		double during = f.tick(0, -60, 0, 0, at(5, 12), true, false).lookX();
		assertEquals(first, during, 1e-9); // they moved, the view didn't
		double later = 0;
		for (int i = 0; i < 60; i++) {
			later = f.tick(0, -60, 0, 0, at(5, 12), true, false).lookX();
		}
		assertEquals(5, later, 1e-9); // by now it has looked again
	}

	@Test
	void doesNotJumpAfterAPlayerWhoHopsOnTheSpot() {
		ObstacleAvoider.Terrain flat = (x, y, z) -> y <= -61;
		FollowPlanner f = new FollowPlanner(params(), new Random(1));
		f.setTerrain(flat);
		FollowPlanner.Output out = null;
		// they are 4 blocks ahead, in the air at the top of a hop, the next point being close
		for (int i = 0; i < 3; i++) {
			out = f.tick(0, -60, 0, 0, new FollowPlanner.Target(0, -59, 6, -56.4), true, false);
			assertFalse(out.jump());
		}
		double[] next = f.snapshot().next();
		assertEquals(-60, next[1], 1e-9); // aims at where they land
	}

	@Test
	void recordsNoTrailWhileStandingNearThemAndStartsFreshWhenTheySetOff() {
		FollowPlanner f = new FollowPlanner(params(), new Random(1));
		f.tick(0, -60, 0, 0, at(0, 2), true, false); // close: standing
		for (int i = 0; i < 20; i++) {
			f.tick(0, -60, 0, 0, at(Math.sin(i) * 2, 2 + (i % 3)), true, false); // shuffling about next to us
		}
		assertTrue(f.snapshot().trail().isEmpty(), "nothing recorded while idle nearby");
		f.tick(0, -60, 0, 0, at(0, 9), true, false); // they run off
		assertEquals(1, f.snapshot().trail().size());
		assertEquals(9, f.snapshot().trail().get(0)[2], 1e-9);
	}

	@Test
	void keepsTheViewLevelOnSomeoneAtTheSameHeightButLooksUpAtSomeoneHigher() {
		WigConfig cfg = new WigConfig();
		cfg.followLookRadius = 0;
		FollowPlanner flat = new FollowPlanner(FollowPlanner.Params.of(cfg), new Random(1));
		// sneaking: their eyes are lower, but they are on our level
		double level = flat.tick(0, -60, 0, 0, new FollowPlanner.Target(0, -60, 8, -58.9), true, false).lookY();
		double angle = Math.toDegrees(Math.atan2(level - (-60 + 1.62), 8));
		assertTrue(Math.abs(angle) <= 2.0 + 1e-6, "level within 2 degrees, was " + angle);
		FollowPlanner up = new FollowPlanner(FollowPlanner.Params.of(cfg), new Random(1));
		double high = up.tick(0, -60, 0, 0, new FollowPlanner.Target(0, -56, 8, -54.4), true, false).lookY();
		assertEquals(-54.4 - 0.2, high, 1e-9);
	}

	@Test
	void relooksWhenCatchingUpToTheHeldSpot() {
		WigConfig cfg = new WigConfig();
		cfg.followLookRadius = 0;
		cfg.followLookInterval = 30; // would hold for ages
		FollowPlanner f = new FollowPlanner(FollowPlanner.Params.of(cfg), new Random(1));
		f.tick(0, -60, 0, 0, at(0, 12), true, false); // the spot to hold: z=12
		// they have walked on; we have caught up to where they were
		FollowPlanner.Output out = f.tick(0, -60, 10.5, 0, at(0, 30), true, false);
		assertEquals(30, out.lookZ(), 1e-9);
	}

	@Test
	void theLevelViewVariesByAboutTwoDegreesWhileWaitingWhereTheyWereLost() {
		WigConfig cfg = new WigConfig();
		cfg.followLookRadius = 0;
		FollowPlanner f = new FollowPlanner(FollowPlanner.Params.of(cfg), new Random(5));
		f.tick(0, -60, 0, 0, at(0, 9), true, false);
		java.util.Set<Double> pitches = new java.util.HashSet<>();
		for (int i = 0; i < 400; i++) {
			FollowPlanner.Output out = f.tick(0, -60, 9, 0, null, true, false); // standing where they were last seen
			double angle = Math.toDegrees(Math.atan2(out.lookY() - (-60 + 1.62), Math.hypot(out.lookX() - 0, out.lookZ() - 9) + 0.0001));
			pitches.add(Math.round(angle * 100) / 100.0);
		}
		assertTrue(pitches.size() > 3, "the pitch should vary");
	}

	@Test
	void doesNotJumpFromASlabOntoTheBlockBesideIt() {
		// the ground top is y=-60; at z=1 there is a full block in the cell above it (a one block rise from the ground)
		ObstacleAvoider.Terrain terrain = (x, y, z) -> y <= -61 || (z == 1 && y == -60);
		FollowPlanner onGround = new FollowPlanner(params(), new Random(1));
		onGround.setTerrain(terrain);
		assertTrue(onGround.tick(0.5, -60, 0.5, 0, at(0.5, 14), true, true).jump(), "a whole block up needs a jump");
		FollowPlanner onSlab = new FollowPlanner(params(), new Random(1));
		onSlab.setTerrain(terrain);
		// standing half a block up, on a slab: the same block is only a half block step
		assertFalse(onSlab.tick(0.5, -59.5, 0.5, 0, at(0.5, 14), true, true).jump(), "half a block is stepped up without jumping");
	}

	@Test
	void hunting_dropsOffALedgeInsteadOfWalkingRoundByTheStairs() {
		// a plateau at y=-60 ground top (cells <= -61 solid) for z < 5; beyond z=5 the ground is 5 blocks lower. The only other way down is a
		// stair 30 blocks along at x=30.
		ObstacleAvoider.Terrain ledge = (x, y, z) -> z < 5 ? y <= -61 : (y <= -66 || (x >= 30 && y <= -61 - (z - 5 < 5 ? 0 : 0)));
		WigConfig cfg = new WigConfig();
		cfg.killMaxDrop = 6;
		FollowPlanner f = new FollowPlanner(FollowPlanner.Params.forHunt(cfg), new Random(1));
		f.setTerrain(ledge);
		FollowPlanner.Output out = null;
		for (int i = 0; i < 5; i++) {
			out = f.tick(0.5, -60, 0.5, 0, new FollowPlanner.Target(0.5, -65, 12, -63.4), true, false);
		}
		double[] next = f.snapshot().next();
		assertTrue(Math.abs(next[0] - 0.5) < 1.5, "heads straight for the edge, x=" + next[0]);
		// the grid search agrees: a short way down with long drops allowed, a long way round (or none) without
		java.util.List<double[]> shortWay = GridPath.find(ledge, 0, -60, 0, 0, -65, 12, true, 6);
		java.util.List<double[]> noDrop = GridPath.find(ledge, 0, -60, 0, 0, -65, 12, true, 2);
		assertTrue(shortWay != null && shortWay.size() < 20, "short way down");
		assertTrue(noDrop == null || noDrop.size() > 40 || noDrop.get(noDrop.size() - 1)[1] > -65, "no way down without long drops: a long way round, or only partway");
	}

	@Test
	void jumpsAtAWallAfterBeingPressedAgainstItForASecond() {
		ObstacleAvoider.Terrain tallWall = (x, y, z) -> y <= -61 || (z == 1 && y <= -57);
		FollowPlanner f = new FollowPlanner(params(), new Random(1));
		f.setTerrain(tallWall);
		boolean jumped = false;
		for (int i = 0; i < 18; i++) {
			jumped |= f.tick(0.5, -60, 0.5, 0, at(0.5, 14), true, true).jump();
		}
		assertFalse(jumped, "a tall wall is not jumped at straight away");
		for (int i = 0; i < 6; i++) {
			jumped |= f.tick(0.5, -60, 0.5, 0, at(0.5, 14), true, true).jump();
		}
		assertTrue(jumped, "after a second against it, it tries a jump");
		WigConfig never = new WigConfig();
		never.followNeverJump = true;
		FollowPlanner n = new FollowPlanner(FollowPlanner.Params.of(never), new Random(1));
		n.setTerrain(tallWall);
		boolean neverJumped = false;
		for (int i = 0; i < 60; i++) {
			neverJumped |= n.tick(0.5, -60, 0.5, 0, at(0.5, 14), true, true).jump();
		}
		assertFalse(neverJumped, "never jump still means never");
	}

	@Test
	void doesNotJumpAtAWallTallerThanFiveBlocks() {
		ObstacleAvoider.Terrain high = (x, y, z) -> y <= -61 || (z == 1 && y <= -52); // 9 tall
		ObstacleAvoider.Terrain five = (x, y, z) -> y <= -61 || (z == 1 && y <= -56); // 5 tall
		for (ObstacleAvoider.Terrain wall : new ObstacleAvoider.Terrain[] {high, five}) {
			FollowPlanner f = new FollowPlanner(params(), new Random(1));
			f.setTerrain(wall);
			boolean jumped = false;
			for (int i = 0; i < 60; i++) {
				jumped |= f.tick(0.5, -60, 0.5, 0, at(0.5, 14), true, true).jump();
			}
			assertEquals(wall == five, jumped, wall == five ? "a five block wall is jumped at" : "a nine block wall is not");
		}
	}

	@Test
	void huntingPathsToTheMobsPositionNotItsOldTrailEvenFarAway() {
		ObstacleAvoider.Terrain flat = (x, y, z) -> y <= -61;
		WigConfig cfg = new WigConfig();
		FollowPlanner f = new FollowPlanner(FollowPlanner.Params.forHunt(cfg), new Random(1));
		f.setTerrain(flat);
		// a far mob that wanders: the aim is always where it is now
		f.tick(0.5, -60, 0.5, 0, at(0.5, 30), true, false);
		f.tick(0.5, -60, 0.5, 0, at(20, 30), true, false);
		FollowPlanner.Output out = f.tick(0.5, -60, 0.5, 0, at(20, 40), true, false);
		double[] next = f.snapshot().next();
		assertEquals(20, next[0], 1e-9);
		assertEquals(40, next[2], 1e-9);
		assertTrue(out.forward() >= 0);
	}

	@Test
	void aFarGoalGetsAPartialPathTowardIt() {
		ObstacleAvoider.Terrain wall = (x, y, z) -> y <= -61 || (z == 30 && x >= -20 && x <= 20 && y <= -58);
		java.util.List<double[]> path = GridPath.find(wall, 0, -60, 0, 0, -60, 150, true, 2);
		assertTrue(path != null && !path.isEmpty(), "heads toward a goal beyond the search area");
		double[] last = path.get(path.size() - 1);
		assertTrue(last[2] > 20, "got a good way along, z=" + last[2]);
	}

	@Test
	void theRouteSearchGoesRoundALedgeInsteadOfJumpingWhenItCostsLess() {
		// a one block high ledge across x -3..3 at z=5; walking round the end of it at x=4 costs a few blocks
		ObstacleAvoider.Terrain ledge = (x, y, z) -> y <= -61 || (z == 5 && x >= -3 && x <= 3 && y <= -60);
		java.util.List<double[]> cheap = GridPath.find(ledge, 0, -60, 0, 0, -60, 10, true, 2, 0.6);
		java.util.List<double[]> costly = GridPath.find(ledge, 0, -60, 0, 0, -60, 10, true, 2, 6.0);
		assertTrue(cheap != null && costly != null);
		boolean cheapClimbs = cheap.stream().anyMatch(n -> n[1] > -60);
		boolean costlyClimbs = costly.stream().anyMatch(n -> n[1] > -60);
		assertTrue(cheapClimbs, "with jumps cheap it climbs the ledge");
		assertFalse(costlyClimbs, "with jumps costly it walks round");
	}

	@Test
	void aFlightOfStairsIsWalkedUpWithoutCountingAsJumps() {
		// four stairs at x=3..6, each a block higher than the last, on solid blocks; ground below x=3
		ObstacleAvoider.Terrain stairs = new ObstacleAvoider.Terrain() {
			private boolean stair(int x, int y) {
				return x >= 3 && x <= 6 && y == -60 + (x - 3);
			}

			@Override
			public boolean solid(int x, int y, int z) {
				return y <= -61 || (x >= 3 && x <= 6 && y < -60 + (x - 3));
			}

			@Override
			public boolean supports(int x, int y, int z) {
				return solid(x, y, z) || stair(x, y);
			}

			@Override
			public boolean stairs(int x, int y, int z) {
				return stair(x, y);
			}

			@Override
			public double top(int x, int y, int z) {
				return stair(x, y) ? 0.5 : solid(x, y, z) ? 1.0 : 0.0;
			}
		};
		// no jumping allowed at all, and jumps would cost a fortune: the stairs still get climbed
		java.util.List<double[]> path = GridPath.find(stairs, 0, -60, 0, 6, -57, 0, false, 2, 100.0);
		assertTrue(path != null && !path.isEmpty(), "found a way up the stairs");
		assertTrue(path.get(path.size() - 1)[1] >= -58, "climbed to within a step of the top, y=" + path.get(path.size() - 1)[1]);
	}

	@Test
	void jumpsJustBeforeAOneBlockStepNotAfterBumpingIntoIt() {
		ObstacleAvoider.Terrain step = (x, y, z) -> y <= -61 || (z >= 3 && y <= -60); // a full block high ledge from z=3
		WigConfig cfg = new WigConfig();
		cfg.followSprintDistance = 0; // always sprinting
		FollowPlanner far = new FollowPlanner(FollowPlanner.Params.of(cfg), new Random(1));
		far.setTerrain(step);
		assertFalse(far.tick(0.5, -60, 0.5, 0, at(0.5, 14), true, false).jump(), "no jump while the block is still far");
		FollowPlanner near = new FollowPlanner(FollowPlanner.Params.of(cfg), new Random(1));
		near.setTerrain(step);
		assertTrue(near.tick(0.5, -60, 1.7, 0, at(0.5, 14), true, false).jump(), "a jump about a block and a half before it, with no bump");
	}
}
