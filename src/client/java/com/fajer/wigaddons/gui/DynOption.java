package com.fajer.wigaddons.gui;

import io.github.notenoughupdates.moulconfig.Config;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;
import io.github.notenoughupdates.moulconfig.common.text.StructuredText;
import io.github.notenoughupdates.moulconfig.gui.GuiOptionEditor;
import io.github.notenoughupdates.moulconfig.processor.ProcessedCategory;
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption;
import java.lang.reflect.Type;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * One row of the settings screen. MoulConfig normally reads rows from annotated fields; routes are made while the
 * game runs, so each row here reads and writes its value through plain functions instead.
 */
final class DynOption implements ProcessedOption {
	private static final SearchTag[] NO_TAGS = new SearchTag[0];

	private final StructuredText name;
	private final StructuredText description;
	private final String path;
	private final DynCategory category;
	private final Config config;
	private final Type type;
	private final Supplier<Object> getter;
	private final Predicate<Object> setter;

	int accordionId = -1;
	GuiOptionEditor editor;

	DynOption(String name, String description, String path, DynCategory category, Config config, Type type,
		Supplier<Object> getter, Predicate<Object> setter) {
		this.name = StructuredText.of(name);
		this.description = StructuredText.of(description);
		this.path = path;
		this.category = category;
		this.config = config;
		this.type = type;
		this.getter = getter;
		this.setter = setter;
	}

	@Override
	public SearchTag[] getSearchTags() {
		return NO_TAGS;
	}

	@Override
	public int getAccordionId() {
		return accordionId;
	}

	@Override
	public GuiOptionEditor getEditor() {
		return editor;
	}

	@Override
	public ProcessedCategory getCategory() {
		return category;
	}

	@Override
	public StructuredText getName() {
		return name;
	}

	@Override
	public StructuredText getDescription() {
		return description;
	}

	@Override
	public String getPath() {
		return path;
	}

	@Override
	public Config getConfig() {
		return config;
	}

	@Override
	public Object get() {
		return getter.get();
	}

	@Override
	public Type getType() {
		return type;
	}

	@Override
	public boolean set(Object value) {
		return setter.test(value);
	}

	@Override
	public void explicitNotifyChange() {
	}

	@Override
	public String getDebugDeclarationLocation() {
		return "wigaddons:" + path;
	}
}
