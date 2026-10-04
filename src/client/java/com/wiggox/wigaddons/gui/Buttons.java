package com.wiggox.wigaddons.gui;

import io.github.notenoughupdates.moulconfig.common.IMinecraft;
import io.github.notenoughupdates.moulconfig.common.text.StructuredText;
import io.github.notenoughupdates.moulconfig.gui.GuiComponent;
import io.github.notenoughupdates.moulconfig.gui.component.ButtonComponent;
import io.github.notenoughupdates.moulconfig.gui.component.TextComponent;
import io.github.notenoughupdates.moulconfig.gui.component.TextFieldComponent;
import io.github.notenoughupdates.moulconfig.observer.GetSetter;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Small pieces shared by the custom rows. */
final class Buttons {
	private Buttons() {
	}

	static GuiComponent button(String label, int width, Runnable action) {
		return new ButtonComponent(new TextComponent(StructuredText.of(label), width, TextComponent.TextAlignment.CENTER), 2, action);
	}

	static GuiComponent textField(int width, GetSetter<String> text) {
		return new TextFieldComponent(text, width, GetSetter.constant(true), "", IMinecraft.INSTANCE.getDefaultFontRenderer(), Set.of('§'));
	}

	static <T> GetSetter<T> property(Supplier<T> getter, Consumer<T> setter) {
		return new GetSetter<T>() {
			@Override
			public T get() {
				return getter.get();
			}

			@Override
			public void set(T newValue) {
				setter.accept(newValue);
			}
		};
	}

	static <T> GetSetter<T> readOnly(Supplier<T> getter) {
		return property(getter, v -> {
		});
	}
}
