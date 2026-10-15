package com.wiggox.wigaddons;

/** Places where a macro check is expected and so ignored: the portals into an area and where they put you. */
public final class SafeZones {
	private SafeZones() {
	}

	/**
	 * @param zones block coordinates (x, y, z) of each place
	 * @param radius how close counts as being at one, in blocks
	 */
	public static boolean inside(double x, double y, double z, double[][] zones, double radius) {
		if (zones == null) {
			return false;
		}
		for (double[] zone : zones) {
			if (zone == null || zone.length < 3) {
				continue;
			}
			double dx = x - (zone[0] + 0.5);
			double dz = z - (zone[2] + 0.5);
			if (Math.hypot(dx, dz) <= radius && Math.abs(y - zone[1]) <= radius + 1.0) {
				return true;
			}
		}
		return false;
	}
}
