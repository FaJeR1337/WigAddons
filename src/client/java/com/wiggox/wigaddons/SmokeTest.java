package com.wiggox.wigaddons;

import com.wiggox.wigaddons.gui.WigGui;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * For development only: started with -Dwigaddons.smoke=true, the game makes a flat singleplayer world, photographs the
 * settings screen's tabs and the waypoint boxes, runs routes (in the open, round a pillar, over a step) logging how
 * the player moves, and quits. Everything it prints starts with [SMOKE]. Does nothing otherwise.
 */
final class SmokeTest {
	private static final Logger LOG = LoggerFactory.getLogger("wigaddons-smoke");

	/** Tab, then the option to scroll into view, for each picture of the settings screen. */
	private static final String[][] TOUR = {
		{"about", "", ""},
		{"list", "", ""},
		{"routes", "routes.route.farm.tabs", "general"},
		{"routes", "routes.route.farm.tabs", "waypoints"},
		{"hud", "", ""},
		{"safety", "safety.macro", ""},
	};
	private static final int TOUR_STEP_TICKS = 25;
	/** Flat world: grass is at y -61, so the player stands at y -60. */
	private static final int GROUND = -61;

	private static int tick;
	private static int worldTicks;
	private static int phase;
	private static int phaseTick;
	private static boolean creating;
	private static boolean strafeSeen;
	private static double lastYaw;
	private static double maxTurn;

	private SmokeTest() {
	}

	static void register() {
		if (Boolean.getBoolean("wigaddons.smoke")) {
			ClientTickEvents.END_CLIENT_TICK.register(SmokeTest::tick);
		}
	}

	private static void log(String message) {
		LOG.info("[SMOKE] {}", message);
	}

