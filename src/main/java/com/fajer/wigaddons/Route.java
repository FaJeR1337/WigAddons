package com.fajer.wigaddons;

import java.util.ArrayList;
import java.util.List;

/** A named, ordered list of waypoints. */
public final class Route {
	/** Radius used by routes that don't set one. */
	public static final double DEFAULT_RADIUS = 2.0;
	public static final double MAX_RADIUS = 32.0;

	private final String name;
	private final List<Waypoint> waypoints;
	private double radius = DEFAULT_RADIUS;
	/** The up/down view angle held while the route runs (negative looks up), or null to leave the view's pitch alone. */
	private Double pitch;
	/** Vary the held pitch by a couple of degrees, so it isn't the same every time. */
	private boolean randomizePitch;

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
