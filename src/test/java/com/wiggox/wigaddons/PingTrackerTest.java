package com.wiggox.wigaddons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PingTrackerTest {
	@Test
	void averagesTheSamplesAndIgnoresNoReading() {
		PingTracker t = new PingTracker();
		t.add(100);
		t.add(0); // no reading yet
		t.add(200);
		assertEquals(150.0, t.average(), 1e-9);
		assertEquals(2, t.samples());
	}

	@Test
	void saysNothingUntilThereAreEnoughSamples() {
		PingTracker t = new PingTracker();
		for (int i = 0; i < PingTracker.MIN_SAMPLES - 1; i++) {
			t.add(50);
		}
		assertFalse(t.above(500));
		t.add(50);
		assertTrue(t.above(500));
	}

	@Test
	void aboveMeansMoreThanTwentyPercentOverTheAverage() {
		PingTracker t = new PingTracker();
		for (int i = 0; i < 20; i++) {
			t.add(100);
		}
		assertFalse(t.above(100));
		assertFalse(t.above(60));
		assertFalse(t.above(120)); // exactly 20 percent over is not more than that
		assertTrue(t.above(121));
		t.reset();
		assertFalse(t.above(500));
	}
}
