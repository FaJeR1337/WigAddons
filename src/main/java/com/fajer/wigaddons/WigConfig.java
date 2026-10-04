package com.fajer.wigaddons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Settings saved as config.json next to the routes. Edit the file by hand to tune the movement. */
public final class WigConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** The most the view turns per tick (20 ticks a second) while running. */
	public double turnSpeedDegPerTick = 10.0;
	/** The run stops if the view is turned this far in a tick by anything but the mod (a server rotation check, say). */
	public double rotationStopDegrees = 20.0;
	/**
	 * Each waypoint is aimed at a random spot up to this many blocks from the middle of its block (along each axis),
	 * picked again every run and every lap, so the path isn't a perfect line through block centres. 0 turns it off.
	 */
	public double humanizeOffset = 0.4;
	/** When the view is turned or the hotbar slot changed by something else, the run keeps going this long, with a warning on screen, then stops. */
	public double macroWarningSeconds = 3.0;
	/** A chat line matching one of these (regular expressions, from the start of the line) means a party member left. */
	public List<String> partyLeavePatterns = new ArrayList<>(PartyLeave.DEFAULT_PATTERNS);
	/** After a party member leaves, the run stops this many seconds later and the command below is sent. */
	public double partyLeaveDelaySeconds = 2.0;
	/** Sent when the run stops because a party member left. Leave empty to only stop. */
	public String partyLeaveCommand = "/warp hub";
	/** The run stops if the player has hardly moved for this long. */
	public double stuckTimeoutSeconds = 3.0;

	/** The selected route, remembered between sessions. */
	public String selectedRoute = "";

	/** A copy of these settings with a different aim offset, for a route that has its own. */
	public WigConfig withHumanizeOffset(double blocks) {
		WigConfig copy = GSON.fromJson(GSON.toJson(this), WigConfig.class);
		copy.humanizeOffset = blocks;
		return copy;
	}

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
		if (!(humanizeOffset >= 0)) {
			humanizeOffset = d.humanizeOffset;
		}
		if (!(macroWarningSeconds >= 0)) {
			macroWarningSeconds = d.macroWarningSeconds;
		}
		if (partyLeavePatterns == null) {
			partyLeavePatterns = d.partyLeavePatterns;
		}
		if (!(partyLeaveDelaySeconds >= 0)) {
			partyLeaveDelaySeconds = d.partyLeaveDelaySeconds;
		}
		if (partyLeaveCommand == null) {
			partyLeaveCommand = "";
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
