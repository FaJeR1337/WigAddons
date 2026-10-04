package com.fajer.wigaddons.gui;

import io.github.notenoughupdates.moulconfig.gui.GuiComponent;
import io.github.notenoughupdates.moulconfig.gui.component.RowComponent;
import io.github.notenoughupdates.moulconfig.gui.editors.ComponentEditor;
import io.github.notenoughupdates.moulconfig.observer.GetSetter;
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption;
import org.jetbrains.annotations.NotNull;

/** One waypoint: its "x y z" in a text field, with buttons to move it up or down the list or delete it. */
final class WaypointEditor extends ComponentEditor {
	private final GuiComponent row;

	WaypointEditor(ProcessedOption option, GetSetter<String> text, Runnable up, Runnable down, Runnable remove) {
		super(option);
		row = wrapComponent(new RowComponent(
			Buttons.textField(100, text),
			Buttons.button("Up", 22, up),
			Buttons.button("Down", 30, down),
			Buttons.button("Del", 22, remove)));
	}

	@Override
	public @NotNull GuiComponent getDelegate() {
		return row;
	}
}
