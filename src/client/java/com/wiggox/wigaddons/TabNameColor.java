package com.wiggox.wigaddons;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.scores.PlayerTeam;

/**
 * Your own name in the tab list in a colour of your choice. Servers that build the tab name from a team (a prefix, the
 * name, a suffix) send no tab name of their own, so one is put together from the same pieces with only the name's colour
 * changed; the prefix and suffix are drawn just as the server sent them.
 */
public final class TabNameColor {
	private static boolean applied;

	private TabNameColor() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(TabNameColor::tick);
	}

	/** #rrggbb or rrggbb as a colour, or -1 for anything else. */
	public static int parse(String text) {
		if (text == null) {
			return -1;
		}
		String hex = text.trim();
		if (hex.startsWith("#")) {
			hex = hex.substring(1);
		}
		if (hex.length() != 6) {
			return -1;
		}
		try {
			return Integer.parseInt(hex, 16);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static void tick(Minecraft mc) {
		if (mc.player == null || mc.getConnection() == null) {
			applied = false;
			return;
		}
		PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
		if (info == null) {
			return;
		}
		int color = WigState.config.tabNameCustom ? parse(WigState.config.tabNameColor) : -1;
		if (color < 0) {
			if (applied) {
				info.setTabListDisplayName(null); // back to the server's own
				applied = false;
			}
			return;
		}
		PlayerTeam team = info.getTeam();
		MutableComponent name = Component.empty();
		if (team != null) {
			name.append(team.getPlayerPrefix());
		}
		name.append(Component.literal(info.getProfile().name()).withColor(color));
		if (team != null) {
			name.append(team.getPlayerSuffix());
		}
		info.setTabListDisplayName(name);
		applied = true;
	}
}
