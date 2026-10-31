package com.wiggox.wigaddons.mixin;

import com.wiggox.wigaddons.TabNameColor;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
	/** The text of the name above an entity: the custom colours of the tab list are put on it too. */
	@Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
	private void wigaddons$recolorNameTag(Entity entity, CallbackInfoReturnable<Component> cir) {
		Component tag = cir.getReturnValue();
		if (tag != null) {
			Component recolored = TabNameColor.recolorNameTag(tag);
			if (recolored != null) {
				cir.setReturnValue(recolored);
			}
		}
	}
}
