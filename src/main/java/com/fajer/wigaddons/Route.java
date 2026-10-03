package com.fajer.wigaddons;

import java.util.ArrayList;
import java.util.List;

/** A named, ordered list of waypoints. */
public final class Route {
	private final String name;
	private final List<Waypoint> waypoints;

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

	public List<Waypoint> waypoints() {
		return waypoints;
	}
}
