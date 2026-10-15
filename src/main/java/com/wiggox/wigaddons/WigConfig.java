package com.wiggox.wigaddons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Settings saved as config.json next to the routes. Edit the file by hand to tune the movement. */
public final class WigConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** The most the view turns per tick (20 ticks a second) while running. */
	public double turnSpeedDegPerTick = 10.0;
	/** The run stops if the view is turned this far in a tick by anything but the mod (a server rotation check, say). */
	public double rotationStopDegrees = 20.0;
	/**
	 * Each waypoint is aimed at a random spot up to this many blocks from the middle of its block (along each axis),
	 * picked again every run and every lap, so the path isn't a perfect line through block centres. 0 turns it off.
	 */
	public double humanizeOffset = 0.4;
	/** When the view is turned or the hotbar slot changed by something else, the run keeps going this long, with a warning on screen, then stops. */
	public double macroWarningSeconds = 3.0;
	/** How loud the alarm (the wither spawning) is when a macro check is raised, 0 to 300 percent. 0 is silent, 100 is the game's full volume for that sound, and above that the sound is layered on itself to be louder. */
	public double macroSoundVolume = 100.0;
	/** A chat line matching one of these (regular expressions, from the start of the line) means a party member left. */
	public List<String> partyLeavePatterns = new ArrayList<>(PartyLeave.DEFAULT_PATTERNS);
	/** After a party member leaves, the run stops this many seconds later and the command below is sent. */
	public double partyLeaveDelaySeconds = 2.0;
	/** Sent when the run stops because a party member left. Leave empty to only stop. */
	public String partyLeaveCommand = "/warp hub";
	/** Show the route's name and how long it has been running on the HUD while a route runs. */
	public boolean showHud = true;
	public boolean hudShowTimer = true;
	public boolean hudShowIcon = true;
	/** Where the HUD box sits: 0 is the left / top edge of the screen and 1 the right / bottom edge, however big the box is. */
	public double hudX = 0.02;
	public double hudY = 0.30;
	public double hudScale = 1.0;
	/** Following a player: stop walking when this close (blocks), and start again once they are further than the resume distance. */
	public double followStopDistance = 3.0;
	public double followResumeDistance = 5.0;
	/** Sprint when they are at least this far away, walk when nearer. */
	public double followSprintDistance = 6.0;
	/** After losing sight of the player, wait this long at the last place they were seen, then stop. */
	public double followLostSeconds = 5.0;
	/** The view looks at a random spot within this many blocks of the player's head, picked again every second or two. */
	public double followLookRadius = 0.5;
	/** Draw the trail being followed in the world, for debugging. */
	/** Seconds between looking at the followed player again; the view holds still in between. 0 keeps the view on them all the time. */
	public double followLookInterval = 0;
	/** About how many ticks following takes to settle a turn of the view; the turning eases in and out. 0 turns at full speed. */
	public double followTurnSmoothing = 4;
	public boolean followShowTrail = false;
	/** Hunting (/wig kill): what to go for, and what to go for first. A name matches when it contains this text, ignoring case. */
	public String killName = "Zealot";
	public String killSpecialName = "Special Zealot";
	/** How far away to look for something to hunt, in blocks. */
	public double killSearchRadius = 40;
	/** How close the target has to be, in blocks, for the right click. */
	public double killRange = 5;
	/** After attacking, the target is treated as dead and left alone for a random time between these, in seconds. */
	public double killMarkMinSeconds = 5;
	public double killMarkMaxSeconds = 10;
	/** How long the right click button is held per attack, in ticks. */
	public int killClickTicks = 3;
	/** How many right clicks a special mob gets before it is assumed dead and left alone, since it usually takes more than one. */
	public int killSpecialClicks = 3;
	/** A mob that has just appeared can't be targeted for this many seconds. */
	public double killSpawnCooldownSeconds = 1;
	/** The longest fall, in blocks, that hunting takes as a shortcut instead of walking a long way round. Falls over 3 blocks hurt. */
	public int killMaxDrop = 6;
	/**
	 * Name tags are separate entities the server may not send, or only send when close. With this on, every mob of the
	 * type below within the search radius counts as hunted, name tag or not. (It can't tell a special one apart without its tag.)
	 */
	public boolean killByType = false;
	/** When this chat message arrives while hunting, stand and look around while jumping for a few seconds before going for it. Blank turns it off. */
	public String killReactMessage = "A special Zealot has spawned nearby!";
	public double killReactMinSeconds = 2;
	public double killReactMaxSeconds = 3;
	/** After this long, a special mob already in sight within the angle below of where the view points ends the looking around early. */
	public double killReactEarlySeconds = 1;
	public double killReactSeeAngle = 60;
	/** Draws the mod's own name tag and box over every mob it is hunting, through walls and from far away. */
	/** Like followTurnSmoothing, for hunting: about how many ticks the view takes to settle a turn. 0 turns at full speed. */
	public double killTurnSmoothing = 4;
	/** Hunting's own fastest turn, in degrees per tick, apart from the default turn speed that routes and following use. */
	public double killTurnSpeed = 10.0;
	/** While sidestepping with A or D with the target within killPreciseAngle of the crosshair: aim straight at it with this little turn smoothing, so the view doesn't lag behind. */
	public boolean killPreciseAim = true;
	public double killCloseSmoothing = 0;
	public double killPreciseAngle = 25;
	public boolean killShowTags = true;
	/** How big those name tags are, the same at any distance (they shrink with distance like any name tag). About 0.45 is the size of an ordinary name tag. */
	public double killTagScale = 0.45;
	public String killType = "minecraft:enderman";
	/** The least time between one right click and the next, in seconds: set it to the weapon's cooldown. A little extra is added at random so it isn't clockwork. */
	public double killClickCooldownSeconds = 0.5;
	/** How much bigger than the target's hitbox counts as looking at it, in blocks. */
	public double killAimMargin = 0.3;
	/** Targets that would need the view pitched up steeper than this many degrees (a pitch from minus this to -90) are ignored. 90 or more ignores none. */
	public double killMaxLookUp = 65;
	/** Go for the target nearest to where the view already points, so the view turns as little as possible; off goes for the nearest to you. */
	public boolean killPickByCrosshair = true;
	/** A target more than this many degrees round to either side counts as behind you. Those wait while there are others, and when they are all there is, for the delay below, so the view doesn't spin round at once. */
	public double killBehindAngle = 100;
	public double killBehindDelaySeconds = 1.5;
	/** How much worse a target counts for being above you: this many extra degrees (blocks when picking by distance) per degree (block) above level. 0 ignores height. */
	public double killUpPenalty = 1.5;
	/** Only right click when nothing solid is between you and where the view meets the target. */
	public boolean killRequireSight = true;
	/** Test option: when only one part of a mob can be seen (head, body or legs, the rest behind something), aim at that part. */
	public boolean killAimVisiblePart = false;
	/** With two or more targets in sight within click range, stand still and deal with them from where you are, until the last one is marked. */
	public boolean killStandWhenCrowded = false;
	/** Standing still gives up after this many seconds, in case nothing manages to get clicked. */
	public double killStandLimitSeconds = 15;
	/** Where to go when there is nothing to hunt (/wig kill home), and whether to wander around it until something turns up. */
	public boolean killHomeSet = false;
	public double killHomeX;
	public double killHomeY;
	public double killHomeZ;
	public boolean killWander = true;
	/** While walking to the home spot or wandering, the view looks around on its own and the keys (W, A, S, D) are whatever the way needs, instead of the view always facing where it is going. */
	public boolean killIdleLookAround = true;
	public double killWanderRadius = 6;
	/** Stop looking at the followed player while a wall is between you, and look where you are walking instead. */
	public boolean followLookOnlyWhenVisible = false;
	/** Never jump while following. */
	public boolean followNeverJump = false;
	/** How much a step up that needs a jump costs the path finder, in blocks of walking. The higher it is, the further it will go round to avoid jumping. */
	public double pathJumpCost = 12.0;
	/** The run stops if the player has hardly moved for this long. */
	public double stuckTimeoutSeconds = 3.0;

	/** The selected route, remembered between sessions. */
	public String selectedRoute = "";

	/** A copy of these settings with a different aim offset, for a route that has its own. */
	public WigConfig withHumanizeOffset(double blocks) {
		WigConfig copy = GSON.fromJson(GSON.toJson(this), WigConfig.class);
		copy.humanizeOffset = blocks;
		return copy;
	}

	public static WigConfig load(Path file) {
		try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			WigConfig loaded = GSON.fromJson(in, WigConfig.class);
			return loaded == null ? new WigConfig() : loaded.sanitised();
		} catch (IOException | RuntimeException e) {
			return new WigConfig();
		}
	}

	public void save(Path file) throws IOException {
		Files.createDirectories(file.getParent());
		try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			GSON.toJson(this, out);
		}
	}

	/** Puts hand-edited nonsense back to the defaults so the follower can't divide by zero or spin. */
	private WigConfig sanitised() {
		WigConfig d = new WigConfig();
		if (!(turnSpeedDegPerTick > 0)) {
			turnSpeedDegPerTick = d.turnSpeedDegPerTick;
		}
		if (!(rotationStopDegrees > 0)) {
			rotationStopDegrees = d.rotationStopDegrees;
		}
		if (!(humanizeOffset >= 0)) {
			humanizeOffset = d.humanizeOffset;
		}
		if (!(macroSoundVolume >= 0)) {
			macroSoundVolume = d.macroSoundVolume;
		}
		macroSoundVolume = Math.min(300.0, macroSoundVolume);
		if (!(macroWarningSeconds >= 0)) {
			macroWarningSeconds = d.macroWarningSeconds;
		}
		if (partyLeavePatterns == null) {
			partyLeavePatterns = d.partyLeavePatterns;
		}
		if (!(partyLeaveDelaySeconds >= 0)) {
			partyLeaveDelaySeconds = d.partyLeaveDelaySeconds;
		}
		if (partyLeaveCommand == null) {
			partyLeaveCommand = "";
		}
		hudX = Math.max(0, Math.min(1, Double.isNaN(hudX) ? d.hudX : hudX));
		hudY = Math.max(0, Math.min(1, Double.isNaN(hudY) ? d.hudY : hudY));
		hudScale = Double.isNaN(hudScale) ? d.hudScale : Math.max(0.5, Math.min(4.0, hudScale));
		if (!(followStopDistance >= 0)) {
			followStopDistance = d.followStopDistance;
		}
		if (!(followResumeDistance > followStopDistance)) {
			followResumeDistance = followStopDistance + 2.0;
		}
		if (!(followSprintDistance >= 0)) {
			followSprintDistance = d.followSprintDistance;
		}
		if (!(followLostSeconds >= 0)) {
			followLostSeconds = d.followLostSeconds;
		}
		if (killName == null) {
			killName = d.killName;
		}
		if (killSpecialName == null) {
			killSpecialName = d.killSpecialName;
		}
		if (!(killSearchRadius > 0)) {
			killSearchRadius = d.killSearchRadius;
		}
		if (!(killRange > 0)) {
			killRange = d.killRange;
		}
		if (!(killMarkMinSeconds >= 0)) {
			killMarkMinSeconds = d.killMarkMinSeconds;
		}
		if (!(killMarkMaxSeconds >= killMarkMinSeconds)) {
			killMarkMaxSeconds = killMarkMinSeconds;
		}
		if (!(killClickCooldownSeconds >= 0)) {
			killClickCooldownSeconds = d.killClickCooldownSeconds;
		}
		if (!(killTurnSmoothing >= 0)) {
			killTurnSmoothing = d.killTurnSmoothing;
		}
		if (!(killBehindAngle > 0)) {
			killBehindAngle = d.killBehindAngle;
		}
		if (!(killBehindDelaySeconds >= 0)) {
			killBehindDelaySeconds = d.killBehindDelaySeconds;
		}
		if (!(pathJumpCost >= 0)) {
			pathJumpCost = d.pathJumpCost;
		}
		if (!(killUpPenalty >= 0)) {
			killUpPenalty = d.killUpPenalty;
		}
		if (!(killTurnSpeed > 0)) {
			killTurnSpeed = d.killTurnSpeed;
		}
		if (!(killCloseSmoothing >= 0)) {
			killCloseSmoothing = d.killCloseSmoothing;
		}
		if (!(killPreciseAngle > 0)) {
			killPreciseAngle = d.killPreciseAngle;
		}
		if (!(killTagScale > 0)) {
			killTagScale = d.killTagScale;
		}
		if (killReactMessage == null) {
			killReactMessage = d.killReactMessage;
		}
		if (!(killReactMinSeconds >= 0)) {
			killReactMinSeconds = d.killReactMinSeconds;
		}
		if (!(killReactMaxSeconds >= killReactMinSeconds)) {
			killReactMaxSeconds = killReactMinSeconds;
		}
		if (!(killReactEarlySeconds >= 0)) {
			killReactEarlySeconds = d.killReactEarlySeconds;
		}
		if (!(killReactSeeAngle > 0)) {
			killReactSeeAngle = d.killReactSeeAngle;
		}
		if (killType == null || killType.isBlank()) {
			killType = d.killType;
		}
		if (!(killSpawnCooldownSeconds >= 0)) {
			killSpawnCooldownSeconds = d.killSpawnCooldownSeconds;
		}
		if (killMaxDrop < 0) {
			killMaxDrop = d.killMaxDrop;
		}
		if (killSpecialClicks < 1) {
			killSpecialClicks = d.killSpecialClicks;
		}
		if (killClickTicks < 1) {
			killClickTicks = d.killClickTicks;
		}
		if (!(killWanderRadius >= 0)) {
			killWanderRadius = d.killWanderRadius;
		}
		if (!(killStandLimitSeconds > 0)) {
			killStandLimitSeconds = d.killStandLimitSeconds;
		}
		if (!(killMaxLookUp > 0)) {
			killMaxLookUp = d.killMaxLookUp;
		}
		if (!(killAimMargin >= 0)) {
			killAimMargin = d.killAimMargin;
		}
		if (!(followTurnSmoothing >= 0)) {
			followTurnSmoothing = d.followTurnSmoothing;
		}
		if (!(followLookInterval >= 0)) {
			followLookInterval = d.followLookInterval;
		}
		if (!(followLookRadius >= 0)) {
			followLookRadius = d.followLookRadius;
		}
		if (!(stuckTimeoutSeconds > 0)) {
			stuckTimeoutSeconds = d.stuckTimeoutSeconds;
		}
		if (selectedRoute == null) {
			selectedRoute = "";
		}
		return this;
	}
}
