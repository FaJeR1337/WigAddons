package com.wiggox.wigaddons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RouteStoreTest {
	@TempDir
	Path dir;

	@Test
	void namesAreLimitedToSafeCharacters() {
		assertTrue(RouteStore.isValidName("farm_1-a"));
		assertFalse(RouteStore.isValidName(""));
		assertFalse(RouteStore.isValidName("a b"));
		assertFalse(RouteStore.isValidName("../x"));
		assertFalse(RouteStore.isValidName("a".repeat(33)));
		assertFalse(RouteStore.isValidName("config"));
		assertFalse(RouteStore.isValidName("CONFIG"));
	}

	@Test
	void saveThenLoadKeepsWaypointsInOrder() throws IOException {
		RouteStore store = new RouteStore(dir.resolve("WigAddons Routes"));
		Route route = new Route("mine", List.of(new Waypoint(1, 64, -3), new Waypoint(10, 70, 5)));
		store.save(route);
		Route loaded = store.load("mine");
		assertEquals(route.waypoints(), loaded.waypoints());
	}

	@Test
	void listsOnlyRoutesNotTheConfigFile() throws IOException {
		RouteStore store = new RouteStore(dir);
		store.save(new Route("b"));
		store.save(new Route("A"));
		new WigConfig().save(dir.resolve("config.json"));
		assertEquals(List.of("A", "b"), store.names());
	}

	@Test
	void radiusIsSavedWithTheRouteAndDefaultsWhenMissing() throws IOException {
		RouteStore store = new RouteStore(dir);
		Route route = new Route("r", List.of(new Waypoint(0, 0, 0)));
		route.setRadius(0.5);
		store.save(route);
		assertEquals(0.5, store.load("r").radius());
		java.nio.file.Files.writeString(dir.resolve("old.json"), "{\"waypoints\": [[1,2,3]]}");
		assertEquals(Route.DEFAULT_RADIUS, store.load("old").radius());
	}

	@Test
	void pitchAndRandomizeAreSavedAndCanBeCleared() throws IOException {
		RouteStore store = new RouteStore(dir);
		Route route = new Route("p", List.of(new Waypoint(0, 0, 0)));
		assertNull(route.pitch());
		route.setPitch(25.0, true);
		store.save(route);
		Route loaded = store.load("p");
		assertEquals(25.0, loaded.pitch());
		assertTrue(loaded.randomizePitch());
		loaded.setPitch(null, true);
		assertNull(loaded.pitch());
		assertFalse(loaded.randomizePitch());
		store.save(loaded);
		assertNull(store.load("p").pitch());
	}

	@Test
	void heldMouseButtonsAreSavedPerRoute() throws IOException {
		RouteStore store = new RouteStore(dir);
		Route route = new Route("c", List.of(new Waypoint(0, 0, 0)));
		assertFalse(route.holdLeft());
		assertFalse(route.holdRight());
		route.setHoldLeft(true);
		store.save(route);
		Route loaded = store.load("c");
		assertTrue(loaded.holdLeft());
		assertFalse(loaded.holdRight());
		loaded.setHoldRight(true);
		loaded.setHoldLeft(false);
		store.save(loaded);
		Route again = store.load("c");
		assertFalse(again.holdLeft());
		assertTrue(again.holdRight());
		// a route saved before this existed has neither
		java.nio.file.Files.writeString(dir.resolve("old.json"), "{\"waypoints\": []}");
		assertFalse(store.load("old").holdLeft());
	}

	@Test
	void displayIconAndOverridesAreSavedPerRoute() throws IOException {
		RouteStore store = new RouteStore(dir);
		Route route = new Route("d", List.of(new Waypoint(0, 0, 0)));
		route.setIcon(" diamond_pickaxe ");
		route.setTurnSpeed(6.0);
		route.setHumanizeOffset(0.0);
		route.setShowWaypoints(true);
		route.setBoxColor(0xFF8800);
		route.setShowRadius(false);
		route.setShowNumbers(false);
		route.setShowLines(false);
		store.save(route);
		Route loaded = store.load("d");
		assertEquals("diamond_pickaxe", loaded.icon());
		assertEquals(6.0, loaded.turnSpeed());
		assertEquals(0.0, loaded.humanizeOffset());
		assertTrue(loaded.showWaypoints());
		assertEquals(0xFF8800, loaded.boxColor());
		assertFalse(loaded.showRadius());
		assertFalse(loaded.showNumbers());
		assertFalse(loaded.showLines());
	}

	@Test
	void aRouteSavedBeforeTheseExistedGetsTheDefaults() throws IOException {
		java.nio.file.Files.writeString(dir.resolve("old.json"), "{\"radius\": 3, \"waypoints\": [[1,2,3]]}");
		Route old = new RouteStore(dir).load("old");
		assertEquals("", old.icon());
		assertNull(old.turnSpeed());
		assertNull(old.humanizeOffset());
		assertFalse(old.showWaypoints());
		assertEquals(Route.DEFAULT_BOX_COLOR, old.boxColor());
		assertTrue(old.showRadius() && old.showNumbers() && old.showLines());
	}

	@Test
	void overridesFallBackToTheGlobalSettings() {
		WigConfig cfg = new WigConfig();
		Route route = new Route("r");
		assertEquals(cfg.turnSpeedDegPerTick, route.effectiveTurnSpeed(cfg));
		assertEquals(cfg.humanizeOffset, route.effectiveHumanizeOffset(cfg));
		route.setTurnSpeed(4.0);
		route.setHumanizeOffset(0.0);
		assertEquals(4.0, route.effectiveTurnSpeed(cfg));
		assertEquals(0.0, route.effectiveHumanizeOffset(cfg));
		route.setTurnSpeed(0.0); // not above zero: back to the global one
		route.setHumanizeOffset(-1.0);
		assertNull(route.turnSpeed());
		assertNull(route.humanizeOffset());
	}

	@Test
	void waypointsCanBeMovedAndTextParsed() {
		Route route = new Route("m", List.of(new Waypoint(1, 1, 1), new Waypoint(2, 2, 2), new Waypoint(3, 3, 3)));
		assertTrue(route.moveWaypoint(0, 1));
		assertEquals(new Waypoint(2, 2, 2), route.waypoints().get(0));
		assertEquals(new Waypoint(1, 1, 1), route.waypoints().get(1));
		assertFalse(route.moveWaypoint(0, -1));
		assertFalse(route.moveWaypoint(2, 1));
		assertFalse(route.moveWaypoint(5, -1));
		assertEquals(new Waypoint(10, -64, 300), Waypoint.parse(" 10  -64, 300 "));
		assertNull(Waypoint.parse("10 20"));
		assertNull(Waypoint.parse("a b c"));
		assertNull(Waypoint.parse("1 2 3 4"));
		assertEquals("10 -64 300", new Waypoint(10, -64, 300).format());
	}

	@Test
	void hudSettingsAreRepairedWhenHandEdited() throws IOException {
		Path file = dir.resolve("config.json");
		java.nio.file.Files.writeString(file, "{\"hudX\": 7, \"hudY\": -3, \"hudScale\": 99, \"showHud\": false}");
		WigConfig cfg = WigConfig.load(file);
		assertEquals(1.0, cfg.hudX);
		assertEquals(0.0, cfg.hudY);
		assertEquals(4.0, cfg.hudScale);
		assertFalse(cfg.showHud);
		assertTrue(new WigConfig().showHud);
	}

	@Test
	void neverJumpIsSavedPerRouteAndOffByDefault() throws IOException {
		RouteStore store = new RouteStore(dir);
		Route route = new Route("nj", List.of(new Waypoint(0, 0, 0)));
		assertFalse(route.noJump());
		route.setNoJump(true);
		store.save(route);
		assertTrue(store.load("nj").noJump());
		java.nio.file.Files.writeString(dir.resolve("old.json"), "{\"waypoints\": []}");
		assertFalse(store.load("old").noJump());
	}

	@Test
	void pitchIsKeptInRange() {
		Route route = new Route("r");
		route.setPitch(500.0, false);
		assertEquals(90.0, route.pitch());
		route.setPitch(-500.0, false);
		assertEquals(-90.0, route.pitch());
	}

	@Test
	void radiusIsKeptInRange() {
		Route route = new Route("r");
		route.setRadius(-3);
		assertEquals(0, route.radius());
		route.setRadius(1000);
		assertEquals(Route.MAX_RADIUS, route.radius());
	}

	@Test
	void missingOrBrokenRoutesLoadAsNull() throws IOException {
		RouteStore store = new RouteStore(dir);
		assertNull(store.load("nope"));
		java.nio.file.Files.writeString(dir.resolve("bad.json"), "{not json");
		assertNull(store.load("bad"));
	}

	@Test
	void deleteRemovesTheFile() throws IOException {
		RouteStore store = new RouteStore(dir);
		store.save(new Route("x"));
		assertTrue(store.delete("x"));
		assertFalse(store.exists("x"));
		assertFalse(store.delete("x"));
	}

	@Test
	void canonicalNameIgnoresCase() throws IOException {
		RouteStore store = new RouteStore(dir);
		store.save(new Route("MyRoute"));
		assertEquals("MyRoute", store.canonicalName("myroute"));
		assertNull(store.canonicalName("other"));
	}

	@Test
	void configKeepsSettingsAndRepairsNonsense() throws IOException {
		Path file = dir.resolve("config.json");
		java.nio.file.Files.writeString(file, "{\"turnSpeedDegPerTick\": -5, \"stuckTimeoutSeconds\": 7, \"selectedRoute\": \"abc\"}");
		WigConfig cfg = WigConfig.load(file);
		assertEquals(10.0, cfg.turnSpeedDegPerTick);
		assertEquals(7.0, cfg.stuckTimeoutSeconds);
		assertEquals("abc", cfg.selectedRoute);
	}
}
