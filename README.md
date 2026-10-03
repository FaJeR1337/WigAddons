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
- the selected hotbar slot changes (scrolling, number keys, or the server)
- the view is turned by 20 degrees or more, in yaw or pitch, by anything but this mod (a server rotation check, for example). The limit is `rotationStopDegrees` in `config.json`.
- you take damage
- you disconnect
- the player barely moves for 3 seconds (stuck)

Minecraft has no sideways tilt (roll) for the player, so only yaw (left/right) and pitch (up/down) are checked.

`/wig debug` toggles a readout above the hotbar while a route runs: the current and wanted yaw, how far the view turned this frame, the turn per tick, FPS, and the waypoint, its distance and the blend toward the next one.

Movement is a straight line between waypoints: no pathfinding around walls, gaps or water. Automated movement is against the rules on many servers; use it where it is allowed.

## Files

Everything is in `config/WigAddons Routes/`: one `<route>.json` per route (global, not tied to a world; it also stores that route's radius) and `config.json` with the settings: `turnSpeedDegPerTick` (10), `rotationStopDegrees` (20), `humanizeOffset` (0.4), `stuckTimeoutSeconds` (3), plus the remembered `showWaypoints` and `selectedRoute`.
