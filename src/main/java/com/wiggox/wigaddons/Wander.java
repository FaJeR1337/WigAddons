package com.wiggox.wigaddons;

import java.util.Random;

/** Picks somewhere to stroll to around a spot, for when there is nothing to do. */
public final class Wander {
	private Wander() {
	}

	/**
	 * @return a spot (x, y, z of the block's centre, standing level) between minDistance and radius blocks from the home
	 *         spot with room to stand and ground underneath, or null when ten tries found none
	 */
	public static double[] pick(ObstacleAvoider.Terrain terrain, double homeX, double homeY, double homeZ, double radius, double minDistance, Random random) {
		if (radius <= 0) {
			return null;
		}
		double lo = Math.min(minDistance, radius);
		for (int attempt = 0; attempt < 10; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = lo + random.nextDouble() * (radius - lo);
			int x = (int) Math.floor(homeX + Math.cos(angle) * distance);
			int z = (int) Math.floor(homeZ + Math.sin(angle) * distance);
			int baseY = (int) Math.floor(homeY + 0.01);
			for (int dy = 1; dy >= -2; dy--) {
				int y = baseY + dy;
				if (!terrain.solid(x, y, z) && !terrain.solid(x, y + 1, z) && terrain.supports(x, y - 1, z)) {
					return new double[] {x + 0.5, y, z + 0.5};
				}
			}
		}
		return null;
	}
}
