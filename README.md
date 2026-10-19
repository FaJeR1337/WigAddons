# WigAddons

A client-side Fabric mod for Minecraft 26.1.2 and 26.2, by Wiggox.

## Install

Put the jar for your game version from `downloads/` in your mods folder, with Fabric Loader and the matching Fabric API:

- `wigaddons-26.1.2.jar` with Fabric API 0.155.3+26.1.2
- `wigaddons-26.2.jar` with Fabric API 0.161.0+26.2

## Commands

`/wig` opens the settings. `/wig stop` stops whatever is running. Everything else is in the settings screen.

## Build

`./gradlew clean build -Ptarget=26.1.2` or `-Ptarget=26.2`. Needs Java 25.
