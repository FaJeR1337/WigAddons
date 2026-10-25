package com.wiggox.wigaddons.gui;

import io.github.notenoughupdates.moulconfig.Config;
import io.github.notenoughupdates.moulconfig.common.text.StructuredText;
import io.github.notenoughupdates.moulconfig.gui.GuiOptionEditor;
import io.github.notenoughupdates.moulconfig.gui.editors.GuiOptionEditorAccordion;
import io.github.notenoughupdates.moulconfig.gui.editors.GuiOptionEditorBoolean;
import io.github.notenoughupdates.moulconfig.gui.editors.GuiOptionEditorButton;
import io.github.notenoughupdates.moulconfig.gui.editors.GuiOptionEditorInfoText;
import io.github.notenoughupdates.moulconfig.gui.editors.GuiOptionEditorKeybind;
import io.github.notenoughupdates.moulconfig.gui.editors.GuiOptionEditorSlider;
import io.github.notenoughupdates.moulconfig.gui.editors.GuiOptionEditorText;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Adds rows to one category, keeping track of which collapsible section they sit in. */
final class OptionBuilder {
	private final DynCategory category;
	private final Config config;
	private final Deque<Integer> openSections = new ArrayDeque<>();
	private int nextSectionId;

	OptionBuilder(DynCategory category, Config config) {
		this.category = category;
		this.config = config;
	}

	private DynOption add(String key, String name, String description, java.lang.reflect.Type type,
		Supplier<Object> getter, java.util.function.Predicate<Object> setter) {
		DynOption option = new DynOption(name, description, category.getIdentifier() + "." + key, category, config, type, getter, setter);
		option.accordionId = openSections.isEmpty() ? -1 : openSections.peek();
		return option;
	}

	private DynOption put(DynOption option, GuiOptionEditor editor) {
		option.editor = editor;
		category.options.add(option);
		return option;
	}

	/** Starts a collapsible section. Everything added until {@link #endSection()} sits inside it. */
	DynOption section(String key, String name, BiFunction<DynOption, Integer, GuiOptionEditorAccordion> editorFactory) {
		DynOption option = add(key, name, "", Object.class, () -> null, v -> true);
		int id = nextSectionId++;
		GuiOptionEditorAccordion editor = editorFactory == null ? new GuiOptionEditorAccordion(option, id) : editorFactory.apply(option, id);
		option.editor = editor;
		category.options.add(option);
		category.accordionAnchors.put(id, option);
		openSections.push(id);
		return option;
	}

	DynOption section(String key, String name) {
		return section(key, name, null);
	}

	void endSection() {
		openSections.pop();
	}

	/** A row of plain information: the text goes in the description area, where long text fits. */
	void info(String key, String name, String text) {
		DynOption option = add(key, name, text, Object.class, () -> null, v -> true);
		put(option, new GuiOptionEditorInfoText(option, StructuredText.of("")));
	}

	void toggle(String key, String name, String description, BooleanSupplier getter, Consumer<Boolean> setter) {
		DynOption option = add(key, name, description, boolean.class, getter::getAsBoolean, v -> {
			setter.accept((Boolean) v);
			return true;
		});
		put(option, new GuiOptionEditorBoolean(option, -1, config));
	}

	/** A key picker: click it, then press the key. The value is the key code, 0 for none. */
	void keybind(String key, String name, String description, IntSupplier getter, IntConsumer setter) {
		DynOption option = add(key, name, description, int.class, getter::getAsInt, v -> {
			setter.accept(((Number) v).intValue());
			return true;
		});
		put(option, new GuiOptionEditorKeybind(option, 0));
	}

	/** A slider whose number box is wide enough for three digits. */
	void wideSlider(String key, String name, String description, double min, double max, double step, DoubleSupplier getter, DoubleConsumer setter) {
		DynOption option = add(key, name, description, float.class, () -> (float) getter.getAsDouble(), v -> {
			setter.accept(((Number) v).doubleValue());
			return true;
		});
		put(option, new WideSliderEditor(option, (float) min, (float) max, (float) step));
	}

	void slider(String key, String name, String description, double min, double max, double step, DoubleSupplier getter, DoubleConsumer setter) {
		// the slider hands floats around, so the value has to be one
		DynOption option = add(key, name, description, float.class, () -> (float) getter.getAsDouble(), v -> {
			setter.accept(((Number) v).doubleValue());
			return true;
		});
		if (min >= 0 && max <= 9 && step >= 1) {
			// one digit: the compact slider, as wide as a switch and lined up with them
			put(option, new WideSliderEditor(option, (float) min, (float) max, (float) step, true));
		} else {
			put(option, new GuiOptionEditorSlider(option, (float) min, (float) max, (float) step));
		}
	}

	/**
	 * A text field. What is typed is kept as typed, and handed to the setter on every change, so half-typed numbers
	 * ("6.") aren't rewritten under the cursor.
	 */
	void text(String key, String name, String description, Supplier<String> getter, Consumer<String> setter) {
		String[] typed = {null};
		DynOption option = add(key, name, description, String.class, () -> typed[0] != null ? typed[0] : getter.get(), v -> {
			typed[0] = (String) v;
			setter.accept(typed[0]);
			return true;
		});
		put(option, new GuiOptionEditorText(option, ""));
	}

	void button(String key, String name, String description, String label, Runnable action) {
		DynOption option = add(key, name, description, Runnable.class, () -> action, v -> false);
		put(option, new GuiOptionEditorButton(option, -1, StructuredText.of(label), config));
	}

	/** A button wider than the standard 48 GUI units, for a label that doesn't fit. */
	void button(String key, String name, String description, String label, int width, Runnable action) {
		DynOption option = add(key, name, description, Runnable.class, () -> action, v -> false);
		put(option, new WideButtonEditor(option, StructuredText.of(label), width, action));
	}

	/** A row with its own editor, such as a waypoint or an icon picker. */
	DynOption custom(String key, String name, String description, java.lang.reflect.Type type, Supplier<Object> getter,
		java.util.function.Predicate<Object> setter, BiFunction<DynOption, Config, GuiOptionEditor> editorFactory) {
		DynOption option = add(key, name, description, type, getter, setter);
		put(option, editorFactory.apply(option, config));
		return option;
	}
}
