package com.wiggox.wigaddons;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The hops of an Aspect of the End or Void (a teleport of a fixed length along the view) from where the player is to a
 * spot, through open air: a chain of points, each a full hop from the one before, with clear room for the player all the
 * way along every hop.
 */
public final class TeleportPath {
	/** Whether the cell has nothing in it to stop a teleporting player. */
	public interface Air {
		boolean clear(int x, int y, int z);
	}

	private static final double SAMPLE_STEP = 0.4;
	/** Half the player's width, so the corners of the body are checked as well as the middle. */
	private static final double HALF_WIDTH = 0.3;
	private static final int MAX_HOPS = 40;
	private static final double[] YAW_OFFSETS = {0, 8, -8, 16, -16, 28, -28, 42, -42, 60, -60, 80, -80};
	private static final double[] PITCH_OFFSETS = {0, 12, -12, 25, -25, 40, -40, 55, -55};
	private static final List<double[]> OFFSETS = new ArrayList<>();

	static {
		for (double yaw : YAW_OFFSETS) {
			for (double pitch : PITCH_OFFSETS) {
				OFFSETS.add(new double[] {yaw, pitch});
			}
		}
		OFFSETS.sort(Comparator.comparingDouble(o -> Math.abs(o[0]) + Math.abs(o[1])));
	}

	private TeleportPath() {
	}

	/**
	 * The points the player is teleported to, in order, to end up within {@code radius} blocks (sideways) of {@code goal}.
	 * Every hop is exactly {@code hop} blocks long, so the last one may carry past the goal; a hop is only made where it
	 * brings the player closer. An empty list means no hop is needed or worth making (the rest is walked), and null means
	 * there is no way through the air.
	 *
	 * @param from where the player's feet are
	 * @param goal the spot to get to
	 * @param hop how many blocks one teleport carries
	 * @param radius how close to the goal, sideways, is close enough
	 */
	public static List<double[]> plan(double[] from, double[] goal, double hop, double radius, Air air) {
		List<double[]> hops = new ArrayList<>();
		double[] at = from.clone();
		for (int i = 0; i < MAX_HOPS; i++) {
			double distance = Math.hypot(goal[0] - at[0], goal[2] - at[2]);
			if (distance <= radius || distance <= hop / 2) {
				return hops; // there, or too near for a hop to help: the rest is walked
			}
			double dx = goal[0] - at[0];
			double dy = goal[1] - at[1];
			double dz = goal[2] - at[2];
			double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
			double azimuth = Math.atan2(dz, dx);
			double elevation = Math.asin(dy / length);
			double[] next = null;
			for (double[] offset : OFFSETS) {
				double a = azimuth + Math.toRadians(offset[0]);
				double e = Math.max(-Math.PI / 2 + 0.05, Math.min(Math.PI / 2 - 0.05, elevation + Math.toRadians(offset[1])));
				double[] candidate = {at[0] + hop * Math.cos(e) * Math.cos(a), at[1] + hop * Math.sin(e), at[2] + hop * Math.cos(e) * Math.sin(a)};
				double after = Math.hypot(goal[0] - candidate[0], goal[2] - candidate[2]);
				if (after >= distance - 0.5 && after > radius) {
					continue; // no nearer, and not there either
				}
				if (clearLine(at, candidate, air)) {
					next = candidate;
					break;
				}
			}
			if (next == null) {
				return null;
			}
			hops.add(next);
			at = next;
		}
		return null;
	}

	/** Whether a player can be teleported from one point to the other: room for the whole body at every step. */
	static boolean clearLine(double[] from, double[] to, Air air) {
		double length = Math.sqrt(Math.pow(to[0] - from[0], 2) + Math.pow(to[1] - from[1], 2) + Math.pow(to[2] - from[2], 2));
		int steps = Math.max(1, (int) Math.ceil(length / SAMPLE_STEP));
		for (int i = 1; i <= steps; i++) {
			double t = i / (double) steps;
			double x = from[0] + (to[0] - from[0]) * t;
			double y = from[1] + (to[1] - from[1]) * t;
			double z = from[2] + (to[2] - from[2]) * t;
			for (double ox : new double[] {-HALF_WIDTH, HALF_WIDTH}) {
				for (double oz : new double[] {-HALF_WIDTH, HALF_WIDTH}) {
					for (double oy : new double[] {0.0, 0.9, 1.79}) {
						if (!air.clear((int) Math.floor(x + ox), (int) Math.floor(y + oy), (int) Math.floor(z + oz))) {
							return false;
						}
					}
				}
			}
		}
		return true;
	}
}
