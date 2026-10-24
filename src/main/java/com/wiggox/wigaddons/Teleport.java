package com.wiggox.wigaddons;

/** Tells being teleported from moving: servers can move a player a long way in one tick, nothing the player does can. */
public final class Teleport {
	/** Moved further than this in one tick, and not by falling or knockback, is a teleport. */
	public static final double BLOCKS = 5.0;

	private Teleport() {
	}

	/**
	 * @param moved how far the player is from where it was a tick ago, in blocks
	 * @param previousSpeed how fast the player was moving a tick ago, in blocks per tick (a long fall is fast, and not a teleport)
	 */
	public static boolean teleported(double moved, double previousSpeed) {
		return moved > BLOCKS && moved > previousSpeed * 1.5 + 1.5;
	}
}
