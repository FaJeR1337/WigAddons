package com.fajer.wigaddons;

import java.util.List;
import java.util.Random;

/**
 * Works out, one tick at a time, where the player should be heading along a route. It never stops at a waypoint.
 * Once the player is within the route's radius of a waypoint, the point it aims at slides from that waypoint toward
 * the next one, so the path bends smoothly instead of having a corner. It has no Minecraft types so it can be unit
 * tested. Turning the view toward the aim is up to the caller. Each waypoint is aimed at a slightly random spot
 * within its block (unless the radius is 0, which means the exact middle).
 */
public final class RouteFollower {
	public enum Status {
		RUNNING,
		/** The last waypoint was reached (never happens when looping). */
		FINISHED,
		/** The player has hardly moved for the stuck timeout. */
		STUCK
	}

	/**
	 * @param aimYaw the direction to face, in Minecraft yaw degrees
	 * @param distance blocks to the waypoint being run to
	 * @param blend 0 when aiming straight at that waypoint, up to 1 when aiming at the one after it
	 */
	public record Output(double aimYaw, boolean jump, Status status, int target, double distance, double blend) {
	}

	/** A radius of 0 means "the middle of the block", which can't be hit exactly, so this is the least that counts. */
	static final double MIN_ARRIVE_RADIUS = 0.25;

	/**
	 * A waypoint inside the turning circle can't be hit by turning toward it, so the player would orbit it. Once the
	 * player has come this close (or within the radius, if larger) and is moving away again, the waypoint counts as
	 * passed.
	 */
	private static final double MISS_WINDOW_MIN = 3.0;
	private static final double MOVING_AWAY = 0.5;

	private final List<Waypoint> points;
	private final boolean loop;
	private final double radius;
	private final int stuckWindowTicks;
	private final double humanize;
	private final Random random;
	/** Where in each waypoint's block the player aims, as an offset from the middle. */
	private final double[] offsetX;
	private final double[] offsetZ;

	private int target;
	private boolean started;
	/** Where the run to the current waypoint began, to tell which way the player is arriving from. */
	private double prevX;
	private double prevZ;
	private double refX;
	private double refZ;
	private int ticksInWindow;
	private double closest = Double.MAX_VALUE;

	public RouteFollower(List<Waypoint> points, boolean loop, double radius, WigConfig config) {
		this(points, loop, radius, config, new Random());
	}

	public RouteFollower(List<Waypoint> points, boolean loop, double radius, WigConfig config, Random random) {
		if (points.isEmpty()) {
			throw new IllegalArgumentException("a route needs at least one waypoint");
		}
		this.points = List.copyOf(points);
		this.loop = loop;
		this.radius = Math.max(radius, MIN_ARRIVE_RADIUS);
		this.stuckWindowTicks = Math.max(1, (int) Math.round(config.stuckTimeoutSeconds * 20));
		// a radius of 0 asks for the very middle of the block, so there is nothing to vary
		this.humanize = radius <= 0 ? 0 : config.humanizeOffset;
		this.random = random;
		this.offsetX = new double[points.size()];
		this.offsetZ = new double[points.size()];
		for (int i = 0; i < points.size(); i++) {
			roll(i);
		}
	}

	private void roll(int i) {
		offsetX[i] = (random.nextDouble() * 2 - 1) * humanize;
		offsetZ[i] = (random.nextDouble() * 2 - 1) * humanize;
	}

	double aimX(int i) {
		return points.get(i).centerX() + offsetX[i];
	}

	double aimZ(int i) {
		return points.get(i).centerZ() + offsetZ[i];
	}

	/** Index of the waypoint being run to, 0-based. */
	public int targetIndex() {
		return target;
	}

	public Output tick(double px, double pz, boolean onGround, boolean horizontalCollision) {
		if (!started) {
			started = true;
			prevX = px;
			prevZ = pz;
			refX = px;
			refZ = pz;
		}

		if (reachedTarget(px, pz) && !advance(px, pz)) {
			return new Output(0, false, Status.FINISHED, target, 0, 0);
		}

		double dist = Math.hypot(aimX(target) - px, aimZ(target) - pz);
		double aimX = aimX(target);
		double aimZ = aimZ(target);
		double blend = 0;
		Integer next = nextIndex();
		if (next != null && dist < radius) {
			blend = 1 - dist / radius;
			aimX += (aimX(next) - aimX) * blend;
			aimZ += (aimZ(next) - aimZ) * blend;
		}
		double aimYaw = yawTo(aimX - px, aimZ - pz);

		Status status = Status.RUNNING;
		if (++ticksInWindow >= stuckWindowTicks) {
			if (Math.hypot(px - refX, pz - refZ) < 1.0) {
				status = Status.STUCK;
			} else {
				refX = px;
				refZ = pz;
				ticksInWindow = 0;
			}
		}
		return new Output(aimYaw, status == Status.RUNNING && onGround && horizontalCollision, status, target, dist, blend);
	}

	/**
	 * With a waypoint to follow it, reached when at its middle, or within the radius and already past it along the
	 * way it was approached, or after coming close and moving away again (turning too wide to hit it). The last
	 * waypoint of a route that doesn't loop is reached within the radius.
	 */
	private boolean reachedTarget(double px, double pz) {
		double tx = aimX(target);
		double tz = aimZ(target);
		double dist = Math.hypot(tx - px, tz - pz);
		closest = Math.min(closest, dist);
		Integer next = nextIndex();
		if (next == null) {
			return dist <= radius;
		}
		if (dist <= MIN_ARRIVE_RADIUS) {
			return true;
		}
		if (dist <= radius) {
			double inX = tx - prevX;
			double inZ = tz - prevZ;
			if (Math.hypot(inX, inZ) > 1e-6 && (px - tx) * inX + (pz - tz) * inZ >= 0) {
				return true;
			}
		}
		return closest <= Math.max(radius, MISS_WINDOW_MIN) && dist > closest + MOVING_AWAY;
	}

	/** Moves to the next waypoint. False when the route is over. */
	private boolean advance(double px, double pz) {
		Integer next = nextIndex();
		if (next == null) {
			return false;
		}
		prevX = aimX(target);
		prevZ = aimZ(target);
		int passed = target;
		target = next;
		roll(passed); // the next lap goes through a different spot
		closest = Double.MAX_VALUE;
		ticksInWindow = 0;
		refX = px;
		refZ = pz;
		return true;
	}

	private Integer nextIndex() {
		if (target + 1 < points.size()) {
			return target + 1;
		}
		return loop ? 0 : null;
	}

	/** Minecraft yaw for a direction: 0 faces +Z (south), 90 faces -X (west). */
	public static double yawTo(double dx, double dz) {
		return Math.toDegrees(Math.atan2(-dx, dz));
	}

	public static double wrapDegrees(double degrees) {
		double d = degrees % 360.0;
		if (d >= 180.0) {
			d -= 360.0;
		}
		if (d < -180.0) {
			d += 360.0;
		}
		return d;
	}

	/** Turns from the current yaw toward the wanted one by the short way, by at most maxStep degrees. */
	public static double stepYaw(double yaw, double wanted, double maxStep) {
		return yaw + clamp(wrapDegrees(wanted - yaw), -maxStep, maxStep);
	}

	public static double clamp(double v, double lo, double hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}
