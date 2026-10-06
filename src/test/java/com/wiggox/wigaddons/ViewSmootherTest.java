package com.wiggox.wigaddons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ViewSmootherTest {
	@Test
	void easesInAndOutAndNeverOvershoots() {
		ViewSmoother s = new ViewSmoother();
		double left = 60;
		double previous = 0;
		double first = 0;
		for (int frame = 0; frame < 400 && left > 0.001; frame++) {
			double step = s.step(left, 0.3, 4, 10);
			if (frame == 0) {
				first = step;
			}
			assertTrue(step >= 0 && step <= left + 1e-9, "no backwards or overshooting step");
			assertTrue(step <= 10 * 0.3 + 1e-9, "never faster than the turn speed");
			previous = step;
			left -= step;
		}
		assertTrue(first < 1.0, "starts gently, first step " + first);
		assertTrue(left < 0.5, "settles on the aim, " + left + " left");
		assertTrue(previous < 1.0, "ends gently");
	}

	@Test
	void zeroSmoothingTurnsAtFullSpeed() {
		ViewSmoother s = new ViewSmoother();
		assertEquals(3.0, s.step(60, 0.3, 0, 10), 1e-9);
		assertEquals(0.5, s.step(0.5, 0.3, 0, 10), 1e-9);
	}
}
