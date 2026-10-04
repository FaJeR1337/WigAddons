package com.fajer.wigaddons;

import java.util.Random;

/**
 * Sidesteps obstacles the player can't jump over, and, on a level stretch of the route, one-block steps too (there is
 * no reason to climb when the route doesn't go up). It looks ahead along the way the player is facing; when a wall
 * (or a step with a low ceiling) is in the way it holds A or D to go round it, and lets go once the line ahead is
 * clear again, so a one-block obstacle costs only a one-block step to the side. It has no Minecraft types so it can
 * be unit tested.
 */
public final class ObstacleAvoider {
	/** Whether a block cell stops the player walking through it. */
	public interface Terrain {
		boolean solid(int x, int y, int z);
	}

	/**
	 * @param strafe +1 to hold A (left), -1 to hold D (right), 0 for neither
	 * @param gaveUp no way round was found
	 */
	public record Output(int strafe, boolean gaveUp) {
		static final Output NONE = new Output(0, false);
	}

	/** How far ahead an obstacle starts a sidestep. */
	static final double LOOK_AHEAD = 3.5;
	/** The line ahead must be clear this far before the sidestep ends. */
	static final double CLEAR_AHEAD = 2.0;
	/** Just after a sidestep, only obstacles this near start a new one. */
	static final double RETRIGGER_AHEAD = 2.0;
	static final int COOLDOWN_TICKS = 20;
	static final double HALF_WIDTH = 0.3;
	static final double SAMPLE_STEP = 0.5;
	/** Ticks pressed against something, with jumping not helping, that count as an obstacle too. */
	static final int PRESSED_TICKS = 10;
	/** A person doesn't react on the exact tick, or let go on the exact tick. */
	static final int MAX_DELAY_TICKS = 3;
	static final int MAX_EXTRA_HOLD_TICKS = 2;
	/** More sideways than this and there is probably no way round. */
	static final double MAX_SIDESTEP = 6.0;
	/** Ticks of no sideways progress before trying the other side, then giving up. */
	static final int NO_PROGRESS_TICKS = 8;

	private enum Cell {
		FREE,
		/** One block up, with room above: jumping gets over it. */
		STEP,
		BLOCK
	}

	/** Whether a one-block step counts as an obstacle on this tick. */
	private boolean stepsBlock;

	private final Terrain terrain;
	private final Random random;

	/** After giving up on going round a step that can simply be jumped, steps are left alone for this many ticks. */
	static final int STEP_SUPPRESS_TICKS = 60;

	private int stepSuppress;
	private int side; // 0 none, +1 left, -1 right
	private int delay;
	private int extraHold = -1;
	private int cooldown;
	private int pressedTicks;
	private int noProgress;
	private boolean flipped;
	private double startX;
	private double startZ;
	private double bestSideways;

	public ObstacleAvoider(Terrain terrain, Random random) {
		this.terrain = terrain;
		this.random = random;
	}

	/**
	 * @param yaw the way the player faces, in Minecraft yaw degrees
	 * @param aimX where the route is heading
	 * @param aimZ where the route is heading
	 * @param pressedAgainst the player is on the ground and pushing into something
	 * @param levelLeg the route stays at the same height here, so one-block steps are walked round, not jumped
	 */
	public Output tick(double px, double py, double pz, double yaw, double aimX, double aimZ, boolean pressedAgainst, boolean levelLeg) {
		pressedTicks = pressedAgainst ? pressedTicks + 1 : 0;
		if (cooldown > 0) {
			cooldown--;
		}
		if (stepSuppress > 0) {
			stepSuppress--;
		}
		stepsBlock = levelLeg && stepSuppress == 0;
		double fx = -Math.sin(Math.toRadians(yaw));
		double fz = Math.cos(Math.toRadians(yaw));
		double lx = Math.cos(Math.toRadians(yaw));
		double lz = Math.sin(Math.toRadians(yaw));

		if (side == 0) {
			double aimDistance = Math.hypot(aimX - px, aimZ - pz);
			// no need to go round something beyond the spot being walked to
			double reach = Math.min(cooldown > 0 ? RETRIGGER_AHEAD : LOOK_AHEAD, aimDistance + 0.5);
			boolean blocked = firstBlock(px, py, pz, fx, fz, lx, lz, 0, reach) >= 0 || pressedTicks >= PRESSED_TICKS;
			if (!blocked) {
				return Output.NONE;
			}
			int chosen = chooseSide(px, py, pz, fx, fz, lx, lz, aimX, aimZ);
			if (chosen == 0) {
				return Output.NONE;
			}
			begin(chosen, px, pz);
		}

		if (delay > 0) {
			delay--;
			return Output.NONE;
		}

		double sideways = ((px - startX) * lx + (pz - startZ) * lz) * side;
		if (sideways > MAX_SIDESTEP) {
			return gaveUp(px, py, pz, yaw);
		}
		if (sideways > bestSideways + 0.01) {
			bestSideways = sideways;
			noProgress = 0;
		} else if (++noProgress >= NO_PROGRESS_TICKS) {
			if (flipped) {
				return gaveUp(px, py, pz, yaw);
			}
			// boxed in on this side: try the other one, once
			flipped = true;
			begin(-side, px, pz);
			flipped = true;
			return Output.NONE;
		}

		if (firstBlock(px, py, pz, fx, fz, lx, lz, 0, CLEAR_AHEAD) < 0) {
			if (extraHold < 0) {
				extraHold = random.nextInt(MAX_EXTRA_HOLD_TICKS + 1);
			}
			if (extraHold == 0) {
				reset();
				cooldown = COOLDOWN_TICKS;
				return Output.NONE;
			}
			extraHold--;
		} else {
			extraHold = -1;
		}
		return new Output(side, false);
	}

