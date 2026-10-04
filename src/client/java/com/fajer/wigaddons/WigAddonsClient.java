package com.fajer.wigaddons;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class WigAddonsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		WigState.load();
		WigCommands.register();
		EditMode.register();
		WaypointRenderer.register();

		// at the end of the tick, after the game has stored the previous rotation, so the camera blends between the two
		ClientTickEvents.END_CLIENT_TICK.register(Runner::tick);
		// servers send party notices as system messages
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				Runner.onChatLine(message.getString());
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			Runner.stop("you disconnected");
			WigState.editing = null;
		});
	}
}
