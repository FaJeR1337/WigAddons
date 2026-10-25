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
	private static long nextLeft;
	private static long nextRight;

	private Autoclicker() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(Autoclicker::tick);
	}

	private static void tick(Minecraft mc) {
		WigConfig config = WigState.config;
		if (!config.autoclickerOn || mc.player == null || Compat.screen(mc) != null) {
			nextLeft = 0;
			nextRight = 0;
			return;
		}
		long now = System.currentTimeMillis();
		if (held(mc, config.autoLeftKey) && config.autoLeftCps > 0) {
			if (now >= nextLeft) {
				click(mc.options.keyAttack);
				nextLeft = now + interval(config.autoLeftCps);
			}
		} else {
			nextLeft = 0;
		}
		if (held(mc, config.autoRightKey) && config.autoRightCps > 0) {
			if (now >= nextRight) {
				click(mc.options.keyUse);
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
		KeyMapping.click(InputConstants.getKey(mapping.saveString()));
	}

	/** Whether the picked key is down. 0 and -1 are none; -100, -99 and -98 are the left, right and middle mouse buttons. */
	private static boolean held(Minecraft mc, int key) {
		if (key == 0 || key == -1) {
			return false;
		}
		if (key < -1) {
			return switch (key + 100) {
				case 0 -> mc.mouseHandler.isLeftPressed();
				case 1 -> mc.mouseHandler.isRightPressed();
				case 2 -> mc.mouseHandler.isMiddlePressed();
				default -> false;
			};
		}
		return InputConstants.isKeyDown(mc.getWindow(), key);
	}
}
