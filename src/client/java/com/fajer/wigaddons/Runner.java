package com.fajer.wigaddons;

import java.util.Random;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Runs the player along a route. Each tick it works out which way to face and feeds forward/sprint/jump into the
 * player's input; every rendered frame it turns the view a little toward that direction, so the turn is smooth at
 * any frame rate. While it runs the mouse is locked, and it stops when it sees something a person wouldn't do or
 * that a server might check for (see {@link #tick}).
 */
public final class Runner {
	/** Ticks after starting during which an open screen (the chat the command came from) doesn't cancel the run. */
	private static final int SCREEN_GRACE_TICKS = 5;
	private static final double TICKS_PER_NANO = 20.0 / 1_000_000_000.0;
	/** A frame never turns the view by more than this many ticks' worth, so a long hitch can't whip it round. */
	private static final double MAX_FRAME_TICKS = 2.0;

	private static final ObstacleAvoider AVOIDER = new ObstacleAvoider(new WorldTerrain(), new Random());

	private static RouteFollower follower;
	private static RouteFollower.Output lastOutput;
	private static boolean jump;
	/** +1 holds A, -1 holds D, 0 neither: set while going round an obstacle. */
	private static int strafe;
	private static int ticks;
	private static int startSlot;
	/** Where the view was left by the last frame: any other change is somebody else's. */
	private static float expectedYaw;
	private static float expectedPitch;
	private static boolean haveAim;
	private static double aimYaw;
	private static long lastFrameNanos;
	// for /wig debug
	private static double yawAtLastTick;
	private static double turnPerTick;
	private static double lastFrameStep;
	private static double lastFrameTicks;

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

	public static int strafe() {
		return strafe;
	}

	public static void start(Route route, boolean loop) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		follower = new RouteFollower(route.waypoints(), loop, route.radius(), WigState.config);
		lastOutput = null;
		jump = false;
		strafe = 0;
		AVOIDER.reset();
		ticks = 0;
		startSlot = player.getInventory().getSelectedSlot();
		expectedYaw = player.getYRot();
		expectedPitch = player.getXRot();
		yawAtLastTick = expectedYaw;
		turnPerTick = 0;
		haveAim = false;
		lastFrameNanos = System.nanoTime();
	}

	public static void stop(String reason) {
		if (follower == null) {
			return;
		}
		end();
		Chat.info("Stopped: " + reason + ".");
	}

	private static void end() {
		follower = null;
		lastOutput = null;
		jump = false;
		strafe = 0;
		AVOIDER.reset();
		if (WigState.debug) {
			Minecraft.getInstance().gui.setOverlayMessage(Component.empty(), false);
		}
	}

	/**
	 * Called at the end of every client tick. Stops the run when a screen opens, a movement key is pressed, the hotbar
	 * slot changes, or the view is turned by something other than this mod.
	 */
	public static void tick(Minecraft mc) {
		if (follower == null) {
			return;
		}
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			end();
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
		if (player.getInventory().getSelectedSlot() != startSlot) {
			stop("the hotbar slot was changed");
			return;
		}
		if (viewWasTurnedByOthers(player)) {
			return;
		}
		ticks++;

		RouteFollower.Output out = follower.tick(player.getX(), player.getZ(), player.onGround(), player.horizontalCollision);
		switch (out.status()) {
			case FINISHED -> {
				end();
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
		lastOutput = out;
		ObstacleAvoider.Output around = AVOIDER.tick(player.getX(), player.getY(), player.getZ(), player.getYRot(),
			out.aimX(), out.aimZ(), player.onGround() && player.horizontalCollision);
		if (around.gaveUp()) {
			stop("no way round an obstacle was found");
			return;
		}
		strafe = around.strafe();
		// no hopping against the obstacle while sidestepping it
		jump = out.jump() && strafe == 0;
		aimYaw = out.aimYaw();
		haveAim = true;
		turnPerTick = RouteFollower.wrapDegrees(player.getYRot() - yawAtLastTick);
		yawAtLastTick = player.getYRot();
	}

	/** Called once per rendered frame, before the camera is set up: turns the view toward the aim by the time that has passed. */
	public static void frame(Minecraft mc) {
		long now = System.nanoTime();
		double frameTicks = Math.min((now - lastFrameNanos) * TICKS_PER_NANO, MAX_FRAME_TICKS);
		lastFrameNanos = now;
		LocalPlayer player = mc.player;
		if (follower == null || player == null) {
			return;
		}
		if (viewWasTurnedByOthers(player)) {
			return;
		}
		if (haveAim) {
			double before = player.getYRot();
			float yaw = (float) RouteFollower.stepYaw(before, aimYaw, WigState.config.turnSpeedDegPerTick * frameTicks);
			player.setYRot(yaw);
			// no blending between ticks: the view is set exactly, every frame
			player.yRotO = yaw;
			lastFrameStep = RouteFollower.wrapDegrees(yaw - before);
			lastFrameTicks = frameTicks;
		}
		expectedYaw = player.getYRot();
		expectedPitch = player.getXRot();
		if (WigState.debug) {
			showDebug(mc, player);
		}
	}

	/** Stops the run and returns true when the view differs from where this mod left it by the configured angle or more. */
	private static boolean viewWasTurnedByOthers(LocalPlayer player) {
		double limit = WigState.config.rotationStopDegrees;
		double yawChange = Math.abs(RouteFollower.wrapDegrees(player.getYRot() - expectedYaw));
		double pitchChange = Math.abs(player.getXRot() - expectedPitch);
		if (yawChange >= limit || pitchChange >= limit) {
			stop("your view was turned by something else");
			return true;
		}
		return false;
	}

	private static void showDebug(Minecraft mc, LocalPlayer player) {
		RouteFollower.Output o = lastOutput;
		String text = String.format("yaw %.1f aim %.1f | frame %.2f deg (%.3f ticks) | turn %.1f deg/tick (max %.1f) | fps %d%s",
			RouteFollower.wrapDegrees(player.getYRot()), haveAim ? RouteFollower.wrapDegrees(aimYaw) : Double.NaN,
			lastFrameStep, lastFrameTicks, turnPerTick, WigState.config.turnSpeedDegPerTick, mc.getFps(),
			o == null ? "" : String.format(" | wp %d dist %.1f blend %.2f", o.target() + 1, o.distance(), o.blend()));
		mc.gui.setOverlayMessage(Component.literal(text), false);
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
