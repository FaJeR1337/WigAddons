package com.fajer.wigaddons;

import java.util.Collection;
import java.util.Locale;
import java.util.TreeSet;

/** Finds an item id from a few typed letters, for picking a route's icon. */
public final class ItemSearch {
	/**
	 * @param best the id to use: an exact match, else the first id starting with the text, else the first containing it
	 * @param count how many ids matched at all
	 * @param exact the text was a whole item id
	 */
	public record Result(String best, int count, boolean exact) {
		static final Result NONE = new Result(null, 0, false);
	}

	private ItemSearch() {
	}

	/** Lower case, "minecraft:" dropped, spaces turned into underscores: "Diamond Pickaxe" becomes "diamond_pickaxe". */
	public static String normalise(String text) {
		String t = text.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
		return t.startsWith("minecraft:") ? t.substring("minecraft:".length()) : t;
	}

	/** @param ids item ids without the "minecraft:" part, such as "diamond_pickaxe" */
	public static Result find(Collection<String> ids, String text) {
		String query = normalise(text);
		if (query.isEmpty()) {
			return Result.NONE;
		}
		TreeSet<String> sorted = new TreeSet<>(ids);
		if (sorted.contains(query)) {
			return new Result(query, 1, true);
		}
		String firstStarting = null;
		String firstContaining = null;
		int count = 0;
		for (String id : sorted) {
			if (id.startsWith(query)) {
				count++;
				if (firstStarting == null) {
					firstStarting = id;
				}
			} else if (id.contains(query)) {
				count++;
				if (firstContaining == null) {
					firstContaining = id;
				}
			}
		}
		String best = firstStarting != null ? firstStarting : firstContaining;
		return best == null ? Result.NONE : new Result(best, count, false);
	}
}
