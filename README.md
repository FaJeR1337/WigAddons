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
- `/wig stop`: stop a run.
- `/wig waypoints`: show or hide the selected route's waypoints (numbered boxes joined by lines). Remembered between sessions.
- `/wig clear`: remove every waypoint from the selected route.
- `/wig delete <route>`: delete a route (run it twice within 10 seconds).

## Running

The player sprints forward, turns smoothly toward the next waypoint and jumps when blocked. It does not stop at waypoints: once within 2 blocks of one it starts turning toward the next. A run ends when the last waypoint is reached, or stops at once when:

- any screen opens (chat, inventory, escape, modded screens)
- you press a movement key (forward, back, left, right, jump, sneak)
- you take damage
- you disconnect
- the player barely moves for 3 seconds (stuck)

Movement is a straight line between waypoints: no pathfinding around walls, gaps or water. Automated movement is against the rules on many servers; use it where it is allowed.

## Files

Everything is in `config/WigAddons Routes/`: one `<route>.json` per route (global, not tied to a world) and `config.json` with the settings: `turnAheadDistance` (2.0), `arriveRadius` (1.0), `turnSpeedDegPerTick` (10), `stuckTimeoutSeconds` (3), plus the remembered `showWaypoints` and `selectedRoute`.
