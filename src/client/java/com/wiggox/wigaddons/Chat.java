package com.wiggox.wigaddons;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** The mod's chat lines: a "[WigAddons]" tag, then white text (red for errors). */
public final class Chat {
	private static final int AQUA = 0x55FFFF;
	private static final int GRAY = 0xAAAAAA;
	private static final int WHITE = 0xFFFFFF;
	private static final int RED = 0xFF5555;

	private Chat() {
	}

	private static MutableComponent prefix() {
		return Component.empty()
			.append(Component.literal("[").withColor(GRAY))
			.append(Component.literal("WigAddons").withColor(AQUA))
			.append(Component.literal("] ").withColor(GRAY));
	}

	public static void info(String text) {
		send(prefix().append(Component.literal(text).withColor(WHITE)));
	}

	public static void error(String text) {
		send(prefix().append(Component.literal(text).withColor(RED)));
	}

	private static void send(Component line) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.sendSystemMessage(line);
		}
	}
}
