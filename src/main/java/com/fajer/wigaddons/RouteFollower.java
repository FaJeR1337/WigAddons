package com.fajer.wigaddons;

import java.util.List;

/**
 * Works out, one tick at a time, how the player should steer along a route. It never stops at a waypoint: once the
 * player is within the route's radius of one, it counts as reached and the player turns toward the next one while
 * still running. It has no Minecraft types so it can be unit tested.
 */
public final class RouteFollower {
	public enum Status {
		RUNNING,
		/** The last waypoint was reached (never happens when looping). */
		FINISHED,
		/** The player has hardly moved for the stuck timeout. */
		STUCK
	}

	public record Output(double yaw, boolean jump, Status status) {
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
	private final WigConfig config;
	private final int stuckWindowTicks;

	private int target;
	private boolean started;
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
		this.config = config;
		this.stuckWindowTicks = Math.max(1, (int) Math.round(config.stuckTimeoutSeconds * 20));
	}

	/** Index of the waypoint being run to, 0-based. */
	public int targetIndex() {
		return target;
	}

	public Output tick(double px, double pz, double yaw, boolean onGround, boolean horizontalCollision) {
		if (!started) {
			started = true;
			refX = px;
			refZ = pz;
		}

		if (reachedTarget(px, pz) && !advance(px, pz)) {
			return new Output(yaw, false, Status.FINISHED);
		}

		Waypoint aim = points.get(target);
		double wanted = yawTo(aim.centerX() - px, aim.centerZ() - pz);
		double newYaw = yaw + clamp(wrapDegrees(wanted - yaw), -config.turnSpeedDegPerTick, config.turnSpeedDegPerTick);

		if (++ticksInWindow >= stuckWindowTicks) {
			if (Math.hypot(px - refX, pz - refZ) < 1.0) {
				return new Output(newYaw, false, Status.STUCK);
			}
			refX = px;
			refZ = pz;
			ticksInWindow = 0;
		}
		return new Output(newYaw, onGround && horizontalCollision, Status.RUNNING);
	}

	/** Reached when within the radius, or when it came that close and is now moving away (turning too wide to hit it). */
	private boolean reachedTarget(double px, double pz) {
		Waypoint t = points.get(target);
		double dist = Math.hypot(t.centerX() - px, t.centerZ() - pz);
		closest = Math.min(closest, dist);
		if (dist <= radius) {
			return true;
		}
		return closest <= Math.max(radius, MISS_WINDOW_MIN) && dist > closest + MOVING_AWAY;
	}

	/** Moves to the next waypoint. False when the route is over. */
	private boolean advance(double px, double pz) {
		Integer next = nextIndex();
		if (next == null) {
			return false;
		}
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
	static double yawTo(double dx, double dz) {
		return Math.toDegrees(Math.atan2(-dx, dz));
	}

	static double wrapDegrees(double degrees) {
		double d = degrees % 360.0;
		if (d >= 180.0) {
			d -= 360.0;
		}
		if (d < -180.0) {
			d += 360.0;
		}
		return d;
	}

	private static double clamp(double v, double lo, double hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}
