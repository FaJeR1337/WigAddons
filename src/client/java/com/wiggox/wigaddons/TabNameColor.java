package com.wiggox.wigaddons;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.scores.PlayerTeam;

/**
 * Colours in the tab list. Some servers draw the tab list from rows of their own (placeholder entries whose text is the
 * players' names, levels and so on) and not from the players' entries, so every row with the text in it is changed:
 * <ul>
 * <li>Names in {@link #NAME_COLORS}: fixed here, for those names only, with no setting.</li>
 * <li>The level at the start of a row, as "[167]": the brackets and the number in the colours picked in the settings.
 * Whatever colour the server drew them in, and whatever the number.</li>
 * </ul>
 * Everything else in a row stays as the server sent it. Where the server sends no tab name and builds it from a team (a
 * prefix, the name, a suffix), one is put together from those.
 */
public final class TabNameColor {
	/** The names with a colour of their own in the tab list, and the colour: hardcoded, nobody can set one. */
	private static final Map<String, NameStyle> NAME_COLORS = Map.of(
		"Wiggox", new NameStyle(0xB11226, true, "Dev", 0xFF5555));

	/** How a name is drawn: its colour, whether it is bold, and the text (in its own colour) shown in place of the level number in the brackets, or null to leave the number. */
	record NameStyle(int color, boolean bold, String levelText, int levelTextColor) {
	}

	/** The sign legacy colour codes start with. */
	private static final char SECTION = (char) 0xA7;
	/** A level at the start of the text: "[167]" with any spaces before it. */
	private static final Pattern LEVEL = Pattern.compile("^\\s*\\[(\\d+)\\]");

