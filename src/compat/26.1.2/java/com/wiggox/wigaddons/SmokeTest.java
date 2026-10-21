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
		{"safety", "safety.alarm", ""},
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
					// -Dwigaddons.smoke.phase=N skips ahead to one phase, to try only that
					int jump = Integer.getInteger("wigaddons.smoke.phase", 0);
					if (jump > 0) {
						phase = jump;
						phaseTick = 0;
					} else {
						next();
					}
				}
			}
			case 1 -> {
				WigState.config.macroSoundVolume = 300;
				setupAndOpenGui(mc);
			}
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
			case 12 -> next();
			case 13 -> jumpCheck(mc, false, 14);
			case 14 -> jumpCheck(mc, true, 15);
			case 15 -> followCheck(mc);
			case 16 -> followLostCheck(mc);
			case 17 -> huntCheck(mc);
			case 18 -> wanderCheck(mc);
			case 19 -> sightCheck(mc);
			case 20 -> crowdCheck(mc);
			case 21 -> typeCheck(mc);
			case 22 -> reactCheck(mc);
			case 23 -> reactEarlyCheck(mc);
			case 24 -> partCheck(mc);
			case 25 -> spawnCooldownCheck(mc);
			case 26 -> teleportCheck(mc);
			case 27 -> protectorCheck(mc);
			case 28 -> hudPosition(mc);
			case 29 -> jerryCheck(mc);
			case 30 -> hudSessionCheck(mc);
			case 31 -> {
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

	/** Three named iron golems standing in for endermen stand about: the special one should be attacked first, then the others, nearest first. */
	private static void huntCheck(Minecraft mc) {
		if (phaseTick == 1) {
			command(mc, "tp @s 0.5 -60 0.5 -90 0");
			command(mc, "kill @e[type=minecraft:iron_golem]");
		}
		if (phaseTick == 10) {
			String tags = ",CustomNameVisible:1b,NoAI:1b,Silent:1b,Invulnerable:1b}";
			command(mc, "summon minecraft:iron_golem 9.5 -60 0.5 {CustomName:\"Zealot\"" + tags);
			command(mc, "summon minecraft:iron_golem 0.5 -60 14.5 {CustomName:\"Special Zealot\"" + tags);
			command(mc, "summon minecraft:iron_golem 5.5 -60 -5.5 {CustomName:\"Zealot Bruiser\"" + tags);
		}
		if (phaseTick == 40) {
			for (net.minecraft.world.entity.Entity e : mc.level.getEntities((net.minecraft.world.entity.Entity) null, mc.player.getBoundingBox().inflate(40))) {
				log("entity " + e.getType() + " name=" + (e.hasCustomName() ? e.getCustomName().getString() : "-") + " at " + String.format("%.1f,%.1f,%.1f", e.getX(), e.getY(), e.getZ()));
			}
			log("start hunting");
			Runner.startHunt();
		}
		if (phaseTick > 40 && phaseTick % 100 == 0) {
			log("hunt t=" + (phaseTick - 40) + ": me " + fmt(mc) + ", attacked " + Runner.huntOrder());
		}
		if (phaseTick == 45) {
			photograph(mc, "hunt");
		}
		if (phaseTick == 300) {
			photograph(mc, "hunt");
		}
		if (phaseTick == 700 || (phaseTick > 40 && Runner.huntOrder().size() >= 5)) { // the special one takes three clicks
			log("hunt ended: attacked in order " + Runner.huntOrder());
			Runner.stop("smoke test over");
			command(mc, "kill @e[type=minecraft:iron_golem]");
			next();
		}
	}

	/** Nothing to hunt, a home spot set 12 blocks away: walk there, then stroll around it. */
	private static void wanderCheck(Minecraft mc) {
		if (phaseTick == 1) {
			command(mc, "kill @e[type=minecraft:iron_golem]");
			command(mc, "tp @s 0.5 -60 0.5 0 0");
			WigState.config.killHomeSet = true;
			WigState.config.killHomeX = 12.5;
			WigState.config.killHomeY = -60;
			WigState.config.killHomeZ = 0.5;
		}
		if (phaseTick == 30) {
			Runner.startHunt();
		}
		if (phaseTick > 30 && phaseTick % 60 == 0) {
			log("wander t=" + (phaseTick - 30) + ": me " + fmt(mc));
		}
		if (phaseTick == 150) {
			photograph(mc, "wander");
		}
		if (phaseTick == 700) {
			Runner.stop("smoke test over");
			WigState.config.killHomeSet = false;
			next();
		}
	}

	/** A named golem behind a long wall must not be aimed at or attacked through it; with the wall gone it is attacked. */
	private static void sightCheck(Minecraft mc) {
		if (phaseTick == 1) {
			command(mc, "tp @s 0.5 -60 0.5 0 0");
			command(mc, "fill -30 -60 4 30 -57 4 stone");
		}
		if (phaseTick == 10) {
			command(mc, "summon minecraft:iron_golem 0.5 -60 8.5 {CustomName:\"Zealot\",CustomNameVisible:1b,NoAI:1b,Silent:1b,Invulnerable:1b}");
		}
		if (phaseTick == 40) {
			Runner.startHunt();
		}
		if (phaseTick == 200) {
			log("behind the wall (walking toward it is fine, attacking is not): me " + fmt(mc) + ", attacked " + Runner.huntOrder());
			command(mc, "fill -30 -60 4 30 -57 4 air");
		}
		if (phaseTick == 400) {
			log("wall gone: me " + fmt(mc) + ", attacked " + Runner.huntOrder());
			Runner.stop("smoke test over");
			command(mc, "kill @e[type=minecraft:iron_golem]");
			next();
		}
	}

	/** Three golems within reach at once with "stand still when crowded" on: the player must not move until all three are attacked. */
	private static void crowdCheck(Minecraft mc) {
		if (phaseTick == 1) {
			command(mc, "tp @s 0.5 -60 0.5 0 0");
			WigState.config.killStandWhenCrowded = true;
			WigState.config.killHomeSet = false;
		}
		if (phaseTick == 10) {
			String tags = ",CustomNameVisible:1b,NoAI:1b,Silent:1b,Invulnerable:1b}";
			command(mc, "summon minecraft:iron_golem 0.5 -60 3.5 {CustomName:\"Zealot\"" + tags);
			command(mc, "summon minecraft:iron_golem 3.5 -60 0.5 {CustomName:\"Zealot\"" + tags);
			command(mc, "summon minecraft:iron_golem -2.5 -60 0.5 {CustomName:\"Zealot\"" + tags);
		}
		if (phaseTick == 40) {
			Runner.startHunt();
		}
		if (phaseTick > 40 && phaseTick % 20 == 0) {
			log("crowd t=" + (phaseTick - 40) + ": me " + fmt(mc) + ", attacked " + Runner.huntOrder().size());
		}
		if (phaseTick == 500 || (phaseTick > 40 && Runner.huntOrder().size() >= 3)) {
			log("crowd ended: attacked " + Runner.huntOrder().size() + ", me " + fmt(mc));
			Runner.stop("smoke test over");
			WigState.config.killStandWhenCrowded = false;
			command(mc, "kill @e[type=minecraft:iron_golem]");
			next();
		}
	}

	/** An unnamed golem with "hunt by mob type" on is attacked without any name tag. */
	private static void typeCheck(Minecraft mc) {
		if (phaseTick == 1) {
			command(mc, "tp @s 0.5 -60 0.5 0 0");
			WigState.config.killByType = true;
			WigState.config.killType = "minecraft:iron_golem";
		}
		if (phaseTick == 10) {
			command(mc, "summon minecraft:iron_golem 0.5 -60 6.5 {NoAI:1b,Silent:1b,Invulnerable:1b}");
		}
		if (phaseTick == 40) {
			Runner.startHunt();
		}
		if (phaseTick == 300) {
			log("by type: me " + fmt(mc) + ", attacked " + Runner.huntOrder());
			Runner.stop("smoke test over");
			WigState.config.killByType = false;
			command(mc, "kill @e[type=minecraft:iron_golem]");
			next();
		}
	}

	private static int reactJumps;
	private static double reactYawMoved;
	private static double reactLastYaw;

	/** The spawn message makes the hunt stand, look around and jump for 2 to 3 seconds. */
	private static void reactCheck(Minecraft mc) {
		if (phaseTick == 1) {
			command(mc, "tp @s 0.5 -60 0.5 0 0");
			WigState.config.killHomeSet = false;
			reactJumps = 0;
			reactYawMoved = 0;
		}
		if (phaseTick == 20) {
			Runner.startHunt();
		}
		if (phaseTick == 40) {
			Runner.onChatLine("A special Zealot has spawned nearby!");
			reactLastYaw = mc.player.getYRot();
		}
		if (phaseTick > 40 && phaseTick <= 120) {
			if (!mc.player.onGround()) {
				reactJumps++;
			}
			reactYawMoved += Math.abs(mc.player.getYRot() - reactLastYaw);
			reactLastYaw = mc.player.getYRot();
		}
		if (phaseTick == 140) {
			log("react: airborne ticks " + reactJumps + ", view moved " + String.format("%.0f", reactYawMoved) + " degrees, me " + fmt(mc));
			Runner.stop("smoke test over");
			next();
		}
	}

	/** A special mob appears straight ahead right after the message: the looking around should end after about a second and the mob be attacked. */
	private static void reactEarlyCheck(Minecraft mc) {
		if (phaseTick == 1) {
			command(mc, "tp @s 0.5 -60 0.5 0 0");
			command(mc, "kill @e[type=minecraft:iron_golem]");
		}
		if (phaseTick == 30) {
			Runner.startHunt();
		}
		if (phaseTick == 40) {
			Runner.onChatLine("A special Zealot has spawned nearby!");
			reactLastYaw = mc.player.getYRot();
			reactYawMoved = 0;
		}
		if (phaseTick == 42) {
			command(mc, "summon minecraft:iron_golem 0.5 -60 4.5 {CustomName:\"Special Zealot\",CustomNameVisible:1b,NoAI:1b,Silent:1b,Invulnerable:1b}");
		}
		if (phaseTick > 40 && phaseTick < 160) {
			reactYawMoved += Math.abs(mc.player.getYRot() - reactLastYaw);
			reactLastYaw = mc.player.getYRot();
			if (!Runner.huntOrder().isEmpty()) {
				log("react early: first attack " + (phaseTick - 40) + " ticks after the message, view moved " + String.format("%.0f", reactYawMoved) + " degrees");
				Runner.stop("smoke test over");
				command(mc, "kill @e[type=minecraft:iron_golem]");
				next();
			}
		}
		if (phaseTick == 160) {
			log("react early: no attack in 120 ticks");
			Runner.stop("smoke test over");
			command(mc, "kill @e[type=minecraft:iron_golem]");
			next();
		}
	}

	/** Only the legs of a golem show through a gap under a wall: with the test option on, the view should aim down at them. */
	private static void partCheck(Minecraft mc) {
		if (phaseTick == 1) {
			command(mc, "tp @s 0.5 -60 0.5 0 0");
			command(mc, "kill @e[type=minecraft:iron_golem]");
			command(mc, "fill -30 -59 6 30 -55 6 stone");
			WigState.config.killAimVisiblePart = true;
			WigState.config.killStandWhenCrowded = false;
		}
		if (phaseTick == 10) {
			command(mc, "summon minecraft:iron_golem 3.5 -60 8.5 {CustomName:\"Zealot\",CustomNameVisible:1b,NoAI:1b,Silent:1b,Invulnerable:1b}");
		}
		if (phaseTick == 40) {
			Runner.startHunt();
		}
		if (phaseTick == 38) {
			net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition();
			for (double y : new double[] {-59.7, -58.65, -57.7}) {
				var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(eye, new net.minecraft.world.phys.Vec3(3.5, y, 8.5),
					net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
				log("part probe eye=" + eye + " to y=" + y + ": " + hit.getType() + " at " + hit.getLocation());
			}
		}
		if (phaseTick == 43 || phaseTick == 46 || phaseTick == 50 || phaseTick == 56) {
			log("part: view pitch " + String.format("%.1f", mc.player.getXRot()) + " (positive is down), me " + fmt(mc) + ", marks " + Runner.huntMarks());
		}
		if (phaseTick == 80) {
			Runner.stop("smoke test over");
			WigState.config.killAimVisiblePart = false;
			command(mc, "fill -30 -59 6 30 -55 6 air");
			command(mc, "kill @e[type=minecraft:iron_golem]");
			next();
		}
	}

	/** A golem that appears while hunting can't be attacked until the spawn cooldown (3 s here) has passed. */
	private static void spawnCooldownCheck(Minecraft mc) {
		if (phaseTick == 1) {
			command(mc, "tp @s 0.5 -60 0.5 0 0");
			command(mc, "kill @e[type=minecraft:iron_golem]");
			WigState.config.killSpawnCooldownSeconds = 3;
			WigState.config.killAimVisiblePart = false;
		}
		if (phaseTick == 20) {
			Runner.startHunt();
		}
		if (phaseTick == 40) {
			command(mc, "summon minecraft:iron_golem 0.5 -60 3.5 {CustomName:\"Zealot\",CustomNameVisible:1b,NoAI:1b,Silent:1b,Invulnerable:1b}");
		}
		if (phaseTick > 40 && phaseTick < 200 && !Runner.huntOrder().isEmpty()) {
			log("spawn cooldown: first attack " + (phaseTick - 41) + " ticks after it appeared (cooldown 60)");
			phaseTick = 200;
		}
		if (phaseTick >= 200) {
			Runner.stop("smoke test over");
			WigState.config.killSpawnCooldownSeconds = 1;
			command(mc, "kill @e[type=minecraft:iron_golem]");
			next();
		}
	}

	/** Being teleported during a run raises a macro check; walking and a short push don't. */
	private static void teleportCheck(Minecraft mc) {
		if (phaseTick == 1) {
			command(mc, "tp @s 0.5 -60 0.5 0 0");
			command(mc, "kill @e[type=minecraft:iron_golem]");
		}
		if (phaseTick == 20) {
			Runner.startHunt();
		}
		if (phaseTick == 40) {
			log("teleport: about to tp 20 blocks");
			command(mc, "tp @s 20.5 -60 20.5");
		}
		if (phaseTick == 80) {
			Runner.stop("smoke test over");
			next();
		}
	}

	private static String protLast = "";

	/** The Kill Protectors sequence: message, wait near the spot, protector appears, fight at its feet sneaking, down message, hop about, warp back. */
	/** The Jerry script during a Zealots run: the spawn message, a villager named after the player appears, and it is run to, sneaked at and punched. */
	private static void jerryCheck(Minecraft mc) {
		String name = mc.player.getName().getString();
		if (phaseTick == 1) {
			command(mc, "tp @s 0.5 -60 0.5 0 0");
			command(mc, "kill @e[type=minecraft:villager]");
			WigState.config.jerryKill = true;
			WigState.config.jerryWait = true;
		}
		if (phaseTick == 20) {
			Runner.startZealots();
		}
		if (phaseTick == 60) {
			Runner.onChatLine("\u263a A Purple Jerry appeared!");
		}
		if (phaseTick == 160) {
			command(mc, "summon minecraft:villager 7.5 -60 7.5 {CustomName:\"" + name + "'s Purple Jerry\",CustomNameVisible:1b,NoAI:1b,Silent:1b,Health:3f}");
			// somebody else's Jerry, which must be left alone
			command(mc, "summon minecraft:villager -7.5 -60 7.5 {CustomName:\"Somebody's Green Jerry\",CustomNameVisible:1b,NoAI:1b,Silent:1b}");
		}
		if (phaseTick >= 40 && phaseTick % 20 == 0) {
			long mine = mc.level.getEntitiesOfClass(net.minecraft.world.entity.npc.villager.Villager.class, new net.minecraft.world.phys.AABB(-20, -64, -20, 20, -50, 20),
				v -> v.hasCustomName() && v.getCustomName().getString().startsWith(name) && v.isAlive()).size();
			log("jerry: tick " + phaseTick + ", state " + Runner.jerryStateName() + ", run " + Runner.runningName() + ", me " + fmt(mc) + ", sneaking " + Runner.isSneaking()
				+ ", my jerries alive " + mine);
		}
		if (phaseTick == 500) {
			log("jerry: end, state " + Runner.jerryStateName() + ", running " + Runner.runningName());
			Runner.stop("smoke test over");
			WigState.config.jerryKill = false;
			WigState.config.jerryWait = false;
			command(mc, "kill @e[type=minecraft:villager]");
			command(mc, "tp @s 0.5 -60 0.5 0 0");
			next();
		}
	}

	/** The HUD after a script stops: the timer freezes, the same script carries on with it, another replaces it. */
	private static void hudSessionCheck(Minecraft mc) {
		if (phaseTick == 1) {
			command(mc, "tp @s 0.5 -60 0.5 0 0");
		}
		if (phaseTick == 10) {
			Runner.startZealots();
		}
		if (phaseTick == 110) {
			log("tps: " + Runner.serverTps());
			log("hud: running 5 s in: visible " + Runner.hudVisible() + ", seconds " + Runner.hudSeconds() + ", stats " + Runner.statLines());
			Runner.onChatLine("Your Kill Combo has expired! You reached a 12 Kill Combo!");
			Runner.stop("smoke test");
		}
		if (phaseTick == 130 || phaseTick == 230) {
			log("hud: stopped, tick " + phaseTick + ": visible " + Runner.hudVisible() + ", seconds " + Runner.hudSeconds() + ", running " + Runner.isRunning() + ", stats " + Runner.statLines());
		}
		if (phaseTick == 235) {
			Runner.onChatLine("Your Kill Combo has expired! You reached a 5 Kill Combo!"); // not running: not counted
			Runner.startZealots();
		}
		if (phaseTick == 335) {
			log("hud: same script again, 5 s on: seconds " + Runner.hudSeconds() + " (should be about 10), stats " + Runner.statLines());
			Runner.onChatLine("Your Kill Combo has expired! You reached a 3 Kill Combo!");
			Runner.startHunt(); // another script
		}
		if (phaseTick == 355) {
			log("hud: another script: name " + Runner.hudName() + ", seconds " + Runner.hudSeconds() + " (should be about 1), stats " + Runner.statLines());
			Runner.stop("smoke test over");
			next();
		}
	}

	private static void protectorCheck(Minecraft mc) {
		if (phaseTick == 1) {
			command(mc, "tp @s 0.5 -60 0.5 0 0");
			command(mc, "kill @e[type=minecraft:iron_golem]");
			WigState.zealots.killProtectors = true;
			WigState.zealots.protectorWarpCommand = "say warp drag";
			WigState.zealots.protectorSpots = new double[][] {{40, -60, 40}}; // a decoy: the head says where it really is
			command(mc, "setblock 20 -57 20 minecraft:player_head");
			protLast = "";
		}
		if (phaseTick == 20) {
			Runner.startZealots();
		}
		if (phaseTick == 30) {
			Runner.onChatLine("The ground begins to shake as an End Stone Protector rises from below!");
		}
		if (phaseTick == 200) {
			command(mc, "wig heads");
			photograph(mc, "heads");
		}
		if (phaseTick == 240) {
			command(mc, "summon minecraft:iron_golem 20.5 -60 20.5 {CustomName:\"End Stone Protector\",CustomNameVisible:1b,NoAI:1b,Silent:1b,Invulnerable:1b}");
		}
		if (phaseTick > 30 && !Runner.protectorState().equals(protLast)) {
			protLast = Runner.protectorState();
			log("protector: state " + protLast + " at tick " + phaseTick + ", me " + fmt(mc) + ", sneaking " + Runner.isSneaking());
		}
		if (phaseTick == 480) {
			log("protector: fighting state " + Runner.protectorState() + ", me " + fmt(mc) + ", sneaking " + Runner.isSneaking());
			Runner.onChatLine("                    END STONE PROTECTOR DOWN!");
		}
		if (phaseTick == 520) {
			log("protector: after the down message: " + Runner.protectorState() + ", me " + fmt(mc));
		}
		if (phaseTick == 720) {
			log("protector: end state " + Runner.protectorState() + ", me " + fmt(mc));
			Runner.stop("smoke test over");
			WigState.zealots.killProtectors = false;
			command(mc, "setblock 20 -57 20 minecraft:air");
			WigState.zealots.protectorSpots = new WigConfig().protectorSpots;
			WigState.zealots.protectorWarpCommand = new WigConfig().protectorWarpCommand;
			command(mc, "kill @e[type=minecraft:iron_golem]");
			command(mc, "tp @s 0.5 -60 0.5 0 0");
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
			log("position screen is " + mc.screen.getClass().getSimpleName() + " at " + String.format("%.2f,%.2f scale %.1f", WigState.zealots.hudX, WigState.zealots.hudY, WigState.zealots.hudScale));
			photograph(mc, "hud-position-before");
		}
		if (phaseTick == 20) {
			// grab the box where it is drawn and drag it to the lower right
			int gx = (int) (WigState.zealots.hudX * (mc.getWindow().getGuiScaledWidth() - 90)) + 10;
			int gy = (int) (WigState.zealots.hudY * (mc.getWindow().getGuiScaledHeight() - 30)) + 10;
			var screen = mc.screen;
			var info = new net.minecraft.client.input.MouseButtonInfo(0, 0);
			screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(gx, gy, info), false);
			screen.mouseDragged(new net.minecraft.client.input.MouseButtonEvent(mc.getWindow().getGuiScaledWidth() - 40, mc.getWindow().getGuiScaledHeight() - 30, info), 0, 0);
			screen.mouseReleased(new net.minecraft.client.input.MouseButtonEvent(mc.getWindow().getGuiScaledWidth() - 40, mc.getWindow().getGuiScaledHeight() - 30, info));
			screen.mouseScrolled(0, 0, 0, 5);
		}
		if (phaseTick == 30) {
			log("after the drag and a scroll: " + String.format("%.2f,%.2f scale %.1f", WigState.zealots.hudX, WigState.zealots.hudY, WigState.zealots.hudScale));
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
