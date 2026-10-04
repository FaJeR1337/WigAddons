package com.fajer.wigaddons;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Recognises the server's "someone left your party" chat lines. The patterns are regular expressions matched from
 * the start of the whole line, so a player typing the same words in chat ("Name: Bob has left the party.") doesn't
 * count.
 */
public final class PartyLeave {
	/** Formatting codes (a section sign and a character) are removed before matching. */
	private static final Pattern FORMATTING = Pattern.compile("\u00a7.");
	private static final Map<String, Pattern> COMPILED = new ConcurrentHashMap<>();

	/** An optional rank like [MVP+] before the name. */
	private static final String RANKS = "(?:\\[[^\\]]+\\]\\s*)*";

	public static final List<String> DEFAULT_PATTERNS = List.of(
		"^" + RANKS + "\\w{1,16} has left the party\\.?",
		"^" + RANKS + "\\w{1,16} was removed from the party(?: because .*)?\\.?",
		"^" + RANKS + "\\w{1,16} has been removed from the party\\.?",
		"^" + RANKS + "\\w{1,16} has been kicked from the party\\.?",
		"^the party was disbanded.*");

	private PartyLeave() {
	}

	public static boolean matches(String line, List<String> patterns) {
		String text = FORMATTING.matcher(line).replaceAll("").trim();
		for (String regex : patterns) {
			Pattern p = compile(regex);
			if (p != null && p.matcher(text).matches()) {
				return true;
			}
		}
		return false;
	}

	private static Pattern compile(String regex) {
		try {
			return COMPILED.computeIfAbsent(regex, r -> Pattern.compile(r, Pattern.CASE_INSENSITIVE));
		} catch (PatternSyntaxException e) {
			return null; // a broken pattern in the config file is ignored
		}
	}
}
