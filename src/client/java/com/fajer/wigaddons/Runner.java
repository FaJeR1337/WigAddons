package com.fajer.wigaddons;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;

/** Runs the player along a route by steering their view and feeding forward/sprint/jump into their input. */
public final class Runner {
	/** Ticks after starting during which an open screen (the chat the command came from) doesn't cancel the run. */
	private static final int SCREEN_GRACE_TICKS = 5;

	private static RouteFollower follower;
	private static boolean jump;
	private static float lastHealth;
	private static int ticks;

	private Runner() {
	}

	public static boolean isRunning() {
		return follower != null;
	}

	/** True while the input mixin should press forward and sprint. */
	public static boolean wantsInput() {
		return follower != null;
	}

	public static boolean wantsJump() {
		return jump;
	}

	public static void start(Route route, boolean loop) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		follower = new RouteFollower(route.waypoints(), loop, WigState.config);
		jump = false;
		ticks = 0;
		lastHealth = mc.player.getHealth();
	}

	public static void stop(String reason) {
		if (follower == null) {
			return;
		}
		follower = null;
		jump = false;
		Chat.info("Stopped: " + reason + ".");
	}

	/** Called at the start of every client tick, before the player's input is read. */
	public static void tick(Minecraft mc) {
		if (follower == null) {
			return;
		}
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			follower = null;
			jump = false;
			return;
		}
		if (mc.screen != null && ticks >= SCREEN_GRACE_TICKS) {
			stop("a screen was opened");
			return;
		}
		if (userIsPressingMovementKey(mc.options)) {
			stop("you pressed a movement key");
			return;
		}
		if (player.getHealth() < lastHealth) {
			stop("you took damage");
			return;
		}
		lastHealth = player.getHealth();
		ticks++;

		RouteFollower.Output out = follower.tick(player.getX(), player.getZ(), player.getYRot(), player.onGround(), player.horizontalCollision);
		switch (out.status()) {
			case FINISHED -> {
				follower = null;
				jump = false;
				Chat.info("Route finished.");
				return;
			}
			case STUCK -> {
				stop("you seem to be stuck");
				return;
			}
			case RUNNING -> {
			}
		}
		jump = out.jump();
		player.setYRot((float) out.yaw());
	}

	/** Our own input is added in the keyboard input after these keys are read, so they only ever hold the player's own presses. */
	private static boolean userIsPressingMovementKey(Options o) {
		for (KeyMapping key : new KeyMapping[] {o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump, o.keyShift}) {
			if (key.isDown()) {
				return true;
			}
		}
		return false;
	}
}
