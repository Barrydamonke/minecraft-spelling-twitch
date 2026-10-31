package com.barrydamonke.twitchspelling.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TimeFormatTest {
	@Test
	void igtUnderAnHour() {
		assertEquals("0:00.00", TimeFormat.igt(0, 0));
		assertEquals("0:01.00", TimeFormat.igt(20, 0));
		assertEquals("0:01.02", TimeFormat.igt(20, 0.5f));
		assertEquals("12:34.55", TimeFormat.igt((12 * 60 + 34) * 20 + 11, 0));
	}

	@Test
	void igtOverAnHour() {
		assertEquals("1:02:03.00", TimeFormat.igt((3600 + 2 * 60 + 3) * 20, 0));
	}

	@Test
	void countdownRoundsUp() {
		assertEquals("10:00", TimeFormat.countdown(600 * 20));
		assertEquals("0:01", TimeFormat.countdown(1));
		assertEquals("7:32", TimeFormat.countdown((7 * 60 + 32) * 20));
		assertEquals("1:00:00", TimeFormat.countdown(3600 * 20));
	}
}
