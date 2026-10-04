package com.wiggox.wigaddons;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TimeFormatTest {
	@Test
	void showsMinutesAndSecondsUnderAnHour() {
		assertEquals("00:00", TimeFormat.clock(0));
		assertEquals("00:09", TimeFormat.clock(9));
		assertEquals("01:23", TimeFormat.clock(83));
		assertEquals("59:59", TimeFormat.clock(3599));
	}

	@Test
	void addsHoursFromAnHourOn() {
		assertEquals("1:00:00", TimeFormat.clock(3600));
		assertEquals("1:02:03", TimeFormat.clock(3723));
		assertEquals("12:34:56", TimeFormat.clock(12 * 3600 + 34 * 60 + 56));
	}

	@Test
	void negativeTimesCountAsZero() {
		assertEquals("00:00", TimeFormat.clock(-5));
	}
}
