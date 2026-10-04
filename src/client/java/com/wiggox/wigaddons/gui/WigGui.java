package com.wiggox.wigaddons.gui;

import com.wiggox.wigaddons.Chat;
import com.wiggox.wigaddons.HudPositionScreen;
import com.wiggox.wigaddons.ItemSearch;
import com.wiggox.wigaddons.PartyLeave;
import com.wiggox.wigaddons.Route;
import com.wiggox.wigaddons.Runner;
import com.wiggox.wigaddons.Waypoint;
import com.wiggox.wigaddons.WigCommands;
import com.wiggox.wigaddons.WigConfig;
import com.wiggox.wigaddons.WigState;
import io.github.notenoughupdates.moulconfig.common.IMinecraft;
import io.github.notenoughupdates.moulconfig.gui.MoulConfigEditor;
import io.github.notenoughupdates.moulconfig.gui.editors.GuiOptionEditorAccordion;
import io.github.notenoughupdates.moulconfig.processor.ProcessedCategory;
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The settings screen: About, Routes (with a section per route holding its waypoints, movement, humanizing and
 * display settings), and Safety. The screen is rebuilt whenever a route is added, removed or reordered, keeping the
 * open sections and the selected tab.
 */
public final class WigGui {
	private static final WigGuiConfig CONFIG = new WigGuiConfig();
	private static final long CONFIRM_MILLIS = 10_000;
	private static final String PATTERN_SEPARATOR = " ;; ";

	/** Development only (see SmokeTest): start with every section open. */
	public static boolean expandAll;

	/** The tabs inside each route, in order, and which one each route was last showing. */
	private static final String[] TAB_IDS = {"general", "waypoints", "movement", "humanize", "display"};
	private static final String[] TAB_LABELS = {"General", "Waypoints", "Movement", "Humanize", "Display"};
	private static final java.util.Map<String, String> ROUTE_TABS = new java.util.HashMap<>();

	private static MoulConfigEditor<WigGuiConfig> shown;
	private static List<DynCategory> categories;
	/** What is typed in the "new route" box. */
	private static String newRouteName = "";
	private static String pendingDelete;
	private static long pendingDeleteAt;

	private WigGui() {
	}

	/** Opens the settings screen. */
	public static void open() {
		open(null);
	}

	/** Opens the settings screen on the tab with this id (or the first one if there is no such tab). */
	public static void open(String categoryId) {
		show();
		if (categoryId != null) {
			for (DynCategory category : categories) {
				if (category.getIdentifier().equals(categoryId)) {
					shown.setSelectedCategory(category);
				}
			}
		}
	}

	/** Development only (see SmokeTest): switches a route to one of its tabs. */
	public static void smokeTab(String route, String tab) {
		ROUTE_TABS.put(route, tab);
		refresh(null);
	}

	/** Development only (see SmokeTest): shows a tab with every section open, scrolled to the option at the given path. */
	public static void smokeStep(String categoryId, String focusPath) {
		if (shown == null) {
			return;
		}
		ProcessedCategory category = shown.getAllCategories().get(categoryId);
		if (category == null) {
			return;
		}
		shown.setSelectedCategory(category);
		// only the section being looked at (and the ones around it) stay open, so it is near the top
		for (ProcessedOption option : category.getOptions()) {
			if (option.getEditor() instanceof GuiOptionEditorAccordion accordion) {
				String path = option.getPath();
				accordion.setToggled(focusPath.equals(path) || focusPath.startsWith(path + "."));
			}
		}
		for (ProcessedOption option : category.getOptions()) {
			if (option.getPath().equals(focusPath)) {
				shown.scrollOptionIntoView(option, 0);
			}
		}
	}

