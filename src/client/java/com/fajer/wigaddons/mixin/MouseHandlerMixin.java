package com.fajer.wigaddons.mixin;

import com.fajer.wigaddons.Runner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.MouseHandler;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Shadow
	private double accumulatedDX;

	@Shadow
	private double accumulatedDY;

	/** While a route runs the mouse doesn't turn the player. The movement is dropped, not saved up for later. */
	@Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
	private void wigaddons$lockMouse(double deltaTime, CallbackInfo ci) {
		if (Runner.isRunning()) {
			this.accumulatedDX = 0.0;
			this.accumulatedDY = 0.0;
			ci.cancel();
		}
	}
}
