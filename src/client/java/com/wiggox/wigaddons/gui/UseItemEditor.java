package com.wiggox.wigaddons.gui;

import io.github.notenoughupdates.moulconfig.common.IItemStack;
import io.github.notenoughupdates.moulconfig.gui.GuiComponent;
import io.github.notenoughupdates.moulconfig.gui.component.ItemStackComponent;
import io.github.notenoughupdates.moulconfig.gui.component.RowComponent;
import io.github.notenoughupdates.moulconfig.gui.editors.ComponentEditor;
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

/** A Use button with the item it would pick shown beside it. */
final class UseItemEditor extends ComponentEditor {
	private final GuiComponent row;

	UseItemEditor(ProcessedOption option, Supplier<IItemStack> preview, Runnable use) {
		super(option);
		row = wrapComponent(new RowComponent(
			Buttons.button("Use", 30, use),
			new ItemStackComponent(Buttons.readOnly(preview))));
	}

	@Override
	public @NotNull GuiComponent getDelegate() {
		return row;
	}
}