	/**
	 * Fills the rows in again in the screen that is already open, keeping the selected tab, the open sections and the
	 * scroll position, so a change looks like a change and not like the screen being closed and opened again. The
	 * option with this path (if any) is scrolled into view.
	 */
	private static void refresh(String focusPath) {
		if (shown == null || categories == null) {
			show();
			return;
		}
		Set<String> openSections = new HashSet<>();
		for (DynCategory category : categories) {
			for (DynOption option : category.options) {
				if (option.editor instanceof GuiOptionEditorAccordion accordion && accordion.getToggled()) {
					openSections.add(option.getPath());
				}
			}
		}
		fillAll();
		DynOption focus = null;
		for (DynCategory category : categories) {
			for (DynOption option : category.options) {
				option.editor.activeConfigGUI = shown;
				if (option.editor instanceof GuiOptionEditorAccordion accordion && (expandAll || openSections.contains(option.getPath()))) {
					accordion.setToggled(true);
				}
				if (option.getPath().equals(focusPath)) {
					focus = option;
				}
			}
		}
		shown.updateSearchResults(true);
		if (focus != null) {
			shown.scrollOptionIntoView(focus, 0);
		}
	}

	/** Opens a new settings screen. */
	private static void show() {
		categories = List.of(
			new DynCategory("about", "About", "What WigAddons is."),
			new DynCategory("list", "Routes", "Run one of your routes."),
			new DynCategory("routes", "Edit Routes", "Your routes and how they are set up."),
			new DynCategory("hud", "HUD", "The running route and its timer."),
			new DynCategory("safety", "Safety", "Macro checks and party changes."));
		fillAll();
		if (expandAll) {
			for (DynCategory category : categories) {
				for (DynOption option : category.options) {
					if (option.editor instanceof GuiOptionEditorAccordion accordion) {
						accordion.setToggled(true);
					}
				}
			}
		}
		LinkedHashMap<String, DynCategory> byId = ProcessedCategory.collect(categories);
		shown = new MoulConfigEditor<>(byId, CONFIG);
		IMinecraft.INSTANCE.openWrappedScreen(shown);
	}

	private static void fillAll() {
		for (DynCategory category : categories) {
			switch (category.getIdentifier()) {
				case "about" -> fillAbout(category);
				case "list" -> fillList(category);
				case "routes" -> fillRoutes(category);
				case "hud" -> fillHud(category);
				case "safety" -> fillSafety(category);
				default -> {
				}
			}
		}
	}

	private static void reset(DynCategory category) {
		category.options.clear();
		category.accordionAnchors.clear();
	}

	// ---- About ----

	private static void fillAbout(DynCategory category) {
		reset(category);
		OptionBuilder b = new OptionBuilder(category, CONFIG);
		b.info("what", "WigAddons", "Record routes of waypoints, then have the player run them.");
		b.info("list", "Routes", "Start or loop any of your finished routes.");
		b.info("routes", "Edit Routes", "Make and edit routes. Each route has tabs for its general settings, waypoints, movement, humanizing and display.");
		b.info("hud", "HUD", "Show the running route's name and how long it has been running, and drag it where you want it.");
		b.info("safety", "Safety", "What happens when a macro check or a party change is noticed is under Safety.");
		b.info("commands", "Commands", "Everything here also works as a command: /wig new, edit, done, start, stop, and more.");
	}

	// ---- Routes (to run) ----

	private static void fillList(DynCategory category) {
		reset(category);
		OptionBuilder b = new OptionBuilder(category, CONFIG);
		boolean any = false;
		for (String name : WigState.routeNames()) {
			Route route = WigState.route(name);
			// a route with no waypoints isn't finished yet, and can't be run
			if (route == null || route.waypoints().isEmpty()) {
				continue;
			}
			any = true;
			int count = route.waypoints().size();
			b.custom("run." + name, name, count + (count == 1 ? " waypoint" : " waypoints") + ", radius " + trim(route.radius()),
				String.class, () -> "", v -> true,
				(option, config) -> new RouteRowEditor(option, () -> RouteIcons.stack(route.icon()), () -> run(route, false), () -> run(route, true)));
		}
		if (!any) {
			b.info("none", "No routes yet", "Make a route under Edit Routes and add waypoints to it. Finished routes show up here.");
		}
	}

