package com.fajer.wigaddons.gui;

import io.github.notenoughupdates.moulconfig.common.IItemStack;
import io.github.notenoughupdates.moulconfig.common.text.StructuredText;
import io.github.notenoughupdates.moulconfig.gui.GuiComponent;
import io.github.notenoughupdates.moulconfig.gui.GuiImmediateContext;
import io.github.notenoughupdates.moulconfig.gui.MouseEvent;
import io.github.notenoughupdates.moulconfig.gui.component.ItemStackComponent;
import io.github.notenoughupdates.moulconfig.gui.editors.GuiOptionEditorAccordion;
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

/** A route's collapsible header, with the route's icon drawn at its right-hand end. */
final class RouteAccordionEditor extends GuiOptionEditorAccordion {
	private static final int ICON = 18;

	private final GuiComponent withIcon;

	RouteAccordionEditor(ProcessedOption option, int id, Supplier<IItemStack> icon) {
		super(option, id);
		GuiComponent header = super.getDelegate();
		ItemStackComponent stack = new ItemStackComponent(Buttons.readOnly(icon));
		withIcon = new GuiComponent() {
			@Override
			public int getWidth() {
				return header.getWidth();
			}

			@Override
			public int getHeight() {
				return header.getHeight();
			}

			@Override
			public void render(@NotNull GuiImmediateContext context) {
				header.render(context);
				stack.render(context.translated(context.getWidth() - ICON - 4, 1, ICON, ICON));
			}

			@Override
			public boolean mouseEvent(@NotNull MouseEvent mouseEvent, @NotNull GuiImmediateContext context) {
				return header.mouseEvent(mouseEvent, context);
			}
		};
	}

	@Override
	public @NotNull GuiComponent getDelegate() {
		return withIcon;
	}
}
