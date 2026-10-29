package com.wiggox.wigaddons;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The hops of an Aspect of the End or Void (a teleport of a fixed length along the view) from where the player is to a
 * spot, through open air: a chain of landing blocks, each a full hop from the one before, with clear room for the player
 * all the way along every hop. The server puts the player in the exact middle of the block the hop ends in (on top of
 * it, in the air or not), so every landing here is the middle of a block cell, with its feet at the cell's floor.
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
	/** A hop along a walking route lands this many blocks above the route's cell (or on it): up in the air over the way, not on it. */
	private static final double LIFT = 2.0;
	/** How many hops the search may try before it gives up on a way. */
	private static final int SEARCH_BUDGET = 3000;
	/** The most a hop may turn away from the one before it, so the route bends gently; given up on only where nothing else is clear. */
	private static final double MAX_TURN_DEGREES = 30;
	private static final double MIN_TURN_COS = Math.cos(Math.toRadians(MAX_TURN_DEGREES));
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
	 * The hops that take the player to within {@code radius} blocks (sideways) of {@code goal}, in order. Each is
	 * {x, y, z, dx, dy, dz}: where the feet land (the middle of a block, on its floor) and the look direction (a unit
	 * vector) that gives that landing from the point before it. Every hop carries {@code hop} blocks along the view, so
	 * the last one may carry past the goal; a hop is only made where it brings the player closer. An empty list means no
	 * hop is needed or worth making (the rest is walked), and null means there is no way through the air.
	 *
	 * @param from where the player's feet are
	 * @param goal the spot to get to
	 * @param hop how many blocks one teleport carries
	 * @param radius how close to the goal, sideways, is close enough
	 */
	public static List<double[]> plan(double[] from, double[] goal, double hop, double radius, Air air) {
		// first a route that only ever bends gently, found by trying the options in order and backing up out of dead ends,
		// then, where there is none, one with sharper turns
		List<double[]> gentle = search(from, null, goal, hop, radius, air, 0, new int[] {SEARCH_BUDGET}, true);
		if (gentle != null) {
			return gentle;
		}
		return search(from, null, goal, hop, radius, air, 0, new int[] {SEARCH_BUDGET}, false);
	}

	/**
	 * Like {@link #plan} but only a route that bends gently all the way (no turn of more than 30 degrees from one hop to
	 * the next), or null. A route like that is as good as a straight one.
	 */
	public static List<double[]> planGentle(double[] from, double[] goal, double hop, double radius, Air air) {
		return search(from, null, goal, hop, radius, air, 0, new int[] {SEARCH_BUDGET}, true);
	}

	/**
	 * Hops that land on the way a walker would take: each lands on (or next to) a point of {@code guide}, the cells to
	 * walk through in order, as far along it as a clear hop reaches. The list can stop short of the goal where no hop
	 * lands on the way any further (the rest is walked), and is empty where none does at all.
	 */
	public static List<double[]> planAlong(double[] from, List<double[]> guide, double[] goal, double hop, double radius, Air air) {
		return planAlong(from, guide, goal, hop, radius, air, false);
	}

	/** As above; with {@code straightOnly}, a hop is only made where the way it skips is a straight line (every cell of it within a block and a half of the hop). */
	public static List<double[]> planAlong(double[] from, List<double[]> guide, double[] goal, double hop, double radius, Air air, boolean straightOnly) {
		List<double[]> hops = new ArrayList<>();
		double[] at = from.clone();
		int fromIndex = 0;
		for (int n = 0; n < MAX_HOPS; n++) {
			if (Math.hypot(goal[0] - at[0], goal[2] - at[2]) <= radius) {
				break;
			}
			double[] best = null;
			int bestIndex = -1;
			for (int i = guide.size() - 1; i >= fromIndex && best == null; i--) {
				double[] g = guide.get(i);
				double dx = g[0] - at[0];
				double dy = g[1] + LIFT - at[1]; // aim at the spot above the route
				double dz = g[2] - at[2];
				double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
				if (Math.abs(length - hop) > 1.5) {
					continue; // not about one hop away
				}
				double[] direction = {dx / length, dy / length, dz / length};
				double[] landing = {Math.floor(at[0] + hop * direction[0]) + 0.5, Math.floor(at[1] + hop * direction[1] + 1e-3), Math.floor(at[2] + hop * direction[2]) + 0.5,
					direction[0], direction[1], direction[2]};
				if (Math.abs(landing[0] - g[0]) <= 1.0 && Math.abs(landing[2] - g[2]) <= 1.0 && landing[1] >= g[1] && landing[1] <= g[1] + LIFT
					&& Math.hypot(goal[0] - landing[0], goal[2] - landing[2]) < Math.hypot(goal[0] - at[0], goal[2] - at[2]) - 0.5
					&& clearLine(at, landing, air) && (!straightOnly || straight(at, landing, guide, fromIndex, i))) {
					best = landing;
					bestIndex = i;
				}
			}
			if (best == null) {
				break;
			}
			hops.add(best);
			at = new double[] {best[0], best[1], best[2]};
			fromIndex = bestIndex + 1;
		}
		return hops;
	}

	/** The hops from here on, or null if there is no way (or the search ran out of its budget). */
	private static List<double[]> search(double[] at, double[] previous, double[] goal, double hop, double radius, Air air,
		int depth, int[] budget, boolean limitTurn) {
		double distance = Math.hypot(goal[0] - at[0], goal[2] - at[2]);
		if (distance <= radius || distance <= hop / 2) {
			return new ArrayList<>(); // there, or too near for a hop to help: the rest is walked
		}
		if (depth >= MAX_HOPS) {
			return null;
		}
		double dx = goal[0] - at[0];
		double dy = goal[1] - at[1];
		double dz = goal[2] - at[2];
		double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
		double azimuth = Math.atan2(dz, dx);
		double elevation = Math.asin(dy / length);
		for (double[] offset : OFFSETS) {
			if (budget[0] <= 0) {
				return null;
			}
			double a = azimuth + Math.toRadians(offset[0]);
			double e = Math.max(-Math.PI / 2 + 0.05, Math.min(Math.PI / 2 - 0.05, elevation + Math.toRadians(offset[1])));
			double[] direction = {Math.cos(e) * Math.cos(a), Math.sin(e), Math.cos(e) * Math.sin(a)};
			if (limitTurn && previous != null
				&& direction[0] * previous[0] + direction[1] * previous[1] + direction[2] * previous[2] < MIN_TURN_COS) {
				continue; // too sharp a turn from the last hop
			}
			// the hop ends in a block cell, and the player stands in the middle of it, on its floor
			double[] candidate = {Math.floor(at[0] + hop * direction[0]) + 0.5, Math.floor(at[1] + hop * direction[1] + 1e-3), Math.floor(at[2] + hop * direction[2]) + 0.5,
				direction[0], direction[1], direction[2]};
			double after = Math.hypot(goal[0] - candidate[0], goal[2] - candidate[2]);
			if (after >= distance - 0.5 && after > radius) {
				continue; // no nearer, and not there either
			}
			budget[0]--;
			if (!clearLine(at, candidate, air)) {
				continue;
			}
			List<double[]> rest = search(new double[] {candidate[0], candidate[1], candidate[2]}, direction, goal, hop, radius, air, depth + 1, budget, limitTurn);
			if (rest != null) {
				rest.add(0, candidate);
				return rest;
			}
		}
		return null;
	}

	/** Whether the guide's cells from one index to another all lie within a block and a half of the line from one point to the other. */
	private static boolean straight(double[] from, double[] to, List<double[]> guide, int fromIndex, int toIndex) {
		double[] line = {to[0] - from[0], to[1] - from[1], to[2] - from[2]};
		double lengthSquared = line[0] * line[0] + line[1] * line[1] + line[2] * line[2];
		for (int j = fromIndex; j <= toIndex; j++) {
			double[] g = guide.get(j);
			double t = lengthSquared == 0 ? 0 : Math.max(0, Math.min(1, ((g[0] - from[0]) * line[0] + (g[1] - from[1]) * line[1] + (g[2] - from[2]) * line[2]) / lengthSquared));
			double dx = g[0] - (from[0] + line[0] * t);
			double dy = g[1] - (from[1] + line[1] * t);
			double dz = g[2] - (from[2] + line[2] * t);
			if (dx * dx + dy * dy + dz * dz > 2.25) {
				return false;
			}
		}
		return true;
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
