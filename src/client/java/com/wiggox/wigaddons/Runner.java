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
	private static boolean idleGoingHome;
	/** Ticks left of looking around and jumping after the message that something special spawned, and the ticks to the next look and the next jump. */
	private static int huntReactTicks;
	/** Aiming straight at the target with little easing (see huntTick). */
	private static boolean huntPrecise;
	private static boolean huntReactSpotted;
	private static int huntReactTotal;
	private static double huntReactStartYaw;
	private static int huntReactDirection = 1;
	private static int huntReactJumpTicks;
	private static double idleLookOffset;
	private static double idleLookPitch;
	/** While walking home or wandering the view stays within this many degrees either side of the way being walked. */
	private static final double IDLE_LOOK_SPREAD_DEGREES = 15.0;
	private static int idleLookTicks;
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
	/** For each mob in sight: the height of the only part that can be seen, or NaN when all of it can. */
	private static final java.util.Map<Integer, Double> HUNT_PART_Y = new java.util.HashMap<>();
	/** The tick each mob was first noticed, for the spawn cooldown. */
	private static final java.util.Map<Integer, Long> HUNT_FIRST_SEEN = new java.util.HashMap<>();
	private static boolean huntFirstScan;
	/** Ticks the only targets there are have been behind the view. */
	private static int huntBehindTicks;
	private static int huntBehindBypass;
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
	/** Which mouse buttons this run is holding down, so only those are let go of. */
	private static boolean holdingLeft;
	private static boolean holdingRight;
	// for /wig debug
	private static double yawAtLastTick;
	private static double turnPerTick;
	private static double lastFrameStep;
	private static double lastFrameTicks;

	/** The settings a Zealots hunt runs on (the Hunt tab's with the Zealots tab's hunting settings over them), or null for the ordinary ones. */
	private static WigConfig huntCfg;

	private static WigConfig cfg() {
		return huntCfg != null ? huntCfg : WigState.config;
	}

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
		if (fishing && planner != null) {
			return "Trophy fishing";
		}
		if (hunting && planner != null) {
			return "Hunting " + cfg().killName;
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
		runTurnSpeed = route.effectiveTurnSpeed(cfg());
		follower = new RouteFollower(route.waypoints(), loop, route.radius(), cfg().withHumanizeOffset(route.effectiveHumanizeOffset(cfg())));
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
		ticks = 0;
		startSlot = player.getInventory().getSelectedSlot();
		expectedYaw = player.getYRot();
		expectedPitch = player.getXRot();
		yawAtLastTick = expectedYaw;
		turnPerTick = 0;
		haveAim = false;
		lastFrameNanos = System.nanoTime();
		resetTeleportCheck();
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
		fishing = false;
		followSource = source;
		yawSmoother.reset();
		pitchSmoother.reset();
		planner = new FollowPlanner(FollowPlanner.Params.of(cfg()), new Random());
		planner.setTerrain(new WorldTerrain());
		followDistance = Double.NaN;
		followWasLost = false;
		follower = null;
		runRoute = null;
		runName = null;
		runLoop = false;
		runRouteForHud = null;
		runStartNanos = System.nanoTime();
		runTurnSpeed = cfg().turnSpeedDegPerTick;
		pitchTarget = Double.NaN;
		lastOutput = null;
		jump = false;
		strafe = 0;
		moveForward = 0;
		sprintOn = false;
		AVOIDER.reset();
		macroCountdown = -1;
		ticks = 0;
		startSlot = player.getInventory().getSelectedSlot();
		expectedYaw = player.getYRot();
		expectedPitch = player.getXRot();
		yawAtLastTick = expectedYaw;
		turnPerTick = 0;
		haveAim = false;
		lastFrameNanos = System.nanoTime();
		resetTeleportCheck();
	}

	/**
	 * Starts hunting: goes for the mobs named in the config one after another, special ones first, looking at each and
	 * right clicking when it is in reach, then treating it as dead for a few seconds and moving on.
	 */
	public static void startHunt() {
		startHunt(false);
	}

	/** Hunts Zealots on the Zealots tab's settings. */
	public static void startZealots() {
		startHunt(true);
	}

	private static void startHunt(boolean zealots) {
		startFollow("mobs", () -> null);
		protBlocked = false;
		huntCfg = zealots ? WigState.config.copy().withHuntSettingsOf(WigState.zealots) : null;
		hunting = true;
		huntTargetId = -1;
		huntKills = 0;
		idleGoal = null;
		idlePauseTicks = 0;
		nextClickTick = 0;
		huntReactTicks = 0;
		standingGround = false;
		HUNT_SEEN.clear();
		HUNT_FIRST_SEEN.clear();
		huntBehindTicks = 0;
		huntBehindBypass = 0;
		protectorReset();
		huntFirstScan = true;
		HUNT_PART_Y.clear();
		HUNT_NAME_OF.clear();
		HUNT_NAMED_AT.clear();
		HUNT_ORDER.clear();
		huntWaitingTold = false;
		useTicksLeft = 0;
		planner = new FollowPlanner(FollowPlanner.Params.forHunt(cfg()), new Random());
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
		protectorReset();
		huntCfg = null;
		huntMarks = java.util.List.of();
		hunting = false;
		fishing = false;
		useTicksLeft = 0;
		if (macroCountdown >= 0 || lagWarningTicks > 0) {
			clearWarning();
		}
		lagWarningTicks = 0;
		macroCountdown = -1;
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
		samplePing();
		if (!active()) {
			return;
		}
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			if (warpGraceTicks > 0) {
				warpGraceTicks--; // a world change after a warp: wait for the new one
				return;
			}
			end();
			return;
		}
		if (warpGraceTicks > 0) {
			warpGraceTicks--;
		}
		if (lagWarningTicks > 0) {
			if (--lagWarningTicks == 0) {
				clearWarning();
			} else if (--warningTimer <= 0) {
				showWarning(mc);
			}
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
			if (expectTeleportTicks <= 0) { // a new world after a warp resets the held slot: not a check
				macroCheck("The hotbar slot was changed");
			}
		}

		if (SafeZones.inside(player.getX(), player.getY(), player.getZ(), safeZones(), cfg().safeZoneRadius + 5.0)) {
			// at a portal (or where it puts you), about to be teleported: the new world's first position may not be inside the zone yet
			macroSuppressTicks = Math.max(macroSuppressTicks, 100);
			expectTeleportTicks = Math.max(expectTeleportTicks, 300);
		}
		if (macroSuppressTicks > 0) {
			macroSuppressTicks--; // the protector just spawned: free to teleport for a moment
			expectedYaw = player.getYRot();
			expectedPitch = player.getXRot();
		} else if (expectTeleportTicks > 0) {
			// a warp this mod sent: the server sets the view as well as the position, so carry on from wherever it is
			expectedYaw = player.getYRot();
			expectedPitch = player.getXRot();
		} else {
			checkView(player);
		}
		checkTeleport(mc, player);
		ticks++;
		holdClicks(mc);

		if (planner != null) {
			if (fishing) {
				fishTick(mc, player);
			} else if (hunting) {
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

	// ---- trophy fishing ----

	private static boolean fishing;
	private enum Fish { CAST, CASTING, WAITING, REEL, RECAST }
	private static Fish fishState = Fish.CAST;
	/** When the next action is due (System.nanoTime), so the short human delays are not rounded to whole ticks. */
	private static long fishDueNanos;
	private static double fishX;
	private static double fishY;
	private static double fishZ;
	private static int fishCasts;
	private static long fishCastedAtTick;
	private static net.minecraft.world.entity.projectile.FishingHook fishStale;

	public static boolean isFishing() {
		return fishing && planner != null;
	}

	/**
	 * Starts trophy fishing with the rod in hand: casts, waits for the bobber's name to turn from the countdown into !!!, reels in a moment
	 * later and casts again. The view and the spot stay as they are; a change of either is a macro check like in every other mode.
	 */
	public static void startFish() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		if (!(player.getMainHandItem().getItem() instanceof net.minecraft.world.item.FishingRodItem)) {
			Chat.error("Hold a fishing rod in your main hand first.");
			return;
		}
		startFollow("fishing", () -> null);
		fishing = true;
		fishX = player.getX();
		fishY = player.getY();
		fishZ = player.getZ();
		fishCasts = 0;
		fishStale = null;
		// a rod already out is reeled in by the first click, so start with the bobber in the water
		fishState = player.fishing != null ? Fish.WAITING : Fish.CAST;
		fishDueNanos = System.nanoTime();
		standStill();
	}

	/** Nothing moves while fishing; the spot is held and the cast and reel are done by fishStep. */
	private static void fishTick(Minecraft mc, LocalPlayer player) {
		standStill();
		if (Math.hypot(player.getX() - fishX, player.getZ() - fishZ) > 0.5 || Math.abs(player.getY() - fishY) > 0.5) {
			macroCheck("You were moved from the fishing spot");
			// carry on from here so one move isn't raised every tick
			fishX = player.getX();
			fishY = player.getY();
			fishZ = player.getZ();
		}
		fishStep(mc, player);
	}

	private static long fishDelay(double minMs, double maxMs) {
		double lo = Math.min(minMs, maxMs);
		double hi = Math.max(minMs, maxMs);
		return Math.round((lo + HUNT_RANDOM.nextDouble() * (hi - lo)) * 1_000_000.0);
	}

	/** The text on the bobber: its own name, or a name tag stand right by it. */
	private static String bobberText(Minecraft mc, net.minecraft.world.entity.projectile.FishingHook hook) {
		StringBuilder text = new StringBuilder();
		if (hook.hasCustomName() && hook.getCustomName() != null) {
			text.append(hook.getCustomName().getString());
		}
		for (Entity near : mc.level.getEntities(hook, hook.getBoundingBox().inflate(1.5))) {
			if (!(near instanceof Player) && near.hasCustomName() && near.getCustomName() != null) {
				text.append(' ').append(near.getCustomName().getString());
			}
		}
		return text.toString();
	}

	private static void useRod(Minecraft mc, LocalPlayer player) {
		var result = mc.gameMode.useItem(player, net.minecraft.world.InteractionHand.MAIN_HAND);
		if (result.consumesAction()) {
			player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
		}
	}

	/** Runs every tick and every frame. */
	private static void fishStep(Minecraft mc, LocalPlayer player) {
		if (!fishing || mc.gameMode == null || macroCountdown >= 0) {
			return;
		}
		WigConfig cfg = cfg();
		long now = System.nanoTime();
		var hook = player.fishing;
		switch (fishState) {
			case CAST, RECAST -> {
				if (now < fishDueNanos) {
					return;
				}
				if (hook != null && hook == fishStale && now - fishDueNanos < 1_000_000_000L) {
					return; // the reeled in bobber has not gone from the client yet: casting now would reel again
				}
				if (!(player.getMainHandItem().getItem() instanceof net.minecraft.world.item.FishingRodItem)) {
					stop("no fishing rod in your main hand");
					return;
				}
				useRod(mc, player);
				fishState = Fish.CASTING;
				fishCastedAtTick = ticks;
			}
			case CASTING -> {
				if (hook != null && hook != fishStale) {
					fishState = Fish.WAITING;
				} else if (ticks - fishCastedAtTick > 60) {
					fishState = Fish.CAST; // the cast did not take: again
					fishDueNanos = now;
				}
			}
			case WAITING -> {
				if (hook == null) {
					fishState = Fish.CAST; // reeled in by something else: cast again
					fishDueNanos = now + fishDelay(cfg.fishRecastMinMs, cfg.fishRecastMaxMs);
					return;
				}
				if (bobberText(mc, hook).contains("!!!")) {
					fishState = Fish.REEL;
					fishDueNanos = now + fishDelay(cfg.fishReelMinMs, cfg.fishReelMaxMs);
				}
			}
			case REEL -> {
				if (now < fishDueNanos) {
					return;
				}
				if (hook != null) {
					fishStale = hook;
					useRod(mc, player);
					fishCasts++;
				}
				fishState = Fish.RECAST;
				fishDueNanos = now + fishDelay(cfg.fishRecastMinMs, cfg.fishRecastMaxMs);
			}
		}
	}

	/** One tick of hunting: pick a target, walk up to it, look at it, and right click once it is in reach. */
	private static void huntTick(Minecraft mc, LocalPlayer player) {
		var cfg = cfg();
		huntPrecise = false;
		if (huntBehindBypass > 0) {
			huntBehindBypass--;
		}
		if (protState != Prot.NONE) {
			protectorTick(mc, player);
			return;
		}
		if (useTicksLeft > 0 && --useTicksLeft == 0) {
			mc.options.keyUse.setDown(false);
			holdingRight = false;
		}
		if (huntReactTicks > 0) {
			huntReactTicks--;
			int elapsed = huntReactTotal - huntReactTicks;
			// a special mob has been in front of the view at some point: after the first second (a person would need that long to react) go for it
			if (specialInFront(mc, player, cfg)) {
				huntReactSpotted = true;
			}
			if (huntReactSpotted && elapsed >= Math.round(cfg.killReactEarlySeconds * 20)) {
				huntReactTicks = 0;
				huntBehindBypass = 60; // it was spotted: turn straight back to it, however far round the look has gone
			} else {
				standStill();
				// all the way round once over the time, a little up and down as it goes
				double progress = huntReactTotal <= 0 ? 1 : (double) elapsed / huntReactTotal;
				aimYaw = huntReactStartYaw + huntReactDirection * 360.0 * progress;
				pitchTarget = Math.sin(progress * Math.PI * 3) * 15.0;
				haveAim = true;
				if (--huntReactJumpTicks <= 0 && player.onGround()) {
					jump = true;
					huntReactJumpTicks = 8 + HUNT_RANDOM.nextInt(6);
				}
				return;
			}
		}
		java.util.List<ZealotHunter.Mob> all = scanMobs(mc, player);
		// a mob that has only just appeared can't be targeted for a moment (the ones there when the hunt starts are not new)
		long spawnTicks = Math.round(cfg.killSpawnCooldownSeconds * 20);
		all = all.stream().filter(m -> ticks - HUNT_FIRST_SEEN.getOrDefault(m.id(), -100000L) >= spawnTicks).toList();
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
		// targets behind you aren't gone for at once (that would just spin the view round): the one already being gone for is kept,
		// the rest wait unless they have been all there is for a moment
		final java.util.List<ZealotHunter.Mob> allCandidates = all;
		final java.util.List<ZealotHunter.Mob> sightCandidates = inSight;
		java.util.function.Predicate<ZealotHunter.Mob> inFront = m -> m.id() == huntTargetId || !ZealotHunter.isBehind(view, m, cfg.killBehindAngle);
		ZealotHunter.Mob pick = HUNTER.pick(sightCandidates.stream().filter(inFront).toList(), view, cfg.killPickByCrosshair, cfg.killUpPenalty, cfg.killSearchRadius, ticks, cfg.killName, cfg.killSpecialName);
		if (pick == null && sightCandidates.size() < allCandidates.size()) {
			pick = HUNTER.pick(allCandidates.stream().filter(inFront).toList(), view, cfg.killPickByCrosshair, cfg.killUpPenalty, cfg.killSearchRadius, ticks, cfg.killName, cfg.killSpecialName);
		}
		boolean waitingForBehind = false;
		if (pick == null) {
			ZealotHunter.Mob behind = HUNTER.pick(sightCandidates, view, cfg.killPickByCrosshair, cfg.killUpPenalty, cfg.killSearchRadius, ticks, cfg.killName, cfg.killSpecialName);
			if (behind == null && sightCandidates.size() < allCandidates.size()) {
				behind = HUNTER.pick(allCandidates, view, cfg.killPickByCrosshair, cfg.killUpPenalty, cfg.killSearchRadius, ticks, cfg.killName, cfg.killSpecialName);
			}
			if (behind != null) {
				if (huntBehindBypass > 0 || ++huntBehindTicks > Math.round(cfg.killBehindDelaySeconds * 20)) {
					pick = behind;
				} else {
					waitingForBehind = true;
				}
			} else {
				huntBehindTicks = 0;
			}
		} else {
			huntBehindTicks = 0;
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
		if (waitingForBehind) {
			standStill(); // something behind: give it a moment before spinning round
			return;
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
		pitchTarget = closeLookPitch(player, out.lookX(), out.lookZ(), pitchTarget);
		// sidestepping with A or D with the target near the crosshair: aim right at it with next to no easing, or the view lags behind a
		// moving target and keeps pointing a block or two ahead of it, never lining up to shoot
		huntPrecise = false;
		boolean seenNow = HUNT_SEEN.getOrDefault(id, -1000L) == ticks;
		if (cfg.killRequireSight && !recentlySeen(id)) {
			// behind a wall: never aim at it, look where the walk is going
			double[] way = planner.snapshot().next();
			if (way != null) {
				aimYaw = FollowPlanner.yawToward(player.getX(), player.getZ(), way[0], way[2]);
				pitchTarget = pathPitchBias(player, way);
			}
		}
		// test option: with only part of the mob in sight, aim at that part
		double partY = seenNow && cfg.killAimVisiblePart ? HUNT_PART_Y.getOrDefault(id, Double.NaN) : Double.NaN;
		if (!Double.isNaN(partY)) {
			aimYaw = FollowPlanner.yawToward(player.getX(), player.getZ(), pick.x(), pick.z());
			pitchTarget = FollowPlanner.pitchToward(player.getX(), player.getEyeY(), player.getZ(), pick.x(), partY, pick.z());
		}
		if (cfg.killPreciseAim && strafe != 0 && (seenNow || !cfg.killRequireSight)) {
			double aimY = !Double.isNaN(partY) ? partY : pick.y() + (pick.eyeY() - pick.y()) * 0.6;
			ZealotHunter.View now = new ZealotHunter.View(player.getX(), player.getEyeY(), player.getZ(), player.getYRot(), player.getXRot());
			if (now.angleTo(pick.x(), aimY, pick.z()) <= cfg.killPreciseAngle) {
				aimYaw = FollowPlanner.yawToward(player.getX(), player.getZ(), pick.x(), pick.z());
				pitchTarget = FollowPlanner.pitchToward(player.getX(), player.getEyeY(), player.getZ(), pick.x(), aimY, pick.z());
				huntPrecise = true;
			}
		}
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

	/** Standing right by what is looked at, a small sideways move of the look spot would swing the pitch wildly: keep it near level there. */
	private static double closeLookPitch(LocalPlayer player, double lookX, double lookZ, double pitch) {
		double horizontal = Math.hypot(lookX - player.getX(), lookZ - player.getZ());
		if (horizontal >= 1.5) {
			return pitch;
		}
		double limit = 3.0 + 10.0 * (horizontal / 1.5); // 3 degrees at the very spot, 13 at 1.5 blocks
		return RouteFollower.clamp(pitch, -limit, limit);
	}

	/** A little up when the way goes up, a little down when it goes down: at most killPathPitch degrees, reached at a 45 degree slope. */
	private static double pathPitchBias(LocalPlayer player, double[] way) {
		double max = cfg().killPathPitch;
		if (max <= 0 || way == null) {
			return 0;
		}
		double rise = way[1] - player.getY();
		if (Math.abs(rise) < 0.5) {
			return 0; // level, or just a slab's worth
		}
		double horizontal = Math.max(0.5, Math.hypot(way[0] - player.getX(), way[2] - player.getZ()));
		double slope = Math.toDegrees(Math.atan2(rise, horizontal));
		return -RouteFollower.clamp(slope / 45.0 * max, -max, max); // pitch is negative looking up
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
				idleGoingHome = true;
			} else if (cfg.killWander) {
				if (idlePauseTicks > 0) {
					idlePauseTicks--;
					standStill();
					return;
				}
				idleGoingHome = false;
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
		// the trip back home is run; the strolls round it are walked
		sprintOn = idleGoingHome && out.forward() > 0;
		jump = out.jump();
		if (cfg.killIdleLookAround) {
			// the view sits a random angle within 15 degrees either side of the way being walked (30 degrees in all), and moves to
			// another every second or two
			if (--idleLookTicks <= 0) {
				idleLookOffset = (HUNT_RANDOM.nextDouble() * 2 - 1) * IDLE_LOOK_SPREAD_DEGREES;
				idleLookPitch = (HUNT_RANDOM.nextDouble() * 2 - 1) * 2.0;
				idleLookTicks = 60 + HUNT_RANDOM.nextInt(100);
			}
			double[] way = planner.snapshot().next();
			double base = way != null
				? FollowPlanner.yawToward(player.getX(), player.getZ(), way[0], way[2])
				: FollowPlanner.yawToward(player.getX(), player.getZ(), goal[0], goal[2]);
			aimYaw = base + idleLookOffset;
			pitchTarget = idleLookPitch + pathPitchBias(player, way);
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
		return !Double.isInfinite(visiblePartY(mc, player, mob));
	}

	/**
	 * The height to aim at: NaN when the mob can be seen all over (aim where you like), the height of the one part that is clear
	 * (the middle, else the head, else the legs) when only some of it can be seen, or negative infinity when none of it can.
	 */
	private static double visiblePartY(Minecraft mc, LocalPlayer player, Entity mob) {
		Vec3 eye = player.getEyePosition();
		double[] heights = {mob.getY() + mob.getBbHeight() * 0.5, mob.getEyeY(), mob.getY() + 0.3};
		double firstClear = Double.NaN;
		int clear = 0;
		for (double y : heights) {
			net.minecraft.world.phys.BlockHitResult block = mc.level.clip(new net.minecraft.world.level.ClipContext(eye, new Vec3(mob.getX(), y, mob.getZ()),
				net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
			if (block.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
				if (clear == 0) {
					firstClear = y;
				}
				clear++;
			}
		}
		if (clear == 0) {
			return Double.NEGATIVE_INFINITY;
		}
		return clear == heights.length ? Double.NaN : firstClear;
	}

	/** Whether a special mob is in sight and within the angle either side of where the view points. */
	private static boolean specialInFront(Minecraft mc, LocalPlayer player, WigConfig cfg) {
		ZealotHunter.View view = new ZealotHunter.View(player.getX(), player.getEyeY(), player.getZ(), player.getYRot(), player.getXRot());
		for (ZealotHunter.Mob mob : scanMobs(mc, player)) {
			if (ZealotHunter.matches(mob.name(), cfg.killSpecialName) && !HUNTER.isMarkedDead(mob.id(), ticks)
				&& (!cfg.killRequireSight || recentlySeen(mob.id()))
				&& view.angleTo(mob.x(), mob.eyeY(), mob.z()) <= cfg.killReactSeeAngle) {
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
		var cfg = cfg();
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
		// when each mob was first noticed, for the spawn cooldown (the ones there when the hunt starts are not new)
		for (ZealotHunter.Mob m : mobs) {
			HUNT_FIRST_SEEN.putIfAbsent(m.id(), huntFirstScan ? (long) ticks - 100000 : (long) ticks);
		}
		huntFirstScan = false;
		if (cfg.killRequireSight) {
			for (ZealotHunter.Mob mob : mobs) {
				Entity body = mc.level.getEntity(mob.id());
				if (body != null) {
					double part = visiblePartY(mc, player, body);
					if (!Double.isInfinite(part)) {
						HUNT_SEEN.put(mob.id(), (long) ticks);
						HUNT_PART_Y.put(mob.id(), part);
					}
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
					Chat.info("Lost sight of " + followName + ". Going to where they were last seen and waiting " + cfg().followLostSeconds + " seconds.");
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
		pitchTarget = closeLookPitch(player, out.lookX(), out.lookZ(), pitchTarget);
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
		if (fishing) {
			fishStep(mc, player); // between ticks too, so the delays are not rounded up to whole ticks
		}
		boolean following = followSource != null;
		// hunting can have its own fastest turn, apart from the one routes (and following) use
		double turnSpeed = hunting ? cfg().killTurnSpeed : runTurnSpeed;
		double smoothing = !hunting ? cfg().followTurnSmoothing : huntPrecise ? cfg().killCloseSmoothing : cfg().killTurnSmoothing;
		if (haveAim) {
			double before = player.getYRot();
			float yaw = (float) (following
				? before + yawSmoother.step(RouteFollower.wrapDegrees(aimYaw - before), frameTicks, smoothing, turnSpeed)
				: RouteFollower.stepYaw(before, aimYaw, turnSpeed * frameTicks));
			player.setYRot(yaw);
			// no blending between ticks: the view is set exactly, every frame
			player.yRotO = yaw;
			lastFrameStep = RouteFollower.wrapDegrees(yaw - before);
			lastFrameTicks = frameTicks;
		}
		if (!Double.isNaN(pitchTarget)) {
			float pitch = (float) (following
				? player.getXRot() + pitchSmoother.step(pitchTarget - player.getXRot(), frameTicks, smoothing, turnSpeed)
				: RouteFollower.stepToward(player.getXRot(), pitchTarget, turnSpeed * frameTicks));
			player.setXRot(pitch);
			player.xRotO = pitch;
		}
		expectedYaw = player.getYRot();
		expectedPitch = player.getXRot();
		if (WigState.debug) {
			showDebug(mc, player);
		}
	}

	/** Ping samples for the whole session, taken once a second, and the verdict on the macro check being shown. */
	private static final PingTracker PING = new PingTracker();
	private static int pingSampleTicks;
	private static boolean macroHighPing;
	/** Ticks left of the yellow high ping warning on screen. */
	private static int lagWarningTicks;

	/** The player's ping now, in milliseconds, or 0 when it isn't known. */
	private static int currentPing() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.getConnection() == null) {
			return 0;
		}
		net.minecraft.client.multiplayer.PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
		return info == null ? 0 : info.getLatency();
	}

	/** Called every tick, running or not: keeps the session's average ping. */
	private static void samplePing() {
		if (++pingSampleTicks >= 20) {
			pingSampleTicks = 0;
			PING.add(currentPing());
		}
	}

	/** A new connection is a new session. */
	public static void resetPing() {
		PING.reset();
		pingSampleTicks = 0;
	}

	// ---- Kill Protectors ----

	private enum Prot { NONE, APPROACH, WAIT, FIGHT, CELEBRATE, RETURN }

	private static Prot protState = Prot.NONE;
	private static int protTicks;
	/** The formation point, where to wait and look before the protector spawns. */
	private static double[] protSpot;
	private static int protSpotIndex;
	private static boolean protHeadFound;
	private static int protWaitTicks;
	/** Where to stand while waiting, then the spot being hopped around after the kill. */
	private static double[] protWait;
	private static double[] protDeath;
	private static double[] protPortal;
	private static final double[] PROT_MOB_GOAL = new double[3];
	private static int protMobId = -1;
	private static FollowPlanner protPlanner;
	private static double[] protGoalRef;
	private static int protLookTicks;
	private static double protLookOffset;
	private static int protJumpTicks;
	private static boolean sneaking;
	/** Ticks left in which a teleport is expected (the warp commands this sends), so it isn't taken for a macro check. */
	private static int expectTeleportTicks;
	/** Ticks left in which the player or the world being gone for a moment (a world change) doesn't end the run. */
	private static int warpGraceTicks;

	/** The protector sequence's state, for the dev test. */
	public static String protectorState() {
		return protState.name();
	}

	public static boolean isSneaking() {
		return sneaking;
	}

	/** True while the input mixin should hold sneak. */
	public static boolean wantsSneak() {
		return sneaking && active();
	}

	/** Ticks until a queued warp is sent: a second's pause first, so the warp isn't sent while the message that asked for it is still landing. */
	private static int warpDelay;
	private static int protStuckTicks;
	private static boolean protNearPortal;
	/** Ticks left in which macro checks are off: three seconds from a protector spawning, to teleport freely. */
	private static int macroSuppressTicks;

	private static void sendWarp(Minecraft mc) {
		warpDelay = 20;
		expectTeleportTicks = 300;
		warpGraceTicks = 300;
	}

	private static void sendWarpNow(Minecraft mc) {
		String command = cfg().protectorWarpCommand.trim();
		if (command.startsWith("/")) {
			command = command.substring(1).trim();
		}
		if (!command.isEmpty() && mc.player != null) {
			mc.player.connection.sendCommand(command);
		}
		macroSuppressTicks = Math.max(macroSuppressTicks, 60);
		expectTeleportTicks = 300;
		warpGraceTicks = 300;
	}

	private static void protectorStart() {
		Minecraft mc = Minecraft.getInstance();
		WigConfig cfg = cfg();
		protState = Prot.APPROACH;
		protTicks = 0;
		protWait = null;
		protDeath = null;
		protMobId = -1;
		protPlanner = null;
		protGoalRef = null;
		protSpotIndex = 0;
		protSpot = cfg.protectorSpots[0];
		protHeadFound = false;
		macroSuppressTicks = 60;
		sendWarp(mc);
		Chat.info("An End Stone Protector is rising: warping to it.");
	}

	private static void protectorDied() {
		Minecraft mc = Minecraft.getInstance();
		sneaking = false;
		mc.options.keyUse.setDown(false);
		holdingRight = false;
		protState = Prot.CELEBRATE;
		protTicks = 0;
		protWait = null;
		protPlanner = null;
		protGoalRef = null;
		if (protDeath == null && mc.player != null) {
			protDeath = new double[] {mc.player.getX(), mc.player.getY(), mc.player.getZ()};
		}
		Chat.info("The End Stone Protector is down. Hopping about for a moment, then warping back.");
	}

	private static void protectorReset() {
		warpDelay = 0;
		protNearPortal = false;
		protState = Prot.NONE;
		sneaking = false;
		protPlanner = null;
		protGoalRef = null;
	}

	/** Walks toward the goal with the path finder: 0 still going, 1 arrived, 2 can't get there. */
	private static int protWalk(LocalPlayer player, double[] goal, boolean sprint, double stop, double resume) {
		WigConfig c = cfg();
		if (protGoalRef != goal || protPlanner == null) {
			protGoalRef = goal;
			protPlanner = new FollowPlanner(new FollowPlanner.Params(stop, resume, 6.0, 1.0, 0, c.followNeverJump, c.stuckTimeoutSeconds, false, 0, 2, true, c.pathJumpCost), new Random());
			protPlanner.setTerrain(new WorldTerrain());
		}
		FollowPlanner.Output out = protPlanner.tick(player.getX(), player.getY(), player.getZ(), player.getYRot(),
			new FollowPlanner.Target(goal[0], goal[1], goal[2], goal[1] + 1.62), player.onGround(), player.horizontalCollision);
		followDistance = out.distance();
		if (out.status() == FollowPlanner.Status.CLOSE) {
			standStill();
			return 1;
		}
		if (out.status() == FollowPlanner.Status.STUCK) {
			// no path (too far for the path finder, or boxed in): head straight for it, jumping what is in the way, and plan again now and then
			if (++protStuckTicks > 60) {
				protStuckTicks = 0;
				protPlanner = null;
			}
			double flat = Math.hypot(goal[0] - player.getX(), goal[2] - player.getZ());
			if (flat <= stop) {
				standStill();
				return 1;
			}
			moveForward = 1;
			strafe = 0;
			sprintOn = sprint;
			jump = player.horizontalCollision && player.onGround();
			aimYaw = FollowPlanner.yawToward(player.getX(), player.getZ(), goal[0], goal[2]);
			pitchTarget = 0;
			haveAim = true;
			return 0;
		}
		protStuckTicks = 0;
		moveForward = out.forward();
		strafe = out.strafe();
		sprintOn = sprint && out.forward() > 0;
		jump = out.jump();
		double[] way = protPlanner.snapshot().next();
		double[] look = way != null ? way : goal;
		aimYaw = FollowPlanner.yawToward(player.getX(), player.getZ(), look[0], look[2]);
		pitchTarget = pathPitchBias(player, way);
		haveAim = true;
		return 0;
	}

	/** The End Stone Protector near the spot (or the player): by name, on the mob or its name tag stand, else an unnamed iron golem. */
	private static Entity findProtector(Minecraft mc, LocalPlayer player) {
		WigConfig cfg = cfg();
		// around the spot and around the player, so it is found on the way there too
		double cx = protSpot != null ? protSpot[0] : player.getX();
		double cy = protSpot != null ? protSpot[1] : player.getY();
		double cz = protSpot != null ? protSpot[2] : player.getZ();
		AABB area = new AABB(Math.min(cx, player.getX()) - 35, Math.min(cy, player.getY()) - 20, Math.min(cz, player.getZ()) - 35,
			Math.max(cx, player.getX()) + 35, Math.max(cy, player.getY()) + 20, Math.max(cz, player.getZ()) + 35);
		java.util.List<Entity> around = mc.level.getEntities((Entity) null, area);
		Entity golem = null;
		for (Entity entity : around) {
			if (entity instanceof Player || !entity.isAlive()) {
				continue;
			}
			String name = entity.hasCustomName() ? entity.getCustomName().getString() : "";
			if (entity instanceof ArmorStand) {
				if (!ZealotHunter.matches(name, cfg.protectorName)) {
					continue;
				}
				Entity best = null;
				double bestDist = 3.0;
				for (Entity other : around) {
					if (other instanceof LivingEntity && !(other instanceof ArmorStand) && !(other instanceof Player) && other.isAlive()) {
						double flat = Math.hypot(other.getX() - entity.getX(), other.getZ() - entity.getZ());
						double above = entity.getY() - other.getY();
						if (flat < bestDist && above > -0.5 && above < 5.0) {
							bestDist = flat;
							best = other;
						}
					}
				}
				if (best != null) {
					return best;
				}
			} else if (entity instanceof LivingEntity) {
				if (ZealotHunter.matches(name, cfg.protectorName)) {
					return entity;
				}
				if (name.isEmpty() && golem == null
					&& net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals("minecraft:iron_golem")) {
					golem = entity;
				}
			}
		}
		return golem;
	}

	/**
	 * The formation has a custom head placed on top of it: once one is seen (matching protectorHeadTexture, if that is set), the formation
	 * is taken to be under it, and that is where to go and look. Returns true the tick it is found.
	 */
	private static boolean lookForFormationHead(Minecraft mc, LocalPlayer player) {
		WigConfig cfg = cfg();
		if (protHeadFound || protTicks % 20 != 0) {
			return false;
		}
		java.util.List<HeadScanner.Head> heads = HeadScanner.scan(mc, player.getX(), player.getZ(), 200); // everything loaded around the player
		String wanted = cfg.protectorHeadTexture.isBlank() ? new WigConfig().protectorHeadTexture : cfg.protectorHeadTexture;
		// the formation's own skin, wherever it is
		for (HeadScanner.Head head : heads) {
			if (HeadScanner.matches(head, wanted)) {
				useHead(player, head, "Found the formation's head");
				return true;
			}
		}
		// otherwise any head close to one of the places it is known to spawn
		HeadScanner.Head near = null;
		int nearSpot = -1;
		for (HeadScanner.Head head : heads) {
			for (int i = 0; i < cfg.protectorSpots.length; i++) {
				double[] spot = cfg.protectorSpots[i];
				if (Math.abs(head.pos().getX() + 0.5 - spot[0]) <= HEAD_NEAR_SPOT && Math.abs(head.pos().getZ() + 0.5 - spot[2]) <= HEAD_NEAR_SPOT
					&& Math.abs(head.pos().getY() - spot[1]) <= HEAD_NEAR_SPOT) {
					near = head;
					nearSpot = i;
					break;
				}
			}
			if (near != null) {
				break;
			}
		}
		if (near != null) {
			Chat.info("No head with the formation's skin (" + wanted.substring(0, Math.min(8, wanted.length())) + "...) found. Taking the head at "
				+ near.pos().getX() + " " + near.pos().getY() + " " + near.pos().getZ() + ", near spawn location " + (nearSpot + 1) + ", skin " + near.textureId() + ".");
			useHead(player, near, "Using that head");
			return true;
		}
		if (protTicks % 100 == 0) {
			Chat.info("Looking for the formation's head: " + heads.size() + " player head(s) loaded around you, none with its skin and none near a spawn location"
				+ (heads.isEmpty() ? "." : " (nearest: " + heads.get(0).pos().getX() + " " + heads.get(0).pos().getY() + " " + heads.get(0).pos().getZ()
				+ ", skin " + heads.get(0).textureId() + ")."));
		}
		return false;
	}

	private static int backStrafe;
	private static int backStrafeTicks;

	/** Closer than three blocks to the protector: back up, or slide left or right (A or D) when something is in the way behind. */
	private static void backAwayFrom(LocalPlayer player, Entity mob) {
		double dx = player.getX() - mob.getX();
		double dz = player.getZ() - mob.getZ();
		double flat = Math.hypot(dx, dz);
		if (flat >= 3.0 || flat < 0.01) {
			return;
		}
		WorldTerrain terrain = new WorldTerrain();
		double ax = dx / flat; // away from it
		double az = dz / flat;
		int y = (int) Math.floor(player.getY() + 0.01);
		boolean blocked = player.horizontalCollision
			|| terrain.solid((int) Math.floor(player.getX() + ax * 0.9), y, (int) Math.floor(player.getZ() + az * 0.9))
			|| terrain.solid((int) Math.floor(player.getX() + ax * 0.9), y + 1, (int) Math.floor(player.getZ() + az * 0.9));
		sprintOn = false;
		if (!blocked) {
			moveForward = -1;
			strafe = 0;
			backStrafeTicks = 0;
			return;
		}
		// left of the player when facing the protector is (az', -ax'): facing (fx, fz) = (-ax, -az) gives left = (fz, -fx) = (-az, ax)
		if (--backStrafeTicks <= 0) {
			double lx = -az;
			double lz = ax;
			boolean leftClear = !terrain.solid((int) Math.floor(player.getX() + lx * 0.9), y, (int) Math.floor(player.getZ() + lz * 0.9));
			boolean rightClear = !terrain.solid((int) Math.floor(player.getX() - lx * 0.9), y, (int) Math.floor(player.getZ() - lz * 0.9));
			backStrafe = leftClear && (!rightClear || HUNT_RANDOM.nextBoolean()) ? 1 : rightClear ? -1 : (HUNT_RANDOM.nextBoolean() ? 1 : -1);
			backStrafeTicks = 15 + HUNT_RANDOM.nextInt(15);
		}
		moveForward = 0;
		strafe = backStrafe;
	}

	/** How far from a known spawn location a head can be and still be taken for the formation's. */
	private static final double HEAD_NEAR_SPOT = 5.0;

	/** Takes the formation to be under this head: the ground below it is where to go and look. */
	private static void useHead(LocalPlayer player, HeadScanner.Head head, String message) {
		WorldTerrain terrain = new WorldTerrain();
		int x = head.pos().getX();
		int z = head.pos().getZ();
		// the head sits on top of the formation: walk at the floor's level, not up onto the structure
		int ground = cfg().protectorFloorY;
		protSpot = new double[] {x, ground, z};
		protHeadFound = true;
		protWait = null;
		Chat.info(message + " at " + x + " " + head.pos().getY() + " " + z + " ("
			+ Math.round(Math.hypot(x + 0.5 - player.getX(), z + 0.5 - player.getZ())) + " blocks away, skin " + head.textureId() + ").");
	}

	/** One tick of the protector sequence (see the Kill Protectors option). */
	private static void protectorTick(Minecraft mc, LocalPlayer player) {
		WigConfig cfg = cfg();
		protTicks++;
		if (warpDelay > 0) {
			if (--warpDelay == 0) {
				sendWarpNow(mc);
			}
			standStill();
			return;
		}
		switch (protState) {
			case APPROACH -> {
				if (protTicks < 80) {
					standStill(); // the warp takes a moment
					return;
				}
				if (lookForFormationHead(mc, player)) {
					protPlanner = null;
					protGoalRef = null;
				}
				Entity early = findProtector(mc, player);
				if (early != null) {
					protMobId = early.getId();
					protState = Prot.FIGHT;
					protPlanner = null;
					protGoalRef = null;
					return;
				}
				protWaitTicks = 0;
				if (protWait == null) {
					double[] pick = Wander.pick(new WorldTerrain(), protSpot[0], protSpot[1], protSpot[2], 9.0, 6.0, HUNT_RANDOM);
					protWait = pick != null ? pick : protSpot;
				}
				if (protWalk(player, protWait, true, 1.5, 2.5) != 0) {
					protState = Prot.WAIT;
					standStill();
				}
			}
			case WAIT -> {
				standStill();
				// sit still looking toward the formation, within ten degrees of it
				if (protSpot != null) {
					if (--protLookTicks <= 0) {
						protLookOffset = (HUNT_RANDOM.nextDouble() * 2 - 1) * 10.0;
						protLookTicks = 30 + HUNT_RANDOM.nextInt(50);
					}
					aimYaw = FollowPlanner.yawToward(player.getX(), player.getZ(), protSpot[0], protSpot[2]) + protLookOffset;
					pitchTarget = RouteFollower.clamp(FollowPlanner.pitchToward(player.getX(), player.getEyeY(), player.getZ(), protSpot[0], protSpot[1] + 3.0, protSpot[2]), -20, 20);
					haveAim = true;
				}
				if (lookForFormationHead(mc, player)) {
					protState = Prot.APPROACH; // the head is somewhere else than where it was waiting: go there
					protTicks = 100;
					return;
				}
				Entity mob = findProtector(mc, player);
				if (mob != null) {
					protMobId = mob.getId();
					protState = Prot.FIGHT;
					protPlanner = null;
					protGoalRef = null;
				} else if (!protHeadFound && cfg.protectorSpots.length > 1 && ++protWaitTicks > Math.round(cfg.protectorSearchSeconds * 20)) {
					// not here: try the next place it can be
					protSpotIndex = (protSpotIndex + 1) % cfg.protectorSpots.length;
					protSpot = cfg.protectorSpots[protSpotIndex];
					protWait = null;
					protWaitTicks = 0;
					protState = Prot.APPROACH;
					protTicks = 100;
					protPlanner = null;
					protGoalRef = null;
				}
			}
			case FIGHT -> {
				Entity mob = protMobId >= 0 ? mc.level.getEntity(protMobId) : null;
				if (mob == null || !mob.isAlive()) {
					mob = findProtector(mc, player);
					if (mob != null) {
						protMobId = mob.getId();
					}
				}
				// right clicking the whole fight, and sneaking once it is within ten blocks
				mc.options.keyUse.setDown(true);
				holdingRight = true;
				if (mob == null) {
					sneaking = false;
					standStill();
					return;
				}
				boolean near = player.distanceTo(mob) <= 10.0;
				sneaking = near;
				PROT_MOB_GOAL[0] = mob.getX();
				PROT_MOB_GOAL[1] = mob.getY();
				PROT_MOB_GOAL[2] = mob.getZ();
				protDeath = new double[] {mob.getX(), mob.getY(), mob.getZ()};
				// three to four blocks away, on its level
				protWalk(player, PROT_MOB_GOAL, !near, 3.2, 4.2);
				backAwayFrom(player, mob);
				aimYaw = FollowPlanner.yawToward(player.getX(), player.getZ(), mob.getX(), mob.getZ());
				pitchTarget = FollowPlanner.pitchToward(player.getX(), player.getEyeY(), player.getZ(), mob.getX(), mob.getY() + 0.15, mob.getZ()); // at its feet
				haveAim = true;
			}
			case CELEBRATE -> {
				if (protTicks >= 80) {
					// four seconds of that: warp back and carry on to the portal to the Bruisers
					double[] portal = cfg.bruiserPortals[HUNT_RANDOM.nextInt(cfg.bruiserPortals.length)];
					protPortal = new double[] {portal[0] + 0.5, portal[1], portal[2] + 0.5};
					protState = Prot.RETURN;
					protNearPortal = false;
					protTicks = 0;
					protPlanner = null;
					protGoalRef = null;
					sendWarp(mc);
					standStill();
					return;
				}
				if (protWait == null) {
					double[] around = protDeath != null ? protDeath : new double[] {player.getX(), player.getY(), player.getZ()};
					double[] pick = Wander.pick(new WorldTerrain(), around[0], around[1], around[2], 3.0, 1.0, HUNT_RANDOM);
					protWait = pick != null ? pick : around;
				}
				if (protWalk(player, protWait, false, 0.6, 1.0) != 0) {
					protWait = null;
				}
				if (--protJumpTicks <= 0 && player.onGround()) {
					jump = true;
					protJumpTicks = 7 + HUNT_RANDOM.nextInt(6);
				}
			}
			case RETURN -> {
				if (protTicks < 80) {
					standStill(); // the warp takes a moment
					return;
				}
				double toPortal = Math.hypot(player.getX() - protPortal[0], player.getZ() - protPortal[2]);
				if (toPortal <= 6.0) {
					protNearPortal = true;
				}
				// through the portal: in the arrival area, or simply far from the portal it was walked into
				boolean through = protNearPortal && toPortal > 25.0;
				if (through || SafeZones.inside(player.getX(), player.getY(), player.getZ(), new double[][] {cfg.bruiserArrival}, Math.max(cfg.safeZoneRadius, 8.0))
					|| protTicks > 20 * 180) {
					// through the portal (or taking far too long): back to hunting
					protectorReset();
					huntTargetId = -1;
					return;
				}
				int r = protWalk(player, protPortal, true, 0.8, 1.2);
				if (r == 2) {
					double[] portal = cfg.bruiserPortals[HUNT_RANDOM.nextInt(cfg.bruiserPortals.length)];
					protPortal = new double[] {portal[0] + 0.5, portal[1], portal[2] + 0.5};
					protPlanner = null;
				}
			}
			default -> {
			}
		}
	}

	private static Vec3 lastTickPos;
	private static double lastTickSpeed;
	private static Object lastTickLevel;

	/** Forgets where the player was, so the next tick isn't compared with an old position. */
	private static void resetTeleportCheck() {
		lastTickPos = null;
	}

	/** Raises a macro check when the player is moved further than three blocks in one tick, or ends up in another world, by something other than itself. */
	private static void checkTeleport(Minecraft mc, LocalPlayer player) {
		Vec3 now = player.position();
		if (expectTeleportTicks > 0) {
			expectTeleportTicks--; // a warp this mod sent: the teleport is expected
			if (lastTickPos != null && expectTeleportTicks > 200
				&& (mc.level != lastTickLevel || Teleport.teleported(now.distanceTo(lastTickPos), lastTickSpeed))) {
				macroSuppressTicks = Math.max(macroSuppressTicks, 60); // and three seconds with nothing checked at all
				expectTeleportTicks = 200; // it has happened: six more seconds for the view and the world to settle, then back to watching
			}
		} else if (lastTickPos != null) {
			boolean otherWorld = mc.level != lastTickLevel;
			if (otherWorld || Teleport.teleported(now.distanceTo(lastTickPos), lastTickSpeed)) {
				macroCheck("You were teleported");
			}
		}
		lastTickPos = now;
		lastTickLevel = mc.level;
		lastTickSpeed = player.getDeltaMovement().length();
	}

	/** Raises a macro check when the view differs from where this mod left it by the configured angle or more. */
	private static void checkView(LocalPlayer player) {
		double limit = cfg().rotationStopDegrees;
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
		if (macroCountdown >= 0 || macroSuppressTicks > 0) {
			return;
		}
		Minecraft here = Minecraft.getInstance();
		if (here.player != null && (SafeZones.inside(here.player.getX(), here.player.getY(), here.player.getZ(), safeZones(), cfg().safeZoneRadius))) {
			return; // at a portal or where it puts you: the teleport and the rest of it are expected
		}
		if (lagWarningTicks > 0) {
			return; // a yellow warning is already showing
		}
		macroReason = reason;
		int ping = currentPing();
		macroHighPing = PING.above(ping);
		if (macroHighPing) {
			// above the session's average ping it may be only lag: a yellow warning, no alarm, and the run carries on
			lagWarningTicks = Math.max(1, (int) Math.round(cfg().macroWarningSeconds * 20));
			Chat.info("Possible macro check (" + reason.toLowerCase(java.util.Locale.ROOT) + "), but your ping was " + ping
				+ " ms, well above your average of " + Math.round(PING.average()) + " ms. Carrying on.");
			showWarning(Minecraft.getInstance());
			return;
		}
		macroCountdown = Math.max(1, (int) Math.round(cfg().macroWarningSeconds * 20));
		Chat.error("Macro check detected: " + reason.toLowerCase(java.util.Locale.ROOT) + ". Stopping in "
			+ cfg().macroWarningSeconds + " seconds.");
		showWarning(Minecraft.getInstance());
		playAlarm();
	}

	/** The wither's idle sound (mob/wither/idle1.ogg), registered by this mod's sounds.json so it plays that one file and not a random one of the four. */
	private static final net.minecraft.sounds.SoundEvent ALARM_SOUND = net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
		net.minecraft.resources.Identifier.fromNamespaceAndPath("wigaddons", "alarm"));

	/** The portals and where they put you. */
	private static double[][] safeZones() {
		WigConfig c = cfg();
		double[][] zones = new double[c.bruiserPortals.length + 1][];
		System.arraycopy(c.bruiserPortals, 0, zones, 0, c.bruiserPortals.length);
		zones[c.bruiserPortals.length] = c.bruiserArrival;
		return zones;
	}

	/** The wither idle sound, at the configured volume, whenever a macro check is raised. */
	private static void playAlarm() {
		// the game won't play a sound above full volume, so anything louder is the same sound played again on top of itself
		double remaining = cfg().macroSoundVolume / 100.0;
		while (remaining > 0.001) {
			float layer = (float) Math.min(1.0, remaining);
			Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(ALARM_SOUND, 1.0F, layer));
			remaining -= 1.0;
		}
	}

	private static void showWarning(Minecraft mc) {
		warningTimer = WARNING_REFRESH_TICKS;
		mc.gui.setTimes(0, WARNING_REFRESH_TICKS * 2, 0);
		// yellow instead of red when the ping was above the session average: it may well be lag and not a real check
		int color = macroHighPing ? 0xFFFF55 : 0xFF5555;
		mc.gui.setTitle(Component.literal("WARNING").withColor(color));
		mc.gui.setSubtitle(Component.literal(macroHighPing ? macroReason + " (high ping)" : macroReason).withColor(color));
	}

	private static void clearWarning() {
		Minecraft mc = Minecraft.getInstance();
		mc.gui.clearTitles();
		mc.gui.resetTitleTimes();
	}

	/** Called for every chat line the server sends. */
	private static final String WARP_LOCKED = "You haven't unlocked this fast travel destination!";
	/** The protector warp turned out to be locked: no more Protectors this hunt. */
	private static boolean protBlocked;

	public static void onChatLine(String line) {
		WigConfig reactCfg = cfg();
		if (hunting && planner != null && reactCfg.killProtectors && protState != Prot.NONE && ZealotHunter.matches(line, WARP_LOCKED)) {
			// the warp did not go anywhere: the destination is not unlocked, so there is no protector to get to. Carry on with the Zealots.
			protectorReset();
			protBlocked = true;
			expectTeleportTicks = 0;
			warpGraceTicks = 0;
			macroSuppressTicks = 0;
			Chat.error("Kill Protectors can't work: you haven't unlocked the fast travel destination (/" + reactCfg.protectorWarpCommand.trim().replaceFirst("^/", "")
				+ "). Skipping Protectors and carrying on with the Zealots.");
			return;
		}
		if (hunting && planner != null && reactCfg.killProtectors && !protBlocked) {
			if (protState == Prot.NONE && !reactCfg.protectorSpawnMessage.isBlank() && ZealotHunter.matches(line, reactCfg.protectorSpawnMessage)) {
				protectorStart();
			} else if ((protState == Prot.APPROACH || protState == Prot.WAIT || protState == Prot.FIGHT)
				&& !reactCfg.protectorDeathMessage.isBlank() && ZealotHunter.matches(line, reactCfg.protectorDeathMessage)) {
				protectorDied();
			}
		}
		if (hunting && planner != null && !reactCfg.killReactMessage.isBlank() && ZealotHunter.matches(line, reactCfg.killReactMessage)) {
			// something special has turned up: stop, look around and hop about for a moment before going for it
			double seconds = reactCfg.killReactMinSeconds + HUNT_RANDOM.nextDouble() * (reactCfg.killReactMaxSeconds - reactCfg.killReactMinSeconds);
			huntReactTicks = Math.max(1, (int) Math.round(seconds * 20));
			huntReactTotal = huntReactTicks;
			huntReactSpotted = false;
			huntReactJumpTicks = 0;
			huntReactDirection = HUNT_RANDOM.nextBoolean() ? 1 : -1;
			net.minecraft.client.player.LocalPlayer me = Minecraft.getInstance().player;
			huntReactStartYaw = me == null ? 0 : me.getYRot();
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