	// ---- HUD ----

	private static void fillHud(DynCategory category) {
		reset(category);
		OptionBuilder b = new OptionBuilder(category, CONFIG);
		WigConfig config = WigState.config;
		b.toggle("show", "Show HUD", "Shows a box with the running route's name and how long it has been running while a route runs.",
			() -> config.showHud, v -> {
				config.showHud = v;
				WigState.saveConfig();
			});
		b.toggle("timer", "Show timer", "Shows how long the route has been running under its name.",
			() -> config.hudShowTimer, v -> {
				config.hudShowTimer = v;
				WigState.saveConfig();
			});
		b.toggle("icon", "Show icon", "Shows the route's icon in the box.",
			() -> config.hudShowIcon, v -> {
				config.hudShowIcon = v;
				WigState.saveConfig();
			});
		b.text("scale", "Scale", "How big the box is: 1 is normal, from 0.5 to 4.",
			() -> trim(config.hudScale), v -> {
				Double parsed = parseOptional(v);
				if (parsed != null && parsed >= 0.5 && parsed <= 4.0) {
					config.hudScale = parsed;
					WigState.saveConfig();
				}
			});
		b.button("position", "Position", "Opens a screen where you drag the box where you want it.", "Edit", () -> {
			Minecraft.getInstance().setScreen(null);
			HudPositionScreen.open();
		});
		b.button("reset", "Reset position", "Puts the box back where it started, at its normal size.", "Reset", () -> {
			HudPositionScreen.resetPosition();
			refresh(null);
		});
	}

	// ---- Routes ----

	private static void fillRoutes(DynCategory category) {
		reset(category);
		OptionBuilder b = new OptionBuilder(category, CONFIG);
		WigConfig config = WigState.config;

		b.section("defaults", "Defaults for all routes");
		b.slider("defaultTurnSpeed", "Turn speed", "Degrees per tick the view turns at, for routes that don't set their own.",
			1, 45, 1, () -> config.turnSpeedDegPerTick, v -> {
				config.turnSpeedDegPerTick = v;
				WigState.saveConfig();
			});
		b.text("defaultHumanize", "Aim offset", "How far from the middle of a waypoint's block, in blocks, the aim can land, for routes that don't set their own. 0 aims at the middle.",
			() -> trim(config.humanizeOffset), v -> {
				Double parsed = parseOptional(v);
				if (parsed != null && parsed >= 0 && parsed <= Route.MAX_HUMANIZE_OFFSET) {
					config.humanizeOffset = parsed;
					WigState.saveConfig();
				}
			});
		b.endSection();

		b.section("new", "New route");
		b.text("newName", "Name", "Letters, numbers, _ and -, up to 32 characters.", () -> newRouteName, v -> newRouteName = v);
		b.button("create", "Create", "Makes an empty route with this name.", "Create", () -> {
			Route route = WigCommands.createRoute(newRouteName.trim(), false);
			if (route != null) {
				newRouteName = "";
				refresh("routes.route." + route.name());
			}
		});
		b.endSection();

		for (String name : WigState.routeNames()) {
			routeSection(b, WigState.route(name));
		}
	}

