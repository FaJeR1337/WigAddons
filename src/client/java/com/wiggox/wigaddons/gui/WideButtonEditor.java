package com.wiggox.wigaddons.gui;

import io.github.notenoughupdates.moulconfig.GuiTextures;
import io.github.notenoughupdates.moulconfig.common.IMinecraft;
import io.github.notenoughupdates.moulconfig.common.text.StructuredText;
import io.github.notenoughupdates.moulconfig.gui.GuiComponent;
import io.github.notenoughupdates.moulconfig.gui.GuiImmediateContext;
import io.github.notenoughupdates.moulconfig.gui.MouseEvent;
import io.github.notenoughupdates.moulconfig.gui.editors.ComponentEditor;
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption;
import org.jetbrains.annotations.NotNull;

/** The ordinary button (48 units wide, 16 high) but with a width of our own, for labels the standard one is too narrow for. */
final class WideButtonEditor extends ComponentEditor {
	private final GuiComponent component;

	WideButtonEditor(ProcessedOption option, StructuredText label, int width, Runnable action) {
		super(option);
		component = wrapComponent(new GuiComponent() {
			@Override
			public int getWidth() {
				return width;
			}

			@Override
			public int getHeight() {
				return 16;
			}

			@Override
			public void render(@NotNull GuiImmediateContext context) {
				context.getRenderContext().drawTexturedRect(GuiTextures.BUTTON, 0f, 0f, context.getWidth(), context.getHeight());
				context.getRenderContext().drawStringCenteredScaledMaxWidth(label, IMinecraft.INSTANCE.getDefaultFontRenderer(),
					context.getWidth() / 2f, context.getHeight() / 2f, false, context.getWidth() - 4, 0xFF303030);
			}

			@Override
			public boolean mouseEvent(@NotNull MouseEvent mouseEvent, @NotNull GuiImmediateContext context) {
				if (mouseEvent instanceof MouseEvent.Click click && click.getMouseState() && context.isHovered() && click.getMouseButton() == 0) {
					action.run();
					return true;
				}
				return super.mouseEvent(mouseEvent, context);
			}
		});
	}

	@Override
	public @NotNull GuiComponent getDelegate() {
		return component;
	}
}
