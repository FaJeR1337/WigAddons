package com.wiggox.wigaddons;

/**
 * Eases the view toward where it should point: the turning speeds up and slows down gradually instead of jumping to full
 * speed and stopping dead, which is what makes a cursor look snappy.
 */
public final class ViewSmoother {
	private double velocity;

	public void reset() {
		velocity = 0;
	}

	/**
	 * @param diff how far there is left to turn, in degrees (positive or negative)
	 * @param frameTicks the time since the last frame, in ticks
	 * @param smoothing about how many ticks the turn takes to settle; 0 turns at full speed with no easing
	 * @param maxPerTick the fastest the view may turn
	 * @return how many degrees to turn this frame
	 */
	public double step(double diff, double frameTicks, double smoothing, double maxPerTick) {
		if (frameTicks <= 0) {
			return 0;
		}
		if (smoothing <= 0) {
			velocity = 0;
			return RouteFollower.clamp(diff, -maxPerTick * frameTicks, maxPerTick * frameTicks);
		}
		double wanted = RouteFollower.clamp(diff / smoothing, -maxPerTick, maxPerTick);
		velocity += (wanted - velocity) * (1 - Math.exp(-frameTicks / smoothing));
		double step = velocity * frameTicks;
		if (Math.abs(step) > Math.abs(diff)) {
			step = diff; // never swing past the aim
			velocity = diff / frameTicks;
		}
		return step;
	}
}
