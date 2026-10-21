package com.wiggox.wigaddons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TpsTrackerTest {
	private static final long SECOND = 1_000_000_000L;

	@Test
	void aServerTickingAtTwentyIsNotLow() {
		TpsTracker t = new TpsTracker();
		for (int i = 0; i < 10; i++) {
			t.onTime(i * 20L, i * SECOND);
		}
		assertEquals(20.0, t.tps(), 0.01);
		assertFalse(t.low(9 * SECOND + SECOND / 2));
	}

	@Test
	void aSlowServerIsLow() {
		TpsTracker t = new TpsTracker();
		// 20 ticks arriving every 1.5 seconds is about 13 a second
		for (int i = 0; i < 10; i++) {
			t.onTime(i * 20L, (long) (i * 1.5 * SECOND));
		}
		assertTrue(t.tps() < 15);
		assertTrue(t.low(14 * SECOND));
	}

	@Test
	void noUpdatesForAWhileIsLow() {
		TpsTracker t = new TpsTracker();
		t.onTime(0, 0);
		t.onTime(20, SECOND);
		assertFalse(t.low(SECOND + SECOND));
		assertTrue(t.low(SECOND + 4 * SECOND));
	}

	@Test
	void nothingMeasuredYetIsNotLow() {
		assertFalse(new TpsTracker().low(100 * SECOND));
	}

	@Test
	void updatesBunchedTogetherAreNotTakenForAFastOrSlowServer() {
		TpsTracker t = new TpsTracker();
		t.onTime(0, 0);
		t.onTime(20, SECOND);
		t.onTime(40, SECOND + 50_000_000L); // arrives with the last one: ignored
		t.onTime(60, 2 * SECOND + 50_000_000L);
		assertEquals(20.0, t.tps(), 0.5);
	}
}
