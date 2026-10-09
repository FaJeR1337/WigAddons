package com.wiggox.wigaddons;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** Chooses which mob to go for next: specials first, then the nearest, never one that was just attacked. */
public final class ZealotHunter {
	/** A mob that could be hunted. */
	public record Mob(int id, String name, double x, double y, double z, double eyeY) {
	}

	/** Mob id to the tick until which it counts as dead. */
	private final Map<Integer, Long> deadUntil = new HashMap<>();
	private int currentId = -1;

	/** Whether the name contains the wanted text, ignoring case, so "Zealot" also finds "Zealot Bruiser". Blank wants nothing. */
	public static boolean matches(String name, String wanted) {
		if (name == null || wanted == null || wanted.isBlank()) {
			return false;
		}
		return name.toLowerCase(Locale.ROOT).contains(wanted.trim().toLowerCase(Locale.ROOT));
	}

	/**
	 * Whether the mob is so far above the eyes that it would take looking up steeper than the limit (a pitch between
	 * -limit and -90). Those are left alone: the view can't reasonably be held that high, and the mob is never found.
	 */
	public static boolean tooSteepUp(double px, double eyeY, double pz, Mob mob, double maxUpDegrees) {
		if (maxUpDegrees >= 90) {
			return false;
		}
		return FollowPlanner.pitchToward(px, eyeY, pz, mob.x(), mob.eyeY(), mob.z()) < -maxUpDegrees;
	}

	/** Assumes the mob is going to die: it is left alone for a random time between the two limits, in seconds. */
	public void markDead(int id, long nowTick, double minSeconds, double maxSeconds, Random random) {
		double lo = Math.min(minSeconds, maxSeconds);
		double hi = Math.max(minSeconds, maxSeconds);
		double seconds = lo + random.nextDouble() * (hi - lo);
		deadUntil.put(id, nowTick + Math.round(seconds * 20));
		if (currentId == id) {
			currentId = -1;
		}
	}

	public boolean isMarkedDead(int id, long nowTick) {
		Long until = deadUntil.get(id);
		return until != null && nowTick < until;
	}

	/** Where the player is and where the view points, for choosing the target nearest the crosshair. */
	public record View(double x, double eyeY, double z, double yaw, double pitch) {
		/** The angle in degrees between the view and the direction to a point. */
		public double angleTo(double tx, double ty, double tz) {
			double dx = tx - x;
			double dy = ty - eyeY;
			double dz = tz - z;
			double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (length < 1e-9) {
				return 0;
			}
			double yawRad = Math.toRadians(yaw);
			double pitchRad = Math.toRadians(pitch);
			double vx = -Math.sin(yawRad) * Math.cos(pitchRad);
			double vy = -Math.sin(pitchRad);
			double vz = Math.cos(yawRad) * Math.cos(pitchRad);
			double dot = (dx * vx + dy * vy + dz * vz) / length;
			return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot))));
		}
	}

	/**
	 * @param byCrosshair choose the target nearest to where the view already points, so it turns as little as possible;
	 *        otherwise the nearest to the player
	 * @return the mob to go for, or null when there is none. A special one within the radius always comes first (the
	 *         best of them); otherwise the one already being gone for is kept, else the best.
	 */
	public Mob pick(List<Mob> mobs, View view, boolean byCrosshair, double radius, long nowTick, String name, String special) {
		deadUntil.values().removeIf(until -> nowTick >= until);
		Mob bestSpecial = null;
		Mob best = null;
		Mob current = null;
		double specialScore = Double.MAX_VALUE;
		double bestScore = Double.MAX_VALUE;
		for (Mob mob : mobs) {
			double dist = Math.hypot(mob.x() - view.x(), mob.z() - view.z());
			if (dist > radius || isMarkedDead(mob.id(), nowTick)) {
				continue;
			}
			boolean isSpecial = matches(mob.name(), special);
			if (!isSpecial && !matches(mob.name(), name)) {
				continue;
			}
			double score = byCrosshair ? view.angleTo(mob.x(), mob.eyeY(), mob.z()) : dist;
			if (isSpecial && score < specialScore) {
				specialScore = score;
				bestSpecial = mob;
			}
			if (score < bestScore) {
				bestScore = score;
				best = mob;
			}
			if (mob.id() == currentId) {
				current = mob;
			}
		}
		Mob chosen = bestSpecial != null ? bestSpecial : current != null ? current : best;
		currentId = chosen == null ? -1 : chosen.id();
		return chosen;
	}
}
