package com.wiggox.wigaddons.gui;

import io.github.notenoughupdates.moulconfig.common.IMinecraft;
import io.github.notenoughupdates.moulconfig.common.RenderContext;
import io.github.notenoughupdates.moulconfig.common.text.StructuredText;
import io.github.notenoughupdates.moulconfig.gui.GuiComponent;
import io.github.notenoughupdates.moulconfig.gui.GuiImmediateContext;
import io.github.notenoughupdates.moulconfig.gui.MouseEvent;
import io.github.notenoughupdates.moulconfig.gui.editors.ComponentEditor;
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption;
import java.util.function.IntConsumer;
import org.jetbrains.annotations.NotNull;

/** A row of tab buttons across the full width of the list, with the selected one picked out. */
final class TabBarEditor extends ComponentEditor {
	private static final int HEIGHT = 20;
	private static final int SELECTED = 0x55FFFF;
	private static final int NORMAL = 0xC0C0C0;

	private final GuiComponent bar;

	TabBarEditor(ProcessedOption option, String[] labels, int selected, IntConsumer onSelect) {
		super(option);
		bar = new GuiComponent() {
			@Override
			public int getWidth() {
				return 0;
			}

			@Override
			public int getHeight() {
				return HEIGHT;
			}

			@Override
			public void render(@NotNull GuiImmediateContext context) {
				RenderContext rc = context.getRenderContext();
				int width = context.getWidth();
				for (int i = 0; i < labels.length; i++) {
					int from = width * i / labels.length;
					int to = width * (i + 1) / labels.length;
					// a pressed-in look for the selected tab, a raised one for the others
					rc.drawDarkRect(from, 0, to - from - 2, HEIGHT, i != selected);
					rc.drawStringCenteredScaledMaxWidth(StructuredText.of(labels[i]), IMinecraft.INSTANCE.getDefaultFontRenderer(),
						(from + to - 2) / 2, HEIGHT / 2 + 1, false, to - from - 8, i == selected ? SELECTED : NORMAL);
				}
			}

			@Override
			public boolean mouseEvent(@NotNull MouseEvent mouseEvent, @NotNull GuiImmediateContext context) {
				if (mouseEvent instanceof MouseEvent.Click click && click.getMouseState() && click.getMouseButton() == 0 && context.isHovered()) {
					int tab = Math.min(labels.length - 1, Math.max(0, context.getMouseX() * labels.length / Math.max(1, context.getWidth())));
					if (tab != selected) {
						onSelect.accept(tab);
					}
					return true;
				}
				return false;
			}
		};
	}

	@Override
	public int getHeight() {
		return HEIGHT;
	}

	@Override
	public @NotNull GuiComponent getDelegate() {
		return bar;
	}
}