	private static void tick(Minecraft mc) {
		tick++;
		// the game window has no focus here, which would open the pause menu and stop every run
		mc.options.pauseOnLostFocus = false;
		if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
			mc.setScreen(null);
		}
		if (mc.player == null || mc.level == null) {
			if (tick == 60 && !creating) {
				creating = true;
				log("creating a flat world");
				mc.createWorldOpenFlows().createFreshLevel("WigSmoke",
					new LevelSettings("WigSmoke", GameType.CREATIVE, new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, true, false), true, WorldDataConfiguration.DEFAULT),
					new WorldOptions(12345L, false, false), WorldPresets::createFlatWorldDimensions, new TitleScreen());
			}
			return;
		}
		worldTicks++;
		phaseTick++;
		if (mc.screen != null && tick % 40 == 0) {
			log("a screen is open: " + mc.screen.getClass().getName() + " in phase " + phase);
		}
		switch (phase) {
			case 0 -> {
				if (worldTicks == 100) {
					command(mc, "tp @s 0.5 -60 0.5 0 0");
					command(mc, "gamerule doDaylightCycle false");
					command(mc, "time set noon");
					next();
				}
			}
			case 1 -> setupAndOpenGui(mc);
			case 2 -> tour(mc);
			case 3 -> showBoxes(mc);
			case 4 -> runRoute(mc, "farm", false, 5);
			case 5 -> buildObstacle(mc, "wall", "fill 0 -60 8 0 -59 8 stone");
			case 6 -> runRoute(mc, "wall", false, 7);
			case 7 -> buildObstacle(mc, "step", "fill 0 -60 8 0 -59 8 air");
			case 8 -> {
				if (phaseTick == 20) {
					command(mc, "setblock 0 -60 8 stone");
					next();
				}
			}
			case 9 -> runRoute(mc, "step", false, 10);
			case 10 -> commandOpensGui(mc);
			case 11 -> macroCheck(mc);
			case 12 -> partyLeave(mc);
			case 13 -> jumpCheck(mc, false, 14);
			case 14 -> jumpCheck(mc, true, 15);
			case 15 -> followCheck(mc);
			case 16 -> followLostCheck(mc);
			case 17 -> hudPosition(mc);
			case 18 -> {
				log("done");
				mc.stop();
				phase = 99;
			}
			default -> {
			}
		}
	}

	/** /wig with nothing after it should open the settings screen. */
	private static void commandOpensGui(Minecraft mc) {
		if (phaseTick == 1) {
			command(mc, "wig");
		}
		if (phaseTick == 20) {
			log("after /wig the open screen is " + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName()));
			mc.setScreen(null);
			next();
		}
	}

	/** Changing the hotbar slot during a run should raise a macro check: a red WARNING for 3 seconds, then stop. */
	private static void macroCheck(Minecraft mc) {
		if (phaseTick == 1) {
			command(mc, "tp @s 0.5 -60 0.5 -90 0");
			command(mc, "fill 0 -60 8 0 -59 8 air");
		}
		if (phaseTick == 20) {
			log("start long, then change the hotbar slot");
			WigCommands.startRoute(WigState.route("long"), false);
		}
		if (phaseTick == 60) {
			mc.player.getInventory().setSelectedSlot(4);
			log("slot changed at tick " + phaseTick + ", running=" + Runner.isRunning());
		}
		if (phaseTick == 85) {
			photograph(mc, "macro-warning");
		}
		if (phaseTick > 60 && !Runner.isRunning()) {
			log("run stopped " + (phaseTick - 60) + " ticks after the slot change");
			mc.player.getInventory().setSelectedSlot(0);
			next();
		}
		if (phaseTick > 400) {
			log("macro check did not stop the run");
			Runner.stop("smoke test timeout");
			next();
		}
	}

	/** A "left the party" chat line during a run should stop it 2 seconds later and send /warp hub. */
	private static void partyLeave(Minecraft mc) {
		if (phaseTick == 1) {
			command(mc, "tp @s 0.5 -60 0.5 -90 0");
		}
		if (phaseTick == 20) {
			log("start long, then a party member leaves");
			WigCommands.startRoute(WigState.route("long"), false);
		}
		if (phaseTick == 60) {
			Runner.onChatLine("[MVP+] Davidcovi has left the party.");
			log("chat line fed at tick " + phaseTick);
		}
		if (phaseTick > 60 && !Runner.isRunning()) {
			log("run stopped " + (phaseTick - 60) + " ticks after the chat line");
			photograph(mc, "party-leave");
			next();
		}
		if (phaseTick > 400) {
			log("party leave did not stop the run");
			Runner.stop("smoke test timeout");
			next();
		}
	}

	private static double targetX;
	private static boolean targetVisible = true;

	/**
	 * A pretend player walks east and then stands still; the follower should keep up, stop close to it, and not keep
	 * starting and stopping while it stands.
	 */
	private static void followCheck(Minecraft mc) {
		if (phaseTick == 1) {
			command(mc, "tp @s 0.5 -60 0.5 -90 0");
			targetX = 8;
			targetVisible = true;
			WigState.config.followShowTrail = true;
		}
		if (phaseTick == 30) {
			log("start following a pretend player at x=" + targetX);
			Runner.startFollow("Tester", () -> targetVisible ? new FollowPlanner.Target(targetX, -60, 0.5, -58.38) : null);
		}
		if (phaseTick > 30) {
			// the pretend player walks east at walking pace for 15 seconds, then stands
			if (phaseTick < 330) {
				targetX += 0.215;
			}
			if (phaseTick % 40 == 0) {
				log("follow t=" + (phaseTick - 30) + ": player at x=" + String.format("%.1f", targetX) + ", me " + fmt(mc) + ", distance " + String.format("%.1f", Runner.followDistance()));
			}
			if (phaseTick == 200) {
				FollowPlanner.Snapshot snap = Runner.followTrail();
				log("trail points " + (snap == null ? "none" : snap.trail().size() + ", next " + (snap.next() == null ? "none" : String.format("%.1f,%.1f", snap.next()[0], snap.next()[2]))
					+ ", last seen " + (snap.lastSeen() == null ? "none" : String.format("%.1f", snap.lastSeen()[0])) + ", showTrail=" + WigState.config.followShowTrail));
				photograph(mc, "follow");
			}
			if (phaseTick == 460) {
				double d = Math.hypot(targetX - mc.player.getX(), 0.5 - mc.player.getZ());
				log("follow ended: still following=" + Runner.isRunning() + ", distance " + String.format("%.1f", d));
				Runner.stop("smoke test over");
				next();
			}
		}
	}

	/** The pretend player vanishes: the follower should go to where it was last seen, wait, then stop. */
	private static void followLostCheck(Minecraft mc) {
		if (phaseTick == 1) {
			command(mc, "tp @s 0.5 -60 0.5 -90 0");
			targetX = 14;
			targetVisible = true;
		}
		if (phaseTick == 30) {
			Runner.startFollow("Tester", () -> targetVisible ? new FollowPlanner.Target(targetX, -60, 0.5, -58.38) : null);
		}
		if (phaseTick == 50) {
			targetVisible = false;
			log("pretend player vanished at x=" + targetX + ", me " + fmt(mc));
		}
		if (phaseTick > 50 && !Runner.isRunning()) {
			log("stopped " + (phaseTick - 50) + " ticks after it vanished, me " + fmt(mc));
			next();
		}
		if (phaseTick > 600) {
			log("lost follow TIMEOUT");
			Runner.stop("smoke test timeout");
			next();
		}
	}

	private static double maxY;

	/**
	 * A route that climbs (its waypoint is a block higher) over a one-block step: with "never jump" off the player
	 * should hop onto the step, with it on the player should stay on the ground and walk round.
	 */
	private static void jumpCheck(Minecraft mc, boolean neverJump, int nextPhase) {
		if (phaseTick == 1) {
			Route up = route("up", "ladder", 1, false, new Waypoint(0, GROUND + 1, 20));
			up.setNoJump(neverJump);
			WigState.save(up);
			command(mc, "tp @s 0.5 -60 0.5 0 0");
			command(mc, "setblock 0 -60 8 stone");
			maxY = -60;
			strafeSeen = false;
		}
		if (phaseTick == 30) {
			log("start up (never jump " + (neverJump ? "on" : "off") + ")");
			WigCommands.startRoute(WigState.route("up"), false);
		}
		if (phaseTick > 30) {
			maxY = Math.max(maxY, mc.player.getY());
			if (Runner.strafe() != 0) {
				strafeSeen = true;
			}
			if (!Runner.isRunning() && phaseTick > 32) {
				log("never jump " + (neverJump ? "on" : "off") + ": highest point " + String.format("%.2f", maxY) + " (ground is -60.00), strafed=" + strafeSeen
					+ ", ended at " + fmt(mc));
				phase = nextPhase;
				phaseTick = 0;
			} else if (phaseTick > 700) {
				log("never jump " + (neverJump ? "on" : "off") + ": TIMEOUT");
				Runner.stop("smoke test timeout");
			}
		}
	}

	/** The HUD position screen: drag the box and see the settings change. */
	private static void hudPosition(Minecraft mc) {
		if (phaseTick == 1) {
			HudPositionScreen.open();
		}
		if (phaseTick == 10) {
			log("position screen is " + mc.screen.getClass().getSimpleName() + " at " + String.format("%.2f,%.2f scale %.1f", WigState.config.hudX, WigState.config.hudY, WigState.config.hudScale));
			photograph(mc, "hud-position-before");
		}
		if (phaseTick == 20) {
			// grab the box where it is drawn and drag it to the lower right
			int gx = (int) (WigState.config.hudX * (mc.getWindow().getGuiScaledWidth() - 90)) + 10;
			int gy = (int) (WigState.config.hudY * (mc.getWindow().getGuiScaledHeight() - 30)) + 10;
			var screen = mc.screen;
			var info = new net.minecraft.client.input.MouseButtonInfo(0, 0);
			screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(gx, gy, info), false);
			screen.mouseDragged(new net.minecraft.client.input.MouseButtonEvent(mc.getWindow().getGuiScaledWidth() - 40, mc.getWindow().getGuiScaledHeight() - 30, info), 0, 0);
			screen.mouseReleased(new net.minecraft.client.input.MouseButtonEvent(mc.getWindow().getGuiScaledWidth() - 40, mc.getWindow().getGuiScaledHeight() - 30, info));
			screen.mouseScrolled(0, 0, 0, 5);
		}
		if (phaseTick == 30) {
			log("after the drag and a scroll: " + String.format("%.2f,%.2f scale %.1f", WigState.config.hudX, WigState.config.hudY, WigState.config.hudScale));
			photograph(mc, "hud-position-after");
		}
		if (phaseTick == 40) {
			mc.screen.onClose();
			log("after Esc the open screen is " + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName()));
			mc.setScreen(null);
			next();
		}
	}

	private static void next() {
		phase++;
		phaseTick = 0;
	}

	private static void command(Minecraft mc, String command) {
		mc.player.connection.sendCommand(command);
	}

	private static Route route(String name, String icon, double radius, boolean show, Waypoint... points) {
		Route route = WigState.route(name);
		if (route == null) {
			route = new Route(name, List.of(points));
		} else {
			route.waypoints().clear();
			route.waypoints().addAll(List.of(points));
		}
		route.setIcon(icon);
		route.setRadius(radius);
		route.setShowWaypoints(show);
		WigState.save(route);
		return route;
	}

	private static void setupAndOpenGui(Minecraft mc) {
		Route farm = route("farm", "diamond_pickaxe", 3, true, new Waypoint(10, GROUND, 10), new Waypoint(14, GROUND, 10), new Waypoint(14, GROUND, 20));
		farm.setPitch(30.0, true);
		WigState.save(farm);
		route("mine", "iron_pickaxe", 1.5, false, new Waypoint(0, GROUND, 0), new Waypoint(5, GROUND, 5));
		route("wall", "stone", 1, false, new Waypoint(0, GROUND, 20));
		route("step", "cobblestone", 1, false, new Waypoint(0, GROUND, 20));
		route("long", "oak_boat", 1, false, new Waypoint(60, GROUND, 0));
		WigGui.expandAll = true;
		WigGui.open();
		log("gui open");
		next();
	}

	private static void tour(Minecraft mc) {
		int step = phaseTick / TOUR_STEP_TICKS;
		if (phaseTick % TOUR_STEP_TICKS == 8 && step < TOUR.length) {
			// the first scroll can be cut short before the tab has been laid out; do it again once it has
			WigGui.smokeStep(TOUR[step][0], TOUR[step][1]);
		}
		if (phaseTick % TOUR_STEP_TICKS == 0) {
			if (step > 0) {
				photograph(mc, "gui-" + String.format("%02d", step - 1));
			}
			if (step < TOUR.length) {
				if (!TOUR[step][2].isEmpty()) {
					int before = System.identityHashCode(mc.screen);
					WigGui.smokeTab("farm", TOUR[step][2]);
					log("tab " + TOUR[step][2] + ": screen " + (before == System.identityHashCode(mc.screen) ? "stayed the same" : "was REPLACED"));
				}
				WigGui.smokeStep(TOUR[step][0], TOUR[step][1]);
			} else {
				WigGui.expandAll = false;
				mc.setScreen(null);
				log("gui tour done");
				next();
			}
		}
	}

	private static void showBoxes(Minecraft mc) {
		if (phaseTick == 1) {
			// stand back from the farm route's waypoints, looking at them
			command(mc, "tp @s 4.5 -60 -6.5 -35 25");
		}
		if (phaseTick == 40) {
			photograph(mc, "world-boxes");
		}
		if (phaseTick == 60) {
			command(mc, "tp @s 0.5 -60 0.5 0 0");
			next();
		}
	}

	private static void buildObstacle(Minecraft mc, String name, String fill) {
		if (phaseTick == 1) {
			command(mc, "tp @s 0.5 -60 0.5 0 0");
			command(mc, fill);
		}
		if (phaseTick == 30) {
			next();
		}
	}

	private static void runRoute(Minecraft mc, String name, boolean loop, int nextPhase) {
		if (phaseTick == 1) {
			Route route = WigState.route(name);
			if (name.equals("farm")) {
				command(mc, "tp @s 0.5 -60 0.5 0 0");
			}
			strafeSeen = false;
			maxTurn = 0;
			lastYaw = mc.player.getYRot();
			log("start " + name + " from " + fmt(mc));
			WigCommands.startRoute(route, loop);
			return;
		}
		if (Runner.isRunning()) {
			if (Runner.strafe() != 0 && !strafeSeen) {
				strafeSeen = true;
				log("strafing " + (Runner.strafe() > 0 ? "A (left)" : "D (right)") + " at " + fmt(mc));
			}
			double yaw = mc.player.getYRot();
			maxTurn = Math.max(maxTurn, Math.abs(RouteFollower.wrapDegrees(yaw - lastYaw)));
			lastYaw = yaw;
			if (phaseTick % 20 == 0) {
				log(name + " t=" + phaseTick + " " + fmt(mc));
			}
			if (phaseTick == 60 && name.equals("farm")) {
				photograph(mc, "run-farm");
			}
			if (phaseTick > 900) {
				log(name + " TIMEOUT at " + fmt(mc));
				Runner.stop("smoke test timeout");
			}
		} else if (phaseTick > 2) {
			log("finished " + name + " at " + fmt(mc) + " strafed=" + strafeSeen + " maxTurnPerTick=" + String.format("%.1f", maxTurn));
			phase = nextPhase;
			phaseTick = 0;
		}
	}

	private static String fmt(Minecraft mc) {
		return String.format("pos=(%.2f, %.2f, %.2f) yaw=%.1f pitch=%.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYRot(), mc.player.getXRot());
	}

	private static void photograph(Minecraft mc, String name) {
		Screenshot.grab(mc.gameDirectory, "smoke-" + name + ".png", mc.getMainRenderTarget(), 1, c -> {
		});
	}
}
