package com.wiggox.wigaddons;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/** A* over the block grid: finds a way around walls for something that walks, steps up one block and drops at most two. */
public final class GridPath {
	static final int MAX_NODES = 3000;
	static final int MAX_RADIUS = 40;
	private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

	private GridPath() {
	}

	private record Node(int x, int y, int z, double g, double f, Node parent) {
	}

	private static long key(int x, int y, int z) {
		return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
	}

	private static boolean free(ObstacleAvoider.Terrain t, int x, int y, int z) {
		return !t.solid(x, y, z) && !t.solid(x, y + 1, z);
	}

	/** Standing room at the cell, with ground under it. */
	private static boolean standable(ObstacleAvoider.Terrain t, int x, int y, int z) {
		return free(t, x, y, z) && t.supports(x, y - 1, z);
	}

	/**
	 * @return the cell centres to walk through, start excluded, or null when no way was found within the search limits
	 */
	public static List<double[]> find(ObstacleAvoider.Terrain t, int sx, int sy, int sz, int gx, int gy, int gz, boolean allowStepUp) {
		return find(t, sx, sy, sz, gx, gy, gz, allowStepUp, 2);
	}

	/** @param maxDrop the longest fall that is taken as a shortcut, in blocks */
	public static List<double[]> find(ObstacleAvoider.Terrain t, int sx, int sy, int sz, int gx, int gy, int gz, boolean allowStepUp, int maxDrop) {
		PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f(), b.f()));
		Map<Long, Double> best = new HashMap<>();
		Node start = new Node(sx, sy, sz, 0, heuristic(sx, sy, sz, gx, gy, gz), null);
		open.add(start);
		best.put(key(sx, sy, sz), 0.0);
		int expanded = 0;
		while (!open.isEmpty() && expanded++ < MAX_NODES) {
			Node n = open.poll();
			if (n.g() > best.getOrDefault(key(n.x(), n.y(), n.z()), Double.MAX_VALUE)) {
				continue;
			}
			if (Math.abs(n.x() - gx) <= 1 && Math.abs(n.z() - gz) <= 1 && Math.abs(n.y() - gy) <= 1) {
				return build(n);
			}
			for (int[] d : DIRS) {
				int nx = n.x() + d[0];
				int nz = n.z() + d[1];
				if (Math.abs(nx - sx) > MAX_RADIUS || Math.abs(nz - sz) > MAX_RADIUS) {
					continue;
				}
				boolean diagonal = d[0] != 0 && d[1] != 0;
				for (int dy = allowStepUp ? 1 : 0; dy >= -maxDrop; dy--) {
					int ny = n.y() + dy;
					if (!standable(t, nx, ny, nz)) {
						continue;
					}
					if (dy == 1 && !free(t, n.x(), n.y() + 1, n.z())) {
						continue; // no headroom to jump up from here
					}
					if (dy < 0 && !fallsFree(t, nx, n.y(), ny, nz)) {
						continue; // the way out over the edge is blocked
					}
					if (diagonal && !(free(t, n.x() + d[0], ny > n.y() ? ny : n.y(), n.z()) && free(t, n.x(), ny > n.y() ? ny : n.y(), n.z() + d[1]))) {
						continue; // would clip a corner
					}
					double g = n.g() + (diagonal ? 1.41 : 1.0) + (dy != 0 ? 0.6 + (dy < -1 ? 0.4 * (-dy - 1) : 0) : 0) + (hugsWall(t, nx, ny, nz) ? 0.7 : 0);
					long k = key(nx, ny, nz);
					if (g < best.getOrDefault(k, Double.MAX_VALUE)) {
						best.put(k, g);
						open.add(new Node(nx, ny, nz, g, g + heuristic(nx, ny, nz, gx, gy, gz), n));
					}
					break; // one level per direction: the first that fits
				}
			}
		}
		return null;
	}

	/** Whether the body can fall straight down the column from standing height to landing height: feet and head room all the way. */
	private static boolean fallsFree(ObstacleAvoider.Terrain t, int x, int fromY, int toY, int z) {
		for (int y = fromY; y >= toY; y--) {
			if (!free(t, x, y, z)) {
				return false;
			}
		}
		return true;
	}

	/** A cell next to a wall: the body brushes it, so paths prefer cells with room. */
	private static boolean hugsWall(ObstacleAvoider.Terrain t, int x, int y, int z) {
		for (int[] d : DIRS) {
			if (t.solid(x + d[0], y, z + d[1]) || t.solid(x + d[0], y + 1, z + d[1])) {
				return true;
			}
		}
		return false;
	}

	private static double heuristic(int x, int y, int z, int gx, int gy, int gz) {
		double dx = Math.abs(x - gx);
		double dz = Math.abs(z - gz);
		return Math.max(dx, dz) + 0.41 * Math.min(dx, dz) + Math.abs(y - gy) * 0.3;
	}

	private static List<double[]> build(Node end) {
		List<double[]> out = new ArrayList<>();
		for (Node n = end; n.parent() != null; n = n.parent()) {
			out.add(new double[] {n.x() + 0.5, n.y(), n.z() + 0.5});
		}
		Collections.reverse(out);
		return out;
	}
}
