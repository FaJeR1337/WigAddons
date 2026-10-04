package com.wiggox.wigaddons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Routes saved as one JSON file each, inside a single folder. Routes are global: they are not tied to a world. */
public final class RouteStore {
	private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");
	private static final String JSON = ".json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** The settings file lives in the same folder, so no route may take its name. */
	public static final String RESERVED_NAME = "config";

	private final Path dir;

	public RouteStore(Path dir) {
		this.dir = dir;
	}

	public static boolean isValidName(String name) {
		return VALID_NAME.matcher(name).matches() && !name.equalsIgnoreCase(RESERVED_NAME);
	}

	public Path dir() {
		return dir;
	}

	private Path fileOf(String name) {
		return dir.resolve(name + JSON);
	}

	public boolean exists(String name) {
		return isValidName(name) && Files.isRegularFile(fileOf(name));
	}

	/** Names of every saved route, alphabetically. */
	public List<String> names() {
		List<String> out = new ArrayList<>();
		if (!Files.isDirectory(dir)) {
			return out;
		}
		try (Stream<Path> files = Files.list(dir)) {
			files.map(p -> p.getFileName().toString())
				.filter(f -> f.endsWith(JSON))
				.map(f -> f.substring(0, f.length() - JSON.length()))
				.filter(RouteStore::isValidName)
				.forEach(out::add);
		} catch (IOException e) {
			// an unreadable folder just looks empty
		}
		out.sort(String.CASE_INSENSITIVE_ORDER);
		return out;
	}

	/** The saved route, or null when there is none (or the file is unreadable). */
	public Route load(String name) {
		if (!exists(name)) {
			return null;
		}
		try (Reader in = Files.newBufferedReader(fileOf(name), StandardCharsets.UTF_8)) {
			JsonObject root = GSON.fromJson(in, JsonObject.class);
			List<Waypoint> points = new ArrayList<>();
			if (root != null && root.has("waypoints")) {
				for (JsonElement e : root.getAsJsonArray("waypoints")) {
					JsonArray p = e.getAsJsonArray();
					points.add(new Waypoint(p.get(0).getAsInt(), p.get(1).getAsInt(), p.get(2).getAsInt()));
				}
			}
			Route route = new Route(name, points);
			if (root != null && root.has("radius")) {
				route.setRadius(root.get("radius").getAsDouble());
			}
			if (root != null) {
				route.setNoJump(root.has("noJump") && root.get("noJump").getAsBoolean());
				route.setHoldLeft(root.has("holdLeft") && root.get("holdLeft").getAsBoolean());
				route.setHoldRight(root.has("holdRight") && root.get("holdRight").getAsBoolean());
			}
			if (root != null) {
				if (root.has("icon")) {
					route.setIcon(root.get("icon").getAsString());
				}
				if (root.has("turnSpeed")) {
					route.setTurnSpeed(root.get("turnSpeed").getAsDouble());
				}
				if (root.has("humanizeOffset")) {
					route.setHumanizeOffset(root.get("humanizeOffset").getAsDouble());
				}
				route.setShowWaypoints(root.has("showWaypoints") && root.get("showWaypoints").getAsBoolean());
				if (root.has("boxColor")) {
					route.setBoxColor(root.get("boxColor").getAsInt());
				}
				route.setShowRadius(!root.has("showRadius") || root.get("showRadius").getAsBoolean());
				route.setShowNumbers(!root.has("showNumbers") || root.get("showNumbers").getAsBoolean());
				route.setShowLines(!root.has("showLines") || root.get("showLines").getAsBoolean());
			}
			if (root != null && root.has("pitch")) {
				route.setPitch(root.get("pitch").getAsDouble(), root.has("randomizePitch") && root.get("randomizePitch").getAsBoolean());
			}
			return route;
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	public void save(Route route) throws IOException {
		Files.createDirectories(dir);
		JsonArray points = new JsonArray();
		for (Waypoint w : route.waypoints()) {
			JsonArray p = new JsonArray();
			p.add(w.x());
			p.add(w.y());
			p.add(w.z());
			points.add(p);
		}
		JsonObject root = new JsonObject();
		root.addProperty("radius", route.radius());
		root.addProperty("icon", route.icon());
		if (route.turnSpeed() != null) {
			root.addProperty("turnSpeed", route.turnSpeed());
		}
		if (route.humanizeOffset() != null) {
			root.addProperty("humanizeOffset", route.humanizeOffset());
		}
		root.addProperty("showWaypoints", route.showWaypoints());
		root.addProperty("boxColor", route.boxColor());
		root.addProperty("showRadius", route.showRadius());
		root.addProperty("showNumbers", route.showNumbers());
		root.addProperty("showLines", route.showLines());
		root.addProperty("noJump", route.noJump());
		root.addProperty("holdLeft", route.holdLeft());
		root.addProperty("holdRight", route.holdRight());
		if (route.pitch() != null) {
			root.addProperty("pitch", route.pitch());
			root.addProperty("randomizePitch", route.randomizePitch());
		}
		root.add("waypoints", points);
		try (Writer out = Files.newBufferedWriter(fileOf(route.name()), StandardCharsets.UTF_8)) {
			GSON.toJson(root, out);
		}
	}

	public boolean delete(String name) throws IOException {
		return isValidName(name) && Files.deleteIfExists(fileOf(name));
	}

	/** Finds the saved name that matches ignoring case, so /wig select MyRoute works for "myroute". */
	public String canonicalName(String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		for (String saved : names()) {
			if (saved.toLowerCase(Locale.ROOT).equals(lower)) {
				return saved;
			}
		}
		return null;
	}
}
