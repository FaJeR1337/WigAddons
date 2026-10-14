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
		return h.pick(List.of(mobs), new ZealotHunter.View(0, 1.62, 0, 0, 0), false, 40, now, "Zealot", "Special Zealot");
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
		assertNull(h.pick(List.of(mob(2, "Special Zealot", 30, 0)), new ZealotHunter.View(0, 1.62, 0, 0, 0), false, 20, 0, "Zealot", "Special Zealot")); // out of range
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

	@Test
	void ignoresTargetsThatNeedTheViewPitchedAlmostStraightUp() {
		// eyes at y=1.62; a mob 10 blocks up and 2 across needs about -79 degrees
		ZealotHunter.Mob above = new ZealotHunter.Mob(1, "Zealot", 2, 10, 0, 12);
		ZealotHunter.Mob ahead = new ZealotHunter.Mob(2, "Zealot", 8, 0, 0, 2.5);
		ZealotHunter.Mob gentle = new ZealotHunter.Mob(3, "Zealot", 5, 5, 0, 7); // about -47
		assertTrue(ZealotHunter.tooSteepUp(0, 1.62, 0, above, 65));
		assertFalse(ZealotHunter.tooSteepUp(0, 1.62, 0, ahead, 65));
		assertFalse(ZealotHunter.tooSteepUp(0, 1.62, 0, gentle, 65));
		assertFalse(ZealotHunter.tooSteepUp(0, 1.62, 0, above, 90)); // switched off
	}

	@Test
	void picksTheTargetNearestTheCrosshairInsteadOfTheNearest() {
		ZealotHunter h = new ZealotHunter();
		// looking south (yaw 0): one mob close behind us, one farther but almost dead ahead
		ZealotHunter.Mob behind = mob(1, "Zealot", 0, -2);
		ZealotHunter.Mob ahead = mob(2, "Zealot", 1, 15);
		ZealotHunter.View view = new ZealotHunter.View(0, 1.62, 0, 0, 0);
		assertEquals(2, h.pick(List.of(behind, ahead), view, true, 40, 0, "Zealot", "Special Zealot").id());
		assertEquals(1, new ZealotHunter().pick(List.of(behind, ahead), view, false, 40, 0, "Zealot", "Special Zealot").id());
		assertEquals(0, view.angleTo(0, 1.62, 10), 1e-6);
		assertEquals(90, view.angleTo(-10, 1.62, 0), 1e-6); // west is +90 yaw: to the right of south... either side is 90
	}

	@Test
	void aSpecialMobTakesSeveralClicksBeforeItIsAssumedDead() {
		ZealotHunter h = new ZealotHunter();
		assertFalse(h.clickLanded(7, 3));
		assertFalse(h.clickLanded(7, 3));
		assertTrue(h.clickLanded(7, 3));
		assertFalse(h.clickLanded(7, 3)); // counting starts again
		assertTrue(h.clickLanded(8, 1)); // an ordinary one is done at once
	}

	@Test
	void aTargetAboveCountsAsFurtherThanOneLevelWithYou() {
		ZealotHunter.View view = new ZealotHunter.View(0, 1.62, 0, 0, 0); // looking level, south
		ZealotHunter.Mob above = new ZealotHunter.Mob(1, "Zealot", 0, 8, 0, 9.0); // straight ahead but about 36 degrees up
		ZealotHunter.Mob level = new ZealotHunter.Mob(2, "Zealot", 8, 0, 0, 1.62); // to the side, 45 degrees round
		assertEquals(1, new ZealotHunter().pick(List.of(above, level), view, true, 0, 40, 0, "Zealot", "Special Zealot").id());
		assertEquals(2, new ZealotHunter().pick(List.of(above, level), view, true, 1.5, 40, 0, "Zealot", "Special Zealot").id());
	}

	@Test
	void knowsWhichTargetsAreBehindTheView() {
		ZealotHunter.View south = new ZealotHunter.View(0, 1.62, 0, 0, 0); // facing +z
		assertFalse(ZealotHunter.isBehind(south, mob(1, "Zealot", 0, 10), 100));
		assertFalse(ZealotHunter.isBehind(south, mob(2, "Zealot", 10, 3), 100)); // well off to the side, but not behind
		assertTrue(ZealotHunter.isBehind(south, mob(3, "Zealot", 1, -10), 100));
		assertTrue(ZealotHunter.isBehind(south, mob(4, "Zealot", -10, -3), 100));
	}
}
