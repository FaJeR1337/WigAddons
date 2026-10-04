package com.wiggox.wigaddons;

import java.util.ArrayList;
import java.util.List;

/** A named, ordered list of waypoints. */
public final class Route {
	/** Radius used by routes that don't set one. */
	public static final double DEFAULT_RADIUS = 2.0;
	public static final double MAX_RADIUS = 32.0;
	public static final int DEFAULT_BOX_COLOR = 0x55FFFF;
	public static final double MAX_TURN_SPEED = 90.0;
	public static final double MAX_HUMANIZE_OFFSET = 5.0;

	private final String name;
	private final List<Waypoint> waypoints;
	private double radius = DEFAULT_RADIUS;
	/** The up/down view angle held while the route runs (negative looks up), or null to leave the view's pitch alone. */
	private Double pitch;
	/** Vary the held pitch by a couple of degrees, so it isn't the same every time. */
	private boolean randomizePitch;
	/** Hold the left mouse button (attack / break) while the route runs. */
	private boolean holdLeft;
	/** Hold the right mouse button (use / place) while the route runs. */
	private boolean holdRight;
	/** An item id (like "diamond_pickaxe") shown beside the route in the GUI, or empty for none. */
	private String icon = "";
	/** This route's own turn speed in degrees per tick, or null to use the global one. */
	private Double turnSpeed;
	/** This route's own aim offset in blocks, or null to use the global one. */
	private Double humanizeOffset;
	/** Draw this route's waypoints in the world. */
	private boolean showWaypoints;
	/** Colour of the waypoint boxes, as 0xRRGGBB. */
	private int boxColor = DEFAULT_BOX_COLOR;
	private boolean showRadius = true;
	private boolean showNumbers = true;
	private boolean showLines = true;

	public Route(String name) {
		this(name, new ArrayList<>());
	}

	public Route(String name, List<Waypoint> waypoints) {
		this.name = name;
		this.waypoints = new ArrayList<>(waypoints);
	}

	public String name() {
		return name;
	}

	/** How close the player must get to a waypoint, in blocks, before turning toward the next one. */
	public double radius() {
		return radius;
	}

	public void setRadius(double radius) {
		this.radius = Math.max(0, Math.min(MAX_RADIUS, radius));
	}

	public String icon() {
		return icon;
	}

	public void setIcon(String icon) {
		this.icon = icon == null ? "" : icon.trim();
	}

	public Double turnSpeed() {
		return turnSpeed;
	}

	/** Null (or anything not above zero) means "use the global turn speed". */
	public void setTurnSpeed(Double degreesPerTick) {
		this.turnSpeed = degreesPerTick == null || !(degreesPerTick > 0) ? null : Math.min(degreesPerTick, MAX_TURN_SPEED);
	}

	public Double humanizeOffset() {
		return humanizeOffset;
	}

	/** Null (or a negative number) means "use the global offset"; 0 turns the offset off for this route. */
	public void setHumanizeOffset(Double blocks) {
		this.humanizeOffset = blocks == null || !(blocks >= 0) ? null : Math.min(blocks, MAX_HUMANIZE_OFFSET);
	}

	public double effectiveTurnSpeed(WigConfig config) {
		return turnSpeed != null ? turnSpeed : config.turnSpeedDegPerTick;
	}

	public double effectiveHumanizeOffset(WigConfig config) {
		return humanizeOffset != null ? humanizeOffset : config.humanizeOffset;
	}

	public boolean showWaypoints() {
		return showWaypoints;
	}

	public void setShowWaypoints(boolean show) {
		this.showWaypoints = show;
	}

	public int boxColor() {
		return boxColor;
	}

	public void setBoxColor(int rgb) {
		this.boxColor = rgb & 0xFFFFFF;
	}

	public boolean showRadius() {
		return showRadius;
	}

	public void setShowRadius(boolean show) {
		this.showRadius = show;
	}

	public boolean showNumbers() {
		return showNumbers;
	}

	public void setShowNumbers(boolean show) {
		this.showNumbers = show;
	}

	public boolean showLines() {
		return showLines;
	}

	public void setShowLines(boolean show) {
		this.showLines = show;
	}

	/** Moves a waypoint up (-1) or down (+1) the list. False when it is already at that end. */
	public boolean moveWaypoint(int index, int delta) {
		int to = index + delta;
		if (index < 0 || index >= waypoints.size() || to < 0 || to >= waypoints.size()) {
			return false;
		}
		Waypoint moved = waypoints.remove(index);
		waypoints.add(to, moved);
		return true;
	}

	public boolean holdLeft() {
		return holdLeft;
	}

	public boolean holdRight() {
		return holdRight;
	}

	public void setHoldLeft(boolean hold) {
		this.holdLeft = hold;
	}

	public void setHoldRight(boolean hold) {
		this.holdRight = hold;
	}

	public Double pitch() {
		return pitch;
	}

	public boolean randomizePitch() {
		return randomizePitch;
	}

	/** Holds this pitch (clamped to straight up .. straight down) while running; null stops holding one. */
	public void setPitch(Double pitch, boolean randomize) {
		this.pitch = pitch == null ? null : Math.max(-90.0, Math.min(90.0, pitch));
		this.randomizePitch = this.pitch != null && randomize;
	}

	public List<Waypoint> waypoints() {
		return waypoints;
	}
}
