package com.wiggox.wigaddons;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;

/** /wig debug tablist: every line of the tab list as the server sends it, with the colour of each piece of text. */
public final class TabListDebug {
	private TabListDebug() {
	}

	/** A component as pieces of text, each with the colour (a name or #rrggbb) and style it is shown in: {red bold}text{-}more. */
	static String describe(Component component) {
		if (component == null) {
			return "(none)";
		}
		StringBuilder out = new StringBuilder();
		component.visit((Style style, String text) -> {
			if (!text.isEmpty()) {
				out.append('{');
				out.append(style.getColor() == null ? "-" : style.getColor().serialize());
				if (style.isBold()) {
					out.append(" bold");
				}
				if (style.isItalic()) {
					out.append(" italic");
				}
				if (style.isUnderlined()) {
					out.append(" underlined");
				}
				if (style.isStrikethrough()) {
					out.append(" strike");
				}
				if (style.isObfuscated()) {
					out.append(" obf");
				}
				out.append('}').append(text.replace('\u00a7', '&')); // legacy colour codes shown as &, not drawn
			}
			return java.util.Optional.empty();
		}, Style.EMPTY);
		return out.toString();
	}

	public static int print() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() == null || mc.level == null) {
			Chat.error("Not in a world.");
			return 0;
		}
		List<PlayerInfo> entries = new ArrayList<>(mc.getConnection().getListedOnlinePlayers());
		// the order the tab list is drawn in
		entries.sort(Comparator.comparingInt(PlayerInfo::getTabListOrder).reversed()
			.thenComparing(info -> info.getTeam() != null ? info.getTeam().getName() : "")
			.thenComparing(info -> info.getProfile().name(), String::compareToIgnoreCase));
		Chat.info("Tab list: " + entries.size() + " entries, in the order they are drawn. {colour style}text pieces; - is the default colour.");
		int number = 0;
		for (PlayerInfo info : entries) {
			PlayerTeam team = info.getTeam();
			String teamText = team == null ? "no team" : "team " + team.getName() + " colour " + String.valueOf(team.getColor()).replace((char) 0xA7, (char) 0x26) + " prefix " + describe(team.getPlayerPrefix()) + " suffix " + describe(team.getPlayerSuffix());
			Chat.info("#" + number++ + " order " + info.getTabListOrder() + " | " + info.getProfile().name() + " | tab name " + describe(info.getTabListDisplayName()) + " | " + teamText);
		}
		Objective list = mc.level.getScoreboard().getDisplayObjective(DisplaySlot.LIST);
		if (list == null) {
			Chat.info("No scoreboard objective is shown in the tab list.");
		} else {
			Chat.info("Scoreboard objective in the tab list: " + list.getName() + " (" + describe(list.getDisplayName()) + ")");
			for (PlayerScoreEntry entry : mc.level.getScoreboard().listPlayerScores(list)) {
				Chat.info("  " + entry.owner() + " = " + entry.value());
			}
		}
		return 1;
	}
}
