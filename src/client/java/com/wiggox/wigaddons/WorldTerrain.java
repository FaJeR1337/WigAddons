package com.wiggox.wigaddons;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/** The loaded world as the obstacle avoider sees it: a cell is solid when its block has something to bump into. */
final class WorldTerrain implements ObstacleAvoider.Terrain {
	/** Collision tops up to this high are stepped onto without jumping (the player steps up 0.6). */
	private static final double STEP = 0.6;
	/** Collision this low or lower (carpets, a thin layer of snow) is treated as not being there at all. */
	private static final double THIN = 0.2;

	@Override
	public boolean solid(int x, int y, int z) {
		if (stairs(x, y, z)) {
			return false; // stairs are walked up, half a block at a time
		}
		VoxelShape shape = shape(x, y, z);
		return shape != null && !shape.isEmpty() && shape.max(Direction.Axis.Y) > STEP;
	}

	@Override
	public boolean supports(int x, int y, int z) {
		VoxelShape shape = shape(x, y, z);
		return shape != null && !shape.isEmpty() && shape.max(Direction.Axis.Y) > THIN;
	}

	@Override
	public double top(int x, int y, int z) {
		if (stairs(x, y, z)) {
			return 0.5; // the first step of a stair, which is the one stepped onto
		}
		VoxelShape shape = shape(x, y, z);
		return shape == null || shape.isEmpty() || shape.max(Direction.Axis.Y) <= THIN ? 0.0 : shape.max(Direction.Axis.Y);
	}

	@Override
	public boolean stairs(int x, int y, int z) {
		ClientLevel level = Minecraft.getInstance().level;
		return level != null && level.getBlockState(new BlockPos(x, y, z)).getBlock() instanceof StairBlock;
	}

	/** Whether the cell has nothing solid in it, so a teleporting player passes through (unloaded cells count as open). */
	static boolean passable(int x, int y, int z) {
		VoxelShape shape = shape(x, y, z);
		return shape == null || shape.isEmpty();
	}

	private static VoxelShape shape(int x, int y, int z) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return null;
		}
		BlockPos pos = new BlockPos(x, y, z);
		BlockState state = level.getBlockState(pos);
		return state.getCollisionShape(level, pos);
	}
}
