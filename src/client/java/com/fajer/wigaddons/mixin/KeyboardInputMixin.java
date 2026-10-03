package com.fajer.wigaddons.mixin;

import com.fajer.wigaddons.Runner;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {
	/** After the real keys are read, replaces them with "forward + sprint (+ jump)" while a route is running. */
	@Inject(method = "tick", at = @At("TAIL"))
	private void wigaddons$runRoute(CallbackInfo ci) {
		if (Runner.wantsInput()) {
			this.keyPresses = new Input(true, false, false, false, Runner.wantsJump(), false, true);
			this.moveVector = new Vec2(0.0F, 1.0F);
		}
	}
}
