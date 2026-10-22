package com.wiggox.wigaddons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Experimentation table's memory games. Chronomatron lights its colours one after another (a stained glass turns into a
 * terracotta of the same colour) while a glowstone called "Remember the pattern!" is shown; the colours seen in that order are
 * recorded and listed beside the chest.
 */
public final class Experiments {
	/** The sixteen dye colours as the item ids spell them, how they are written, and the colour they are drawn in. */
	private static final String[] IDS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
	private static final String[] NAMES = {"White", "Orange", "Magenta", "Light Blue", "Yellow", "Lime", "Pink", "Gray", "Light Gray", "Cyan", "Purple", "Blue", "Brown", "Green", "Red", "Black"};
	private static final int[] COLORS = {0xFFFFFF, 0xFFA040, 0xFF55FF, 0x55CCFF, 0xFFFF55, 0x88FF33, 0xFF99BB, 0x8A8A8A, 0xD5D5D5, 0x22DDDD, 0xAA44DD, 0x5577FF, 0xAA7744, 0x2FAA2F, 0xFF4444, 0x666666};
	private static final int GOLD = 0xFFAA00;
	private static final int PADDING = 4;

	/** The colours of the pattern being shown now, in order, and the last full pattern. */
	private static final List<Integer> CURRENT = new ArrayList<>();
	private static final List<Integer> PATTERN = new ArrayList<>();
	private static final Map<Integer, Item> PREVIOUS = new HashMap<>();
	private static boolean remembering;
	/** Ultrasequencer: the slot of each number (stack size of a dye), as last shown, and how far the clicks have got. */
	private static final Map<Integer, Integer> ULTRA = new HashMap<>();
	private static boolean ultraRemembering;
	private static int ultraIndex;
	private static int autoIndex; // how many colours of the pattern Auto Experiments has clicked this round
	private static long closeAt; // when the finished table is closed, 0 while not waiting
	private static long nextClick; // when the next automatic click is due, in milliseconds
	private static int skipTicks; // ticks after a showing starts in which changes are still your own click

