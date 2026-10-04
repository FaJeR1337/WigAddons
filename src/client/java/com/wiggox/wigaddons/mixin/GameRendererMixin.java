package com.wiggox.wigaddons.mixin;

import com.wiggox.wigaddons.Runner;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	/** The start of the frame's extraction, which is where the camera reads the player's view. */
	@Inject(method = "extract", at = @At("HEAD"))
	private void wigaddons$turnView(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
		Runner.frame(Minecraft.getInstance());
	}
}
