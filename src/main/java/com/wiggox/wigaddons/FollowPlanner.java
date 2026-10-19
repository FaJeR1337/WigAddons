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

	/** The most hunting varies where on the mob it looks, in blocks. */
	static final double HUNT_LOOK_RADIUS = 0.1;

	public record Params(double stopDistance, double resumeDistance, double sprintDistance, double lostSeconds, double lookRadius,
		boolean neverJump, double stuckSeconds, boolean lookOnlyWhenVisible, double lookInterval, int maxDrop, boolean direct, double jumpCost) {
		/** For hunting: walk right up to the target, always look at it. */
		public static Params forHunt(WigConfig c) {
			return new Params(2.0, 2.5, c.followSprintDistance, 1.0, Math.min(c.followLookRadius, HUNT_LOOK_RADIUS), c.followNeverJump,
				c.stuckTimeoutSeconds, c.killRequireSight, 0, c.killMaxDrop, true, c.pathJumpCost);
		}

		public static Params of(WigConfig c) {
			return new Params(c.followStopDistance, c.followResumeDistance, c.followSprintDistance, c.followLostSeconds,
				c.followLookRadius, c.followNeverJump, c.stuckTimeoutSeconds, c.followLookOnlyWhenVisible, c.followLookInterval, 2, false, c.pathJumpCost);
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
	/** Corners are only cut to points this close, and checked this often (ticks). */
	static final double SHORTCUT_RANGE = 24.0;
	static final int SHORTCUT_EVERY = 4;
	/** How far either side of the walked line has to be free: a bit more than half the body's width. */
	static final double BODY_MARGIN = 0.35;
	/** A jump is at most this many blocks above where it lands. */
	static final int MAX_AIR_DROP = 2;
	/** Within this height of us counts as the same level. */
	static final double LEVEL_JITTER_DEGREES = 2.0;
	static final double FLAT_LEVEL = 0.6;
	static final double EYE_HEIGHT = 1.62;
	/** Fewest ticks between two jumps, so a staircase isn't bunny hopped. */
	static final int JUMP_GAP_TICKS = 8;
	/** How far before a block that needs a jump the jump is made: further when running. */
	static final double JUMP_REACH_WALK = 1.0;
	static final double JUMP_REACH_SPRINT = 1.5;
	/** Pressed against a wall for longer than this many ticks (one second) and it jumps. */
	static final int WALL_JUMP_TICKS = 20;
	/** Walls up to this many blocks high are jumped at; anything taller is not worth it. */
	static final int WALL_JUMP_MAX = 5;
	static final int REPLAN_EVERY = 10;
	/** How long the view has to be blocked, or clear, before the look switches. */
	static final int LOOK_SWITCH_TICKS = 10;
	static final double STEP_UP = 0.6;
	/** How long a randomly picked look spot is kept, in ticks. */
	static final int MIN_LOOK_TICKS = 25;
	static final int MAX_LOOK_TICKS = 55;

	private final Params params;
	private final Random random;
	private final Deque<double[]> trail = new ArrayDeque<>();
	private final int lostTicksLimit;
	private final int stuckWindow;

	private ObstacleAvoider.Terrain terrain;
	private int shortcutCooldown;
	private int jumpCooldown;
	private int wallTicks;
	private List<double[]> detour;
	private double[] detourGoal;
	private int replanCooldown;
	private boolean lookingAtPlayer = true;
	private int visibilityTicks;
	private Target lastSeen;
	private double[] lastNext;
	/** Ticks spent standing at the last place they were seen, after losing sight of them. */
	private int waitTicks;
	private boolean moving;
	private double lookDx;
	private double lookDy;
	private double lookDz;
	private int lookTicksLeft;
	/** Degrees up or down from level, for when the view is held level. */
	private double pitchJitter;
	/** The spot being held while the view only looks at the player now and then, and the ticks until the next look. */
	private double[] heldLook;
	private int relookTicksLeft;
	private double refX = Double.NaN;
	private double refZ;
	private int ticksInWindow;
	/** The strafe being held and for how many ticks, so it can't change faster than STRAFE_HOLD_TICKS. */
	private int heldStrafe;
	private int strafeHeldTicks = 1000;
	private static final int STRAFE_HOLD_TICKS = 6;

	public FollowPlanner(Params params, Random random) {
		this.params = params;
		this.random = random;
		this.lostTicksLimit = (int) Math.round(params.lostSeconds() * 20);
		this.stuckWindow = Math.max(1, (int) Math.round(params.stuckSeconds() * 20));
	}

	/** Lets the planner cut across a curve in the trail when the world shows the way is clear. Without it the trail is followed exactly. */
	public void setTerrain(ObstacleAvoider.Terrain terrain) {
		this.terrain = terrain;
	}

	/** @param target where the followed player is, or null when they can't be seen */
	public Output tick(double px, double py, double pz, double viewYaw, Target target, boolean onGround, boolean horizontalCollision) {
		jumpCooldown--;
		if (Double.isNaN(refX)) {
			refX = px;
			refZ = pz;
		}
		target = grounded(target);
		if (target != null) {
			waitTicks = 0;
			lastSeen = target;
			boolean idleNearby = !moving && Math.hypot(target.x() - px, target.z() - pz) <= params.resumeDistance();
			if (!moving && !idleNearby) {
				trail.clear(); // about to set off: start a fresh trail from where they are, not from what they did while we stood still
			}
			double[] last = trail.peekLast();
			if (!idleNearby && (last == null || Math.hypot(target.x() - last[0], target.z() - last[2]) >= TRAIL_SPACING)) {
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
				trail.clear();
			}
		} else {
			// out of sight: keep going until the last place they were seen has been reached
			moving = !trail.isEmpty() || dist > REACH;
			// the wait only starts once they have got there
			waitTicks = moving ? 0 : waitTicks + 1;
		}

		if (moving && terrain != null && --shortcutCooldown <= 0) {
			shortcutCooldown = SHORTCUT_EVERY;
			if (!params.direct()) {
				cutCorner(px, py, pz);
			}
		}
		double[] next = detourAround(px, py, pz, nextPoint(target, dist));
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
			// A and D are held for a little before they change, so a heading hovering at the edge of a band, or two targets pulling either way, doesn't make them flicker
			if (strafe != heldStrafe && strafeHeldTicks < STRAFE_HOLD_TICKS) {
				strafe = heldStrafe;
			} else if (strafe != heldStrafe) {
				heldStrafe = strafe;
				strafeHeldTicks = 0;
			}
			strafeHeldTicks++;
			sprint = forward > 0 && dist >= params.sprintDistance();
			// a rise only matters when it is close: the followed player hopping about far away shouldn't make us jump
			// with the world known, only an actual bump jumps: slabs, stairs and the like are stepped up by the walk itself, and path heights are only block-accurate
			boolean riseAhead = terrain == null && next[1] > py + STEP_UP && Math.hypot(next[0] - px, next[2] - pz) < 2.5;
			boolean blockedByStep = horizontalCollision && canStepOver(px, py, pz, next);
			if (horizontalCollision && !blockedByStep) {
				replanCooldown = 0; // rubbing along a wall that can't be jumped: plan the way again, now
			}
			// pressed against something that can't be stepped over (a wall two blocks or higher) for over a second: try jumping at it
			wallTicks = horizontalCollision && !blockedByStep ? wallTicks + 1 : 0;
			int wallHeight = wallTicks > WALL_JUMP_TICKS ? wallHeightAhead(px, py, pz, next) : 0;
			// a wall of two to five blocks, or something that blocks but isn't counted as solid (the back of a stair): try jumping at it
			boolean stuckAtWall = wallHeight <= WALL_JUMP_MAX && (wallHeight >= 2 || wallTicks > WALL_JUMP_TICKS * 2);
			// a person jumps just before the block, running at it, not after bumping into it and stopping
			boolean beforeBlock = forward > 0 && jumpableBlockAt(px, py, pz, next, sprint ? JUMP_REACH_SPRINT : JUMP_REACH_WALK);
			jump = !params.neverJump() && onGround && jumpCooldown <= 0 && (blockedByStep || riseAhead || stuckAtWall || beforeBlock);
			if (jump) {
				jumpCooldown = JUMP_GAP_TICKS;
			}
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
		// someone on the same level as us gets a level view: aiming at their actual head would pan down whenever they sneak, or when they stand close
		if (Math.abs(lastSeen.y() - py) <= FLAT_LEVEL) {
			// not a perfect zero: the pitch drifts a couple of degrees either way, a new amount every second or two
			headY = py + EYE_HEIGHT + Math.tan(Math.toRadians(pitchJitter)) * Math.max(dist, 1.0);
		}
		// up close the view stays at head height, so it doesn't bob up and down at someone standing right there
		double vertical = dist <= params.resumeDistance() ? 0 : lookDy;
		if (params.lookOnlyWhenVisible() && terrain != null) {
			boolean visible = clearSight(px, py + EYE_HEIGHT, pz, lastSeen.x(), headY, lastSeen.z());
			// the look only switches once the new situation has lasted a moment, so doorways and corners don't make the view flick back and forth
			visibilityTicks = visible == lookingAtPlayer ? 0 : visibilityTicks + 1;
			if (visibilityTicks >= LOOK_SWITCH_TICKS) {
				lookingAtPlayer = visible;
				visibilityTicks = 0;
			}
			if (!lookingAtPlayer && lastNext != null) {
				return new Output(forward, strafe, sprint, jump, status, dist, lastNext[0], py + EYE_HEIGHT, lastNext[2]);
			}
		}
		double[] look = {lastSeen.x() + lookDx, headY + vertical, lastSeen.z() + lookDz};
		if (params.lookInterval() > 0) {
			// look at them, then hold that spot until it is time to look again (a little uneven, so it isn't clockwork)
			// catching up to the spot being held would swing the view down at it: close in, look at them where they are now
			boolean caughtUp = heldLook != null && Math.hypot(heldLook[0] - px, heldLook[2] - pz) < Math.max(params.stopDistance(), 2.0);
			if (heldLook == null || caughtUp || --relookTicksLeft <= 0) {
				heldLook = look;
				relookTicksLeft = (int) Math.max(1, Math.round(params.lookInterval() * 20 * (0.75 + random.nextDouble() * 0.5)));
			}
			look = heldLook;
		}
		return new Output(forward, strafe, sprint, jump, status, dist, look[0], look[1], look[2]);
	}

	public Snapshot snapshot() {
		List<double[]> points = new ArrayList<>(trail);
		return new Snapshot(points, lastNext, lastSeen == null ? null : new double[] {lastSeen.x(), lastSeen.y(), lastSeen.z()});
	}

	/**
	 * When the straight way to the next point is blocked, walks around: plans a route over the block grid and aims at the
	 * furthest point of it that can be seen in a straight line.
	 */
	private double[] detourAround(double px, double py, double pz, double[] next) {
		if (terrain == null || next == null || !moving) {
			detour = null;
			return next;
		}
		if (clearLine(px, py, pz, next[0], next[1], next[2])) {
			detour = null;
			return next;
		}
		boolean goalMoved = detourGoal == null || Math.hypot(detourGoal[0] - next[0], detourGoal[2] - next[2]) > 2.0;
		if (detour == null || goalMoved || --replanCooldown <= 0) {
			replanCooldown = REPLAN_EVERY;
			detourGoal = next;
			detour = GridPath.find(terrain, (int) Math.floor(px), (int) Math.floor(py + 0.01), (int) Math.floor(pz),
				(int) Math.floor(next[0]), (int) Math.floor(next[1] + 0.01), (int) Math.floor(next[2]), !params.neverJump(), params.maxDrop(), params.jumpCost());
		}
		if (detour == null || detour.isEmpty()) {
			return next;
		}
		while (detour.size() > 1 && Math.hypot(detour.get(0)[0] - px, detour.get(0)[2] - pz) < 0.7) {
			detour.remove(0);
		}
		for (int i = detour.size() - 1; i >= 1; i--) {
			double[] node = detour.get(i);
			if (clearLine(px, py, pz, node[0], node[1], node[2])) {
				return node;
			}
		}
		return detour.get(0);
	}

	/** Whether nothing solid is between the two points, checked every fifth of a block. */
	private boolean clearSight(double x0, double y0, double z0, double x1, double y1, double z1) {
		double length = Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) + (z1 - z0) * (z1 - z0));
		int steps = Math.max(1, (int) Math.ceil(length / 0.2));
		for (int i = 1; i < steps; i++) {
			double t = i / (double) steps;
			if (terrain.solid((int) Math.floor(x0 + (x1 - x0) * t), (int) Math.floor(y0 + (y1 - y0) * t), (int) Math.floor(z0 + (z1 - z0) * t))) {
				return false;
			}
		}
		return true;
	}

	/** Skips ahead to the furthest trail point that can be walked to in a straight, flat-enough line. */
	private void cutCorner(double px, double py, double pz) {
		if (trail.size() < 2) {
			return;
		}
		List<double[]> points = new ArrayList<>(trail);
		for (int i = points.size() - 1; i >= 1; i--) {
			double[] point = points.get(i);
			if (Math.hypot(point[0] - px, point[2] - pz) > SHORTCUT_RANGE) {
				continue;
			}
			if (clearLine(px, py, pz, point[0], point[1], point[2])) {
				for (int k = 0; k < i; k++) {
					trail.pollFirst();
				}
				return;
			}
		}
	}

	/**
	 * A followed player in mid-jump is treated as standing where they will land, so a hop on the spot doesn't make us jump
	 * after them. If they really are going up a block, the points they walk after landing say so.
	 */
	private Target grounded(Target target) {
		if (target == null || terrain == null) {
			return target;
		}
		int cx = (int) Math.floor(target.x());
		int cz = (int) Math.floor(target.z());
		int cy = (int) Math.floor(target.y() + 0.01);
		if (terrain.supports(cx, cy - 1, cz)) {
			return target; // standing on something
		}
		for (int k = 1; k <= MAX_AIR_DROP; k++) {
			if (terrain.supports(cx, cy - 1 - k, cz)) {
				return new Target(target.x(), cy - k, target.z(), target.eyeY());
			}
		}
		return target; // falling a long way, or over a void: leave it be
	}

	/** Whether, this far ahead in the direction of travel, there is a single block that needs a jump to get onto (and room above it to do it). */
	private boolean jumpableBlockAt(double px, double py, double pz, double[] next, double reach) {
		if (terrain == null) {
			return false;
		}
		double dx = next[0] - px;
		double dz = next[2] - pz;
		double length = Math.hypot(dx, dz);
		if (length < 1e-6) {
			return false;
		}
		int cy = (int) Math.floor(py + 0.01);
		int cx = (int) Math.floor(px + dx / length * reach);
		int cz = (int) Math.floor(pz + dz / length * reach);
		if (!terrain.solid(cx, cy, cz) || terrain.solid(cx, cy + 1, cz) || terrain.solid(cx, cy + 2, cz)) {
			return false;
		}
		return cy + terrain.top(cx, cy, cz) - py > STEP_UP;
	}

	/** How many blocks tall the wall ahead is (counting up from the feet, in the direction of travel), at most one more than the jump limit. */
	private int wallHeightAhead(double px, double py, double pz, double[] next) {
		if (terrain == null) {
			return 2;
		}
		double dx = next[0] - px;
		double dz = next[2] - pz;
		double length = Math.hypot(dx, dz);
		if (length < 1e-6) {
			return 0;
		}
		int cx = (int) Math.floor(px + dx / length * 0.6);
		int cz = (int) Math.floor(pz + dz / length * 0.6);
		int cy = (int) Math.floor(py + 0.01);
		int height = 0;
		while (height <= WALL_JUMP_MAX && terrain.solid(cx, cy + height, cz)) {
			height++;
		}
		return height;
	}

	/** Whether what is blocking the way ahead is a single block that can be jumped onto, rather than a wall. */
	private boolean canStepOver(double px, double py, double pz, double[] next) {
		if (terrain == null) {
			return true;
		}
		double dx = next[0] - px;
		double dz = next[2] - pz;
		double length = Math.hypot(dx, dz);
		if (length < 1e-6) {
			return false;
		}
		int cx = (int) Math.floor(px + dx / length * 0.6);
		int cz = (int) Math.floor(pz + dz / length * 0.6);
		int cy = (int) Math.floor(py + 0.01);
		if (!terrain.solid(cx, cy, cz) || terrain.solid(cx, cy + 1, cz) || terrain.solid(cx, cy + 2, cz)) {
			return false;
		}
		// the walk steps up anything 0.6 or lower by itself: standing on a slab, a full block next to it needs no jump
		return cy + terrain.top(cx, cy, cz) - py > STEP_UP;
	}

	/**
	 * Whether a body can walk from one spot to another in a straight line: it stays at its height while there is ground,
	 * and where the ground ends it drops (down to the longest drop allowed) and carries on, with room to stand all along.
	 */
	private boolean clearLine(double x0, double y0, double z0, double x1, double y1, double z1) {
		double length = Math.hypot(x1 - x0, z1 - z0);
		int steps = Math.max(1, (int) Math.ceil(length / 0.4));
		// the direction sideways to the line
		double px = length < 1e-6 ? 0 : -(z1 - z0) / length;
		double pz = length < 1e-6 ? 0 : (x1 - x0) / length;
		int level = (int) Math.floor(y0 + 0.01);
		for (int i = 1; i <= steps; i++) {
			double t = i / (double) steps;
			double sx = x0 + (x1 - x0) * t;
			double sz = z0 + (z1 - z0) * t;
			int cx = (int) Math.floor(sx);
			int cz = (int) Math.floor(sz);
			if (terrain.solid(cx, level, cz) || terrain.solid(cx, level + 1, cz)) {
				return false;
			}
			// no ground here: fall through free air until there is some, within the longest drop
			int landed = level;
			int drop = 0;
			while (!terrain.supports(cx, landed - 1, cz)) {
				landed--;
				drop++;
				if (drop > params.maxDrop() || terrain.solid(cx, landed, cz)) {
					return false;
				}
			}
			// the body is wider than the line: leave room either side so the path doesn't scrape along corners
			for (int side = -1; side <= 1; side += 2) {
				int ox = (int) Math.floor(sx + side * px * BODY_MARGIN);
				int oz = (int) Math.floor(sz + side * pz * BODY_MARGIN);
				if ((ox != cx || oz != cz) && (terrain.solid(ox, level, oz) || terrain.solid(ox, level + 1, oz))) {
					return false;
				}
			}
			level = landed;
		}
		// it has to end up where the goal is, not on some other level
		return Math.abs(level - (int) Math.floor(y1 + 0.01)) <= 1;
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
		if (target != null && (params.direct() || dist <= STRAIGHT_DISTANCE)) {
			// hunting goes for where the mob is now, however far; following walks the trail until close
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
		pitchJitter = (random.nextDouble() * 2 - 1) * LEVEL_JITTER_DEGREES;
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

	/** Up to this many degrees either side of straight ahead is W alone; past it the direction is snapped to the eight a keyboard gives. */
	static final double KEYS_W_ONLY = 10.0;

	/**
	 * The keys to press to walk toward a direction that is this many degrees to the right (positive) or left of
	 * where the view points: W alone within 10 degrees, otherwise snapped to the eight directions a keyboard gives
	 * (45 degree steps, so W with A or D up to 67.5, A or D alone to 112.5, S with A or D to 157.5, S alone beyond).
	 * Returns {forward (1 W, -1 S), strafe (1 A, -1 D)}.
	 */
	public static int[] keysFor(double relativeDegrees) {
		double rel = RouteFollower.wrapDegrees(relativeDegrees);
		if (Math.abs(rel) <= KEYS_W_ONLY) {
			return new int[] {1, 0};
		}
		int sector = (int) Math.round(rel / 45.0);
		if (sector == 0) {
			sector = rel > 0 ? 1 : -1; // past the W alone band but not yet halfway to the next step: the diagonal
		}
		return switch (sector) {
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
