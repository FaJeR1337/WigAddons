package com.wiggox.wigaddons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ItemSearchTest {
	private static final List<String> IDS = List.of("diamond", "diamond_pickaxe", "diamond_sword", "golden_pickaxe", "stone_pickaxe", "oak_log", "stone");

	@Test
	void anExactIdWins() {
		ItemSearch.Result r = ItemSearch.find(IDS, "diamond");
		assertEquals("diamond", r.best());
		assertTrue(r.exact());
	}

	@Test
	void minecraftPrefixCaseAndSpacesAreNormalised() {
		assertEquals("diamond_pickaxe", ItemSearch.find(IDS, "minecraft:Diamond_Pickaxe").best());
		assertEquals("diamond_pickaxe", ItemSearch.find(IDS, "  Diamond Pickaxe ").best());
		assertEquals("diamond_pickaxe", ItemSearch.normalise("minecraft:diamond_pickaxe"));
	}

	@Test
	void startingWithBeatsContainingAndTheFirstAlphabeticalIsPicked() {
		ItemSearch.Result r = ItemSearch.find(IDS, "stone");
		assertEquals("stone", r.best()); // exact beats stone_pickaxe
		ItemSearch.Result pick = ItemSearch.find(IDS, "pick");
		assertEquals("diamond_pickaxe", pick.best()); // none start with it: first containing it
		assertEquals(3, pick.count());
		assertFalse(pick.exact());
		ItemSearch.Result d = ItemSearch.find(IDS, "dia");
		assertEquals("diamond", d.best());
		assertEquals(3, d.count());
	}

	@Test
	void noMatchOrEmptyTextGivesNothing() {
		assertNull(ItemSearch.find(IDS, "zzz").best());
		assertEquals(0, ItemSearch.find(IDS, "zzz").count());
		assertNull(ItemSearch.find(IDS, "   ").best());
	}
}
