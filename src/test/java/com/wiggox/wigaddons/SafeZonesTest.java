package com.wiggox.wigaddons;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SafeZonesTest {
	private static final double[][] ZONES = {{-617, 5, -269}, {-617, 5, -282}, {618, 85, -203}};

	@Test
	void withinThreeBlocksOfAZoneCounts() {
		assertTrue(SafeZones.inside(-616.5, 5, -268.5, ZONES, 3));
		assertTrue(SafeZones.inside(-614.0, 5, -281.5, ZONES, 3));
		assertTrue(SafeZones.inside(618.5, 85, -200.0, ZONES, 3));
	}

	@Test
	void furtherAwayOrAtAnotherHeightDoesNot() {
		assertFalse(SafeZones.inside(-610.0, 5, -269.0, ZONES, 3));
		assertFalse(SafeZones.inside(-616.5, 30, -268.5, ZONES, 3)); // right above, but 25 blocks up
		assertFalse(SafeZones.inside(0, 64, 0, ZONES, 3));
		assertFalse(SafeZones.inside(0, 64, 0, null, 3));
	}
}
