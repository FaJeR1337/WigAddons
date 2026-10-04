package com.wiggox.wigaddons.gui;

import io.github.notenoughupdates.moulconfig.common.IItemStack;
import io.github.notenoughupdates.moulconfig.gui.GuiComponent;
import io.github.notenoughupdates.moulconfig.gui.component.ItemStackComponent;
import io.github.notenoughupdates.moulconfig.gui.component.RowComponent;
import io.github.notenoughupdates.moulconfig.gui.editors.ComponentEditor;
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

/** One route in the list of routes to run: its icon with Start and Loop buttons (its name is the row's title). */
final class RouteRowEditor extends ComponentEditor {
	private final GuiComponent row;

	RouteRowEditor(ProcessedOption option, Supplier<IItemStack> icon, Runnable start, Runnable loop) {
		super(option);
		row = wrapComponent(new RowComponent(
			new ItemStackComponent(Buttons.readOnly(icon)),
			Buttons.button("Start", 36, start),
			Buttons.button("Loop", 32, loop)));
	}

	@Override
	public @NotNull GuiComponent getDelegate() {
		return row;
	}
}
