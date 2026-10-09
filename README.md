# WigAddons

A client-side Fabric mod for Minecraft 26.1.2. Record routes of waypoints with right-clicks, then have the player run them automatically.

## Requirements

- Minecraft 26.1.2, Fabric Loader 0.19.5 or newer, Java 25
- [Fabric API](https://modrinth.com/mod/fabric-api) 0.155.3+26.1.2 or newer

Build with `./gradlew build` inside this folder. The jar is in `build/libs/`.

## The settings screen

`/wig` (or `/wig gui`) opens a settings screen built with [MoulConfig](https://github.com/NotEnoughUpdates/MoulConfig) (LGPL-3.0, bundled inside the mod jar together with the Kotlin runtime it needs). There is also an **Open GUI** key (unbound until you set it) in Controls under a **WigAddons** heading.

- **About**: what the mod is.
- **Routes**: your finished routes (ones with at least one waypoint), each with its icon, name, waypoint count and **Start** and **Loop** buttons. The buttons close the screen and run the route.
- **Edit Routes**: *Defaults for all routes* (turn speed and aim offset, used by routes that don't set their own), *New route* (type a name and press Create), then one section per route. A route's section has its icon at the end of its header and a row of tabs, so only one part of a route shows at a time:
  - **General**: the icon (type part of an item's name, see the match, press **Use**), a button to place waypoints with right-click, and Delete route (press twice within 10 seconds).
  - **Waypoints**: each waypoint's "x y z" is a text field with Up, Down and Del buttons, plus buttons to add the block you were looking at or standing on.
  - **Movement**: radius, turn speed (empty = default), pitch to hold, **Never jump**, hold left / right click.
  - **Humanize**: aim offset (empty = default) and whether the held pitch varies.
  - **Display**: show the waypoints, their numbers, radius circles and lines, and the box colour. Several routes can be shown at once.
- **Follow**: the distances, look radius, lost-sight wait and never-jump setting for `/wig follow`.
- **HUD**: a box on screen, while a route runs, with the route's icon, its name and how long it has been running (`01:23`, or `1:02:03` after an hour, with "(loop)" when looping). Switch the whole box, the timer and the icon on or off, set its scale, and press **Edit** under Position to open a screen where you drag the box where you want it (scroll to resize, R to reset, Esc to finish). Its place is kept as a share of the screen, so it stays put if the window size changes.
- **Safety**: macro-check limits, the warning time, the stuck timeout, and what to do when a party member leaves (delay, command and the chat lines that count).

Everything saves as you change it. Opening any screen stops a running route, so a route can't be running while the screen is open; the Run buttons close the screen first. Everything in the screen is also a command.

## Commands

`/wig` is a short form of `/wigaddons`.

- `/wig new <route>`: create a route, select it and start editing it. Names use letters, numbers, `_` and `-`.
- `/wig edit <route>`: select a route and edit it (adds waypoints at the end).
- While editing, **right-click a block with an empty hand** to add it as the next waypoint. A subtitle reminds you. `/wig done` leaves edit mode.
- `/wig select <route>`: select a route without editing.
- `/wig routes`: list routes. `/wig routes <route>`: list that route's waypoints.
- `/wig start <route>`: run the route from waypoint 1 to the last. `/wig start <route> loop` goes back to waypoint 1 and never ends.
- `/wig radius <route> <blocks>`: how close the player must get to each waypoint of that route before turning toward the next one. `0` means the middle of the block, `1` means within one block, and so on (up to 32; new routes start at 2). It is saved with the route and drawn as an orange circle on top of each block while waypoints are shown.
- `/wig facing <route> <pitch> [randomize]`: hold an up/down view angle while that route runs, from -90 (straight up) through 0 (level) to 90 (straight down). The view eases to it at the normal turn speed. With `randomize`, the angle is picked within 2 degrees of the value and picked again at every waypoint. `/wig facing <route> off` goes back to leaving the pitch alone. Saved with the route; running the command again replaces the old setting (leave `randomize` off to turn it off).
- `/wig left <route> on|off` and `/wig right <route> on|off`: hold the left (attack / break blocks) or right (use / place) mouse button for as long as that route runs, and let go when it ends for any reason. Saved with the route; both can be on at once. Holding a button breaks blocks or uses items over and over; it does not click once per swing at an entity.
- `/wig hud`: switch the HUD box on or off. `/wig hud position` opens the screen for dragging it.
- `/wig nojump <route> on|off`: force a route never to jump (see below). Off by default.
- `/wig trail`: show or hide the trail being followed, drawn in the world while you follow (for debugging).
- `/wig follow <player>` (or `/wig follow` while looking at a player): follow another player. `/wig stop` ends it. See "Following a player" below.
- `/wig debug`: show or hide the turn readout.
- `/wig stop`: stop a run.
- `/wig waypoints`: show or hide the selected route's waypoints (numbered boxes joined by lines; the numbers show through walls). This is the route's "Show waypoints" setting and is saved with it.
- `/wig clear`: remove every waypoint from the selected route.
- `/wig delete <route>`: delete a route (run it twice within 10 seconds).

## Running

The player sprints forward and jumps when blocked. The view turns toward the aim point at up to 10 degrees per tick, updated every rendered frame so it stays smooth at any frame rate. The aim point is the next waypoint, but not its exact middle: each waypoint is aimed at a random spot up to `humanizeOffset` (0.4) blocks from the middle in each direction, picked again every run and every lap. A radius of `0` means the exact middle, so it turns the offset off for that route. Once you are within the route's radius of that spot (see `/wig radius`), the aim point slides toward the next waypoint's spot, so the path bends round corners instead of making a hard turn. The player never stops at waypoints. A run ends when the last waypoint is reached.

While a route runs **the mouse is locked** (mouse movement is thrown away, not saved up).

A run stops at once when:

- any screen opens (chat, inventory, escape, modded screens)
- you press a movement key (forward, back, left, right, jump, sneak)
- a party member leaves (see below)
- you disconnect
- the player barely moves for 3 seconds (stuck)

### Macro checks

Two things a server might use to catch a macro raise a **macro check**: the selected hotbar slot changing (scrolling, number keys, or the server), and the view being turned by 20 degrees or more in yaw or pitch by anything but this mod (a server rotation check, for example; the limit is `rotationStopDegrees`). The run does **not** stop at once: it carries on for `macroWarningSeconds` (3) while a red **WARNING** title with the reason as its subtitle shows on screen, and a line goes to chat, and then it stops. Minecraft has no sideways tilt (roll) for the player, so only yaw (left/right) and pitch (up/down) are checked.

`/wig debug` toggles a readout above the hotbar while a route runs: the current and wanted yaw, how far the view turned this frame, the turn per tick, FPS, and the waypoint, its distance and the blend toward the next one.

### Party members leaving

If a chat line from the server says someone left your party (for example `Name has left the party.`, `Name was removed from the party...`, `The party was disbanded...`) while a route is running, the run stops `partyLeaveDelaySeconds` (2) later and sends `partyLeaveCommand` (`/warp hub`). If the run has already ended by then (you stopped it, it finished, or a macro check stopped it) nothing is sent. The lines are matched from their start with the regular expressions in `partyLeavePatterns`, so a player typing the same words in chat doesn't count. Set `partyLeaveCommand` to an empty string to only stop.

### Following a player

`/wig follow <player>` (names complete from the players near you), or `/wig follow` while looking at a player, makes you walk the path that player walks, a few blocks behind them. Within about six blocks of them you walk straight at them; further back you follow the trail of places they stood, so you go round whatever they went round. You stop when you are within `followStopDistance` (3) blocks of them and start again only once they are more than `followResumeDistance` (5) away, so you don't keep starting and stopping. You sprint when they are `followSprintDistance` (6) or more away and walk when nearer, and you jump when you are blocked or they are higher (unless `followNeverJump` is on). The view eases into and out of every turn while following: `followTurnSmoothing` (**Turn smoothing** on the Follow tab, 4 ticks by default, 0 for no easing) is about how long a turn takes to settle, still limited by the turn speed. With `followLookInterval` (**Look again every** on the Follow tab, in seconds, 0 by default) the view looks at them only that often, give or take a quarter, and holds still in between instead of tracking them every tick. Your view looks at a random spot within `followLookRadius` (0.5) blocks of their head, picked again every second or two, and you walk with W, A, S and D relative to where you are looking, so you strafe or back up when the way you need to go isn't straight ahead of your view. If the player curves and the straight way is clear you cut across the curve. With **Stop looking behind walls** on (Follow tab, or `/wig lookwall on|off`; off by default) you stop looking at them while a wall is between you and look where you are walking instead, turning back smoothly once they have been in sight for half a second. If a wall is in the way you plan a route around it over the block grid (steps up one block unless Never jump is on, drops of up to two) and walk that, replanning every half second.

## Hunting mobs

`/wig kill` hunts the mobs named in the **Hunt** tab (`killName`, "Zealot" by default, which also matches "Zealot Bruiser" and any other name containing it, ignoring capitals). Special ones (`killSpecialName`, "Special Zealot") are gone for first, however far away within the search radius (`killSearchRadius`, 40 blocks). The name is read from the mob itself or from the name tag stand floating above it. It uses the same walking, path finding around walls and eased turning as following. You walk up to the target, keep the view on it, and once you are looking at its hitbox (made `killAimMargin` bigger) within `killRange` (5) blocks you hold right click for `killClickTicks` (3) ticks. `/wig kill home` sets the spot you are standing on as home (`/wig kill home clear` removes it). While nothing is in range you walk back there if you are further than the wander radius (`killWanderRadius`, 6) plus 2 blocks, and with `killWander` on you stroll to random open spots 3 or more blocks away within that radius, resting one to four seconds between them, until a mob turns up. The home spot is drawn as a green box while hunting. Between one right click and the next it waits at least `killClickCooldownSeconds` (0.5, a little more at random), so it can be set to a weapon's cooldown; it keeps aiming and moving on meanwhile. Among the targets of the same kind it goes for the one nearest to where the view already points (`killPickByCrosshair`, on by default), so the view turns as little as possible and doesn't spin round; off picks the nearest to you. Targets that would need the view pitched up steeper than `killMaxLookUp` (65) degrees, a pitch between -65 and -90, are ignored, so the view never points almost straight up at something it can't find (90 turns this off). With `killStandWhenCrowded` on (**Stand still when crowded**, off by default), as soon as two or more targets are in sight within click range you stop walking and turn and click from where you stand until the last of them is marked (giving up after `killStandLimitSeconds`, 15 by default, if nothing gets clicked). With the depth check on (`killRequireSight`, on by default) mobs in sight are always chosen before ones behind walls (a special one behind a wall does not beat a normal one in sight); with none in sight it still walks toward one behind a wall, but doesn't look at it (the view goes where you are walking) or click until it is in sight, with a short grace of about 0.75 seconds so stepping past a corner doesn't make the target flicker, and it only clicks when no solid block is between your eyes and where the view meets the mob. The mob is then assumed dead and ignored for a random 5 to 10 seconds (`killMarkMinSeconds`, `killMarkMaxSeconds`) while you go for the next one. With nothing in range you stand still and wait. A target you can't reach (stuck) is skipped the same way. `/wig stop`, a movement key, a screen, a macro check or a party change stop it like any run.

If you lose sight of them (they leave range, teleport, die or log off) you walk to where they were last seen, wait `followLostSeconds` (5), then stop with a message; if they come back in that time you carry on. It stops for the same reasons a route does: a screen opening, a movement key, a macro check, a party member leaving, being stuck, or `/wig stop` (taking damage does not stop it). The HUD shows "Following Name", how long, and how far away they are. To see what it is doing, switch on **Show trail** (Follow tab, or `/wig trail`): orange dots and lines mark where the player walked, a green dot the point you are walking to (with a cyan line from your feet to it), and a red dot where they were last seen. All the numbers are in `config.json` and on the **Follow** tab.

### Going round obstacles

The player looks about 3.5 blocks ahead. A wall two or more blocks high, or a step with a low ceiling, can't be jumped, so the player holds **A or D** (with forward and sprint still held and the view still on the waypoint) to go round it. It picks the side toward the waypoint when that side is open (otherwise the other side, or the shorter way round when the waypoint is dead ahead), and lets go as soon as the line ahead has been clear for 2 blocks, so a one-block obstacle costs only a short step sideways. A one-block step is walked round too when the waypoint being run to is at the same height as the previous one (or as where the run began, for the first waypoint), because there is no reason to climb; when the route goes up or down, it is jumped. A step wall too wide to walk round is jumped instead of stopping the run.

**Never jump:** a route can be set never to jump at all (Movement tab, or `/wig nojump <route> on`). On such a route the mod makes no jump of its own, whatever the heights: one-block steps are always walked round with A or D, and if there is no way round the run stops with a message instead of jumping. Leave it off for routes that climb or need to jump. Presses start after a small random delay and end after a slightly varying hold. Being pushed against something for about half a second counts as an obstacle too (a fence you can't jump, say). If it has gone about 6 blocks sideways, or both sides are boxed in, the run stops with a message. It does not look for hazards such as lava, drops or water.

Movement is otherwise a straight line between waypoints: no full pathfinding around gaps or water. Automated movement is against the rules on many servers; use it where it is allowed.

## Files

Everything is in `config/WigAddons Routes/`: one `<route>.json` per route (global, not tied to a world; it also stores that route's radius) and `config.json` with the settings: `turnSpeedDegPerTick` (10), `rotationStopDegrees` (20), `macroWarningSeconds` (3), `partyLeaveDelaySeconds` (2), `partyLeaveCommand` (`/warp hub`), `partyLeavePatterns`, `humanizeOffset` (0.4), `stuckTimeoutSeconds` (3), plus the remembered `showWaypoints` and `selectedRoute`.

## Development

`./gradlew runClient -Dwigaddons.smoke=true` (set the property through `JAVA_TOOL_OPTIONS` or the run configuration) runs a scripted check of the real game: it makes a flat world, photographs the settings screen and the waypoint boxes, runs routes in the open, round a pillar and over a step, triggers a macro check and a party-leave line, and quits. Everything it prints starts with `[SMOKE]`; pictures go to `run/screenshots`. It does nothing unless that property is set.