	private static void routeSection(OptionBuilder b, Route route) {
		String key = "route." + route.name();
		String title = route.name() + " (" + route.waypoints().size() + (route.waypoints().size() == 1 ? " waypoint)" : " waypoints)");
		b.section(key, title, (option, id) -> new RouteAccordionEditor(option, id, () -> RouteIcons.stack(route.icon())));

		String tab = ROUTE_TABS.getOrDefault(route.name(), TAB_IDS[0]);
		int selected = Math.max(0, Arrays.asList(TAB_IDS).indexOf(tab));
		b.custom(key + ".tabs", "Section", "", String.class, () -> tab, v -> true,
			(option, config) -> new TabBarEditor(option, TAB_LABELS, selected, i -> {
				ROUTE_TABS.put(route.name(), TAB_IDS[i]);
				refresh(null);
			}));

		switch (tab) {
			case "waypoints" -> waypointsSection(b, route, key);
			case "movement" -> movementSection(b, route, key);
			case "humanize" -> humanizeSection(b, route, key);
			case "display" -> displaySection(b, route, key);
			default -> {
			String icon = key + ".icon";
			String[] typedIcon = {route.icon()};
			b.custom(icon, "Icon", "Type part of an item's name (pick, chest, diamond). The first match is shown; press Use to pick it. A whole id is picked as you type; empty removes the icon.",
				String.class, () -> typedIcon[0], v -> true, (option, config) -> new IconEditor(option,
					Buttons.property(() -> typedIcon[0], v -> {
						typedIcon[0] = v;
						ItemSearch.Result r = RouteIcons.search(v);
						if (v.isBlank()) {
							route.setIcon("");
							WigState.save(route);
						} else if (r.exact()) {
							route.setIcon(r.best());
							WigState.save(route);
						}
					}),
					() -> RouteIcons.stack(RouteIcons.search(typedIcon[0]).best()),
					() -> {
						ItemSearch.Result r = RouteIcons.search(typedIcon[0]);
						if (r.best() != null) {
							route.setIcon(r.best());
							WigState.save(route);
							refresh(null);
						}
					}));

			b.button(key + ".start", "Run", "Closes this screen and runs the route once.", "Start", () -> run(route, false));
			b.button(key + ".loop", "Run on a loop", "Closes this screen and runs the route, going back to the first waypoint after the last, until stopped.", "Loop", () -> run(route, true));
			b.button(key + ".edit", "Place waypoints", "Closes this screen. Right click blocks with an empty hand to add waypoints; /wig done leaves edit mode.", "Edit", () -> {
				Minecraft.getInstance().setScreen(null);
				WigCommands.editRoute(route);
			});

			b.button(key + ".delete", "Delete route", "Deletes the route and its file for good. Press twice within 10 seconds.", "Delete", () -> {
				long now = System.currentTimeMillis();
				if (!route.name().equals(pendingDelete) || now - pendingDeleteAt > CONFIRM_MILLIS) {
					pendingDelete = route.name();
					pendingDeleteAt = now;
					Chat.info("Press Delete on " + route.name() + " again within 10 seconds to delete it for good.");
					return;
				}
				pendingDelete = null;
				if (WigState.editing == route) {
					com.wiggox.wigaddons.EditMode.leave();
				}
				if (WigState.delete(route)) {
					if (route.name().equals(WigState.config.selectedRoute)) {
						WigState.select("");
					}
					Chat.info("Deleted " + route.name() + ".");
					refresh(null);
				} else {
					Chat.error("Could not delete the route file.");
				}
			});
			}
		}
		b.endSection();
	}

	private static void run(Route route, boolean loop) {
		Minecraft.getInstance().setScreen(null);
		WigCommands.startRoute(route, loop);
	}

	private static void waypointsSection(OptionBuilder b, Route route, String key) {
		String sectionPath = "routes." + key + ".tabs";
		List<Waypoint> points = route.waypoints();
		for (int i = 0; i < points.size(); i++) {
			int index = i;
			String rowKey = key + ".waypoint" + index;
			String[] typed = {null};
			b.custom(rowKey, "Waypoint " + (index + 1), "x y z of the block.",
				String.class, () -> typed[0] != null ? typed[0] : points.get(index).format(), v -> true,
				(option, config) -> new WaypointEditor(option,
					Buttons.property(() -> typed[0] != null ? typed[0] : points.get(index).format(), v -> {
						typed[0] = v;
						Waypoint parsed = Waypoint.parse(v);
						if (parsed != null) {
							points.set(index, parsed);
							WigState.save(route);
						}
					}),
					() -> {
						if (route.moveWaypoint(index, -1)) {
							WigState.save(route);
							refresh(null);
						}
					},
					() -> {
						if (route.moveWaypoint(index, 1)) {
							WigState.save(route);
							refresh(null);
						}
					},
					() -> {
						points.remove(index);
						WigState.save(route);
						refresh(null);
					}));
		}
		b.button(key + ".addLooking", "Add where I'm looking", "Adds the block you were looking at when you opened this screen as the last waypoint.", "Add",
			() -> addWaypoint(route, true, sectionPath));
		b.button(key + ".addHere", "Add under my feet", "Adds the block you are standing on as the last waypoint.", "Add",
			() -> addWaypoint(route, false, sectionPath));
	}

