package com.fajer.wigaddons;

import java.util.List;

/**
 * Works out, one tick at a time, how the player should steer along a route. It never stops at a waypoint: it
 * keeps running and starts turning toward the next waypoint while still near the current one. It has no
 * Minecraft types so it can be unit tested.
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

	private final List<Waypoint> points;
	private final boolean loop;
	private final WigConfig config;
	private final int stuckWindowTicks;

	private int target;
	private boolean started;
	private double prevX;
	private double prevZ;
	private double refX;
	private double refZ;
	private int ticksInWindow;

	public RouteFollower(List<Waypoint> points, boolean loop, WigConfig config) {
		if (points.isEmpty()) {
			throw new IllegalArgumentException("a route needs at least one waypoint");
		}
		this.points = List.copyOf(points);
		this.loop = loop;
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
			prevX = px;
			prevZ = pz;
			refX = px;
			refZ = pz;
		}

		if (reachedTarget(px, pz) && !advance(px, pz)) {
			return new Output(yaw, false, Status.FINISHED);
		}

		Waypoint aim = points.get(target);
		Waypoint t = points.get(target);
		if (Math.hypot(t.centerX() - px, t.centerZ() - pz) <= config.turnAheadDistance) {
			Integer next = nextIndex();
			if (next != null) {
				aim = points.get(next);
			}
		}
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

	/** Reached when close enough, or when already past it (so turning early can't make it miss the radius). */
	private boolean reachedTarget(double px, double pz) {
		Waypoint t = points.get(target);
		double dx = t.centerX() - px;
		double dz = t.centerZ() - pz;
		double dist = Math.hypot(dx, dz);
		if (dist <= config.arriveRadius) {
			return true;
		}
		if (dist > config.turnAheadDistance) {
			return false;
		}
		double segX = t.centerX() - prevX;
		double segZ = t.centerZ() - prevZ;
		if (Math.hypot(segX, segZ) < 1e-6) {
			return false;
		}
		return dx * segX + dz * segZ <= 0;
	}

	/** Moves to the next waypoint. False when the route is over. */
	private boolean advance(double px, double pz) {
		Waypoint t = points.get(target);
		prevX = t.centerX();
		prevZ = t.centerZ();
		Integer next = nextIndex();
		if (next == null) {
			return false;
		}
		target = next;
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
