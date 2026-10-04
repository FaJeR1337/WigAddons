package com.fajer.wigaddons;

import com.fajer.wigaddons.gui.WigGui;
import java.io.File;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * For development only: when the game is started with -Dwigaddons.smoke=true it makes a couple of sample routes, opens
 * the settings screen with every section expanded, photographs each tab, and quits, so the screen can be checked
 * without anyone at the controls. Does nothing otherwise.
 */
final class SmokeTest {
	private static final int OPEN_AT_TICK = 100;
	private static final int TICKS_PER_STEP = 30;

	/** Tab, then the option to scroll into view, for each picture. */
	private static final String[][] STEPS = {
		{"about", ""},
		{"routes", "routes.defaults"},
		{"routes", "routes.route.farm"},
		{"routes", "routes.route.farm.waypoints"},
		{"routes", "routes.route.farm.movement"},
		{"routes", "routes.route.farm.humanize"},
		{"routes", "routes.route.farm.display"},
		{"routes", "routes.route.mine"},
		{"safety", "safety.macro"},
		{"safety", "safety.party"},
	};

	private SmokeTest() {
	}

	static void register() {
		if (!Boolean.getBoolean("wigaddons.smoke")) {
			return;
		}
		int[] tick = {0};
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			tick[0]++;
			if (tick[0] == OPEN_AT_TICK) {
				sample("farm", "diamond_pickaxe", 3, new Waypoint(10, 64, 10), new Waypoint(14, 64, 10), new Waypoint(14, 64, 20));
				sample("mine", "iron_pickaxe", 1.5, new Waypoint(0, 70, 0), new Waypoint(5, 70, 5));
				WigGui.expandAll = true;
				WigGui.open();
				return;
			}
			int since = tick[0] - OPEN_AT_TICK;
			if (since > 0 && since % TICKS_PER_STEP == 0) {
				int step = since / TICKS_PER_STEP - 1;
				if (step > 0) {
					photograph(mc, step - 1);
				}
				if (step < STEPS.length) {
					WigGui.smokeStep(STEPS[step][0], STEPS[step][1]);
				} else {
					mc.stop();
				}
			}
		});
	}

	private static void photograph(Minecraft mc, int index) {
		Screenshot.grab(mc.gameDirectory, "smoke-" + String.format("%02d", index) + ".png", mc.getMainRenderTarget(), 1, c -> {
		});
	}

	private static void sample(String name, String icon, double radius, Waypoint... points) {
		Route route = WigState.route(name);
		if (route == null) {
			route = new Route(name, java.util.List.of(points));
			route.setIcon(icon);
			route.setRadius(radius);
			route.setPitch(30.0, true);
			route.setShowWaypoints(true);
			WigState.save(route);
		}
	}
}
