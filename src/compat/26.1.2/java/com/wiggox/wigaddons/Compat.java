package com.wiggox.wigaddons;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The few game calls that differ between Minecraft versions: this is the 26.1.2 one. */
public final class Compat {
	private Compat() {
	}

	public static Screen screen(Minecraft mc) {
		return mc.screen;
	}

	public static void setScreen(Minecraft mc, Screen screen) {
		mc.setScreen(screen);
	}

	public static void setTimes(Minecraft mc, int in, int stay, int out) {
		mc.gui.setTimes(in, stay, out);
	}

	public static void setTitle(Minecraft mc, Component text) {
		mc.gui.setTitle(text);
	}

	public static void setSubtitle(Minecraft mc, Component text) {
		mc.gui.setSubtitle(text);
	}

	public static void clearTitles(Minecraft mc) {
		mc.gui.clearTitles();
	}

	public static void resetTitleTimes(Minecraft mc) {
		mc.gui.resetTitleTimes();
	}

	public static void setOverlayMessage(Minecraft mc, Component text, boolean animate) {
		mc.gui.setOverlayMessage(text, animate);
	}
}
