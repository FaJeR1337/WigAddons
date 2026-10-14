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
			new DynCategory("follow", "Follow", "Following a player with /wig follow."),
			new DynCategory("hunt", "Hunt", "Hunting mobs with /wig kill."),
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
				case "follow" -> fillFollow(category);
				case "hunt" -> fillHunt(category);
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
		b.info("follow", "Follow", "Follow another player with /wig follow, and set how close to stay.");
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

	// ---- Follow ----

	private static void fillFollow(DynCategory category) {
		reset(category);
		OptionBuilder b = new OptionBuilder(category, CONFIG);
		WigConfig config = WigState.config;
		b.info("how", "How it works", "Type /wig follow <player> (or look at a player and type /wig follow). You walk the path they walk, keep a few blocks back and look at them. /wig stop ends it.");
		followNumber(b, "stop", "Stop distance", "You stop walking when you are this many blocks from them.", () -> config.followStopDistance, v -> config.followStopDistance = v, 0, 20);
		followNumber(b, "resume", "Resume distance", "You start walking again when they are further than this many blocks away. It has to be more than the stop distance, so you don't keep starting and stopping.",
			() -> config.followResumeDistance, v -> config.followResumeDistance = v, 0, 40);
		followNumber(b, "sprint", "Sprint distance", "You sprint while they are at least this far away, and walk when nearer.", () -> config.followSprintDistance, v -> config.followSprintDistance = v, 0, 60);
		followNumber(b, "lost", "Wait when lost", "Seconds to wait at the last place they were seen, after losing sight of them, before stopping.", () -> config.followLostSeconds, v -> config.followLostSeconds = v, 0, 120);
		followNumber(b, "look", "Look radius", "Blocks around their head that you look within: a new random spot every second or two, so the view isn't fixed on one point. 0 looks right at their head.",
			() -> config.followLookRadius, v -> config.followLookRadius = v, 0, 3);
		followNumber(b, "relook", "Look again every", "Seconds between looking at the player again. In between, the view holds still instead of tracking them every tick. 0 keeps the view on them all the time.",
			() -> config.followLookInterval, v -> config.followLookInterval = v, 0, 30);
		followNumber(b, "smooth", "Turn smoothing", "About how many ticks the view takes to settle a turn while following. The turning eases in and out instead of snapping. 0 turns at full speed with no easing.",
			() -> config.followTurnSmoothing, v -> config.followTurnSmoothing = v, 0, 20);
		b.toggle("trail", "Show trail", "Draws the trail you are following in the world, for debugging: orange dots and lines where the player walked, a green dot at the point you are walking to (with a cyan line to it), and a red dot where they were last seen.",
			() -> config.followShowTrail, v -> {
				config.followShowTrail = v;
				WigState.saveConfig();
			});
		b.toggle("lookwall", "Stop looking behind walls", "Stops looking at the player while a wall is between you, and looks where you are walking instead. The view only switches once it has been blocked (or clear) for half a second.",
			() -> config.followLookOnlyWhenVisible, v -> {
				config.followLookOnlyWhenVisible = v;
				WigState.saveConfig();
			});
		followNumber(b, "jumpcost", "Jump cost (blocks)", "How much a step up that needs a jump counts against a path, in blocks of walking. The higher it is, the further the path goes round to avoid jumping, so it only jumps when there is no walk of about that many extra blocks that avoids it. Used when following and hunting.",
			() -> config.pathJumpCost, v -> config.pathJumpCost = v, 0, 50);
		b.toggle("nojump", "Never jump", "Never jump while following.", () -> config.followNeverJump, v -> {
			config.followNeverJump = v;
			WigState.saveConfig();
		});
	}

	/** A number setting for following, as a text field (fractions get cut off in the slider's small box). */
	private static void followNumber(OptionBuilder b, String key, String name, String description,
		java.util.function.DoubleSupplier getter, java.util.function.DoubleConsumer setter, double min, double max) {
		b.text(key, name, description, () -> trim(getter.getAsDouble()), v -> {
			Double parsed = parseOptional(v);
			if (parsed != null && parsed >= min && parsed <= max) {
				setter.accept(parsed);
				WigState.saveConfig();
			}
		});
	}

	// ---- HUD ----

	private static void fillHunt(DynCategory category) {
		reset(category);
		OptionBuilder b = new OptionBuilder(category, CONFIG);
		WigConfig config = WigState.config;
		b.info("how", "How it works", "Type /wig kill. You walk up to the nearest mob with the name below (special ones first), look at it and right click once it is in reach. The mob is then treated as dead for a few seconds and you move on to the next. /wig stop ends it.");
		b.text("name", "Mob name", "Mobs whose name contains this text are hunted, ignoring capitals, so Zealot also finds Zealot Bruiser.",
			() -> config.killName, v -> {
				config.killName = v.trim();
				WigState.saveConfig();
			});
		b.text("special", "Special mob name", "Mobs whose name contains this text are always gone for first, however far away (within the search radius).",
			() -> config.killSpecialName, v -> {
				config.killSpecialName = v.trim();
				WigState.saveConfig();
			});
		b.info("home", "Home spot", "/wig kill home sets the spot you are standing on as home, /wig kill home clear removes it. With nothing to hunt you walk back there and wander around it until something turns up.");
		b.toggle("wander", "Wander at home", "While nothing is in range, stroll to random spots around the home spot, resting a moment between them, instead of standing still.",
			() -> config.killWander, v -> {
				config.killWander = v;
				WigState.saveConfig();
			});
		b.toggle("idlelook", "Look around while walking home", "While walking to the home spot or wandering, the view sits at a random angle within 30 degrees either side of the way you are going (60 degrees in all) and changes every second or two. Off keeps the view straight on where you are going, so mostly W.",
			() -> config.killIdleLookAround, v -> {
				config.killIdleLookAround = v;
				WigState.saveConfig();
			});
		followNumber(b, "wanderradius", "Wander radius", "How far from the home spot to stroll, in blocks.", () -> config.killWanderRadius, v -> config.killWanderRadius = v, 0, 50);
		followNumber(b, "radius", "Search radius", "How many blocks away to look for something to hunt.", () -> config.killSearchRadius, v -> config.killSearchRadius = v, 1, 500);
		followNumber(b, "range", "Click range", "You right click once you are looking at the mob and it is within this many blocks.", () -> config.killRange, v -> config.killRange = v, 1, 500);
		followNumber(b, "markmin", "Dead for at least (s)", "After attacking, the mob is assumed dead and ignored for a random time between this and the next number, in seconds.",
			() -> config.killMarkMinSeconds, v -> config.killMarkMinSeconds = v, 0, 300);
		followNumber(b, "markmax", "Dead for at most (s)", "The longest the mob is ignored after an attack, in seconds.",
			() -> config.killMarkMaxSeconds, v -> config.killMarkMaxSeconds = v, 0, 300);
		followNumber(b, "click", "Click length (ticks)", "How long the right click button is held for each attack.", () -> config.killClickTicks, v -> config.killClickTicks = (int) Math.round(v), 1, 40);
		b.toggle("tags", "Show my own name tags", "Draws the mod's own name tag (white, like an ordinary one) and a box over every mob being hunted, through walls. The box is gold for special mobs, green for the current target, red for the others and grey for ones assumed dead.",
			() -> config.killShowTags, v -> {
				config.killShowTags = v;
				WigState.saveConfig();
			});
		followNumber(b, "smooth", "Turn smoothing", "About how many ticks the view takes to settle a turn while hunting, the same as the setting on the Follow tab but just for hunting. The turning eases in and out instead of snapping. Lower is quicker to lock on, 0 turns at full speed with no easing.",
			() -> config.killTurnSmoothing, v -> config.killTurnSmoothing = v, 0, 20);
		b.toggle("precise", "Aim straight at close targets", "While sidestepping with A or D with the target close to your crosshair, aim straight at it with next to no turn smoothing, so the view doesn't lag behind a moving target and keep pointing a block or two ahead of it.",
			() -> config.killPreciseAim, v -> {
				config.killPreciseAim = v;
				WigState.saveConfig();
			});
		followNumber(b, "closesmooth", "Smoothing for close targets", "The turn smoothing used for that, in ticks. 0 is no easing at all.",
			() -> config.killCloseSmoothing, v -> config.killCloseSmoothing = v, 0, 20);
		followNumber(b, "preciseangle", "Close to the crosshair (deg)", "How many degrees from the crosshair still counts as close, for the setting above.",
			() -> config.killPreciseAngle, v -> config.killPreciseAngle = v, 1, 90);
		followNumber(b, "tagscale", "Name tag size", "How big the name tags are, the same at any distance: they shrink with distance like any name tag. About 0.45 is the size of an ordinary name tag, make it bigger to read them from further away.",
			() -> config.killTagScale, v -> config.killTagScale = v, 0.1, 5);
		b.text("react", "Spawn message", "When this chat message arrives while hunting, you stand still, look around and jump for a few seconds before going for the mob. Leave it empty to turn that off.",
			() -> config.killReactMessage, v -> {
				config.killReactMessage = v.trim();
				WigState.saveConfig();
			});
		followNumber(b, "reactmin", "Look around for at least (s)", "The shortest time spent looking around and jumping after the spawn message, in seconds.",
			() -> config.killReactMinSeconds, v -> config.killReactMinSeconds = v, 0, 30);
		followNumber(b, "reactmax", "Look around for at most (s)", "The longest time spent looking around and jumping after the spawn message, in seconds.",
			() -> config.killReactMaxSeconds, v -> config.killReactMaxSeconds = v, 0, 30);
		followNumber(b, "reactearly", "Go early after (s)", "While looking around after the spawn message, if a special mob has come into sight in front of you (within the angle below), then once this many seconds have passed the looking around ends and you go for it.",
			() -> config.killReactEarlySeconds, v -> config.killReactEarlySeconds = v, 0, 30);
		followNumber(b, "reactangle", "In front means within (deg)", "How many degrees either side of where you are facing counts as in front of you, for going early.",
			() -> config.killReactSeeAngle, v -> config.killReactSeeAngle = v, 1, 180);
		b.toggle("bytype", "Hunt by mob type (no name tag needed)", "Name tags are separate entities, and the server may not send them or may only send them when you are close. With this on, every mob of the type below within the search radius is hunted whether or not its name tag is there. It can't tell special mobs apart without their tag, and it will also go for any other mob of that type.",
			() -> config.killByType, v -> {
				config.killByType = v;
				WigState.saveConfig();
			});
		b.text("type", "Mob type", "The type to hunt when the option above is on, as the game names it, for example minecraft:enderman.",
			() -> config.killType, v -> {
				if (!v.isBlank()) {
					config.killType = v.trim();
					WigState.saveConfig();
				}
			});
		followNumber(b, "maxdrop", "Longest drop (blocks)", "The longest fall hunting takes as a shortcut, instead of walking a long way round by stairs or slopes. Falls over 3 blocks hurt. 0 never drops more than a step.",
			() -> config.killMaxDrop, v -> config.killMaxDrop = (int) Math.round(v), 0, 40);
		followNumber(b, "spawncooldown", "Spawn cooldown (s)", "A mob that has just appeared can't be targeted for this many seconds. The mobs already there when you start hunting are not counted as new.",
			() -> config.killSpawnCooldownSeconds, v -> config.killSpawnCooldownSeconds = v, 0, 60);
		followNumber(b, "specialclicks", "Clicks on a special mob", "How many right clicks a special mob gets before it is assumed dead and left alone, since it usually takes more than one. Ordinary mobs are assumed dead after one.",
			() -> config.killSpecialClicks, v -> config.killSpecialClicks = (int) Math.round(v), 1, 50);
		followNumber(b, "cooldown", "Click cooldown (s)", "The least time between one right click and the next, in seconds. Set it to your weapon's cooldown. A little extra is added at random so it isn't clockwork.",
			() -> config.killClickCooldownSeconds, v -> config.killClickCooldownSeconds = v, 0, 60);
		b.toggle("stand", "Stand still when crowded", "With two or more targets in sight and within click range, stop walking and deal with them from where you stand, until the last one is marked.",
			() -> config.killStandWhenCrowded, v -> {
				config.killStandWhenCrowded = v;
				WigState.saveConfig();
			});
		followNumber(b, "standlimit", "Stand still limit (s)", "Standing still gives up after this many seconds if nothing gets clicked, so a blocked shot can't freeze you in place. Then you walk on as normal.",
			() -> config.killStandLimitSeconds, v -> config.killStandLimitSeconds = v, 1, 600);
		followNumber(b, "maxup", "Ignore above pitch", "Targets that would need you to look up steeper than this many degrees (a pitch from minus this to -90) are ignored, so the view never ends up pointing straight up. 90 ignores none.",
			() -> config.killMaxLookUp, v -> config.killMaxLookUp = v, 1, 90);
		followNumber(b, "uppenalty", "Prefer targets level with me", "How much worse a target counts for being above you, so one you don't have to look up at is gone for first. Each degree you would have to pitch up counts as this many extra degrees away from your crosshair (blocks, when picking by distance). 0 ignores height. Special mobs still come first, but only among themselves.",
			() -> config.killUpPenalty, v -> config.killUpPenalty = v, 0, 10);
		followNumber(b, "behindangle", "Behind me means beyond (deg)", "A target more than this many degrees round to either side of where you are facing counts as behind you. Targets behind you are only gone for when none in front is left and they have been the only ones for the delay below, so you don't spin round at once.",
			() -> config.killBehindAngle, v -> config.killBehindAngle = v, 30, 180);
		followNumber(b, "behinddelay", "Wait before turning round (s)", "How long targets behind you have to be the only ones left before you turn round to them. The target you are already going for is never dropped for this.",
			() -> config.killBehindDelaySeconds, v -> config.killBehindDelaySeconds = v, 0, 30);
		b.toggle("part", "Aim at the visible part (test)", "A test option. When only one part of a mob can be seen, say its head over a ledge or its body past a corner, aim at that part instead of the middle of it, so shots aren't wasted on the part behind something.",
			() -> config.killAimVisiblePart, v -> {
				config.killAimVisiblePart = v;
				WigState.saveConfig();
			});
		b.toggle("crosshair", "Pick nearest the crosshair", "Go for the target nearest to where you are already looking, so the view turns as little as possible and doesn't spin round. Off goes for the nearest to you. Special mobs still come first.",
			() -> config.killPickByCrosshair, v -> {
				config.killPickByCrosshair = v;
				WigState.saveConfig();
			});
		b.toggle("sight", "Depth check", "Mobs behind walls are only walked toward when none is in sight, and are never looked at or clicked until they are in sight. It only right clicks when nothing solid is between you and the mob, so no shot is wasted on a wall.",
			() -> config.killRequireSight, v -> {
				config.killRequireSight = v;
				WigState.saveConfig();
			});
		followNumber(b, "margin", "Aim margin", "How much bigger than the mob's hitbox still counts as looking at it, in blocks.", () -> config.killAimMargin, v -> config.killAimMargin = v, 0, 2);
	}

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
			String[] typedIcon = {route.icon()};
		b.custom(key + ".icon", "Icon", "Type part of an item's name (pick, chest, diamond). The first match is shown beside Use; press Use to pick it. A whole item id is picked as you type; empty removes the icon.",
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
		b.toggle(key + ".noJump", "Never jump", "Forces this route never to jump. One-block steps are walked round with A or D, whatever the heights, and the run stops if there is no way round. Leave it off for routes that climb or need to jump.",
			route::noJump, v -> {
				route.setNoJump(v);
				WigState.save(route);
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
		b.wideSlider("alarm", "Alarm volume", "How loud the macro check alarm is. Above 100 it is layered to be louder. 0 is silent.",
			0, 300, 1, () -> config.macroSoundVolume, v -> {
				config.macroSoundVolume = v;
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
