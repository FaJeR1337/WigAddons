package com.fajer.wigaddons;

/** A block position a route passes through. */
public record Waypoint(int x, int y, int z) {
	public double centerX() {
		return x + 0.5;
	}

	public double centerZ() {
		return z + 0.5;
	}
}
