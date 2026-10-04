package com.fajer.wigaddons.gui;

import io.github.notenoughupdates.moulconfig.common.text.StructuredText;
import io.github.notenoughupdates.moulconfig.processor.ProcessedCategory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A tab in the list on the left of the settings screen. */
final class DynCategory implements ProcessedCategory {
	private final String id;
	private final StructuredText name;
	private final StructuredText description;
	final List<DynOption> options = new ArrayList<>();
	final Map<Integer, DynOption> accordionAnchors = new LinkedHashMap<>();

	DynCategory(String id, String name, String description) {
		this.id = id;
		this.name = StructuredText.of(name);
		this.description = StructuredText.of(description);
	}

	@Override
	public StructuredText getDisplayName() {
		return name;
	}

	@Override
	public StructuredText getDescription() {
		return description;
	}

	@Override
	public String getIdentifier() {
		return id;
	}

	@Override
	public String getParentCategoryId() {
		return null;
	}

	@Override
	public List<? extends io.github.notenoughupdates.moulconfig.processor.ProcessedOption> getOptions() {
		return options;
	}

	@Override
	public Map<? extends Integer, ? extends io.github.notenoughupdates.moulconfig.processor.ProcessedOption> getAccordionAnchors() {
		return accordionAnchors;
	}

	@Override
	public String getDebugDeclarationLocation() {
		return "wigaddons:" + id;
	}
}
