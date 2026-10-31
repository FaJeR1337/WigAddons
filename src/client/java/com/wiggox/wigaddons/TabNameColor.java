package com.wiggox.wigaddons;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.scores.PlayerTeam;

/**
 * Your own name in the tab list in a colour of your choice. Some servers draw the tab list from rows of their own (placeholder
 * entries whose text is the players' names, level and so on) and not from the players' entries, so every row whose text
 * has your name in it is changed: the name text gets the colour, the rest of the row is left as the server sent it. Where
 * the server builds the tab name from a team (a prefix, the name, a suffix) and sends none, one is put together from those.
 */
public final class TabNameColor {
	/** For each row changed: the text the server sent and the text put in its place, so a row can be put back, and a new text from the server noticed. */
	private static final Map<PlayerInfo, Component[]> CHANGED = new IdentityHashMap<>();

	/** The sign legacy colour codes start with. */
	private static final char SECTION = (char) 0xA7;

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

	private static void restoreAll() {
		for (Map.Entry<PlayerInfo, Component[]> entry : CHANGED.entrySet()) {
			if (entry.getKey().getTabListDisplayName() == entry.getValue()[1]) {
				entry.getKey().setTabListDisplayName(entry.getValue()[0]); // the server's own text back, unless it has sent a new one since
			}
		}
		CHANGED.clear();
	}

	private static void tick(Minecraft mc) {
		if (mc.player == null || mc.getConnection() == null) {
			CHANGED.clear();
			return;
		}
		int color = WigState.config.tabNameCustom ? parse(WigState.config.tabNameColor) : -1;
		if (color < 0) {
			if (!CHANGED.isEmpty()) {
				restoreAll();
			}
			return;
		}
		String name = mc.player.getGameProfile().name();
		for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
			Component shown = info.getTabListDisplayName();
			Component[] before = CHANGED.get(info);
			if (before != null && shown == before[1]) {
				continue; // still the text put there
			}
			Component original = shown;
			Component replaced;
			if (shown != null) {
				replaced = recolor(shown, name, color);
			} else if (info.getProfile().name().equals(name)) {
				replaced = fromTeam(info, name, color); // no tab name sent: made from the team's prefix, the name and the suffix
			} else {
				replaced = null;
			}
			if (replaced != null) {
				info.setTabListDisplayName(replaced);
				CHANGED.put(info, new Component[] {original, replaced});
			}
		}
	}

	private static Component fromTeam(PlayerInfo info, String name, int color) {
		PlayerTeam team = info.getTeam();
		MutableComponent out = Component.empty();
		if (team != null) {
			out.append(team.getPlayerPrefix());
		}
		out.append(Component.literal(name).withColor(color));
		if (team != null) {
			out.append(team.getPlayerSuffix());
		}
		return out;
	}

	/** The legacy colour and format codes in force at the end of the text: a colour letter and the format letters after it, as the section sign, b, the section sign, l. */
	static String activeCodes(String text) {
		String colour = "";
		StringBuilder formats = new StringBuilder();
		for (int i = 0; i + 1 < text.length(); i++) {
			if (text.charAt(i) == SECTION) {
				char code = Character.toLowerCase(text.charAt(i + 1));
				if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
					colour = String.valueOf(SECTION) + code;
					formats.setLength(0); // a colour code ends the formats before it
				} else if (code == 'r') {
					colour = "";
					formats.setLength(0);
				} else if ("klmno".indexOf(code) >= 0) {
					formats.append(SECTION).append(code);
				}
				i++;
			}
		}
		return colour + formats;
	}

	/**
	 * The component with the colour put on the text of {@code name}, wherever it stands in it (inside a longer text, split out;
	 * or a text of its own), or null where the name isn't in it or already has the colour.
	 */
	static Component recolor(Component component, String name, int color) {
		List<Style> styles = new ArrayList<>();
		List<String> texts = new ArrayList<>();
		component.visit((Style style, String text) -> {
			styles.add(style);
			texts.add(text);
			return Optional.empty();
		}, Style.EMPTY);
		MutableComponent out = Component.empty();
		boolean changed = false;
		StringBuilder soFar = new StringBuilder(); // the text before, for the legacy codes carried over a split
		for (int i = 0; i < texts.size(); i++) {
			String text = texts.get(i);
			Style style = styles.get(i);
			int at = text.indexOf(name);
			if (at < 0) {
				out.append(Component.literal(text).setStyle(style));
			} else if (text.equals(name) && style.getColor() != null && style.getColor().getValue() == color) {
				out.append(Component.literal(text).setStyle(style)); // already the colour
			} else {
				String head = text.substring(0, at);
				String tail = text.substring(at + name.length());
				if (!head.isEmpty()) {
					out.append(Component.literal(head).setStyle(style));
				}
				out.append(Component.literal(name).setStyle(style.withColor(color)));
				if (!tail.isEmpty()) {
					// what followed the name in the colour of the text before it keeps that colour
					String codes = activeCodes(soFar + head);
					out.append(Component.literal(codes + tail).setStyle(style));
				}
				changed = true;
			}
			soFar.append(text);
		}
		return changed ? out : null;
	}
}
