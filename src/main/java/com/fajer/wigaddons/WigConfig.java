package com.fajer.wigaddons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Settings saved as config.json next to the routes. Edit the file by hand to tune the movement. */
public final class WigConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** The most the view turns per tick (20 ticks a second) while running. */
	public double turnSpeedDegPerTick = 10.0;
	/** The run stops if the view is turned this far in a tick by anything but the mod (a server rotation check, say). */
	public double rotationStopDegrees = 20.0;
	/** The run stops if the player has hardly moved for this long. */
	public double stuckTimeoutSeconds = 3.0;

	/** Whether the selected route's waypoints are drawn. Remembered between sessions. */
	public boolean showWaypoints = false;
	/** The selected route, remembered between sessions. */
	public String selectedRoute = "";

	public static WigConfig load(Path file) {
		try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			WigConfig loaded = GSON.fromJson(in, WigConfig.class);
			return loaded == null ? new WigConfig() : loaded.sanitised();
		} catch (IOException | RuntimeException e) {
			return new WigConfig();
		}
	}

	public void save(Path file) throws IOException {
		Files.createDirectories(file.getParent());
		try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			GSON.toJson(this, out);
		}
	}

	/** Puts hand-edited nonsense back to the defaults so the follower can't divide by zero or spin. */
	private WigConfig sanitised() {
		WigConfig d = new WigConfig();
		if (!(turnSpeedDegPerTick > 0)) {
			turnSpeedDegPerTick = d.turnSpeedDegPerTick;
		}
		if (!(rotationStopDegrees > 0)) {
			rotationStopDegrees = d.rotationStopDegrees;
		}
		if (!(stuckTimeoutSeconds > 0)) {
			stuckTimeoutSeconds = d.stuckTimeoutSeconds;
		}
		if (selectedRoute == null) {
			selectedRoute = "";
		}
		return this;
	}
}