	public void reset() {
		side = 0;
		delay = 0;
		extraHold = -1;
		noProgress = 0;
		flipped = false;
		bestSideways = 0;
	}

	private Output gaveUp(double px, double py, double pz, double yaw) {
		double fx = -Math.sin(Math.toRadians(yaw));
		double fz = Math.cos(Math.toRadians(yaw));
		double lx = Math.cos(Math.toRadians(yaw));
		double lz = Math.sin(Math.toRadians(yaw));
		boolean wasOnlySteps = stepsBlock;
		stepsBlock = false;
		boolean realObstacle = firstBlock(px, py, pz, fx, fz, lx, lz, 0, CLEAR_AHEAD) >= 0;
		reset();
		if (wasOnlySteps && !realObstacle) {
			// nothing here that jumping can't deal with: stop trying to walk round it
			stepSuppress = STEP_SUPPRESS_TICKS;
			cooldown = COOLDOWN_TICKS;
			return Output.NONE;
		}
		return new Output(0, true);
	}

	private void begin(int chosen, double px, double pz) {
		side = chosen;
		delay = random.nextInt(MAX_DELAY_TICKS + 1);
		startX = px;
		startZ = pz;
		bestSideways = 0;
		noProgress = 0;
		extraHold = -1;
		flipped = false;
	}

	/** The side to go round on: toward where the route is heading when that side is open, else the other, else the shorter way. */
	private int chooseSide(double px, double py, double pz, double fx, double fz, double lx, double lz, double aimX, double aimZ) {
		boolean leftOpen = sideOpen(px, py, pz, lx, lz);
		boolean rightOpen = sideOpen(px, py, pz, -lx, -lz);
		if (!leftOpen && !rightOpen) {
			return 0;
		}
		if (leftOpen != rightOpen) {
			return leftOpen ? 1 : -1;
		}
		double lateral = (aimX - px) * lx + (aimZ - pz) * lz;
		if (Math.abs(lateral) > 0.5) {
			return lateral > 0 ? 1 : -1;
		}
		// dead ahead: whichever way round is shorter
		int left = stepsToClear(px, py, pz, fx, fz, lx, lz, 1);
		int right = stepsToClear(px, py, pz, fx, fz, lx, lz, -1);
		if (left != right) {
			return left < right ? 1 : -1;
		}
		return random.nextBoolean() ? 1 : -1;
	}

	/** How many blocks to the side the line ahead is clear, or a large number if it never is within the limit. */
	private int stepsToClear(double px, double py, double pz, double fx, double fz, double lx, double lz, int dir) {
		for (int k = 1; k <= (int) MAX_SIDESTEP; k++) {
			if (!bodyFree(px + lx * dir * k, py, pz + lz * dir * k)) {
				return Integer.MAX_VALUE;
			}
			if (firstBlock(px, py, pz, fx, fz, lx, lz, dir * k, CLEAR_AHEAD) < 0) {
				return k;
			}
		}
		return Integer.MAX_VALUE;
	}

	/** Open when a block beside the player, where the body would go, has room. */
	private boolean sideOpen(double px, double py, double pz, double dx, double dz) {
		return bodyFree(px + dx * 0.9, py, pz + dz * 0.9);
	}

	private boolean bodyFree(double x, double py, double z) {
		int y = feetCell(py);
		int cx = (int) Math.floor(x);
		int cz = (int) Math.floor(z);
		return !terrain.solid(cx, y, cz) && !terrain.solid(cx, y + 1, cz);
	}

	/**
	 * Distance to the first block ahead that the player can't walk or jump through, checked across the player's
	 * width, or -1 if there is none within the reach. offset shifts the whole line sideways, to the left.
	 */
	private double firstBlock(double px, double py, double pz, double fx, double fz, double lx, double lz, double offset, double reach) {
		int y = feetCell(py);
		for (double d = SAMPLE_STEP; d <= reach + 1e-9; d += SAMPLE_STEP) {
			for (double edge : new double[] {-HALF_WIDTH, 0, HALF_WIDTH}) {
				double x = px + fx * d + lx * (offset + edge);
				double z = pz + fz * d + lz * (offset + edge);
				if (classify((int) Math.floor(x), y, (int) Math.floor(z)) == Cell.BLOCK) {
					return d;
				}
			}
		}
		return -1;
	}

	private Cell classify(int x, int y, int z) {
		boolean feet = terrain.solid(x, y, z);
		boolean head = terrain.solid(x, y + 1, z);
		if (!feet && !head) {
			return Cell.FREE;
		}
		if (feet && !head && !terrain.solid(x, y + 2, z)) {
			return stepsBlock ? Cell.BLOCK : Cell.STEP;
		}
		return Cell.BLOCK;
	}

	private static int feetCell(double py) {
		return (int) Math.floor(py + 1e-4);
	}
}
