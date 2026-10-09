package com.wiggox.wigaddons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ZealotHunterTest {
	private static ZealotHunter.Mob mob(int id, String name, double x, double z) {
		return new ZealotHunter.Mob(id, name, x, 0, z, 2.5);
	}

	private static ZealotHunter.Mob pick(ZealotHunter h, long now, ZealotHunter.Mob... mobs) {
		return h.pick(List.of(mobs), 0, 0, 40, now, "Zealot", "Special Zealot");
	}

	@Test
	void matchesVariationsIgnoringCase() {
		assertTrue(ZealotHunter.matches("[Lv55] Zealot Bruiser 13M", "Zealot"));
		assertTrue(ZealotHunter.matches("ZEALOT", "zealot"));
		assertFalse(ZealotHunter.matches("Enderman", "Zealot"));
		assertFalse(ZealotHunter.matches("Zealot", " "));
	}

	@Test
	void goesForTheNearestAndIgnoresOtherMobs() {
		ZealotHunter h = new ZealotHunter();
		assertEquals(2, pick(h, 0, mob(1, "Zealot", 10, 0), mob(2, "Zealot Bruiser", 5, 0), mob(3, "Enderman", 1, 0)).id());
		assertNull(pick(h, 0, mob(3, "Enderman", 1, 0)));
	}

	@Test
	void aSpecialZealotComesFirstEvenWhenFarther() {
		ZealotHunter h = new ZealotHunter();
		assertEquals(2, pick(h, 0, mob(1, "Zealot", 3, 0), mob(2, "Special Zealot", 30, 0)).id());
		assertNull(h.pick(List.of(mob(2, "Special Zealot", 30, 0)), 0, 0, 20, 0, "Zealot", "Special Zealot")); // out of range
	}

	@Test
	void keepsTheCurrentOneInsteadOfSwitchingToAnotherThatGotNearer() {
		ZealotHunter h = new ZealotHunter();
		assertEquals(1, pick(h, 0, mob(1, "Zealot", 5, 0), mob(2, "Zealot", 8, 0)).id());
		assertEquals(1, pick(h, 1, mob(1, "Zealot", 7, 0), mob(2, "Zealot", 4, 0)).id());
	}

	@Test
	void aMarkedMobIsLeftAloneUntilTheTimeIsUp() {
		ZealotHunter h = new ZealotHunter();
		h.markDead(1, 0, 5, 10, new Random(1));
		assertEquals(2, pick(h, 10, mob(1, "Zealot", 2, 0), mob(2, "Zealot", 9, 0)).id());
		assertTrue(h.isMarkedDead(1, 99)); // at most 10 s = 200 ticks, at least 100
		assertFalse(h.isMarkedDead(1, 201));
		assertEquals(1, pick(h, 201, mob(1, "Zealot", 2, 0)).id());
	}
}
