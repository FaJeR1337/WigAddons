package com.fajer.wigaddons.gui;

import com.fajer.wigaddons.ItemSearch;
import io.github.notenoughupdates.moulconfig.common.IItemStack;
import io.github.notenoughupdates.moulconfig.platform.MoulConfigPlatform;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Looks items up for route icons. */
final class RouteIcons {
	private static List<String> ids;

	private RouteIcons() {
	}

	/** Every vanilla item id without the "minecraft:" part. */
	static List<String> allIds() {
		if (ids == null) {
			ids = BuiltInRegistries.ITEM.keySet().stream()
				.filter(id -> id.getNamespace().equals("minecraft"))
				.map(Identifier::getPath)
				.toList();
		}
		return ids;
	}

	static ItemSearch.Result search(String text) {
		return ItemSearch.find(allIds(), text);
	}

	/** The item to draw for an icon id (a whole id, as saved on the route), or an empty stack. */
	static IItemStack stack(String id) {
		if (id == null || id.isEmpty()) {
			return MoulConfigPlatform.wrap(ItemStack.EMPTY);
		}
		Identifier key = Identifier.tryParse("minecraft:" + ItemSearch.normalise(id));
		Item item = key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
		try {
			return MoulConfigPlatform.wrap(item == null ? ItemStack.EMPTY : new ItemStack(item));
		} catch (RuntimeException e) {
			// items can't be made before a world is loaded (on the title screen); show no icon then
			return MoulConfigPlatform.wrap(ItemStack.EMPTY);
		}
	}
}
