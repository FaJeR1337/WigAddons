package com.wiggox.wigaddons;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TeleportTest {
	@Test
	void walkingSprintingAndSmallPushesAreNotTeleports() {
		assertFalse(Teleport.teleported(0.3, 0.3));
		assertFalse(Teleport.teleported(0.6, 0.5));
		assertFalse(Teleport.teleported(3.0, 0)); // exactly three blocks is not more than three
		assertFalse(Teleport.teleported(2.0, 0));
	}

	@Test
	void aLongFallIsNotATeleport() {
		assertFalse(Teleport.teleported(3.9, 3.9)); // terminal velocity
		assertFalse(Teleport.teleported(3.5, 3.4));
	}

	@Test
	void aJumpOfMoreThanThreeBlocksFromStandingIsATeleport() {
		assertTrue(Teleport.teleported(3.5, 0.2)); // a short hop with an ability
		assertTrue(Teleport.teleported(8.0, 0.3)); // aspect of the end
		assertTrue(Teleport.teleported(250.0, 0)); // another island
		assertTrue(Teleport.teleported(12.0, 3.9)); // even while falling, when it is far more than the fall
	}
}
