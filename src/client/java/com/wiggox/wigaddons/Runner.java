package com.wiggox.wigaddons;

import java.util.Random;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
	/** The turn speed of the route being run: its own, or the global one. */
	private static double runTurnSpeed;
	private static String runName;
	private static boolean runLoop;
	private static Route runRouteForHud;
	private static long runStartNanos;
	private static boolean jump;
	/** Following a player instead of running a route: the planner and where the player is found. */
	private static FollowPlanner planner;
	private static java.util.function.Supplier<FollowPlanner.Target> followSource;
	private static final ViewSmoother yawSmoother = new ViewSmoother();
	private static final ViewSmoother pitchSmoother = new ViewSmoother();
	private static String followName;
	/** Hunting mobs (/wig kill) instead of following a player: the planner is aimed at one mob at a time. */
	private static boolean hunting;
	private static final ZealotHunter HUNTER = new ZealotHunter();
	private static final Random HUNT_RANDOM = new Random();
	private static int huntTargetId = -1;
	private static int huntKills;
	/** While nothing is in range: where the planner is walking to (the home spot, or a spot to stroll to), and how long to stand before the next stroll. */
	private static double[] idleGoal;
	private static int idlePauseTicks;
	private static double idleLookYaw;
	private static double idleLookPitch;
	private static int idleLookTicks;
	/** While walking home or wandering the view stays within this many degrees of the way to the goal. */
	private static final double IDLE_LOOK_SPREAD_DEGREES = 90.0;
	/** The names attacked so far this hunt, in order (kept for the dev test). */
	private static final java.util.List<String> HUNT_ORDER = new java.util.ArrayList<>();
	private static boolean huntWaitingTold;
	/** Ticks left of the right click being held for an attack. */
	private static int useTicksLeft;
	/** The tick before which the next right click has to wait: the weapon's cooldown. */
	private static long nextClickTick;
	/** What each recognised mob was called, and when it last was seen with its name tag. */
	/** A mob the hunt knows about, for drawing its own name tag over it. */
	public record HuntMark(double x, double y, double z, double eyeY, String name, boolean special, boolean target, boolean dead, boolean visible) {
	}

	private static volatile java.util.List<HuntMark> huntMarks = java.util.List.of();

	/** The mobs being hunted right now, for drawing. Empty when not hunting. */
	public static java.util.List<HuntMark> huntMarks() {
		return huntMarks;
	}

	private static final java.util.Map<Integer, String> HUNT_NAME_OF = new java.util.HashMap<>();
	private static final java.util.Map<Integer, Long> HUNT_NAMED_AT = new java.util.HashMap<>();
	/** A mob stays recognised this long after its name tag was last seen, in ticks. */
	private static final int NAME_MEMORY_TICKS = 400;
	/** How far sideways from a mob its name tag stand may be found, in blocks (the stand trails behind a moving mob). */
	private static final double NAME_TAG_REACH = 3.0;
	/** Standing still because several targets are in reach; since when (ticks). */
	private static boolean standingGround;
	private static long standingSince;
	/** The tick each mob was last in clear sight, for the depth check. */
	private static final java.util.Map<Integer, Long> HUNT_SEEN = new java.util.HashMap<>();
	/** A mob that has just stepped out of sight is still gone for this many ticks, so a pillar edge doesn't make the target flicker. */
	private static final int SIGHT_GRACE_TICKS = 15;
	private static double followDistance = Double.NaN;
	private static boolean followWasLost;
	/** Which movement keys the input mixin presses: forward 1 / back -1, and sprint. */
	private static int moveForward = 1;
	private static boolean sprintOn = true;
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

	/** A route is being run, or a player is being followed. */
	private static boolean active() {
		return follower != null || planner != null;
	}

	public static boolean isRunning() {
		return active();
	}

	/** What is being run, for the HUD: the route's name, or "Following Name". Null when nothing is. */
	public static String runningName() {
		if (follower != null) {
			return runName;
		}
		if (hunting && planner != null) {
			return "Hunting " + WigState.config.killName;
		}
		return planner != null ? "Following " + followName : null;
	}

	public static boolean runningOnLoop() {
		return follower != null && runLoop;
	}

	public static Route runningRoute() {
		return follower == null ? null : runRouteForHud;
	}

	/** What is being followed along, for drawing: null when no player is being followed. */
	public static FollowPlanner.Snapshot followTrail() {
		return planner == null ? null : planner.snapshot();
	}

	/** Blocks to the player being followed, or NaN when no player is being followed. */
	public static double followDistance() {
		return planner == null ? Double.NaN : followDistance;
	}

	/** Whole seconds since the current run started. */
	public static long elapsedSeconds() {
		return active() ? (System.nanoTime() - runStartNanos) / 1_000_000_000L : 0;
	}

	/** True while the input mixin should press keys. */
	public static boolean wantsInput() {
		return active();
	}

	/** 1 presses W, -1 presses S, 0 neither. */
	public static int forward() {
		return moveForward;
	}

	public static boolean sprint() {
		return sprintOn;
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
		planner = null;
		followSource = null;
		hunting = false;
		moveForward = 1;
		sprintOn = true;
		runName = route.name();
		runLoop = loop;
		runRouteForHud = route;
		runStartNanos = System.nanoTime();
		runTurnSpeed = route.effectiveTurnSpeed(WigState.config);
		follower = new RouteFollower(route.waypoints(), loop, route.radius(), WigState.config.withHumanizeOffset(route.effectiveHumanizeOffset(WigState.config)));
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

	/**
	 * Starts following a player: walks the path they walk, keeping a few blocks behind, looking at them.
	 *
	 * @param source where the player is right now, or null when they can't be seen
	 */
	public static void startFollow(String name, java.util.function.Supplier<FollowPlanner.Target> source) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		if (active()) {
			end();
		}
		followName = name;
		hunting = false;
		followSource = source;
		yawSmoother.reset();
		pitchSmoother.reset();
		planner = new FollowPlanner(FollowPlanner.Params.of(WigState.config), new Random());
		planner.setTerrain(new WorldTerrain());
		followDistance = Double.NaN;
		followWasLost = false;
		follower = null;
		runRoute = null;
		runName = null;
		runLoop = false;
		runRouteForHud = null;
		runStartNanos = System.nanoTime();
		runTurnSpeed = WigState.config.turnSpeedDegPerTick;
		pitchTarget = Double.NaN;
		lastOutput = null;
		jump = false;
		strafe = 0;
		moveForward = 0;
		sprintOn = false;
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

	/**
	 * Starts hunting: goes for the mobs named in the config one after another, special ones first, looking at each and
	 * right clicking when it is in reach, then treating it as dead for a few seconds and moving on.
	 */
	public static void startHunt() {
		startFollow("mobs", () -> null);
		hunting = true;
		huntTargetId = -1;
		huntKills = 0;
		idleGoal = null;
		idlePauseTicks = 0;
		nextClickTick = 0;
		standingGround = false;
		HUNT_SEEN.clear();
		HUNT_NAME_OF.clear();
		HUNT_NAMED_AT.clear();
		HUNT_ORDER.clear();
		huntWaitingTold = false;
		useTicksLeft = 0;
		planner = new FollowPlanner(FollowPlanner.Params.forHunt(WigState.config), new Random());
		planner.setTerrain(new WorldTerrain());
	}

	/** The names attacked so far in this hunt, in order. */
	public static java.util.List<String> huntOrder() {
		return java.util.List.copyOf(HUNT_ORDER);
	}

	/** Whether a hunt is running, for drawing the home spot. */
	public static boolean isHunting() {
		return hunting && planner != null;
	}

	public static void stop(String reason) {
		if (!active()) {
			return;
		}
		end();
		Chat.info("Stopped: " + reason + ".");
	}

	/** Holds the mouse buttons the route asks for. The keys are only ever set by this, so a real press is never mistaken for it. */
	private static void holdClicks(Minecraft mc) {
		if (runRoute == null) {
			return;
		}
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
		huntMarks = java.util.List.of();
		hunting = false;
		useTicksLeft = 0;
		if (macroCountdown >= 0) {
			clearWarning();
		}
		macroCountdown = -1;
		partyCountdown = -1;
		follower = null;
		planner = null;
		followSource = null;
		moveForward = 1;
		sprintOn = true;
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
		if (!active()) {
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

		if (planner != null) {
			if (hunting) {
				huntTick(mc, player);
			} else {
				followTick(mc, player);
			}
			return;
		}

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
			out.aimX(), out.aimZ(), player.onGround() && player.horizontalCollision, levelLeg, runRouteForHud.noJump());
		if (around.gaveUp()) {
			stop(runRouteForHud.noJump() ? "no way round an obstacle was found, and this route never jumps" : "no way round an obstacle was found");
			return;
		}
		strafe = around.strafe();
		// no hopping against the obstacle while sidestepping it, and none at all on a route set never to jump
		jump = out.jump() && strafe == 0 && !runRouteForHud.noJump();
		aimYaw = out.aimYaw();
		haveAim = true;
		turnPerTick = RouteFollower.wrapDegrees(player.getYRot() - yawAtLastTick);
		yawAtLastTick = player.getYRot();
	}

	/** One tick of hunting: pick a target, walk up to it, look at it, and right click once it is in reach. */
	private static void huntTick(Minecraft mc, LocalPlayer player) {
		var cfg = WigState.config;
		if (useTicksLeft > 0 && --useTicksLeft == 0) {
			mc.options.keyUse.setDown(false);
			holdingRight = false;
		}
		java.util.List<ZealotHunter.Mob> all = scanMobs(mc, player);
		// a target almost straight above can't be looked at: leave it alone
		all = all.stream().filter(m -> !ZealotHunter.tooSteepUp(player.getX(), player.getEyeY(), player.getZ(), m, cfg.killMaxLookUp)).toList();
		// mobs in sight come first; ones behind walls are only walked toward when nothing is in sight
		java.util.List<ZealotHunter.Mob> inSight = cfg.killRequireSight ? all.stream().filter(m -> recentlySeen(m.id())).toList() : all;
		if (cfg.killStandWhenCrowded) {
			java.util.List<ZealotHunter.Mob> reachable = inSight.stream().filter(m -> huntable(m, cfg) && Math.hypot(m.x() - player.getX(), m.z() - player.getZ()) <= cfg.killRange).toList();
			if (!standingGround && reachable.size() >= 2) {
				standingGround = true;
				standingSince = ticks;
			} else if (standingGround && (reachable.isEmpty() || ticks - standingSince > Math.round(cfg.killStandLimitSeconds * 20))) {
				standingGround = false;
			}
			if (standingGround) {
				inSight = reachable; // deal with the ones in reach, where you stand
			}
		} else {
			standingGround = false;
		}
		ZealotHunter.View view = new ZealotHunter.View(player.getX(), player.getEyeY(), player.getZ(), player.getYRot(), player.getXRot());
		ZealotHunter.Mob pick = HUNTER.pick(inSight, view, cfg.killPickByCrosshair, cfg.killSearchRadius, ticks, cfg.killName, cfg.killSpecialName);
		if (pick == null && inSight.size() < all.size()) {
			pick = HUNTER.pick(all, view, cfg.killPickByCrosshair, cfg.killSearchRadius, ticks, cfg.killName, cfg.killSpecialName);
		}
		int id = pick == null ? -1 : pick.id();
		int markedTarget = pick == null ? -1 : pick.id();
		huntMarks = all.stream().map(m -> new HuntMark(m.x(), m.y(), m.z(), m.eyeY(), m.name(),
			ZealotHunter.matches(m.name(), cfg.killSpecialName), m.id() == markedTarget, HUNTER.isMarkedDead(m.id(), ticks), !cfg.killRequireSight || recentlySeen(m.id()))).toList();
		if (pick != null && id != huntTargetId) {
			// a new target: a fresh plan, so the old one's trail isn't walked
			huntTargetId = id;
			planner = new FollowPlanner(FollowPlanner.Params.forHunt(cfg), new Random());
			planner.setTerrain(new WorldTerrain());
		}
		if (pick == null) {
			idleTick(mc, player, cfg);
			return;
		}
		idleGoal = null;
		huntWaitingTold = false;
		FollowPlanner.Output out = planner.tick(player.getX(), player.getY(), player.getZ(), player.getYRot(),
			new FollowPlanner.Target(pick.x(), pick.y(), pick.z(), pick.eyeY()), player.onGround(), player.horizontalCollision);
		followDistance = out.distance();
		if (out.status() == FollowPlanner.Status.STUCK) {
			// can't get to this one: leave it for a while and try another
			HUNTER.markDead(id, ticks, cfg.killMarkMinSeconds, cfg.killMarkMaxSeconds, HUNT_RANDOM);
			return;
		}
		moveForward = out.forward();
		strafe = out.strafe();
		sprintOn = out.sprint();
		jump = out.jump();
		if (standingGround) {
			// several targets in reach: no walking, just turn and click until they are all marked
			moveForward = 0;
			strafe = 0;
			sprintOn = false;
			jump = false;
		}
		aimYaw = FollowPlanner.yawToward(player.getX(), player.getZ(), out.lookX(), out.lookZ());
		pitchTarget = FollowPlanner.pitchToward(player.getX(), player.getEyeY(), player.getZ(), out.lookX(), out.lookY(), out.lookZ());
		haveAim = true;
		turnPerTick = RouteFollower.wrapDegrees(player.getYRot() - yawAtLastTick);
		yawAtLastTick = player.getYRot();
		if (useTicksLeft == 0 && ticks >= nextClickTick && lookingAtInReach(mc, player, id, cfg)) {
			mc.options.keyUse.setDown(true);
			holdingRight = true;
			useTicksLeft = cfg.killClickTicks;
			// the weapon's cooldown, plus up to a fifth more at random
			nextClickTick = ticks + Math.max(cfg.killClickTicks, Math.round(cfg.killClickCooldownSeconds * 20 * (1 + HUNT_RANDOM.nextDouble() * 0.2)));
			// an ordinary mob is assumed dead after one click; a special one gets a few before it is
			int needed = ZealotHunter.matches(pick.name(), cfg.killSpecialName) ? cfg.killSpecialClicks : 1;
			if (HUNTER.clickLanded(id, needed)) {
				HUNTER.markDead(id, ticks, cfg.killMarkMinSeconds, cfg.killMarkMaxSeconds, HUNT_RANDOM);
			}
			huntKills++;
			HUNT_ORDER.add(pick.name());
		}
	}

	/** Nothing to hunt: go to the home spot if there is one, then stroll around it until something turns up. */
	private static void idleTick(Minecraft mc, LocalPlayer player, WigConfig cfg) {
		if (!cfg.killHomeSet) {
			huntTargetId = -1;
			if (!huntWaitingTold) {
				huntWaitingTold = true;
				Chat.info("No " + cfg.killName + " in range. Waiting for one to come close. (/wig kill home sets a place to go to meanwhile.)");
			}
			standStill();
			return;
		}
		huntWaitingTold = false;
		double toHome = Math.hypot(cfg.killHomeX - player.getX(), cfg.killHomeZ - player.getZ());
		double[] goal = idleGoal;
		if (goal == null) {
			if (toHome > Math.max(3.0, cfg.killWander ? cfg.killWanderRadius + 2 : 3.0)) {
				goal = new double[] {cfg.killHomeX, cfg.killHomeY, cfg.killHomeZ}; // too far from home: go back
			} else if (cfg.killWander) {
				if (idlePauseTicks > 0) {
					idlePauseTicks--;
					standStill();
					return;
				}
				goal = Wander.pick(new WorldTerrain(), cfg.killHomeX, cfg.killHomeY, cfg.killHomeZ, cfg.killWanderRadius, 3.0, HUNT_RANDOM);
				if (goal == null) {
					standStill();
					return;
				}
			} else {
				standStill();
				return;
			}
			idleGoal = goal;
			huntTargetId = -2;
			planner = new FollowPlanner(FollowPlanner.Params.forHunt(cfg), new Random());
			planner.setTerrain(new WorldTerrain());
		}
		FollowPlanner.Output out = planner.tick(player.getX(), player.getY(), player.getZ(), player.getYRot(),
			new FollowPlanner.Target(goal[0], goal[1], goal[2], goal[1] + 1.62), player.onGround(), player.horizontalCollision);
		followDistance = out.distance();
		if (out.status() == FollowPlanner.Status.CLOSE || out.status() == FollowPlanner.Status.STUCK) {
			// got there (or can't): rest a moment, then pick the next stroll
			idleGoal = null;
			idlePauseTicks = 20 + HUNT_RANDOM.nextInt(60);
			standStill();
			return;
		}
		moveForward = out.forward();
		strafe = out.strafe();
		sprintOn = false;
		jump = out.jump();
		if (cfg.killIdleLookAround) {
			// the view wanders on its own; the planner already picks W, A, S and D relative to it, so the way round things is sidestepped or backed into
			if (--idleLookTicks <= 0) {
				// somewhere within 90 degrees either side of the way to the goal: the keys vary, but the view never faces away
				idleLookYaw = FollowPlanner.yawToward(player.getX(), player.getZ(), goal[0], goal[2]) + (HUNT_RANDOM.nextDouble() * 2 - 1) * IDLE_LOOK_SPREAD_DEGREES;
				idleLookPitch = (HUNT_RANDOM.nextDouble() * 2 - 1) * 2.0;
				idleLookTicks = 60 + HUNT_RANDOM.nextInt(100);
			}
			aimYaw = idleLookYaw;
			pitchTarget = idleLookPitch;
		} else {
			aimYaw = FollowPlanner.yawToward(player.getX(), player.getZ(), out.lookX(), out.lookZ());
			pitchTarget = FollowPlanner.pitchToward(player.getX(), player.getEyeY(), player.getZ(), out.lookX(), out.lookY(), out.lookZ());
		}
		haveAim = true;
		turnPerTick = RouteFollower.wrapDegrees(player.getYRot() - yawAtLastTick);
		yawAtLastTick = player.getYRot();
	}

	private static void standStill() {
		moveForward = 0;
		strafe = 0;
		sprintOn = false;
		jump = false;
		haveAim = false;
		pitchTarget = Double.NaN;
		followDistance = Double.NaN;
	}

	/** Whether the mob is one to hunt and not marked dead. */
	private static boolean huntable(ZealotHunter.Mob mob, WigConfig cfg) {
		return !HUNTER.isMarkedDead(mob.id(), ticks)
			&& (ZealotHunter.matches(mob.name(), cfg.killName) || ZealotHunter.matches(mob.name(), cfg.killSpecialName));
	}

	/** Whether the mob was in clear sight a moment ago. */
	private static boolean recentlySeen(int id) {
		return ticks - HUNT_SEEN.getOrDefault(id, -1000L) <= SIGHT_GRACE_TICKS;
	}

	/** Whether a line from the eyes to the mob's eyes, middle or lower body is free of blocks. */
	private static boolean inSight(Minecraft mc, LocalPlayer player, Entity mob) {
		Vec3 eye = player.getEyePosition();
		double[] heights = {mob.getEyeY(), mob.getY() + mob.getBbHeight() * 0.5, mob.getY() + 0.3};
		for (double y : heights) {
			net.minecraft.world.phys.BlockHitResult block = mc.level.clip(new net.minecraft.world.level.ClipContext(eye, new Vec3(mob.getX(), y, mob.getZ()),
				net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
			if (block.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
				return true;
			}
		}
		return false;
	}

	/** Whether the view points at the mob (its hitbox, a little bigger) within the click range. */
	private static boolean lookingAtInReach(Minecraft mc, LocalPlayer player, int id, WigConfig cfg) {
		Entity mob = mc.level.getEntity(id);
		if (mob == null || !mob.isAlive()) {
			return false;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(cfg.killRange));
		java.util.Optional<Vec3> hit = mob.getBoundingBox().inflate(cfg.killAimMargin).clip(eye, end);
		if (hit.isEmpty()) {
			return false;
		}
		if (!cfg.killRequireSight) {
			return true;
		}
		// the depth check: a block between the eyes and where the view meets the mob means the shot would hit the block
		net.minecraft.world.phys.BlockHitResult block = mc.level.clip(new net.minecraft.world.level.ClipContext(eye, hit.get(),
			net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
		return block.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
	}

	/** The mobs around that carry the hunted name: on the mob itself, or on the name tag stand floating above it. */
	private static java.util.List<ZealotHunter.Mob> scanMobs(Minecraft mc, LocalPlayer player) {
		var cfg = WigState.config;
		AABB area = player.getBoundingBox().inflate(cfg.killSearchRadius);
		java.util.List<Entity> around = mc.level.getEntities((Entity) null, area);
		java.util.Map<Integer, ZealotHunter.Mob> found = new java.util.LinkedHashMap<>();
		for (Entity entity : around) {
			if (!entity.hasCustomName()) {
				continue;
			}
			String name = entity.getCustomName().getString();
			if (!ZealotHunter.matches(name, cfg.killName) && !ZealotHunter.matches(name, cfg.killSpecialName)) {
				continue;
			}
			Entity body = entity;
			if (entity instanceof ArmorStand) {
				body = null;
				double best = Double.MAX_VALUE;
				for (Entity other : around) {
					if (!(other instanceof LivingEntity) || other instanceof ArmorStand || other instanceof Player) {
						continue;
					}
					double flat = Math.hypot(other.getX() - entity.getX(), other.getZ() - entity.getZ());
					double above = entity.getY() - other.getY();
					if (flat < NAME_TAG_REACH && above > -0.5 && above < 4.0 && flat < best) {
						best = flat;
						body = other;
					}
				}
			}
			if (body != null && body.isAlive() && !(body instanceof Player)) {
				found.putIfAbsent(body.getId(), new ZealotHunter.Mob(body.getId(), name, body.getX(), body.getY(), body.getZ(), body.getEyeY()));
				HUNT_NAME_OF.put(body.getId(), name);
				HUNT_NAMED_AT.put(body.getId(), (long) ticks);
			}
		}
		// a mob that was recognised a moment ago stays recognised when its name tag isn't there for the moment (it lags behind a moving mob, or comes and goes)
		for (Entity entity : around) {
			if (found.containsKey(entity.getId()) || !(entity instanceof LivingEntity) || entity instanceof ArmorStand || entity instanceof Player || !entity.isAlive()) {
				continue;
			}
			String remembered = HUNT_NAME_OF.get(entity.getId());
			if (remembered != null && ticks - HUNT_NAMED_AT.getOrDefault(entity.getId(), -100000L) <= NAME_MEMORY_TICKS) {
				found.put(entity.getId(), new ZealotHunter.Mob(entity.getId(), remembered, entity.getX(), entity.getY(), entity.getZ(), entity.getEyeY()));
			}
		}
		if (cfg.killByType) {
			// no name tag needed: anything of the hunted type within the radius counts
			String type = cfg.killType.trim();
			for (Entity entity : around) {
				if (found.containsKey(entity.getId()) || !(entity instanceof LivingEntity) || entity instanceof ArmorStand || entity instanceof Player || !entity.isAlive()) {
					continue;
				}
				if (!net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals(type)) {
					continue;
				}
				if (entity.hasCustomName() && !ZealotHunter.matches(entity.getCustomName().getString(), cfg.killName)) {
					continue; // it has a name, and not the hunted one
				}
				found.put(entity.getId(), new ZealotHunter.Mob(entity.getId(), cfg.killName, entity.getX(), entity.getY(), entity.getZ(), entity.getEyeY()));
			}
		}
		java.util.List<ZealotHunter.Mob> mobs = new java.util.ArrayList<>(found.values());
		if (cfg.killRequireSight) {
			for (ZealotHunter.Mob mob : mobs) {
				Entity body = mc.level.getEntity(mob.id());
				if (body != null && inSight(mc, player, body)) {
					HUNT_SEEN.put(mob.id(), (long) ticks);
				}
			}
		}
		return mobs;
	}

	/** One tick of following: walk the trail, keep the distance, look at the player. */
	private static void followTick(Minecraft mc, LocalPlayer player) {
		FollowPlanner.Target target = followSource.get();
		FollowPlanner.Output out = planner.tick(player.getX(), player.getY(), player.getZ(), player.getYRot(), target, player.onGround(), player.horizontalCollision);
		followDistance = out.distance();
		switch (out.status()) {
			case LOST -> {
				stop("lost sight of " + followName);
				return;
			}
			case STUCK -> {
				stop("you seem to be stuck");
				return;
			}
			case LOST_WAITING -> {
				if (!followWasLost) {
					followWasLost = true;
					Chat.info("Lost sight of " + followName + ". Going to where they were last seen and waiting " + WigState.config.followLostSeconds + " seconds.");
				}
			}
			default -> followWasLost = false;
		}
		moveForward = out.forward();
		strafe = out.strafe();
		sprintOn = out.sprint();
		jump = out.jump();
		aimYaw = FollowPlanner.yawToward(player.getX(), player.getZ(), out.lookX(), out.lookZ());
		pitchTarget = FollowPlanner.pitchToward(player.getX(), player.getEyeY(), player.getZ(), out.lookX(), out.lookY(), out.lookZ());
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
		if (!active() || player == null) {
			return;
		}
		checkView(player);
		boolean following = followSource != null;
		if (haveAim) {
			double before = player.getYRot();
			float yaw = (float) (following
				? before + yawSmoother.step(RouteFollower.wrapDegrees(aimYaw - before), frameTicks, WigState.config.followTurnSmoothing, runTurnSpeed)
				: RouteFollower.stepYaw(before, aimYaw, runTurnSpeed * frameTicks));
			player.setYRot(yaw);
			// no blending between ticks: the view is set exactly, every frame
			player.yRotO = yaw;
			lastFrameStep = RouteFollower.wrapDegrees(yaw - before);
			lastFrameTicks = frameTicks;
		}
		if (!Double.isNaN(pitchTarget)) {
			float pitch = (float) (following
				? player.getXRot() + pitchSmoother.step(pitchTarget - player.getXRot(), frameTicks, WigState.config.followTurnSmoothing, runTurnSpeed)
				: RouteFollower.stepToward(player.getXRot(), pitchTarget, runTurnSpeed * frameTicks));
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
		if (!active() || partyCountdown >= 0 || !PartyLeave.matches(line, WigState.config.partyLeavePatterns)) {
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
			lastFrameStep, lastFrameTicks, turnPerTick, runTurnSpeed, mc.getFps(),
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
