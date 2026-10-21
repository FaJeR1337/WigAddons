package com.wiggox.wigaddons;

import com.wiggox.wigaddons.gui.RouteIcons;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** The box on screen showing which route is running and for how long. */
public final class HudOverlay {
	private static final int PADDING = 4;
	private static final int ICON = 16;
	private static final int BACKGROUND = 0x99000000;
	private static final int NAME_COLOR = 0xFFFFFF;
	private static final int TIME_COLOR = 0x55FFFF;

	private HudOverlay() {
	}

	public static void register() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("wigaddons", "run_hud"), (graphics, tracker) -> {
			WigConfig config = WigState.config;
			String name = Runner.hudName();
			if (!config.showHud || name == null || !Runner.hudVisible()) {
				return;
			}
			Route route = Runner.hudRoute();
			ItemStack icon = route == null ? ItemStack.EMPTY : RouteIcons.itemStack(route.icon());
			String clock = TimeFormat.clock(Runner.hudSeconds()) + (Runner.hudLoop() ? " (loop)" : "");
			if (Runner.isRunning() && !Double.isNaN(Runner.followDistance())) {
				clock += String.format("  %.1f blocks", Runner.followDistance());
			}
			draw(graphics, Minecraft.getInstance(), name, clock, icon, Runner.statLines());
		});
	}

	/** The size of the box, before scaling. */
	public static int[] size(Minecraft mc, String name, String clock, ItemStack icon, java.util.List<String> stats) {
		WigConfig config = WigState.config;
		Font font = mc.font;
		boolean showIcon = config.hudShowIcon && !icon.isEmpty();
		int textWidth = font.width(name);
		int lines = 1;
		if (config.hudShowTimer) {
			textWidth = Math.max(textWidth, font.width(clock));
			lines = 2;
		}
		for (String stat : stats) {
			textWidth = Math.max(textWidth, font.width(stat));
			lines++;
		}
		int width = PADDING * 2 + (showIcon ? ICON + 3 : 0) + textWidth;
		int height = PADDING * 2 + Math.max(showIcon ? ICON : 0, lines * font.lineHeight + (lines - 1));
		return new int[] {width, height};
	}

	/**
	 * Draws the box where the settings put it and returns where it ended up on screen: left, top, width, height (after scaling).
	 */
	public static int[] draw(GuiGraphicsExtractor graphics, Minecraft mc, String name, String clock, ItemStack icon, java.util.List<String> stats) {
		WigConfig config = WigState.config;
		int[] size = size(mc, name, clock, icon, stats);
		double scale = config.hudScale;
		int scaledWidth = (int) Math.round(size[0] * scale);
		int scaledHeight = (int) Math.round(size[1] * scale);
		int left = (int) Math.round(config.hudX * Math.max(0, mc.getWindow().getGuiScaledWidth() - scaledWidth));
		int top = (int) Math.round(config.hudY * Math.max(0, mc.getWindow().getGuiScaledHeight() - scaledHeight));

		graphics.pose().pushMatrix();
		graphics.pose().translate(left, top);
		graphics.pose().scale((float) scale, (float) scale);
		graphics.fill(0, 0, size[0], size[1], BACKGROUND);
		int x = PADDING;
		if (config.hudShowIcon && !icon.isEmpty()) {
			graphics.item(icon, x, (size[1] - ICON) / 2);
			x += ICON + 3;
		}
		Font font = mc.font;
		int lines = (config.hudShowTimer ? 2 : 1) + stats.size();
		int textHeight = lines * font.lineHeight + (lines - 1);
		int y = (size[1] - textHeight) / 2;
		graphics.text(font, name, x, y, 0xFF000000 | NAME_COLOR, true);
		int line = 1;
		if (config.hudShowTimer) {
			graphics.text(font, clock, x, y + font.lineHeight + 1, 0xFF000000 | TIME_COLOR, true);
			line = 2;
		}
		// the stats of a Zealots run, under the time, always shown
		for (String stat : stats) {
			graphics.text(font, stat, x, y + line * (font.lineHeight + 1), 0xFF000000 | NAME_COLOR, true);
			line++;
		}
		graphics.pose().popMatrix();
		return new int[] {left, top, scaledWidth, scaledHeight};
	}

	/** Turns a screen position into the 0..1 settings, for a box of this size. */
	public static double fractionOf(int position, int screen, int boxSize) {
		int room = screen - boxSize;
		return room <= 0 ? 0 : Math.max(0, Math.min(1, position / (double) room));
	}
}
