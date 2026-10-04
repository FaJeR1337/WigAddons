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

	/** How often the on-screen warning is shown again. It is shown for longer than this, so it never flickers. */
	private static final int WARNING_REFRESH_TICKS = 20;

	private static final ObstacleAvoider AVOIDER = new ObstacleAvoider(new WorldTerrain(), new Random());

	private static RouteFollower follower;
	private static java.util.List<Waypoint> points;
	private static Route runRoute;
	private static final Random PITCH_RANDOM = new Random();
	/** The pitch the view is easing to, or NaN when the route doesn't hold one. */
	private static double pitchTarget = Double.NaN;
	/** Standing height of the previous waypoint (or where the run began), to tell level stretches from climbs. */
	private static int previousLevel;
	private static int lastTarget;
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
	/** Ticks left before the run stops for a macro check, or -1 when none was seen. */
	private static int macroCountdown = -1;
	private static String macroReason = "";
	private static int warningTimer;
	/** Ticks left before the run stops because a party member left, or -1. */
	private static int partyCountdown = -1;
	/** Which mouse buttons this run is holding down, so only those are let go of. */
	private static boolean holdingLeft;
	private static boolean holdingRight;
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
		points = java.util.List.copyOf(route.waypoints());
		runRoute = route;
		pickPitch();
		previousLevel = (int) Math.floor(player.getY() + 1e-4);
		lastTarget = 0;
		lastOutput = null;
		jump = false;
		strafe = 0;
		AVOIDER.reset();
		macroCountdown = -1;
		partyCountdown = -1;
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

	/** Holds the mouse buttons the route asks for. The keys are only ever set by this, so a real press is never mistaken for it. */
	private static void holdClicks(Minecraft mc) {
		if (runRoute.holdLeft()) {
			mc.options.keyAttack.setDown(true);
			holdingLeft = true;
		}
		if (runRoute.holdRight()) {
			mc.options.keyUse.setDown(true);
			holdingRight = true;
		}
	}

	private static void releaseClicks() {
		Options options = Minecraft.getInstance().options;
		if (holdingLeft) {
			options.keyAttack.setDown(false);
			holdingLeft = false;
		}
		if (holdingRight) {
			options.keyUse.setDown(false);
			holdingRight = false;
		}
	}

	private static void end() {
		releaseClicks();
		if (macroCountdown >= 0) {
			clearWarning();
		}
		macroCountdown = -1;
		partyCountdown = -1;
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
		if (partyCountdown > 0 && --partyCountdown == 0) {
			leaveBecauseOfParty(mc);
			return;
		}
		if (macroCountdown > 0) {
			if (--macroCountdown == 0) {
				String reason = macroReason;
				stop("a macro check was detected (" + reason.toLowerCase(java.util.Locale.ROOT) + ")");
				return;
			}
			if (--warningTimer <= 0) {
				showWarning(mc);
			}
		}
		if (mc.screen != null && ticks >= SCREEN_GRACE_TICKS) {
			stop("a screen was opened");
			return;
		}
		if (userIsPressingMovementKey(mc.options)) {
			stop("you pressed a movement key");
			return;
		}
		int slot = player.getInventory().getSelectedSlot();
		if (slot != startSlot) {
			startSlot = slot;
			macroCheck("The hotbar slot was changed");
		}
		checkView(player);
		ticks++;
		holdClicks(mc);

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
		if (out.target() != lastTarget) {
			previousLevel = standingLevel(lastTarget);
			lastTarget = out.target();
			pickPitch(); // with randomize, a new spot near the set pitch at every waypoint
		}
		boolean levelLeg = standingLevel(out.target()) == previousLevel;
		ObstacleAvoider.Output around = AVOIDER.tick(player.getX(), player.getY(), player.getZ(), player.getYRot(),
			out.aimX(), out.aimZ(), player.onGround() && player.horizontalCollision, levelLeg);
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

	private static void pickPitch() {
		Double set = runRoute.pitch();
		pitchTarget = set == null ? Double.NaN : RouteFollower.pitchTarget(set, runRoute.randomizePitch(), PITCH_RANDOM);
	}

	/** The height the player stands at on a waypoint: the block above the one that was clicked. */
	private static int standingLevel(int index) {
		return points.get(index).y() + 1;
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
		checkView(player);
		if (haveAim) {
			double before = player.getYRot();
			float yaw = (float) RouteFollower.stepYaw(before, aimYaw, WigState.config.turnSpeedDegPerTick * frameTicks);
			player.setYRot(yaw);
			// no blending between ticks: the view is set exactly, every frame
			player.yRotO = yaw;
			lastFrameStep = RouteFollower.wrapDegrees(yaw - before);
			lastFrameTicks = frameTicks;
		}
		if (!Double.isNaN(pitchTarget)) {
			float pitch = (float) RouteFollower.stepToward(player.getXRot(), pitchTarget, WigState.config.turnSpeedDegPerTick * frameTicks);
			player.setXRot(pitch);
			player.xRotO = pitch;
		}
		expectedYaw = player.getYRot();
		expectedPitch = player.getXRot();
		if (WigState.debug) {
			showDebug(mc, player);
		}
	}

	/** Raises a macro check when the view differs from where this mod left it by the configured angle or more. */
	private static void checkView(LocalPlayer player) {
		double limit = WigState.config.rotationStopDegrees;
		double yawChange = Math.abs(RouteFollower.wrapDegrees(player.getYRot() - expectedYaw));
		double pitchChange = Math.abs(player.getXRot() - expectedPitch);
		if (yawChange >= limit || pitchChange >= limit) {
			macroCheck("Your view was turned by something else");
			// carry on from wherever the view is now, so one change doesn't raise the check every tick
			expectedYaw = player.getYRot();
			expectedPitch = player.getXRot();
		}
	}

	/**
	 * Something a server might check for happened. The run carries on for a few seconds so it doesn't freeze the
	 * moment it is noticed, with a warning on screen, and then stops.
	 */
	private static void macroCheck(String reason) {
		if (macroCountdown >= 0) {
			return;
		}
		macroReason = reason;
		macroCountdown = Math.max(1, (int) Math.round(WigState.config.macroWarningSeconds * 20));
		Chat.error("Macro check detected: " + reason.toLowerCase(java.util.Locale.ROOT) + ". Stopping in "
			+ WigState.config.macroWarningSeconds + " seconds.");
		showWarning(Minecraft.getInstance());
	}

	private static void showWarning(Minecraft mc) {
		warningTimer = WARNING_REFRESH_TICKS;
		mc.gui.setTimes(0, WARNING_REFRESH_TICKS * 2, 0);
		mc.gui.setTitle(Component.literal("WARNING").withColor(0xFF5555));
		mc.gui.setSubtitle(Component.literal(macroReason).withColor(0xFF5555));
	}

	private static void clearWarning() {
		Minecraft mc = Minecraft.getInstance();
		mc.gui.clearTitles();
		mc.gui.resetTitleTimes();
	}

	/** Called for every chat line the server sends. */
	public static void onChatLine(String line) {
		if (follower == null || partyCountdown >= 0 || !PartyLeave.matches(line, WigState.config.partyLeavePatterns)) {
			return;
		}
		WigConfig cfg = WigState.config;
		partyCountdown = Math.max(1, (int) Math.round(cfg.partyLeaveDelaySeconds * 20));
		String command = partyCommand();
		Chat.info("A party member left: stopping in " + cfg.partyLeaveDelaySeconds + " seconds" + (command.isEmpty() ? "." : " and running /" + command + "."));
	}

	/** The command to send, without a leading slash, or empty for none. */
	private static String partyCommand() {
		String command = WigState.config.partyLeaveCommand.trim();
		return command.startsWith("/") ? command.substring(1).trim() : command;
	}

	private static void leaveBecauseOfParty(Minecraft mc) {
		String command = partyCommand();
		stop("a party member left");
		if (!command.isEmpty() && mc.player != null) {
			mc.player.connection.sendCommand(command);
		}
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
