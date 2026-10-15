package com.wiggox.wigaddons;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Finds the player heads placed in the loaded world: the custom head on top of a protector's formation, and a debug view of them. */
final class HeadScanner {
	/** A head block, and what is known about whose it is (the profile as text, which holds its texture). */
	record Head(BlockPos pos, String profile) {
	}

	/** Heads shown by /wig heads: drawn through walls and listed. */
	static boolean showing;
	private static List<Head> cached = List.of();
	private static long cachedAt = -1000;

	private HeadScanner() {
	}

	/** Every head block within the radius (blocks, horizontally) of the point, nearest first. */
	static List<Head> scan(Minecraft mc, double x, double z, int radius) {
		ClientLevel level = mc.level;
		List<Head> found = new ArrayList<>();
		if (level == null) {
			return found;
		}
		int minChunkX = (int) Math.floor((x - radius) / 16.0);
		int maxChunkX = (int) Math.floor((x + radius) / 16.0);
		int minChunkZ = (int) Math.floor((z - radius) / 16.0);
		int maxChunkZ = (int) Math.floor((z + radius) / 16.0);
		for (int cx = minChunkX; cx <= maxChunkX; cx++) {
			for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity entity : chunk.getBlockEntities().values()) {
					if (entity instanceof SkullBlockEntity skull) {
						BlockPos pos = skull.getBlockPos();
						if (Math.hypot(pos.getX() + 0.5 - x, pos.getZ() + 0.5 - z) <= radius) {
							found.add(new Head(pos, String.valueOf(skull.getOwnerProfile())));
						}
					}
				}
			}
		}
		found.sort(Comparator.comparingDouble(h -> Math.hypot(h.pos().getX() + 0.5 - x, h.pos().getZ() + 0.5 - z)));
		return found;
	}

	/** Whether the head's texture (or owner) text contains what is wanted; blank wants any head. */
	static boolean matches(Head head, String wanted) {
		return wanted == null || wanted.isBlank() || head.profile().contains(wanted.trim());
	}

	/** The heads around the player, refreshed once a second, for drawing. */
	static List<Head> cached(Minecraft mc) {
		long now = mc.level == null ? 0 : mc.level.getGameTime();
		if (now - cachedAt >= 20 || now < cachedAt) {
			cachedAt = now;
			cached = mc.player == null ? List.of() : scan(mc, mc.player.getX(), mc.player.getZ(), 80);
		}
		return cached;
	}

	/** Prints what /wig heads sees to chat, nearest first. */
	static void report(Minecraft mc) {
		if (mc.player == null) {
			return;
		}
		List<Head> heads = scan(mc, mc.player.getX(), mc.player.getZ(), 80);
		if (heads.isEmpty()) {
			Chat.info("No player heads within 80 blocks.");
			return;
		}
		Chat.info(heads.size() + " player head(s) within 80 blocks, nearest first:");
		int shown = 0;
		for (Head head : heads) {
			if (shown++ >= 8) {
				break;
			}
			double dist = Math.hypot(head.pos().getX() + 0.5 - mc.player.getX(), head.pos().getZ() + 0.5 - mc.player.getZ());
			String profile = head.profile().length() > 90 ? head.profile().substring(0, 90) + "..." : head.profile();
			Chat.info(String.format("%d %d %d (%.0f blocks): %s", head.pos().getX(), head.pos().getY(), head.pos().getZ(), dist, profile));
		}
	}
}
