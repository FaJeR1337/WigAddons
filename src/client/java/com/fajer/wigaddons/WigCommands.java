package com.fajer.wigaddons;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/** /wigaddons and its short form /wig. */
public final class WigCommands {
	private static final long CONFIRM_MILLIS = 10_000;

	private static final SuggestionProvider<FabricClientCommandSource> ROUTE_NAMES = (c, b) -> {
		String prefix = b.getRemaining().toLowerCase(Locale.ROOT);
		for (String name : WigState.STORE.names()) {
			if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
				b.suggest(name);
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
			.then(ClientCommands.literal("stop").executes(c -> stop()))
			.then(ClientCommands.literal("waypoints").executes(c -> toggleWaypoints()))
			.then(ClientCommands.literal("clear").executes(c -> clear()))
			.then(ClientCommands.literal("delete")
				.then(ClientCommands.argument("route", StringArgumentType.word()).suggests(ROUTE_NAMES)
					.executes(c -> delete(StringArgumentType.getString(c, "route")))));
	}

	/** Resolves a typed name to a saved route, telling the player when it doesn't exist. */
	private static Route findRoute(String typed) {
		String name = WigState.STORE.canonicalName(typed);
		Route route = name == null ? null : WigState.STORE.load(name);
		if (route == null) {
			Chat.error("There is no route called \"" + typed + "\". /wig routes lists them.");
		}
		return route;
	}

	private static int create(String name) {
		if (!RouteStore.isValidName(name)) {
			Chat.error("Route names use only letters, numbers, _ and -, up to 32 characters, and can't be \"config\".");
			return 0;
		}
		if (WigState.STORE.canonicalName(name) != null) {
			Chat.error("A route called " + name + " already exists. Use /wig edit " + name + " to add to it.");
			return 0;
		}
		Route route = new Route(name);
		try {
			WigState.STORE.save(route);
		} catch (IOException e) {
			Chat.error("Could not save the route file.");
			return 0;
		}
		WigState.select(name);
		EditMode.enter(route);
		Chat.info("Created " + name + ". Right click blocks with an empty hand to place waypoints, then /wig done.");
		return 1;
	}

	private static int edit(String typed) {
		Route route = findRoute(typed);
		if (route == null) {
			return 0;
		}
		WigState.select(route.name());
		EditMode.enter(route);
		Chat.info("Editing " + route.name() + " (" + route.waypoints().size() + " waypoints). /wig done when finished.");
		return 1;
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
		List<String> names = WigState.STORE.names();
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
		if (route == null) {
			return 0;
		}
		if (route.waypoints().isEmpty()) {
			Chat.error(route.name() + " has no waypoints.");
			return 0;
		}
		if (WigState.editing != null) {
			EditMode.leave();
		}
		WigState.select(route.name());
		Runner.start(route, loop);
		Chat.info("Running " + route.name() + (loop ? " on a loop" : "") + ". /wig stop, a movement key or any screen stops it.");
		return 1;
	}

	private static int radius(String typed, double blocks) {
		Route route = findRoute(typed);
		if (route == null) {
			return 0;
		}
		route.setRadius(blocks);
		try {
			WigState.STORE.save(route);
		} catch (IOException e) {
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

	private static String trim(double v) {
		return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
	}

	private static int stop() {
		if (!Runner.isRunning()) {
			Chat.error("No route is running.");
			return 0;
		}
		Runner.stop("you used /wig stop");
		return 1;
	}

	private static int toggleWaypoints() {
		WigState.config.showWaypoints = !WigState.config.showWaypoints;
		WigState.saveConfig();
		Chat.info("Waypoints " + (WigState.config.showWaypoints ? "shown" : "hidden") + " for the selected route.");
		return 1;
	}

	private static int clear() {
		Route route = WigState.editing != null ? WigState.editing : WigState.selectedRoute();
		if (route == null) {
			Chat.error("No route is selected. /wig select <route> picks one.");
			return 0;
		}
		route.waypoints().clear();
		try {
			WigState.STORE.save(route);
		} catch (IOException e) {
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
		try {
			WigState.STORE.delete(route.name());
		} catch (IOException e) {
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
}
