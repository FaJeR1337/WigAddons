package com.wiggox.wigaddons.gui;

import io.github.notenoughupdates.moulconfig.common.IMinecraft;
import io.github.notenoughupdates.moulconfig.gui.GuiComponent;
import io.github.notenoughupdates.moulconfig.gui.GuiImmediateContext;
import io.github.notenoughupdates.moulconfig.gui.KeyboardEvent;
import io.github.notenoughupdates.moulconfig.gui.MouseEvent;
import io.github.notenoughupdates.moulconfig.gui.component.SliderComponent;
import io.github.notenoughupdates.moulconfig.gui.component.TextFieldComponent;
import io.github.notenoughupdates.moulconfig.gui.editors.ComponentEditor;
import io.github.notenoughupdates.moulconfig.observer.GetSetter;
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption;
import java.util.Set;
import java.util.function.BiFunction;
import org.jetbrains.annotations.NotNull;

/** The ordinary slider with its number box, but with a number box wide enough for three digits (the standard one is cut off at 100). */
final class WideSliderEditor extends ComponentEditor {
	private static final int SLIDER_WIDTH = 55;
	private static final int BOX_WIDTH = 34;

	private final GuiComponent component;

	WideSliderEditor(ProcessedOption option, float min, float max, float step) {
		super(option);
		component = wrapComponent(new WideSlider((GetSetter<Float>) option.intoProperty(), min, max, step));
	}

	@Override
	public @NotNull GuiComponent getDelegate() {
		return component;
	}

	private static final class WideSlider extends SliderComponent {
		private final GetSetter<Float> value;
		private final float minValue;
		private final float maxValue;
		private final float step;
		private final TextFieldComponent box;
		private String editing = "";

		WideSlider(GetSetter<Float> value, float min, float max, float step) {
			super(value, min, max, step, SLIDER_WIDTH);
			this.value = value;
			this.minValue = min;
			this.maxValue = max;
			this.step = step <= 0 ? 0.01f : step;
			TextFieldComponent[] self = new TextFieldComponent[1];
			box = new TextFieldComponent(new GetSetter<String>() {
				@Override
				public String get() {
					if (self[0] != null && self[0].isFocused()) {
						return editing;
					}
					String text = Float.toString(value.get());
					if (text.endsWith(".0")) {
						text = text.substring(0, text.length() - 2);
					}
					editing = text;
					return text;
				}

				@Override
				public void set(String text) {
					editing = text;
					float parsed;
					try {
						parsed = Float.parseFloat(text);
					} catch (NumberFormatException e) {
						parsed = 0f;
					}
					value.set(parsed);
				}
			}, BOX_WIDTH, GetSetter.constant(true), "", IMinecraft.INSTANCE.getDefaultFontRenderer(), Set.of('§'));
			self[0] = box;
		}

		@Override
		public void render(@NotNull GuiImmediateContext context) {
			context.getRenderContext().translate(-(SLIDER_WIDTH / 3), 0f);
			super.render(context);
			context.getRenderContext().translate(60f, -5f);
			box.render(context.translated(60, -5, box.getWidth(), 18));
		}

		private boolean hovered(GuiImmediateContext context) {
			return context.getMouseX() >= -SLIDER_WIDTH / 3 && context.getMouseX() < SLIDER_WIDTH - 13
				&& context.getMouseY() >= 0 && context.getMouseY() < context.getHeight();
		}

		@Override
		public boolean mouseEvent(@NotNull MouseEvent mouseEvent, @NotNull GuiImmediateContext context) {
			if (!context.getRenderContext().isMouseButtonDown(0)) {
				setClicked(false);
			}
			if (hovered(context) && mouseEvent instanceof MouseEvent.Click click && click.getMouseState() && click.getMouseButton() == 0) {
				setClicked(true);
			}
			if (getClicked()) {
				setValueFromContext(context);
				return true;
			}
			return box.mouseEvent(mouseEvent, context.translated(45, -5, box.getWidth(), 18));
		}

		@Override
		public boolean keyboardEvent(@NotNull KeyboardEvent event, @NotNull GuiImmediateContext context) {
			box.setShouldExpandToFit(true);
			return box.keyboardEvent(event, context);
		}

		@Override
		public void setValueFromContext(@NotNull GuiImmediateContext context) {
			float v = (context.getMouseX() + SLIDER_WIDTH / 3f) * (maxValue - minValue) / context.getWidth() + minValue;
			v = Math.max(minValue, Math.min(maxValue, v));
			v = Math.round(v / step) * step;
			value.set(v);
		}

		@Override
		public <T> T foldChildren(T initial, @NotNull BiFunction<GuiComponent, T, T> visitor) {
			return visitor.apply(box, initial);
		}
	}
}
