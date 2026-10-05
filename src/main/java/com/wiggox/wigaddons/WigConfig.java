package com.wiggox.wigaddons;

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
	/** Show the route's name and how long it has been running on the HUD while a route runs. */
	public boolean showHud = true;
	public boolean hudShowTimer = true;
	public boolean hudShowIcon = true;
	/** Where the HUD box sits: 0 is the left / top edge of the screen and 1 the right / bottom edge, however big the box is. */
	public double hudX = 0.02;
	public double hudY = 0.30;
	public double hudScale = 1.0;
	/** Following a player: stop walking when this close (blocks), and start again once they are further than the resume distance. */
	public double followStopDistance = 3.0;
	public double followResumeDistance = 5.0;
	/** Sprint when they are at least this far away, walk when nearer. */
	public double followSprintDistance = 6.0;
	/** After losing sight of the player, wait this long at the last place they were seen, then stop. */
	public double followLostSeconds = 5.0;
	/** The view looks at a random spot within this many blocks of the player's head, picked again every second or two. */
	public double followLookRadius = 0.5;
	/** Draw the trail being followed in the world, for debugging. */
	public boolean followShowTrail = false;
	/** Never jump while following. */
	public boolean followNeverJump = false;
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
		hudX = Math.max(0, Math.min(1, Double.isNaN(hudX) ? d.hudX : hudX));
		hudY = Math.max(0, Math.min(1, Double.isNaN(hudY) ? d.hudY : hudY));
		hudScale = Double.isNaN(hudScale) ? d.hudScale : Math.max(0.5, Math.min(4.0, hudScale));
		if (!(followStopDistance >= 0)) {
			followStopDistance = d.followStopDistance;
		}
		if (!(followResumeDistance > followStopDistance)) {
			followResumeDistance = followStopDistance + 2.0;
		}
		if (!(followSprintDistance >= 0)) {
			followSprintDistance = d.followSprintDistance;
		}
		if (!(followLostSeconds >= 0)) {
			followLostSeconds = d.followLostSeconds;
		}
		if (!(followLookRadius >= 0)) {
			followLookRadius = d.followLookRadius;
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
