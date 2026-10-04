package com.fajer.wigaddons.gui;

import com.fajer.wigaddons.Chat;
import com.fajer.wigaddons.ItemSearch;
import com.fajer.wigaddons.PartyLeave;
import com.fajer.wigaddons.Route;
import com.fajer.wigaddons.Runner;
import com.fajer.wigaddons.Waypoint;
import com.fajer.wigaddons.WigCommands;
import com.fajer.wigaddons.WigConfig;
import com.fajer.wigaddons.WigState;
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

	private static MoulConfigEditor<WigGuiConfig> shown;
	/** What is typed in the "new route" box. */
	private static String newRouteName = "";
	private static String pendingDelete;
	private static long pendingDeleteAt;

	private WigGui() {
	}

	/** Opens the settings screen. */
	public static void open() {
		show(null);
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
		for (ProcessedOption option : category.getOptions()) {
			if (option.getPath().equals(focusPath)) {
				shown.scrollOptionIntoView(option, 0);
			}
		}
	}

	/** Builds the screen again (keeping the selected tab and open sections) and brings the option with this path into view. */
	private static void refresh(String focusPath) {
		show(focusPath);
	}

	private static void show(String focusPath) {
		Set<String> openSections = new HashSet<>();
		String selected = null;
		if (shown != null) {
			selected = shown.getSelectedCategory();
			for (ProcessedCategory category : shown.getAllCategories().values()) {
				for (ProcessedOption option : category.getOptions()) {
					if (option.getEditor() instanceof GuiOptionEditorAccordion accordion && accordion.getToggled()) {
						openSections.add(option.getPath());
					}
				}
			}
		}

		LinkedHashMap<String, DynCategory> categories = ProcessedCategory.collect(List.of(about(), routes(), safety()));
		MoulConfigEditor<WigGuiConfig> editor = new MoulConfigEditor<>(categories, CONFIG);
		ProcessedOption focus = null;
		for (DynCategory category : categories.values()) {
			for (DynOption option : category.options) {
				if (option.editor instanceof GuiOptionEditorAccordion accordion && (expandAll || openSections.contains(option.getPath()))) {
					accordion.setToggled(true);
				}
				if (option.getPath().equals(focusPath)) {
					focus = option;
				}
			}
		}
		if (selected != null && categories.containsKey(selected)) {
			editor.setSelectedCategory(categories.get(selected));
		}
		if (focus != null) {
			editor.scrollOptionIntoView(focus, 0);
		}
		shown = editor;
		IMinecraft.INSTANCE.openWrappedScreen(editor);
	}

	// ---- About ----

	private static DynCategory about() {
		DynCategory category = new DynCategory("about", "About", "What WigAddons is.");
		OptionBuilder b = new OptionBuilder(category, CONFIG);
		b.info("what", "WigAddons", "Record routes of waypoints, then have the player run them.");
		b.info("routes", "Routes", "Make and edit routes under Routes. Every setting of a route is in its own section.");
		b.info("safety", "Safety", "What happens when a macro check or a party change is noticed is under Safety.");
		b.info("commands", "Commands", "Everything here also works as a command: /wig new, edit, done, start, stop, and more.");
		return category;
	}

	// ---- Routes ----

	private static DynCategory routes() {
		DynCategory category = new DynCategory("routes", "Routes", "Your routes and how they run.");
		OptionBuilder b = new OptionBuilder(category, CONFIG);
		WigConfig config = WigState.config;

		b.section("defaults", "Defaults for all routes");
		b.slider("defaultTurnSpeed", "Turn speed", "Degrees per tick the view turns at, for routes that don't set their own.",
			1, 45, 0.5, () -> config.turnSpeedDegPerTick, v -> {
				config.turnSpeedDegPerTick = v;
				WigState.saveConfig();
			});
		b.slider("defaultHumanize", "Aim offset", "How far from the middle of a waypoint's block, in blocks, the aim can land, for routes that don't set their own. 0 aims at the middle.",
			0, 2, 0.05, () -> config.humanizeOffset, v -> {
				config.humanizeOffset = v;
				WigState.saveConfig();
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
		return category;
	}

	private static void routeSection(OptionBuilder b, Route route) {
		String key = "route." + route.name();
		String title = route.name() + " (" + route.waypoints().size() + (route.waypoints().size() == 1 ? " waypoint)" : " waypoints)");
		b.section(key, title, option -> new RouteAccordionEditor(option, b.lastSectionId() + 1, () -> RouteIcons.stack(route.icon())));

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
						refresh(icon);
					}
				}));

		b.button(key + ".start", "Run", "Closes this screen and runs the route once.", "Start", () -> run(route, false));
		b.button(key + ".loop", "Run on a loop", "Closes this screen and runs the route, going back to the first waypoint after the last, until stopped.", "Loop", () -> run(route, true));
		b.button(key + ".edit", "Place waypoints", "Closes this screen. Right click blocks with an empty hand to add waypoints; /wig done leaves edit mode.", "Edit", () -> {
			Minecraft.getInstance().setScreen(null);
			WigCommands.editRoute(route);
		});

		waypointsSection(b, route, key);
		movementSection(b, route, key);
		humanizeSection(b, route, key);
		displaySection(b, route, key);

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
				com.fajer.wigaddons.EditMode.leave();
			}
			if (WigState.delete(route)) {
				if (route.name().equals(WigState.config.selectedRoute)) {
					WigState.select("");
				}
				Chat.info("Deleted " + route.name() + ".");
				refresh("routes");
			} else {
				Chat.error("Could not delete the route file.");
			}
		});
		b.endSection();
	}

	private static void run(Route route, boolean loop) {
		Minecraft.getInstance().setScreen(null);
		WigCommands.startRoute(route, loop);
	}

	private static void waypointsSection(OptionBuilder b, Route route, String key) {
		String sectionPath = "routes." + key + ".waypoints";
		b.section(key + ".waypoints", "Waypoints (" + route.waypoints().size() + ")");
		List<Waypoint> points = route.waypoints();
		for (int i = 0; i < points.size(); i++) {
			int index = i;
			String rowKey = key + ".waypoint" + index;
			String[] typed = {null};
			b.custom(rowKey, "Waypoint " + (index + 1), "x y z of the block. Edit the text, or move or delete the waypoint.",
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
							refresh("routes." + rowKey);
						}
					},
					() -> {
						if (route.moveWaypoint(index, 1)) {
							WigState.save(route);
							refresh("routes." + rowKey);
						}
					},
					() -> {
						points.remove(index);
						WigState.save(route);
						refresh(sectionPath);
					}));
		}
		b.button(key + ".addLooking", "Add where I'm looking", "Adds the block you were looking at when you opened this screen as the last waypoint.", "Add",
			() -> addWaypoint(route, true, sectionPath));
		b.button(key + ".addHere", "Add under my feet", "Adds the block you are standing on as the last waypoint.", "Add",
			() -> addWaypoint(route, false, sectionPath));
		b.endSection();
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
			refresh(focus);
		} else {
			route.waypoints().removeLast();
			Chat.error("Could not save the route, so that waypoint was not added.");
		}
	}

	private static void movementSection(OptionBuilder b, Route route, String key) {
		b.section(key + ".movement", "Movement");
		b.slider(key + ".radius", "Radius", "How close to a waypoint the player gets before turning toward the next one, in blocks. 0 is the middle of the block.",
			0, Route.MAX_RADIUS, 0.5, route::radius, v -> {
				route.setRadius(v);
				WigState.save(route);
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
		b.endSection();
	}

	private static void humanizeSection(OptionBuilder b, Route route, String key) {
		b.section(key + ".humanize", "Humanize");
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
		b.endSection();
	}

	private static void displaySection(OptionBuilder b, Route route, String key) {
		b.section(key + ".display", "Display");
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
		b.endSection();
	}

	// ---- Safety ----

	private static DynCategory safety() {
		DynCategory category = new DynCategory("safety", "Safety", "What the mod does when it notices something a server might check for.");
		OptionBuilder b = new OptionBuilder(category, CONFIG);
		WigConfig config = WigState.config;

		b.section("macro", "Macro checks");
		b.slider("rotation", "View turn limit", "A run raises a macro check when the view is turned by this many degrees or more by something other than the mod.",
			5, 90, 1, () -> config.rotationStopDegrees, v -> {
				config.rotationStopDegrees = v;
				WigState.saveConfig();
			});
		b.slider("warning", "Warning time", "Seconds the run carries on, with a WARNING on screen, after a macro check before it stops.",
			0, 10, 0.5, () -> config.macroWarningSeconds, v -> {
				config.macroWarningSeconds = v;
				WigState.saveConfig();
			});
		b.slider("stuck", "Stuck timeout", "Seconds of hardly moving after which the run stops.",
			1, 30, 0.5, () -> config.stuckTimeoutSeconds, v -> {
				config.stuckTimeoutSeconds = v;
				WigState.saveConfig();
			});
		b.endSection();

		b.section("party", "Party members leaving");
		b.slider("partyDelay", "Delay", "Seconds after a party member leaves before the run stops and the command is sent.",
			0, 10, 0.5, () -> config.partyLeaveDelaySeconds, v -> {
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
			refresh("safety.partyPatterns");
		});
		b.endSection();
		return category;
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
