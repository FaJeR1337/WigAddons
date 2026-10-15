package com.wiggox.wigaddons.mixin;

import com.wiggox.wigaddons.Runner;
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
	/** After the real keys are read, replaces them with "forward + sprint (+ A or D, + jump)" while a route is running. */
	@Inject(method = "tick", at = @At("TAIL"))
	private void wigaddons$runRoute(CallbackInfo ci) {
		if (Runner.wantsInput()) {
			int strafe = Runner.strafe();
			int forward = Runner.forward();
			this.keyPresses = new Input(forward > 0, forward < 0, strafe > 0, strafe < 0, Runner.wantsJump(), Runner.wantsSneak(), Runner.sprint());
			this.moveVector = strafe == 0 && forward == 0 ? Vec2.ZERO : new Vec2(strafe, forward).normalized();
		}
	}
}
