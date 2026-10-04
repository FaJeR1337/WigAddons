package com.wiggox.wigaddons;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** The loaded world as the obstacle avoider sees it: a cell is solid when its block has something to bump into. */
final class WorldTerrain implements ObstacleAvoider.Terrain {
	@Override
	public boolean solid(int x, int y, int z) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return false;
		}
		BlockPos pos = new BlockPos(x, y, z);
		BlockState state = level.getBlockState(pos);
		return !state.getCollisionShape(level, pos).isEmpty();
	}
}
