package com.fajer.wigaddons.gui;

import com.fajer.wigaddons.WigState;
import io.github.notenoughupdates.moulconfig.Config;
import io.github.notenoughupdates.moulconfig.common.text.StructuredText;
import net.fabricmc.loader.api.FabricLoader;

/** The look and behaviour of the settings screen as a whole: its title bar and when it saves. */
final class WigGuiConfig extends Config {
	@Override
	public StructuredText getTitle() {
		String version = FabricLoader.getInstance().getModContainer("wigaddons")
			.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
		return StructuredText.of("WigAddons " + version + " by FaJeR1337");
	}

	@Override
	public void saveNow() {
		WigState.saveConfig();
	}
}
