package com.fajer.wigaddons;

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
		java.nio.file.Files.writeString(file, "{\"turnSpeedDegPerTick\": -5, \"stuckTimeoutSeconds\": 7, \"showWaypoints\": true}");
		WigConfig cfg = WigConfig.load(file);
		assertEquals(10.0, cfg.turnSpeedDegPerTick);
		assertEquals(7.0, cfg.stuckTimeoutSeconds);
		assertTrue(cfg.showWaypoints);
	}
}
