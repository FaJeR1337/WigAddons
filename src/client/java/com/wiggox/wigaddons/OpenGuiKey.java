package com.wiggox.wigaddons;

import com.wiggox.wigaddons.gui.WigGui;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** The "Open GUI" key in Controls (under a WigAddons heading), and the way commands ask for the screen to open. */
public final class OpenGuiKey {
	private static KeyMapping key;
	private static boolean requested;
	private static boolean requestedHudPosition;

	private OpenGuiKey() {
	}

	public static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("wigaddons", "general"));
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.wigaddons.open_gui", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));
		ClientTickEvents.END_CLIENT_TICK.register(OpenGuiKey::tick);
	}

	/** Opens the HUD position screen on the next tick (the chat box a command was typed in is closed by then). */
	public static void requestHudPosition() {
		requestedHudPosition = true;
	}

	/** Opens the screen on the next tick. */
	public static void requestOpen() {
		requested = true;
	}

	private static void tick(Minecraft mc) {
		boolean pressed = false;
		while (key.consumeClick()) {
			pressed = true;
		}
		if (mc.player == null) {
			requested = false;
			requestedHudPosition = false;
			return;
		}
		if (requestedHudPosition) {
			requestedHudPosition = false;
			HudPositionScreen.open();
			return;
		}
		// a chat box or other screen the request came from is gone by now; a key press only counts in the open world
		if (requested || (pressed && mc.screen == null)) {
			requested = false;
			WigGui.open();
		}
	}
}
