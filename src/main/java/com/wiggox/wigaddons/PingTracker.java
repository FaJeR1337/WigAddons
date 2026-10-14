package com.wiggox.wigaddons;

/** The average ping over the session, to tell a macro check that came with a laggy connection from one that didn't. */
public final class PingTracker {
	/** Fewest samples before the average means anything. */
	static final int MIN_SAMPLES = 10;
	/** A ping counts as high once it is this much above the average (20 percent), so ordinary ups and downs aren't. */
	static final double MARGIN = 1.2;

	private long total;
	private int count;

	public void reset() {
		total = 0;
		count = 0;
	}

	public void add(int pingMillis) {
		if (pingMillis > 0) {
			total += pingMillis;
			count++;
		}
	}

	public int samples() {
		return count;
	}

	/** The average so far, or 0 when there are no samples. */
	public double average() {
		return count == 0 ? 0 : (double) total / count;
	}

	/** Whether this ping is well above the session average (never, until enough has been sampled to have one). */
	public boolean above(int pingMillis) {
		return count >= MIN_SAMPLES && pingMillis > average() * MARGIN;
	}
}
