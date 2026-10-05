package com.wiggox.wigaddons.gui;

import io.github.notenoughupdates.moulconfig.common.IItemStack;
import io.github.notenoughupdates.moulconfig.gui.GuiComponent;
import io.github.notenoughupdates.moulconfig.gui.component.ItemStackComponent;
import io.github.notenoughupdates.moulconfig.gui.component.RowComponent;
import io.github.notenoughupdates.moulconfig.gui.editors.ComponentEditor;
import io.github.notenoughupdates.moulconfig.observer.GetSetter;
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

/**
 * The route's icon in one row: a text field for part of an item's name, and underneath it a Use button with the
 * item that matches shown beside it.
 */
final class IconEditor extends ComponentEditor {
	private final GuiComponent row;

	IconEditor(ProcessedOption option, GetSetter<String> text, Supplier<IItemStack> preview, Runnable use) {
		super(option);
		row = wrapComponent(Buttons.textField(80, text), new RowComponent(
			Buttons.button("Use", 30, use),
			new ItemStackComponent(Buttons.readOnly(preview))));
	}

	@Override
	public @NotNull GuiComponent getDelegate() {
		return row;
	}
}
