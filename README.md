# WigAddons

A client-side Fabric mod for Minecraft 26.1.2. Record routes of waypoints with right-clicks, then have the player run them automatically.

## Requirements

- Minecraft 26.1.2, Fabric Loader 0.19.5 or newer, Java 25
- [Fabric API](https://modrinth.com/mod/fabric-api) 0.155.3+26.1.2 or newer

Build with `./gradlew build` inside this folder. The jar is in `build/libs/`.

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
- `/wig debug`: show or hide the turn readout.
- `/wig stop`: stop a run.
- `/wig waypoints`: show or hide the selected route's waypoints (numbered boxes joined by lines; the numbers show through walls). Remembered between sessions.
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

### Going round obstacles

The player looks about 3.5 blocks ahead. A wall two or more blocks high, or a step with a low ceiling, can't be jumped, so the player holds **A or D** (with forward and sprint still held and the view still on the waypoint) to go round it. It picks the side toward the waypoint when that side is open (otherwise the other side, or the shorter way round when the waypoint is dead ahead), and lets go as soon as the line ahead has been clear for 2 blocks, so a one-block obstacle costs only a short step sideways. A one-block step is walked round too when the waypoint being run to is at the same height as the previous one (or as where the run began, for the first waypoint), because there is no reason to climb; when the route goes up or down, it is jumped. A step wall too wide to walk round is jumped instead of stopping the run. Presses start after a small random delay and end after a slightly varying hold. Being pushed against something for about half a second counts as an obstacle too (a fence you can't jump, say). If it has gone about 6 blocks sideways, or both sides are boxed in, the run stops with a message. It does not look for hazards such as lava, drops or water.

Movement is otherwise a straight line between waypoints: no full pathfinding around gaps or water. Automated movement is against the rules on many servers; use it where it is allowed.

## Files

Everything is in `config/WigAddons Routes/`: one `<route>.json` per route (global, not tied to a world; it also stores that route's radius) and `config.json` with the settings: `turnSpeedDegPerTick` (10), `rotationStopDegrees` (20), `macroWarningSeconds` (3), `partyLeaveDelaySeconds` (2), `partyLeaveCommand` (`/warp hub`), `partyLeavePatterns`, `humanizeOffset` (0.4), `stuckTimeoutSeconds` (3), plus the remembered `showWaypoints` and `selectedRoute`.
