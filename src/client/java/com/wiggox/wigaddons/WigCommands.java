package com.wiggox.wigaddons;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/** /wigaddons and its short form /wig. */
public final class WigCommands {
	private static final long CONFIRM_MILLIS = 10_000;

	private static final SuggestionProvider<FabricClientCommandSource> ROUTE_NAMES = (c, b) -> {
		String prefix = b.getRemaining().toLowerCase(Locale.ROOT);
		for (String name : WigState.routeNames()) {
			if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
				b.suggest(name);
			}
		}
		return b.buildFuture();
	};

	/** Names of the other players in range, for completing /wig follow. */
	private static final SuggestionProvider<FabricClientCommandSource> NEARBY_PLAYERS = (c, b) -> {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null) {
			String prefix = b.getRemaining().toLowerCase(Locale.ROOT);
			for (AbstractClientPlayer other : mc.level.players()) {
				String name = other.getGameProfile().name();
				if (other != mc.player && name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
					b.suggest(name);
				}
			}
		}
		return b.buildFuture();
	};

	private static String pendingDelete;
	private static long pendingDeleteAt;

	private WigCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> {
			for (String root : new String[] {"wigaddons", "wig"}) {
				dispatcher.register(build(ClientCommands.literal(root)));
			}
		});
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> build(LiteralArgumentBuilder<FabricClientCommandSource> root) {
		return root
			.executes(c -> openGui())
			.then(ClientCommands.literal("gui").executes(c -> openGui()))
			.then(ClientCommands.literal("new")
				.then(ClientCommands.argument("route", StringArgumentType.word())
					.executes(c -> create(StringArgumentType.getString(c, "route")))))
			.then(ClientCommands.literal("edit")
				.then(ClientCommands.argument("route", StringArgumentType.word()).suggests(ROUTE_NAMES)
					.executes(c -> edit(StringArgumentType.getString(c, "route")))))
			.then(ClientCommands.literal("done").executes(c -> done()))
			.then(ClientCommands.literal("select")
				.then(ClientCommands.argument("route", StringArgumentType.word()).suggests(ROUTE_NAMES)
					.executes(c -> select(StringArgumentType.getString(c, "route")))))
			.then(ClientCommands.literal("routes")
				.executes(c -> routes())
				.then(ClientCommands.argument("route", StringArgumentType.word()).suggests(ROUTE_NAMES)
					.executes(c -> routeDetails(StringArgumentType.getString(c, "route")))))
			.then(ClientCommands.literal("start")
				.then(ClientCommands.argument("route", StringArgumentType.word()).suggests(ROUTE_NAMES)
					.executes(c -> start(StringArgumentType.getString(c, "route"), false))
					.then(ClientCommands.literal("loop")
						.executes(c -> start(StringArgumentType.getString(c, "route"), true)))))
			.then(ClientCommands.literal("radius")
				.then(ClientCommands.argument("route", StringArgumentType.word()).suggests(ROUTE_NAMES)
					.then(ClientCommands.argument("blocks", DoubleArgumentType.doubleArg(0, Route.MAX_RADIUS))
						.executes(c -> radius(StringArgumentType.getString(c, "route"), DoubleArgumentType.getDouble(c, "blocks"))))))
			.then(ClientCommands.literal("facing")
				.then(ClientCommands.argument("route", StringArgumentType.word()).suggests(ROUTE_NAMES)
					.then(ClientCommands.literal("off").executes(c -> facing(StringArgumentType.getString(c, "route"), null, false)))
					.then(ClientCommands.argument("pitch", DoubleArgumentType.doubleArg(-90, 90))
						.executes(c -> facing(StringArgumentType.getString(c, "route"), DoubleArgumentType.getDouble(c, "pitch"), false))
						.then(ClientCommands.literal("randomize")
							.executes(c -> facing(StringArgumentType.getString(c, "route"), DoubleArgumentType.getDouble(c, "pitch"), true))))))
			.then(ClientCommands.literal("nojump")
				.then(ClientCommands.argument("route", StringArgumentType.word()).suggests(ROUTE_NAMES)
					.then(ClientCommands.literal("on").executes(c -> noJump(StringArgumentType.getString(c, "route"), true)))
					.then(ClientCommands.literal("off").executes(c -> noJump(StringArgumentType.getString(c, "route"), false)))))
			.then(clickCommand("left", true))
			.then(clickCommand("right", false))
			.then(ClientCommands.literal("kill")
				.then(ClientCommands.literal("home")
					.executes(c -> setHuntHome(true, false))
					.then(ClientCommands.literal("clear").executes(c -> setHuntHome(false, false))))
				.executes(c -> {
				if (WigState.editing != null) {
					EditMode.leave();
				}
				Runner.startHunt();
				Chat.info("Hunting " + WigState.config.killName + " (" + WigState.config.killSpecialName + " first). The mouse is locked. /wig stop, a movement key or any screen stops it.");
				return 1;
			}))
			.then(ClientCommands.literal("zealots")
				.then(ClientCommands.literal("home")
					.executes(c -> setHuntHome(true, true))
					.then(ClientCommands.literal("clear").executes(c -> setHuntHome(false, true))))
				.executes(c -> {
				if (WigState.editing != null) {
					EditMode.leave();
				}
				Runner.startZealots();
				Chat.info("Hunting Zealots (the Zealots tab's settings). The mouse is locked. /wig stop, a movement key or any screen stops it.");
				return 1;
			}))
			.then(ClientCommands.literal("trophy").executes(c -> {
				if (WigState.editing != null) {
					EditMode.leave();
				}
				Runner.startFish();
				if (Runner.isFishing()) {
					Chat.info("Trophy fishing. Stay where you are and keep the view as it is. /wig stop, a movement key or any screen stops it.");
				}
				return 1;
			}))
			.then(ClientCommands.literal("follow")
				.executes(c -> follow(null))
				.then(ClientCommands.argument("player", StringArgumentType.word()).suggests(NEARBY_PLAYERS)
					.executes(c -> follow(StringArgumentType.getString(c, "player")))))
			.then(ClientCommands.literal("heads")
				.executes(c -> {
					HeadScanner.showing = !HeadScanner.showing;
					Chat.info("Player heads are " + (HeadScanner.showing ? "marked through walls and listed below, with their skin ids." : "no longer marked."));
					if (HeadScanner.showing) {
						HeadScanner.report(net.minecraft.client.Minecraft.getInstance());
					}
					return 1;
				})
				.then(ClientCommands.literal("raw").executes(c -> {
					HeadScanner.reportNearestRaw(net.minecraft.client.Minecraft.getInstance());
					return 1;
				})))
			.then(ClientCommands.literal("stop").executes(c -> stop()))
			.then(ClientCommands.literal("waypoints").executes(c -> toggleWaypoints()))
			.then(ClientCommands.literal("debug").executes(c -> toggleDebug())
				.then(ClientCommands.literal("tablist").executes(c -> TabListDebug.print())))
			.then(ClientCommands.literal("trail").executes(c -> {
				WigState.config.followShowTrail = !WigState.config.followShowTrail;
				WigState.saveConfig();
				Chat.info("The follow trail is " + (WigState.config.followShowTrail ? "shown" : "hidden") + ".");
				return 1;
			}))
			.then(ClientCommands.literal("lookwall")
				.then(ClientCommands.literal("on").executes(c -> setLookWall(true)))
				.then(ClientCommands.literal("off").executes(c -> setLookWall(false))))
			.then(ClientCommands.literal("hud")
				.executes(c -> toggleHud())
				.then(ClientCommands.literal("position").executes(c -> {
					OpenGuiKey.requestHudPosition();
					return 1;
				})))
			.then(ClientCommands.literal("clear").executes(c -> clear()))
			.then(ClientCommands.literal("delete")
				.then(ClientCommands.argument("route", StringArgumentType.word()).suggests(ROUTE_NAMES)
					.executes(c -> delete(StringArgumentType.getString(c, "route")))));
	}

	/** Resolves a typed name to a saved route, telling the player when it doesn't exist. */
	private static Route findRoute(String typed) {
		Route route = WigState.routeIgnoringCase(typed);
		if (route == null) {
			Chat.error("There is no route called \"" + typed + "\". /wig routes lists them.");
		}
		return route;
	}

	private static int openGui() {
		// the chat screen the command was typed in is closed after the command runs, so open the screen a moment later
		OpenGuiKey.requestOpen();
		return 1;
	}

	private static int create(String name) {
		return createRoute(name, true) == null ? 0 : 1;
	}

	/** Makes and saves a new, empty route. Null (with a chat message) when the name is no good. */
	public static Route createRoute(String name, boolean startEditing) {
		if (!RouteStore.isValidName(name)) {
			Chat.error("Route names use only letters, numbers, _ and -, up to 32 characters, and can't be \"config\".");
			return null;
		}
		if (WigState.routeIgnoringCase(name) != null) {
			Chat.error("A route called " + name + " already exists. Use /wig edit " + name + " to add to it.");
			return null;
		}
		Route route = new Route(name);
		if (!WigState.save(route)) {
			Chat.error("Could not save the route file.");
			return null;
		}
		WigState.select(name);
		if (startEditing) {
			EditMode.enter(route);
			Chat.info("Created " + name + ". Right click blocks with an empty hand to place waypoints, then /wig done.");
		} else {
			Chat.info("Created " + name + ".");
		}
		return route;
	}

	private static int edit(String typed) {
		Route route = findRoute(typed);
		if (route == null) {
			return 0;
		}
		editRoute(route);
		return 1;
	}

	/** Selects the route and switches on right-click placement of waypoints. */
	public static void editRoute(Route route) {
		WigState.select(route.name());
		EditMode.enter(route);
		Chat.info("Editing " + route.name() + " (" + route.waypoints().size() + " waypoints). /wig done when finished.");
	}

	private static int done() {
		if (WigState.editing == null) {
			Chat.error("You are not editing a route.");
			return 0;
		}
		String name = WigState.editing.name();
		EditMode.leave();
		Chat.info("Saved " + name + ".");
		return 1;
	}

	private static int select(String typed) {
		Route route = findRoute(typed);
		if (route == null) {
			return 0;
		}
		if (WigState.editing != null) {
			EditMode.leave();
		}
		WigState.select(route.name());
		Chat.info("Selected " + route.name() + ".");
		return 1;
	}

	private static int routes() {
		List<String> names = WigState.routeNames();
		if (names.isEmpty()) {
			Chat.info("No routes yet. /wig new <route> makes one.");
			return 1;
		}
		StringBuilder out = new StringBuilder("Routes: ");
		for (int i = 0; i < names.size(); i++) {
			String name = names.get(i);
			out.append(i == 0 ? "" : ", ").append(name);
			if (name.equals(WigState.config.selectedRoute)) {
				out.append(" (selected)");
			}
		}
		Chat.info(out.toString());
		return 1;
	}

	private static int routeDetails(String typed) {
		Route route = findRoute(typed);
		if (route == null) {
			return 0;
		}
		if (route.waypoints().isEmpty()) {
			Chat.info(route.name() + " has no waypoints yet.");
			return 1;
		}
		Chat.info(route.name() + " has " + route.waypoints().size() + " waypoints:");
		for (int i = 0; i < route.waypoints().size(); i++) {
			Waypoint w = route.waypoints().get(i);
			Chat.info((i + 1) + ": " + w.x() + " " + w.y() + " " + w.z());
		}
		return 1;
	}

	private static int start(String typed, boolean loop) {
		Route route = findRoute(typed);
		return route != null && startRoute(route, loop) ? 1 : 0;
	}

	/** Starts running the route. False (with a chat message) when it can't be run. */
	public static boolean startRoute(Route route, boolean loop) {
		if (route.waypoints().isEmpty()) {
			Chat.error(route.name() + " has no waypoints.");
			return false;
		}
		if (WigState.editing != null) {
			EditMode.leave();
		}
		WigState.select(route.name());
		Runner.start(route, loop);
		Chat.info("Running " + route.name() + (loop ? " on a loop" : "") + ". The mouse is locked. /wig stop, a movement key or any screen stops it.");
		return true;
	}

	private static int radius(String typed, double blocks) {
		Route route = findRoute(typed);
		if (route == null) {
			return 0;
		}
		route.setRadius(blocks);
		if (!WigState.save(route)) {
			Chat.error("Could not save the route file.");
			return 0;
		}
		// the copy being edited is the one saved on the next waypoint, so it has to hear about this too
		if (WigState.editing != null && WigState.editing.name().equals(route.name())) {
			WigState.editing.setRadius(blocks);
		}
		Chat.info(route.name() + " now turns " + (blocks == 0 ? "at the middle of each block" : "within " + trim(route.radius()) + " blocks of each waypoint") + ".");
		return 1;
	}

	/** /wig left|right <route> on|off: hold that mouse button while the route runs. */
	private static LiteralArgumentBuilder<FabricClientCommandSource> clickCommand(String name, boolean left) {
		return ClientCommands.literal(name)
			.then(ClientCommands.argument("route", StringArgumentType.word()).suggests(ROUTE_NAMES)
				.then(ClientCommands.literal("on").executes(c -> hold(StringArgumentType.getString(c, "route"), left, true)))
				.then(ClientCommands.literal("off").executes(c -> hold(StringArgumentType.getString(c, "route"), left, false))));
	}

	private static int hold(String typed, boolean left, boolean on) {
		Route route = findRoute(typed);
		if (route == null) {
			return 0;
		}
		if (left) {
			route.setHoldLeft(on);
		} else {
			route.setHoldRight(on);
		}
		if (!WigState.save(route)) {
			Chat.error("Could not save the route file.");
			return 0;
		}
		if (WigState.editing != null && WigState.editing.name().equals(route.name())) {
			if (left) {
				WigState.editing.setHoldLeft(on);
			} else {
				WigState.editing.setHoldRight(on);
			}
		}
		Chat.info(route.name() + " will " + (on ? "hold" : "not hold") + " the " + (left ? "left" : "right") + " mouse button while it runs.");
		return 1;
	}

	private static int facing(String typed, Double pitch, boolean randomize) {
		Route route = findRoute(typed);
		if (route == null) {
			return 0;
		}
		route.setPitch(pitch, randomize);
		if (!WigState.save(route)) {
			Chat.error("Could not save the route file.");
			return 0;
		}
		if (WigState.editing != null && WigState.editing.name().equals(route.name())) {
			WigState.editing.setPitch(pitch, randomize);
		}
		if (pitch == null) {
			Chat.info(route.name() + " no longer holds a pitch: the view's up/down angle is left alone.");
		} else {
			Chat.info(route.name() + " now holds pitch " + trim(route.pitch()) + (randomize ? " (varying by up to " + trim(RouteFollower.RANDOM_PITCH_DEGREES) + " degrees)" : "")
				+ " while it runs. 0 is level, positive looks down, negative looks up.");
		}
		return 1;
	}

	private static String trim(double v) {
		return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
	}

	/** /wig follow <player>, or /wig follow while looking at a player. */
	private static int follow(String typed) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return 0;
		}
		AbstractClientPlayer target = null;
		if (typed == null) {
			if (mc.crosshairPickEntity instanceof AbstractClientPlayer looked && looked != mc.player) {
				target = looked;
			} else {
				Chat.error("Look at a player, or give a name: /wig follow <player>.");
				return 0;
			}
		} else {
			for (AbstractClientPlayer other : mc.level.players()) {
				if (other != mc.player && other.getGameProfile().name().equalsIgnoreCase(typed)) {
					target = other;
				}
			}
			if (target == null) {
				Chat.error(typed + " isn't near you. Players can only be followed while they are in range.");
				return 0;
			}
		}
		java.util.UUID id = target.getUUID();
		String name = target.getGameProfile().name();
		if (WigState.editing != null) {
			EditMode.leave();
		}
		Runner.startFollow(name, () -> {
			if (mc.level == null) {
				return null;
			}
			for (AbstractClientPlayer other : mc.level.players()) {
				if (other.getUUID().equals(id)) {
					return new FollowPlanner.Target(other.getX(), other.getY(), other.getZ(), other.getEyeY());
				}
			}
			return null;
		});
		Chat.info("Following " + name + ". The mouse is locked. /wig stop, a movement key or any screen stops it.");
		return 1;
	}

	private static int stop() {
		if (!Runner.isRunning()) {
			Chat.error("Nothing is running.");
			return 0;
		}
		Runner.stop("you used /wig stop");
		return 1;
	}

	private static int toggleWaypoints() {
		Route route = WigState.editing != null ? WigState.editing : WigState.selectedRoute();
		if (route == null) {
			Chat.error("No route is selected. /wig select <route> picks one.");
			return 0;
		}
		route.setShowWaypoints(!route.showWaypoints());
		if (!WigState.save(route)) {
			Chat.error("Could not save the route file.");
			return 0;
		}
		Chat.info("Waypoints " + (route.showWaypoints() ? "shown" : "hidden") + " for " + route.name() + ".");
		return 1;
	}

	private static int noJump(String typed, boolean on) {
		Route route = findRoute(typed);
		if (route == null) {
			return 0;
		}
		route.setNoJump(on);
		if (!WigState.save(route)) {
			Chat.error("Could not save the route file.");
			return 0;
		}
		Chat.info(route.name() + (on ? " will never jump." : " may jump again when it has to."));
		return 1;
	}

	private static int toggleHud() {
		WigState.config.showHud = !WigState.config.showHud;
		WigState.saveConfig();
		Chat.info("The HUD is " + (WigState.config.showHud ? "on" : "off") + ".");
		return 1;
	}

	private static int toggleDebug() {
		WigState.debug = !WigState.debug;
		Chat.info("Debug " + (WigState.debug ? "on: turn information shows above the hotbar while a route runs." : "off."));
		return 1;
	}

	private static int clear() {
		Route route = WigState.editing != null ? WigState.editing : WigState.selectedRoute();
		if (route == null) {
			Chat.error("No route is selected. /wig select <route> picks one.");
			return 0;
		}
		route.waypoints().clear();
		if (!WigState.save(route)) {
			Chat.error("Could not save the route file.");
			return 0;
		}
		Chat.info("Removed every waypoint from " + route.name() + ".");
		return 1;
	}

	private static int delete(String typed) {
		Route route = findRoute(typed);
		if (route == null) {
			return 0;
		}
		long now = System.currentTimeMillis();
		if (!route.name().equals(pendingDelete) || now - pendingDeleteAt > CONFIRM_MILLIS) {
			pendingDelete = route.name();
			pendingDeleteAt = now;
			Chat.info("Run /wig delete " + route.name() + " again within 10 seconds to delete it for good.");
			return 1;
		}
		pendingDelete = null;
		if (!WigState.delete(route)) {
			Chat.error("Could not delete the route file.");
			return 0;
		}
		if (WigState.editing != null && WigState.editing.name().equals(route.name())) {
			EditMode.leave();
		}
		if (route.name().equals(WigState.config.selectedRoute)) {
			WigState.select("");
		}
		Chat.info("Deleted " + route.name() + ".");
		return 1;
	}

	private static int setLookWall(boolean on) {
		WigState.config.followLookOnlyWhenVisible = on;
		WigState.saveConfig();
		Chat.info("When following, you " + (on ? "stop looking at the player while a wall is between you." : "always look at the player."));
		return 1;
	}

	public static int setHuntHome(boolean set, boolean zealots) {
		var mc = net.minecraft.client.Minecraft.getInstance();
		if (set && mc.player == null) {
			return 0;
		}
		WigConfig target = zealots ? WigState.zealots : WigState.config;
		target.killHomeSet = set;
		if (set) {
			target.killHomeX = Math.floor(mc.player.getX()) + 0.5;
			target.killHomeY = Math.floor(mc.player.getY() + 0.01);
			target.killHomeZ = Math.floor(mc.player.getZ()) + 0.5;
		}
		if (zealots) {
			WigState.saveZealots();
		} else {
			WigState.saveConfig();
		}
		String name = zealots ? "Zealots" : "Hunt"; // the home of the script it was set for
		Chat.info(set ? name + " home set here. With nothing to hunt you walk back here and wander around it."
			: name + " home cleared. With nothing to hunt you stand still.");
		return 1;
	}
}
