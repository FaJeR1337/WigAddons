package com.fajer.wigaddons;

import java.io.IOException;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** The mod's shared state: where routes live, the settings, which route is selected and whether it is being edited. */
public final class WigState {
	public static final RouteStore STORE = new RouteStore(FabricLoader.getInstance().getConfigDir().resolve("WigAddons Routes"));
	private static final Path CONFIG_FILE = STORE.dir().resolve("config.json");

	public static WigConfig config = new WigConfig();

	/** The route being edited, or null when not in edit mode. */
	public static Route editing;

	/** Shows turn information on screen while a route runs. Not remembered between sessions. */
	public static boolean debug;

	private WigState() {
	}

	public static void load() {
		config = WigConfig.load(CONFIG_FILE);
		// the file is written back so a fresh install shows every setting that can be tuned
		saveConfig();
	}

	public static void saveConfig() {
		try {
			config.save(CONFIG_FILE);
		} catch (IOException e) {
			// settings still work for this session
		}
	}

	/** The selected route, loaded fresh from disk, or null when none is selected or it no longer exists. */
	public static Route selectedRoute() {
		return config.selectedRoute.isEmpty() ? null : STORE.load(config.selectedRoute);
	}

	public static void select(String name) {
		config.selectedRoute = name;
		saveConfig();
	}
}
