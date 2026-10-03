package com.fajer.wigaddons;

import java.util.List;

/**
 * Works out, one tick at a time, where the player should be heading along a route. It never stops at a waypoint.
 * Once the player is within the route's radius of a waypoint, the point it aims at slides from that waypoint toward
 * the next one, so the path bends smoothly instead of having a corner. It has no Minecraft types so it can be unit
 * tested. Turning the view toward the aim is up to the caller.
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
		if (points.isEmpty()) {
			throw new IllegalArgumentException("a route needs at least one waypoint");
		}
		this.points = List.copyOf(points);
		this.loop = loop;
		this.radius = Math.max(radius, MIN_ARRIVE_RADIUS);
		this.stuckWindowTicks = Math.max(1, (int) Math.round(config.stuckTimeoutSeconds * 20));
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

		Waypoint t = points.get(target);
		double dist = Math.hypot(t.centerX() - px, t.centerZ() - pz);
		double aimX = t.centerX();
		double aimZ = t.centerZ();
		double blend = 0;
		Integer next = nextIndex();
		if (next != null && dist < radius) {
			blend = 1 - dist / radius;
			aimX += (points.get(next).centerX() - aimX) * blend;
			aimZ += (points.get(next).centerZ() - aimZ) * blend;
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
		Waypoint t = points.get(target);
		double dist = Math.hypot(t.centerX() - px, t.centerZ() - pz);
		closest = Math.min(closest, dist);
		Integer next = nextIndex();
		if (next == null) {
			return dist <= radius;
		}
		if (dist <= MIN_ARRIVE_RADIUS) {
			return true;
		}
		if (dist <= radius) {
			double inX = t.centerX() - prevX;
			double inZ = t.centerZ() - prevZ;
			if (Math.hypot(inX, inZ) > 1e-6 && (px - t.centerX()) * inX + (pz - t.centerZ()) * inZ >= 0) {
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
		prevX = points.get(target).centerX();
		prevZ = points.get(target).centerZ();
		target = next;
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
