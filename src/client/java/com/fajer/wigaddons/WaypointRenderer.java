package com.fajer.wigaddons;

import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.phys.Vec3;

/** Draws the selected route as numbered boxes joined by lines, when waypoints are switched on. */
public final class WaypointRenderer {
	private static final int BOX_COLOR = 0xFF55FFFF;
	private static final int RADIUS_COLOR = 0xFFFFAA00;
	private static final int LINE_COLOR = 0xFFFFFFFF;

	private WaypointRenderer() {
	}

	public static void register() {
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
			if (!WigState.config.showWaypoints) {
				return;
			}
			// the route being edited is the live copy; otherwise read what is saved
			Route route = WigState.editing != null ? WigState.editing : WigState.selectedRoute();
			if (route == null) {
				return;
			}
			try {
				draw(route);
			} catch (IllegalStateException e) {
				// no gizmo collector at this point of the frame; nothing to draw into
			}
		});
	}

	private static void draw(Route route) {
		List<Waypoint> points = route.waypoints();
		Vec3 previous = null;
		for (int i = 0; i < points.size(); i++) {
			Waypoint w = points.get(i);
			net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(w.x(), w.y(), w.z());
			Gizmos.cuboid(pos, GizmoStyle.stroke(BOX_COLOR, 3.0F));
			// the circle on top of the block is how close the player has to get before turning toward the next waypoint
			if (route.radius() > 0) {
				Gizmos.circle(new Vec3(w.x() + 0.5, w.y() + 1.02, w.z() + 0.5), (float) route.radius(), GizmoStyle.stroke(RADIUS_COLOR, 2.0F));
			}
			Vec3 top = new Vec3(w.x() + 0.5, w.y() + 1.6, w.z() + 0.5);
			Gizmos.billboardText(String.valueOf(i + 1), top, TextGizmo.Style.forColorAndCentered(0xFFFFFFFF).withScale(1.5F)).setAlwaysOnTop();
			Vec3 center = new Vec3(w.x() + 0.5, w.y() + 0.5, w.z() + 0.5);
			if (previous != null) {
				Gizmos.line(previous, center, LINE_COLOR, 2.0F);
			}
			previous = center;
		}
	}
}