	/** For each row changed: the text the server sent and the text put in its place, so a row can be put back, and a new text from the server noticed. */
	private static final Map<PlayerInfo, Component[]> CHANGED = new IdentityHashMap<>();

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
		int bracket = -1;
		int level = -1;
		if (WigState.config.tabLevelCustom) {
			bracket = parse(WigState.config.tabLevelBracketColor);
			level = parse(WigState.config.tabLevelColor);
		}
		String ownName = mc.player.getGameProfile().name();
		for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
			Component shown = info.getTabListDisplayName();
			Component[] before = CHANGED.get(info);
			if (before != null && shown == before[1]) {
				continue; // still the text put there
			}
			Component replaced = null;
			if (shown != null) {
				replaced = recolor(shown, NAME_COLORS, bracket, level);
			} else if (info.getProfile().name().equals(ownName) && NAME_COLORS.containsKey(ownName)) {
				// no tab name sent: made from the team's prefix, the name and the suffix
				Component made = fromTeam(info, ownName);
				Component levelled = recolor(made, Map.of(), bracket, level);
				replaced = levelled != null ? levelled : made;
			}
			if (replaced != null) {
				info.setTabListDisplayName(replaced);
				CHANGED.put(info, new Component[] {shown, replaced});
			} else if (before != null) {
				CHANGED.remove(info); // a new text from the server with nothing to change in it
			}
		}
	}

	private static Component fromTeam(PlayerInfo info, String name) {
		PlayerTeam team = info.getTeam();
		MutableComponent out = Component.empty();
		if (team != null) {
			out.append(team.getPlayerPrefix());
		}
		out.append(Component.literal(name).setStyle(Style.EMPTY.withColor(NAME_COLORS.get(name).color()).withBold(NAME_COLORS.get(name).bold())));
		if (team != null) {
			out.append(team.getPlayerSuffix());
		}
		return out;
	}

	/** The text of a name above an entity with the same custom colours as the tab list, or null where there is nothing to change. */
	public static Component recolorNameTag(Component tag) {
		int bracket = -1;
		int level = -1;
		if (WigState.config.tabLevelCustom) {
			bracket = parse(WigState.config.tabLevelBracketColor);
			level = parse(WigState.config.tabLevelColor);
		}
		return recolor(tag, NAME_COLORS, bracket, level);
	}

	/** Puts the rows back as the server sent them: for when a setting is turned off. */
	public static void reset() {
		restoreAll();
	}

	/** The text with its legacy colour codes turned into styles, a style for each character. */
	private static void expand(Component component, StringBuilder text, List<Style> styles) {
		component.visit((Style base, String piece) -> {
			Style style = base;
			for (int i = 0; i < piece.length(); i++) {
				char c = piece.charAt(i);
				if (c == SECTION && i + 1 < piece.length()) {
					ChatFormatting format = ChatFormatting.getByCode(piece.charAt(i + 1));
					if (format != null) {
						style = format == ChatFormatting.RESET ? Style.EMPTY : style.applyLegacyFormat(format);
					}
					i++;
				} else {
					text.append(c);
					styles.add(style);
				}
			}
			return Optional.empty();
		}, Style.EMPTY);
	}

	private static boolean sameColor(Style style, int color) {
		return style.getColor() != null && style.getColor().getValue() == color;
	}

	private static boolean nameChar(char c) {
		return Character.isLetterOrDigit(c) || c == '_';
	}

	/**
	 * The component with the colours put on it: each name of {@code names} wherever it stands as a whole word, and, where the
	 * text begins with a level ("[167]"), the brackets in {@code bracket} and the number in {@code level} (each skipped
	 * when -1). Null where there is nothing to change or it already has the colours.
	 */
	static Component recolor(Component component, Map<String, NameStyle> names, int bracket, int level) {
		StringBuilder text = new StringBuilder();
		List<Style> styles = new ArrayList<>();
		expand(component, text, styles);
		String plain = text.toString();
		boolean changed = false;
		NameStyle levelOf = null; // the name in this row that has a text of its own for the level
		for (Map.Entry<String, NameStyle> entry : names.entrySet()) {
			String name = entry.getKey();
			for (int at = plain.indexOf(name); at >= 0; at = plain.indexOf(name, at + name.length())) {
				int end = at + name.length();
				boolean whole = (at == 0 || !nameChar(plain.charAt(at - 1))) && (end >= plain.length() || !nameChar(plain.charAt(end)));
				if (!whole) {
					continue;
				}
				NameStyle look = entry.getValue();
				if (look.levelText() != null) {
					levelOf = look;
				}
				for (int i = at; i < end; i++) {
					if (!sameColor(styles.get(i), look.color()) || styles.get(i).isBold() != look.bold()) {
						styles.set(i, styles.get(i).withColor(look.color()).withBold(look.bold()));
						changed = true;
					}
				}
			}
		}
		if (bracket >= 0 && level >= 0) {
			Matcher m = LEVEL.matcher(plain);
			if (m.find()) {
				int open = m.start(1) - 1;
				int close = m.end(1);
				for (int i = open; i <= close; i++) {
					int color = (i == open || i == close) ? bracket : level;
					if (!sameColor(styles.get(i), color)) {
						styles.set(i, styles.get(i).withColor(color));
						changed = true;
					}
				}
			}
		}
		if (levelOf != null) {
			Matcher m = LEVEL.matcher(plain);
			if (m.find()) {
				// the number in the brackets is the name's own text, in its own colour
				Style numberStyle = styles.get(m.start(1)).withColor(levelOf.levelTextColor());
				StringBuilder shown = new StringBuilder(plain);
				shown.replace(m.start(1), m.end(1), levelOf.levelText());
				List<Style> restyled = new ArrayList<>(styles.subList(0, m.start(1)));
				for (int i = 0; i < levelOf.levelText().length(); i++) {
					restyled.add(numberStyle);
				}
				restyled.addAll(styles.subList(m.end(1), styles.size()));
				plain = shown.toString();
				styles = restyled;
				changed = true;
			}
		}
		if (!changed) {
			return null;
		}
		MutableComponent out = Component.empty();
		int from = 0;
		for (int i = 1; i <= plain.length(); i++) {
			if (i == plain.length() || !styles.get(i).equals(styles.get(from))) {
				out.append(Component.literal(plain.substring(from, i)).setStyle(styles.get(from)));
				from = i;
			}
		}
		return out;
	}
}
