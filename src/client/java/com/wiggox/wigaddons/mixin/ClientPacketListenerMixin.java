package com.wiggox.wigaddons.mixin;

import com.wiggox.wigaddons.Runner;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	/** The server sends its tick count every 20 ticks: how fast those arrive is how fast the server is running. */
	@Inject(method = "handleSetTime", at = @At("HEAD"))
	private void wigaddons$serverTime(ClientboundSetTimePacket packet, CallbackInfo ci) {
		Runner.onServerTime(packet.gameTime());
	}
}