	private Experiments() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(Experiments::tick);
		ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
			if (screen instanceof AbstractContainerScreen<?>) {
				ScreenEvents.afterExtract(screen).register(Experiments::draw);
			}
		});
	}

	private static boolean isChronomatron(Screen screen) {
		return screen instanceof AbstractContainerScreen<?> chest && chest.getTitle().getString().contains("Chronomatron");
	}

	private static boolean isUltra(Screen screen) {
		return screen instanceof AbstractContainerScreen<?> chest && chest.getTitle().getString().contains("Ultrasequencer");
	}

	private static void reset() {
		ULTRA.clear();
		ultraRemembering = false;
		ultraIndex = 0;
		CURRENT.clear();
		PATTERN.clear();
		PREVIOUS.clear();
		remembering = false;
		skipTicks = 0;
		autoIndex = 0;
		closeAt = 0;
	}

	/** The index of the dye colour at the start of the path ("red_stained_glass" is 14), or -1. */
	private static int colorOf(String path, String suffix) {
		if (!path.endsWith(suffix)) {
			return -1;
		}
		String color = path.substring(0, path.length() - suffix.length());
		for (int i = 0; i < IDS.length; i++) {
			if (IDS[i].equals(color)) {
				return i;
			}
		}
		return -1;
	}

	private static String pathOf(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).getPath();
	}

	/** A stained glass block or pane of any colour. */
	static boolean isGlass(Item item) {
		String path = pathOf(item);
		return colorOf(path, "_stained_glass") >= 0 || colorOf(path, "_stained_glass_pane") >= 0;
	}

	/** The colour of a terracotta (not glazed), or -1 for anything else. */
	static int terracottaColor(Item item) {
		return colorOf(pathOf(item), "_terracotta");
	}

	private static void tick(Minecraft mc) {
		Screen screen = Compat.screen(mc);
		if (isUltra(screen)) {
			ultraTick(mc, (AbstractContainerScreen<?>) screen);
			return;
		}
		if (!ULTRA.isEmpty()) {
			reset();
		}
		if (!isChronomatron(screen)) {
			if (!PATTERN.isEmpty() || !CURRENT.isEmpty() || !PREVIOUS.isEmpty()) {
				reset(); // out of the game: the next one starts from nothing
			}
			return;
		}
		AbstractContainerScreen<?> chest = (AbstractContainerScreen<?>) screen;
		List<Slot> slots = chest.getMenu().slots;
		int chestSlots = Math.max(0, slots.size() - 36); // the rest is the player's inventory
		boolean prompt = false;
		for (int i = 0; i < chestSlots; i++) {
			ItemStack stack = slots.get(i).getItem();
			if (stack.is(Items.GLOWSTONE) && stack.getHoverName().getString().contains("Remember the pattern!")) {
				prompt = true;
				break;
			}
		}
		if (prompt && !remembering) {
			CURRENT.clear(); // a new showing starts: the whole pattern comes again from the first colour
		}
		if (prompt && !remembering) {
			skipTicks = 2; // your own last click turns glass to terracotta on this tick and the one after
		}
		boolean starting = skipTicks > 0;
		if (skipTicks > 0) {
			skipTicks--;
		}
		boolean[] alreadyLit = new boolean[IDS.length]; // colours that were terracotta on the last tick: their group is still lighting up
		for (Item item : PREVIOUS.values()) {
			int color = terracottaColor(item);
			if (color >= 0) {
				alreadyLit[color] = true;
			}
		}
		boolean lit = false; // several blocks light up together: that is still one colour of the pattern
		for (int i = 0; i < chestSlots; i++) {
			Item now = slots.get(i).getItem().getItem();
			Item before = PREVIOUS.get(i);
			if (!starting && (prompt || remembering) && before != null && isGlass(before)) {
				int color = terracottaColor(now);
				if (color >= 0 && !lit && !alreadyLit[color]) {
					lit = true;
					CURRENT.add(color); // a glass lit up as terracotta: the next colour of the pattern
				}
			}
			PREVIOUS.put(i, now);
		}
		if (!prompt && remembering && !CURRENT.isEmpty()) {
			PATTERN.clear();
			PATTERN.addAll(CURRENT); // the showing is over: that is the pattern to repeat
		}
		boolean showed = remembering && !prompt;
		remembering = prompt;
		if (prompt) {
			autoIndex = 0;
		}
		if (showed) {
			autoIndex = 0;
			nextClick = System.currentTimeMillis() + 200; // the start delay
		}
		autoClick(mc, chest, slots, chestSlots, prompt);
	}

	/** An item that carries a number in Ultrasequencer: any item but the glass background, the timer clock and the prompt. */
	private static boolean isNumbered(ItemStack stack) {
		if (stack.isEmpty() || stack.is(Items.CLOCK) || stack.is(Items.GLOWSTONE) || stack.is(Items.BOOKSHELF)) {
			return false;
		}
		String path = pathOf(stack.getItem());
		return !path.contains("glass") && !path.equals("barrier");
	}

	/** Ultrasequencer: the numbered items show their numbers as stack sizes while "Remember the pattern!" is up; then click them 1, 2, 3... */
	private static void ultraTick(Minecraft mc, AbstractContainerScreen<?> chest) {
		List<Slot> slots = chest.getMenu().slots;
		int chestSlots = Math.max(0, slots.size() - 36);
		boolean prompt = false;
		Map<Integer, Integer> seen = new HashMap<>();
		for (int i = 0; i < chestSlots; i++) {
			ItemStack stack = slots.get(i).getItem();
			if (stack.is(Items.GLOWSTONE) && stack.getHoverName().getString().contains("Remember the pattern!")) {
				prompt = true;
			} else if (isNumbered(stack)) {
				seen.put(stack.getCount(), i);
			}
		}
		long now = System.currentTimeMillis();
		int highest = seen.keySet().stream().max(Integer::compare).orElse(0);
		if (WigState.config.experimentsAuto && mc.player != null && highest >= 9 - WigState.config.experimentsSerums) {
			// the 9 is on the board (each serum drunk lowers it by one): the table is done and closes
			if (closeAt == 0) {
				closeAt = now + 200;
			} else if (now >= closeAt) {
				closeAt = 0;
				mc.player.closeContainer();
			}
			return;
		}
		closeAt = 0;
		if (prompt) {
			if (!seen.isEmpty()) {
				ULTRA.clear();
				ULTRA.putAll(seen); // the numbers as they are shown now
			}
			ultraRemembering = true;
			ultraIndex = 0;
			return;
		}
		if (ultraRemembering) {
			ultraRemembering = false;
			ultraIndex = 0;
			nextClick = now + 200; // the start delay
		}
		if (!WigState.config.experimentsAuto || mc.gameMode == null || mc.player == null
				|| ultraIndex >= ULTRA.size() || now < nextClick) {
			return;
		}
		Integer slot = ULTRA.get(ultraIndex + 1);
		if (slot == null) {
			return;
		}
		mc.gameMode.handleContainerInput(chest.getMenu().containerId, slot, 2, ContainerInput.CLONE, mc.player);
		ultraIndex++;
		nextClick = now + 200 + (long) (Math.random() * 51);
	}

	/** The number on the bookshelf called "Round: X", or 0 when it is not there. */
	private static int roundOf(List<Slot> slots, int chestSlots) {
		for (int i = 0; i < chestSlots; i++) {
			ItemStack stack = slots.get(i).getItem();
			if (stack.is(Items.BOOKSHELF)) {
				java.util.regex.Matcher m = java.util.regex.Pattern.compile("Round:\\s*(\\d+)").matcher(stack.getHoverName().getString());
				if (m.find()) {
					return Integer.parseInt(m.group(1));
				}
			}
		}
		return 0;
	}

	/** Auto Experiments: pick-block (middle click) a glass of each colour of the pattern in order, 200 to 250 ms apart. */
	private static void autoClick(Minecraft mc, AbstractContainerScreen<?> chest, List<Slot> slots, int chestSlots, boolean prompt) {
		if (!WigState.config.experimentsAuto || mc.gameMode == null || mc.player == null) {
			return;
		}
		long now = System.currentTimeMillis();
		if (roundOf(slots, chestSlots) >= 13 - WigState.config.experimentsSerums) {
			// the last round: each serum drunk takes one round off the 13, so the table is done and closes
			if (closeAt == 0) {
				closeAt = now + 200;
			} else if (now >= closeAt) {
				closeAt = 0;
				mc.player.closeContainer();
			}
			return;
		}
		closeAt = 0;
		if (prompt || autoIndex >= PATTERN.size() || now < nextClick) {
			return;
		}
		int want = PATTERN.get(autoIndex);
		for (int i = 0; i < chestSlots; i++) {
			String path = pathOf(slots.get(i).getItem().getItem());
			if (colorOf(path, "_stained_glass") == want || colorOf(path, "_stained_glass_pane") == want) {
				mc.gameMode.handleContainerInput(chest.getMenu().containerId, i, 2, ContainerInput.CLONE, mc.player);
				autoIndex++;
				nextClick = System.currentTimeMillis() + 200 + (long) (Math.random() * 51);
				return;
			}
		}
	}

	private static void draw(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		if (!WigState.config.experimentsHud || !isChronomatron(screen)) {
			return;
		}
		List<Integer> list = remembering ? CURRENT : PATTERN;
		if (list.isEmpty()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		int line = font.lineHeight + 1;
		int numberWidth = font.width(list.size() + ".") + 3;
		int width = 0;
		for (int color : list) {
			width = Math.max(width, font.width(NAMES[color]));
		}
		int boxWidth = PADDING * 2 + numberWidth + width;
		int boxHeight = PADDING * 2 + list.size() * line - 1;
		// to the left of the chest (176 wide), or to the right when there is no room
		int left = (screen.width - 176) / 2 - boxWidth - 6;
		if (left < 2) {
			left = (screen.width + 176) / 2 + 6;
		}
		int top = Math.max(2, (screen.height - 222) / 2);
		for (int i = 0; i < list.size(); i++) {
			int y = top + PADDING + i * line;
			// no background: the text carries its own black shadow, one pixel down and right, under the game's usual one
			shadowed(graphics, font, (i + 1) + ".", left + PADDING, y, 0xFF000000 | GOLD);
			shadowed(graphics, font, NAMES[list.get(i)], left + PADDING + numberWidth, y, 0xFF000000 | COLORS[list.get(i)]);
		}
	}

	private static void shadowed(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
		graphics.text(font, text, x + 1, y + 1, 0xFF000000, false);
		graphics.text(font, text, x, y, color, true);
	}

	/** For the dev test: the colours of the pattern being shown. */
	public static List<String> shownNames() {
		List<String> names = new ArrayList<>();
		for (int color : (remembering ? CURRENT : PATTERN)) {
			names.add(NAMES[color]);
		}
		return names;
	}
}
