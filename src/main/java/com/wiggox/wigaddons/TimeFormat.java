package com.wiggox.wigaddons;

/** How long a route has been running, as shown on the HUD. */
public final class TimeFormat {
	private TimeFormat() {
	}

	/** "01:23" for under an hour, "1:02:03" from an hour on. Negative times count as zero. */
	public static String clock(long totalSeconds) {
		long s = Math.max(0, totalSeconds);
		long hours = s / 3600;
		long minutes = s % 3600 / 60;
		long seconds = s % 60;
		return hours > 0 ? String.format("%d:%02d:%02d", hours, minutes, seconds) : String.format("%02d:%02d", minutes, seconds);
	}
}
