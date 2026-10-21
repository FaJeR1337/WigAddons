package com.wiggox.wigaddons;

/**
 * Estimates the server's ticks per second from the time updates it sends (one every 20 ticks, so one a second when all is well):
 * how many ticks passed between two of them, over how long it took them to arrive.
 */
public final class TpsTracker {
	/** Below this many ticks a second, the server counts as lagging. */
	public static final double LOW_TPS = 18.0;
	/** No time update for this long means the server is behind, or the connection is. */
	private static final long STALE_NANOS = 3_000_000_000L;
	/** Updates closer together than this are bunched up by the connection and say nothing about the speed. */
	private static final long MIN_INTERVAL_NANOS = 400_000_000L;

	private long lastTicks = Long.MIN_VALUE;
	private long lastNanos;
	private double tps = 20.0;

	/** A time update arrived: the server's tick count in it, and when (System.nanoTime). */
	public synchronized void onTime(long gameTime, long nowNanos) {
		if (lastTicks != Long.MIN_VALUE && gameTime > lastTicks && nowNanos - lastNanos >= MIN_INTERVAL_NANOS) {
			double reading = Math.min(20.0, (gameTime - lastTicks) * 1e9 / (nowNanos - lastNanos));
			tps = tps * 0.4 + reading * 0.6;
		} else if (lastTicks != Long.MIN_VALUE && gameTime <= lastTicks) {
			tps = 20.0; // the time was set back: another world, start again
		} else if (lastTicks != Long.MIN_VALUE && nowNanos - lastNanos < MIN_INTERVAL_NANOS) {
			return; // bunched up with the last one: keep the old mark so the next one measures from there
		}
		lastTicks = gameTime;
		lastNanos = nowNanos;
	}

	public synchronized void reset() {
		lastTicks = Long.MIN_VALUE;
		tps = 20.0;
	}

	public synchronized double tps() {
		return tps;
	}

	/** Whether the server's ticking has dropped, or its updates have stopped coming. */
	public synchronized boolean low(long nowNanos) {
		if (lastTicks == Long.MIN_VALUE) {
			return false; // nothing measured yet
		}
		return tps < LOW_TPS || nowNanos - lastNanos > STALE_NANOS;
	}
}