	private static void addWaypoint(Route route, boolean lookingAt, String focus) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		BlockPos pos;
		if (lookingAt) {
			HitResult hit = player.pick(100.0, 1.0F, false);
			if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
				Chat.error("There is no block in sight to add.");
				return;
			}
			pos = blockHit.getBlockPos();
		} else {
			pos = BlockPos.containing(player.getX(), player.getY() - 0.5, player.getZ());
		}
		route.waypoints().add(new Waypoint(pos.getX(), pos.getY(), pos.getZ()));
		if (WigState.save(route)) {
			refresh(null);
		} else {
			route.waypoints().removeLast();
			Chat.error("Could not save the route, so that waypoint was not added.");
		}
	}

	private static void movementSection(OptionBuilder b, Route route, String key) {
		b.text(key + ".radius", "Radius", "How close to a waypoint the player gets before turning toward the next one, in blocks, from 0 to 32. 0 is the middle of the block.",
			() -> trim(route.radius()), v -> {
				Double parsed = parseOptional(v);
				if (parsed != null) {
					route.setRadius(parsed);
					WigState.save(route);
				}
			});
		b.text(key + ".turnSpeed", "Turn speed", "Degrees per tick the view turns at. Empty uses the default for all routes.",
			() -> route.turnSpeed() == null ? "" : trim(route.turnSpeed()), v -> {
				Double parsed = parseOptional(v);
				if (v.isBlank() || parsed != null) {
					route.setTurnSpeed(parsed);
					WigState.save(route);
				}
			});
		b.text(key + ".pitch", "Pitch", "Up/down view angle to hold while running: 0 level, positive down, negative up, from -90 to 90. Empty leaves the pitch alone.",
			() -> route.pitch() == null ? "" : trim(route.pitch()), v -> {
				Double parsed = parseOptional(v);
				if (v.isBlank()) {
					route.setPitch(null, false);
					WigState.save(route);
				} else if (parsed != null) {
					route.setPitch(parsed, route.randomizePitch());
					WigState.save(route);
				}
			});
		b.toggle(key + ".holdLeft", "Hold left click", "Holds the left mouse button (attack, break blocks) while the route runs.",
			route::holdLeft, v -> {
				route.setHoldLeft(v);
				WigState.save(route);
			});
		b.toggle(key + ".holdRight", "Hold right click", "Holds the right mouse button (use, place) while the route runs.",
			route::holdRight, v -> {
				route.setHoldRight(v);
				WigState.save(route);
			});
	}

	private static void humanizeSection(OptionBuilder b, Route route, String key) {
		b.text(key + ".offset", "Aim offset", "How far from the middle of a waypoint's block, in blocks, the aim can land. 0 aims at the middle. Empty uses the default for all routes.",
			() -> route.humanizeOffset() == null ? "" : trim(route.humanizeOffset()), v -> {
				Double parsed = parseOptional(v);
				if (v.isBlank() || parsed != null) {
					route.setHumanizeOffset(parsed);
					WigState.save(route);
				}
			});
		b.toggle(key + ".randomPitch", "Vary the pitch", "Keeps the held pitch within 2 degrees of its value, picking a new spot at every waypoint. Needs a pitch under Movement.",
			route::randomizePitch, v -> {
				route.setPitch(route.pitch(), v);
				WigState.save(route);
			});
	}

	private static void displaySection(OptionBuilder b, Route route, String key) {
		b.toggle(key + ".show", "Show waypoints", "Draws this route's waypoints in the world.", route::showWaypoints, v -> {
			route.setShowWaypoints(v);
			WigState.save(route);
		});
		b.toggle(key + ".showNumbers", "Show numbers", "Draws each waypoint's number above it, visible through walls.", route::showNumbers, v -> {
			route.setShowNumbers(v);
			WigState.save(route);
		});
		b.toggle(key + ".showRadius", "Show radius", "Draws a circle on top of each waypoint as wide as the route's radius.", route::showRadius, v -> {
			route.setShowRadius(v);
			WigState.save(route);
		});
		b.toggle(key + ".showLines", "Show lines", "Joins the waypoints with lines.", route::showLines, v -> {
			route.setShowLines(v);
			WigState.save(route);
		});
		b.text(key + ".color", "Box colour", "Colour of the waypoint boxes as a hex code, like 55FFFF.",
			() -> String.format("%06X", route.boxColor()), v -> {
				try {
					String hex = v.trim().startsWith("#") ? v.trim().substring(1) : v.trim();
					if (hex.length() == 6) {
						route.setBoxColor(Integer.parseInt(hex, 16));
						WigState.save(route);
					}
				} catch (NumberFormatException e) {
					// keep the old colour until a whole code is typed
				}
			});
	}

	// ---- Safety ----

	private static void fillSafety(DynCategory category) {
		reset(category);
		OptionBuilder b = new OptionBuilder(category, CONFIG);
		WigConfig config = WigState.config;

		b.section("macro", "Macro checks");
		b.slider("rotation", "View turn limit", "A run raises a macro check when the view is turned by this many degrees or more by something other than the mod.",
			5, 90, 1, () -> config.rotationStopDegrees, v -> {
				config.rotationStopDegrees = v;
				WigState.saveConfig();
			});
		b.slider("warning", "Warning time", "Seconds the run carries on, with a WARNING on screen, after a macro check before it stops.",
			0, 10, 1, () -> config.macroWarningSeconds, v -> {
				config.macroWarningSeconds = v;
				WigState.saveConfig();
			});
		b.slider("stuck", "Stuck timeout", "Seconds of hardly moving after which the run stops.",
			1, 30, 1, () -> config.stuckTimeoutSeconds, v -> {
				config.stuckTimeoutSeconds = v;
				WigState.saveConfig();
			});
		b.endSection();

		b.section("party", "Party members leaving");
		b.slider("partyDelay", "Delay", "Seconds after a party member leaves before the run stops and the command is sent.",
			0, 10, 1, () -> config.partyLeaveDelaySeconds, v -> {
				config.partyLeaveDelaySeconds = v;
				WigState.saveConfig();
			});
		b.text("partyCommand", "Command", "Sent when the run stops because a party member left. Leave empty to only stop.",
			() -> config.partyLeaveCommand, v -> {
				config.partyLeaveCommand = v;
				WigState.saveConfig();
			});
		b.text("partyPatterns", "Chat lines", "Regular expressions, separated by ;; , matched from the start of a chat line to recognise someone leaving. Empty turns this off.",
			() -> String.join(PATTERN_SEPARATOR, config.partyLeavePatterns), v -> {
				List<String> patterns = new ArrayList<>();
				for (String part : v.split(";;")) {
					if (!part.isBlank()) {
						patterns.add(part.trim());
					}
				}
				config.partyLeavePatterns = patterns;
				WigState.saveConfig();
			});
		b.button("partyDefaults", "Reset chat lines", "Puts the chat lines back to the built-in ones.", "Reset", () -> {
			config.partyLeavePatterns = new ArrayList<>(PartyLeave.DEFAULT_PATTERNS);
			WigState.saveConfig();
			refresh(null);
		});
		b.endSection();
	}

	// ---- helpers ----

	/** The number typed, or null if it is empty or not a number. */
	private static Double parseOptional(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		try {
			return Double.parseDouble(text.trim().replace(',', '.'));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String trim(double v) {
		return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%s", v);
	}
}
