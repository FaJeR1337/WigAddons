package com.wiggox.wigaddons;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Decides, one tick at a time, how to follow a player: walk the path they walked (a trail of the places they were),
 * stop when close, start again when they get away, wait at the last place they were seen if they vanish, and look at
 * a spot near their head. It has no Minecraft types so it can be unit tested.
 */
public final class FollowPlanner {
	public enum Status {
		/** Walking after them. */
		FOLLOWING,
		/** Close enough: standing and watching. */
		CLOSE,
		/** Out of sight: going to, or waiting at, the last place they were seen. */
		LOST_WAITING,
		/** Out of sight for too long. */
		LOST,
		/** Barely moving for the stuck timeout. */
		STUCK
	}

	/** Where the followed player is: feet position, and eye height above sea level. */
	public record Target(double x, double y, double z, double eyeY) {
	}

	/**
	 * @param forward 1 W, -1 S, 0 neither
	 * @param strafe 1 A (left), -1 D (right), 0 neither
	 * @param lookX the spot to look at
	 */
	public record Output(int forward, int strafe, boolean sprint, boolean jump, Status status, double distance, double lookX, double lookY, double lookZ) {
	}

	public record Params(double stopDistance, double resumeDistance, double sprintDistance, double lostSeconds, double lookRadius,
		boolean neverJump, double stuckSeconds) {
		public static Params of(WigConfig c) {
			return new Params(c.followStopDistance, c.followResumeDistance, c.followSprintDistance, c.followLostSeconds,
				c.followLookRadius, c.followNeverJump, c.stuckTimeoutSeconds);
		}
	}

	/**
	 * What the planner is working from, for drawing it while debugging.
	 *
	 * @param trail the places the player walked that haven't been reached yet, oldest first (x, y, z)
	 * @param next the point being walked to, or null when standing
	 * @param lastSeen where the player was last seen, or null
	 */
	public record Snapshot(List<double[]> trail, double[] next, double[] lastSeen) {
	}

	/** Trail points are kept this far apart. */
	static final double TRAIL_SPACING = 0.5;
	static final int MAX_TRAIL = 400;
	/** A trail point this close counts as reached. */
	static final double REACH = 1.0;
	/** Within this distance (and in sight) the player is walked at directly instead of along the trail. */
	static final double STRAIGHT_DISTANCE = 6.0;
	static final double STEP_UP = 0.6;
	/** How long a randomly picked look spot is kept, in ticks. */
	static final int MIN_LOOK_TICKS = 25;
	static final int MAX_LOOK_TICKS = 55;

	private final Params params;
	private final Random random;
	private final Deque<double[]> trail = new ArrayDeque<>();
	private final int lostTicksLimit;
	private final int stuckWindow;

	private Target lastSeen;
	private double[] lastNext;
	/** Ticks spent standing at the last place they were seen, after losing sight of them. */
	private int waitTicks;
	private boolean moving;
	private double lookDx;
	private double lookDy;
	private double lookDz;
	private int lookTicksLeft;
	private double refX = Double.NaN;
	private double refZ;
	private int ticksInWindow;

	public FollowPlanner(Params params, Random random) {
		this.params = params;
		this.random = random;
		this.lostTicksLimit = (int) Math.round(params.lostSeconds() * 20);
		this.stuckWindow = Math.max(1, (int) Math.round(params.stuckSeconds() * 20));
	}

