package com.wiggox.wigaddons;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

/** While a route is being edited, right-clicking a block with an empty hand adds it as the next waypoint. */
public final class EditMode {
	private static final Component HINT = Component.literal("Right click with an empty hand to place waypoints");
	/** How often the on-screen hint is shown again. It is shown for longer than this, so it never flickers. */
	private static final int HINT_REFRESH_TICKS = 20;

	private static int hintTimer;

	private EditMode() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (WigState.editing == null || !level.isClientSide() || hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty()) {
				return InteractionResult.PASS;
			}
			addWaypoint(hit.getBlockPos());
			return InteractionResult.SUCCESS;
		});
		ClientTickEvents.END_CLIENT_TICK.register(EditMode::showHint);
	}

	public static void enter(Route route) {
		WigState.editing = route;
		hintTimer = 0;
	}

	public static void leave() {
		WigState.editing = null;
		Minecraft mc = Minecraft.getInstance();
		mc.gui.clearTitles();
		mc.gui.resetTitleTimes();
	}

	private static void addWaypoint(BlockPos pos) {
		Route route = WigState.editing;
		route.waypoints().add(new Waypoint(pos.getX(), pos.getY(), pos.getZ()));
		if (WigState.save(route)) {
			Chat.info("Waypoint " + route.waypoints().size() + " placed at " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " in " + route.name() + ".");
		} else {
			route.waypoints().removeLast();
			Chat.error("Could not save the route, so that waypoint was not placed.");
		}
	}

	private static void showHint(Minecraft mc) {
		if (WigState.editing == null || mc.player == null) {
			return;
		}
		if (hintTimer-- <= 0) {
			hintTimer = HINT_REFRESH_TICKS;
			mc.gui.setTimes(0, HINT_REFRESH_TICKS * 2, 0);
			mc.gui.setTitle(Component.literal(" "));
			mc.gui.setSubtitle(HINT);
		}
	}
}
