package com.wiggox.wigaddons;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Clicks the left and right mouse button for you while the key picked for it is held. Each click is the ordinary click
 * of the button (as if it had been pressed), so blocks, entities and items behave as they would for a real click.
 * The speed is the chosen clicks per second, a little different every click (one more or less) so it isn't a fixed beat.
 */
public final class Autoclicker {
	/** Dev test only: pretend every picked key is held, and count the clicks sent. */
	public static boolean testHeld;
	public static int clicks;
	private static boolean pressedLeft;
	private static boolean pressedRight;
	private static long nextLeft;
	private static long nextRight;

	private Autoclicker() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(Autoclicker::tick);
	}

	private static void tick(Minecraft mc) {
		WigConfig config = WigState.config;
		// the press of the last click ends one tick after it started, like a real click, so blocks get their break progress
		if (pressedLeft) {
			mc.options.keyAttack.setDown(false);
			pressedLeft = false;
		}
		if (pressedRight) {
			mc.options.keyUse.setDown(false);
			pressedRight = false;
		}
		if (!config.autoclickerOn || mc.player == null || Compat.screen(mc) != null) {
			nextLeft = 0;
			nextRight = 0;
			return;
		}
		long now = System.currentTimeMillis();
		if (held(mc, config.autoLeftKey) && !isAttackOrUse(mc, config.autoLeftKey) && config.autoLeftCps > 0) {
			if (now >= nextLeft) {
				click(mc.options.keyAttack);
				pressedLeft = true;
				nextLeft = now + interval(config.autoLeftCps);
			}
		} else {
			nextLeft = 0;
		}
		if (held(mc, config.autoRightKey) && !isAttackOrUse(mc, config.autoRightKey) && config.autoRightCps > 0) {
			if (now >= nextRight) {
				click(mc.options.keyUse);
				pressedRight = true;
				nextRight = now + interval(config.autoRightCps);
			}
		} else {
			nextRight = 0;
		}
	}

	/** Milliseconds to the next click: the clicks per second, one more or less, as the humanizer. */
	private static long interval(int cps) {
		int speed = Math.max(1, cps + (int) Math.floor(Math.random() * 3) - 1);
		return 1000L / speed;
	}

	private static void click(KeyMapping mapping) {
		clicks++;
		mapping.setDown(true);
		KeyMapping.click(InputConstants.getKey(mapping.saveString()));
	}

	/** The key bound to the mapping in the picker's numbering: a key code, or the plain button number for the mouse. */
	private static int codeOf(KeyMapping mapping) {
		return InputConstants.getKey(mapping.saveString()).getValue();
	}

	/** Whether the key is the one bound to attack or to use/place: those are what this clicks, so holding one would fight the clicks. */
	public static boolean isAttackOrUse(Minecraft mc, int key) {
		return key != 0 && (key == codeOf(mc.options.keyAttack) || key == codeOf(mc.options.keyUse));
	}

	/** Whether the picked key is down. 0 and -1 are none; 1 to 9 are mouse buttons (the picker keeps them as their number). */
	private static boolean held(Minecraft mc, int key) {
		if (key == 0 || key == -1) {
			return false;
		}
		if (testHeld) {
			return true;
		}
		if (key >= 1 && key <= 9) {
			// the picker keeps a mouse button as its plain number: 1 right, 2 middle, 3 and 4 the side buttons (0, the left one, can't be told from none)
			return org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc.getWindow().handle(), key) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
		}
		return InputConstants.isKeyDown(mc.getWindow(), key);
	}
}
