package com.fajer.wigaddons.gui;

import io.github.notenoughupdates.moulconfig.common.IItemStack;
import io.github.notenoughupdates.moulconfig.gui.GuiComponent;
import io.github.notenoughupdates.moulconfig.gui.component.ItemStackComponent;
import io.github.notenoughupdates.moulconfig.gui.component.RowComponent;
import io.github.notenoughupdates.moulconfig.gui.editors.ComponentEditor;
import io.github.notenoughupdates.moulconfig.observer.GetSetter;
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

/** The route's icon: type part of an item's name, see the item that matches, and press Use to pick it. */
final class IconEditor extends ComponentEditor {
	private final GuiComponent row;

	IconEditor(ProcessedOption option, GetSetter<String> text, Supplier<IItemStack> preview, Runnable use) {
		super(option);
		row = wrapComponent(new RowComponent(
			Buttons.textField(90, text),
			new ItemStackComponent(Buttons.readOnly(preview)),
			Buttons.button("Use", 26, use)));
	}

	@Override
	public @NotNull GuiComponent getDelegate() {
		return row;
	}
}
