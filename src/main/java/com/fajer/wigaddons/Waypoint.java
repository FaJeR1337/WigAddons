package com.fajer.wigaddons;

/** A block position a route passes through. */
public record Waypoint(int x, int y, int z) {
	public double centerX() {
		return x + 0.5;
	}

	public double centerZ() {
		return z + 0.5;
	}

	/** "x y z", as shown and typed in the GUI. */
	public String format() {
		return x + " " + y + " " + z;
	}

	/** Reads "x y z" (whole numbers separated by spaces or commas), or null if it isn't that. */
	public static Waypoint parse(String text) {
		String[] parts = text.trim().split("[\\s,]+");
		if (parts.length != 3) {
			return null;
		}
		try {
			return new Waypoint(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