	/** @param target where the followed player is, or null when they can't be seen */
	public Output tick(double px, double py, double pz, double viewYaw, Target target, boolean onGround, boolean horizontalCollision) {
		if (Double.isNaN(refX)) {
			refX = px;
			refZ = pz;
		}
		if (target != null) {
			waitTicks = 0;
			lastSeen = target;
			double[] last = trail.peekLast();
			if (last == null || Math.hypot(target.x() - last[0], target.z() - last[2]) >= TRAIL_SPACING) {
				trail.addLast(new double[] {target.x(), target.y(), target.z()});
				if (trail.size() > MAX_TRAIL) {
					trail.pollFirst();
				}
			}
		}
		if (lastSeen == null) {
			return new Output(0, 0, false, false, Status.LOST, 0, px, py + 1.6, pz);
		}

		double dist = Math.hypot(lastSeen.x() - px, lastSeen.z() - pz);
		pruneTrail(px, pz);

		if (target != null) {
			if (dist > params.resumeDistance()) {
				moving = true;
			} else if (dist <= params.stopDistance()) {
				moving = false;
			}
		} else {
			// out of sight: keep going until the last place they were seen has been reached
			moving = !trail.isEmpty() || dist > REACH;
			// the wait only starts once they have got there
			waitTicks = moving ? 0 : waitTicks + 1;
		}

		double[] next = nextPoint(target, dist);
		lastNext = moving ? next : null;
		int forward = 0;
		int strafe = 0;
		boolean sprint = false;
		boolean jump = false;
		if (moving && next != null) {
			double moveYaw = RouteFollower.yawTo(next[0] - px, next[2] - pz);
			int[] keys = keysFor(RouteFollower.wrapDegrees(moveYaw - viewYaw));
			forward = keys[0];
			strafe = keys[1];
			sprint = forward > 0 && dist >= params.sprintDistance();
			jump = !params.neverJump() && onGround && (horizontalCollision || next[1] > py + STEP_UP);
		}

		Status status;
		if (target == null) {
			status = waitTicks > lostTicksLimit ? Status.LOST : Status.LOST_WAITING;
		} else {
			status = moving ? Status.FOLLOWING : Status.CLOSE;
		}
		if (moving) {
			if (++ticksInWindow >= stuckWindow) {
				if (Math.hypot(px - refX, pz - refZ) < 1.0) {
					status = Status.STUCK;
				}
				refX = px;
				refZ = pz;
				ticksInWindow = 0;
			}
		} else {
			refX = px;
			refZ = pz;
			ticksInWindow = 0;
		}

		wobble();
		double headY = lastSeen.eyeY() - 0.2;
		// up close the view stays at head height, so it doesn't bob up and down at someone standing right there
		double vertical = dist <= params.resumeDistance() ? 0 : lookDy;
		return new Output(forward, strafe, sprint, jump, status, dist,
			lastSeen.x() + lookDx, headY + vertical, lastSeen.z() + lookDz);
	}

	public Snapshot snapshot() {
		List<double[]> points = new ArrayList<>(trail);
		return new Snapshot(points, lastNext, lastSeen == null ? null : new double[] {lastSeen.x(), lastSeen.y(), lastSeen.z()});
	}

	/** Drops trail points up to the furthest one that has been reached, so the player doesn't walk back to them. */
	private void pruneTrail(double px, double pz) {
		int reachedIndex = -1;
		int i = 0;
		for (double[] point : trail) {
			if (Math.hypot(point[0] - px, point[2] - pz) <= REACH) {
				reachedIndex = i;
			}
			i++;
		}
		for (int k = 0; k <= reachedIndex; k++) {
			trail.pollFirst();
		}
	}

	private double[] nextPoint(Target target, double dist) {
		if (target != null && dist <= STRAIGHT_DISTANCE) {
			return new double[] {target.x(), target.y(), target.z()};
		}
		if (!trail.isEmpty()) {
			return trail.peekFirst();
		}
		return lastSeen == null ? null : new double[] {lastSeen.x(), lastSeen.y(), lastSeen.z()};
	}

	/** A new random spot near the head every second or two, so the view doesn't stare at one exact point. */
	private void wobble() {
		if (lookTicksLeft-- > 0) {
			return;
		}
		lookTicksLeft = MIN_LOOK_TICKS + random.nextInt(MAX_LOOK_TICKS - MIN_LOOK_TICKS + 1);
		double r = params.lookRadius();
		if (r <= 0) {
			lookDx = 0;
			lookDy = 0;
			lookDz = 0;
			return;
		}
		// a random point inside a ball of radius r
		double x;
		double y;
		double z;
		do {
			x = random.nextDouble() * 2 - 1;
			y = random.nextDouble() * 2 - 1;
			z = random.nextDouble() * 2 - 1;
		} while (x * x + y * y + z * z > 1);
		lookDx = x * r;
		lookDy = y * r;
		lookDz = z * r;
	}

	/**
	 * The keys to press to walk toward a direction that is this many degrees to the right (positive) or left of
	 * where the view points, snapped to the eight directions a keyboard gives.
	 */
	public static int[] keysFor(double relativeDegrees) {
		int sector = (int) Math.round(RouteFollower.wrapDegrees(relativeDegrees) / 45.0);
		return switch (sector) {
			case 0 -> new int[] {1, 0};
			case 1 -> new int[] {1, -1};
			case 2 -> new int[] {0, -1};
			case 3 -> new int[] {-1, -1};
			case -1 -> new int[] {1, 1};
			case -2 -> new int[] {0, 1};
			case -3 -> new int[] {-1, 1};
			default -> new int[] {-1, 0}; // 4 and -4: straight behind
		};
	}

	/** Yaw to look at a point from an eye position. */
	public static double yawToward(double fromX, double fromZ, double toX, double toZ) {
		return RouteFollower.yawTo(toX - fromX, toZ - fromZ);
	}

	/** Pitch to look at a point from an eye position: 0 level, positive down. */
	public static double pitchToward(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
		double horizontal = Math.hypot(toX - fromX, toZ - fromZ);
		return RouteFollower.clamp(-Math.toDegrees(Math.atan2(toY - fromY, Math.max(horizontal, 1e-6))), -90.0, 90.0);
	}
}
