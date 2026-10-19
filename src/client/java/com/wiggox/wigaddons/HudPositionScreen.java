package com.wiggox.wigaddons;

import com.wiggox.wigaddons.gui.RouteIcons;
import com.wiggox.wigaddons.gui.WigGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Drag the HUD box where you want it. Scroll to resize it, R to put it back, Esc to finish. */
public final class HudPositionScreen extends net.minecraft.client.gui.screens.Screen {
	private static final int KEY_R = 82;

	private boolean dragging;
	private int grabX;
	private int grabY;
	private int[] box = {0, 0, 0, 0};

	public HudPositionScreen() {
		super(Component.literal("HUD position"));
	}

	public static void open() {
		Compat.setScreen(Minecraft.getInstance(), new HudPositionScreen());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		extractTransparentBackground(graphics);
		Route route = WigState.selectedRoute();
		String name = route != null ? route.name() : "My route";
		ItemStack icon = route != null ? RouteIcons.itemStack(route.icon()) : ItemStack.EMPTY;
		box = HudOverlay.draw(graphics, minecraft, name, "01:23", icon, java.util.List.of("Eyes: 0"));
		graphics.centeredText(font, "Drag the box to move it. Scroll to resize. R puts it back. Esc to finish.", width / 2, height / 2 - 10, 0xFFFFFFFF);
		graphics.centeredText(font, String.format("Position %.0f%% across, %.0f%% down, scale %.1f", WigState.config.hudX * 100, WigState.config.hudY * 100, WigState.config.hudScale),
			width / 2, height / 2 + 4, 0xFFAAAAAA);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 && inside(event.x(), event.y())) {
			dragging = true;
			grabX = (int) event.x() - box[0];
			grabY = (int) event.y() - box[1];
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging) {
			moveTo((int) event.x() - grabX, (int) event.y() - grabY);
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging) {
			dragging = false;
			WigState.saveConfig();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		WigConfig config = WigState.config;
		config.hudScale = Math.max(0.5, Math.min(4.0, Math.round((config.hudScale + Math.signum(scrollY) * 0.1) * 10) / 10.0));
		WigState.saveConfig();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == KEY_R) {
			resetPosition();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		WigState.saveConfig();
		WigGui.open("hud");
	}

	/** Moves the box so its top-left corner is at this screen position. */
	void moveTo(int left, int top) {
		WigConfig config = WigState.config;
		config.hudX = HudOverlay.fractionOf(left, width, box[2]);
		config.hudY = HudOverlay.fractionOf(top, height, box[3]);
	}

	/** Puts the box back at its starting position and size. */
	public static void resetPosition() {
		WigConfig defaults = new WigConfig();
		WigState.config.hudX = defaults.hudX;
		WigState.config.hudY = defaults.hudY;
		WigState.config.hudScale = defaults.hudScale;
		WigState.saveConfig();
	}

	private boolean inside(double x, double y) {
		return x >= box[0] && x < box[0] + box[2] && y >= box[1] && y < box[1] + box[3];
	}
}
