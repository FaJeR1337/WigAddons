package com.wiggox.wigaddons;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/** The mod's shared state: where routes live, the settings, the loaded routes, which one is selected and whether it is being edited. */
public final class WigState {
	public static final RouteStore STORE = new RouteStore(configFolder());
	private static final Path CONFIG_FILE = STORE.dir().resolve("config.json");

	public static WigConfig config = new WigConfig();

	/** Every saved route, loaded once. Everything (commands, the GUI, the renderer) works on these same objects. */
	private static final Map<String, Route> ROUTES = new LinkedHashMap<>();

	/** The route being edited, or null when not in edit mode. It is one of the loaded routes. */
	public static Route editing;

	/** Shows turn information on screen while a route runs. Not remembered between sessions. */
	public static boolean debug;

	private WigState() {
	}

	/** config/WigAddons; a folder from before the rename (config/WigAddons Routes) is moved there, routes and settings with it. */
	private static Path configFolder() {
		Path root = FabricLoader.getInstance().getConfigDir();
		Path folder = root.resolve("WigAddons");
		Path old = root.resolve("WigAddons Routes");
		try {
			if (java.nio.file.Files.isDirectory(old) && !java.nio.file.Files.exists(folder)) {
				java.nio.file.Files.move(old, folder);
			}
		} catch (IOException e) {
			// the old folder stays where it is; a new, empty one is made
		}
		return folder;
	}

	public static void load() {
		config = WigConfig.load(CONFIG_FILE);
		// the file is written back so a fresh install shows every setting that can be tuned
		saveConfig();
		ROUTES.clear();
		for (String name : STORE.names()) {
			Route route = STORE.load(name);
			if (route != null) {
				ROUTES.put(name, route);
			}
		}
	}

	public static void saveConfig() {
		try {
			config.save(CONFIG_FILE);
		} catch (IOException e) {
			// settings still work for this session
		}
	}

	/** Names of every route, alphabetically. */
	public static List<String> routeNames() {
		List<String> names = new ArrayList<>(ROUTES.keySet());
		names.sort(String.CASE_INSENSITIVE_ORDER);
		return names;
	}

	public static List<Route> routes() {
		return new ArrayList<>(ROUTES.values());
	}

	/** The route with exactly this name, or null. */
	public static Route route(String name) {
		return ROUTES.get(name);
	}

	/** The route with this name ignoring case, or null. */
	public static Route routeIgnoringCase(String name) {
		for (Map.Entry<String, Route> e : ROUTES.entrySet()) {
			if (e.getKey().equalsIgnoreCase(name)) {
				return e.getValue();
			}
		}
		return null;
	}

	/** Saves the route to its file and keeps it in the loaded set. False when the file couldn't be written. */
	public static boolean save(Route route) {
		try {
			STORE.save(route);
		} catch (IOException e) {
			return false;
		}
		ROUTES.put(route.name(), route);
		return true;
	}

	public static boolean delete(Route route) {
		try {
			STORE.delete(route.name());
		} catch (IOException e) {
			return false;
		}
		ROUTES.remove(route.name());
		return true;
	}

	/** The selected route, or null when none is selected or it no longer exists. */
	public static Route selectedRoute() {
		return config.selectedRoute.isEmpty() ? null : ROUTES.get(config.selectedRoute);
	}

	public static void select(String name) {
		config.selectedRoute = name;
		saveConfig();
	}
}
