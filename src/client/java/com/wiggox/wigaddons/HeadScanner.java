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
		/** The skin's texture id (the long hex name at the end of its texture address), or "" when the head has no custom texture. */
		String textureId() {
			return HeadScanner.textureId(profile);
		}
	}

	private static final java.util.regex.Pattern BASE64 = java.util.regex.Pattern.compile("[A-Za-z0-9+/=_-]{60,}");
	private static final java.util.regex.Pattern TEXTURE = java.util.regex.Pattern.compile("texture/([0-9a-fA-F]{16,})");

	/** Digs the texture id out of a head's profile text: the profile holds a base64 blob that says where the skin is. */
	static String textureId(String profile) {
		java.util.regex.Matcher direct = TEXTURE.matcher(profile);
		if (direct.find()) {
			return direct.group(1);
		}
		java.util.regex.Matcher blob = BASE64.matcher(profile);
		while (blob.find()) {
			try {
				String decoded = new String(java.util.Base64.getDecoder().decode(blob.group().replace('-', '+').replace('_', '/')), java.nio.charset.StandardCharsets.UTF_8);
				java.util.regex.Matcher inner = TEXTURE.matcher(decoded);
				if (inner.find()) {
					return inner.group(1);
				}
			} catch (IllegalArgumentException e) {
				// not base64 after all
			}
		}
		return "";
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
		return wanted == null || wanted.isBlank() || head.textureId().contains(wanted.trim()) || head.profile().contains(wanted.trim());
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

	/** Prints the full profile text of the nearest head (for when the skin id isn't found in it), to chat and the game log. */
	static void reportNearestRaw(Minecraft mc) {
		if (mc.player == null) {
			return;
		}
		List<Head> heads = scan(mc, mc.player.getX(), mc.player.getZ(), 80);
		if (heads.isEmpty()) {
			Chat.info("No player heads within 80 blocks.");
			return;
		}
		Head head = heads.get(0);
		org.slf4j.LoggerFactory.getLogger("wigaddons").info("Nearest head {} profile: {}", head.pos(), head.profile());
		Chat.info("Nearest head " + head.pos().getX() + " " + head.pos().getY() + " " + head.pos().getZ() + ", skin id: " + (head.textureId().isEmpty() ? "none found" : head.textureId()));
		Chat.info("Its full profile text is in the game log (latest.log) under 'Nearest head'.");
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
			String id = head.textureId();
			String label = id.isEmpty() ? (head.profile().length() > 90 ? head.profile().substring(0, 90) + "..." : head.profile()) : "skin id " + id;
			Chat.info(String.format("%d %d %d (%.0f blocks): %s", head.pos().getX(), head.pos().getY(), head.pos().getZ(), dist, label));
		}
	}
}
