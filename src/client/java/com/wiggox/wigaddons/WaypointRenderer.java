package com.wiggox.wigaddons;

import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.phys.Vec3;

/** Draws every route whose waypoints are switched on: boxes, numbers, radius circles and lines, as that route is set up to. */
public final class WaypointRenderer {
	private static final int OPAQUE = 0xFF000000;
	private static final int RADIUS_COLOR = 0xFFFFAA00;
	private static final int LINE_COLOR = 0xFFFFFFFF;

	private WaypointRenderer() {
	}

	public static void register() {
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
			try {
				for (Route route : WigState.routes()) {
					if (route.showWaypoints()) {
						draw(route);
					}
				}
			} catch (IllegalStateException e) {
				// no gizmo collector at this point of the frame; nothing to draw into
			}
		});
	}

	private static void draw(Route route) {
		List<Waypoint> points = route.waypoints();
		int boxColor = OPAQUE | route.boxColor();
		Vec3 previous = null;
		for (int i = 0; i < points.size(); i++) {
			Waypoint w = points.get(i);
			Gizmos.cuboid(new BlockPos(w.x(), w.y(), w.z()), GizmoStyle.stroke(boxColor, 3.0F));
			// the circle on top of the block is how close the player has to get before turning toward the next waypoint
			if (route.showRadius() && route.radius() > 0) {
				Gizmos.circle(new Vec3(w.x() + 0.5, w.y() + 1.02, w.z() + 0.5), (float) route.radius(), GizmoStyle.stroke(RADIUS_COLOR, 2.0F));
			}
			if (route.showNumbers()) {
				Vec3 top = new Vec3(w.x() + 0.5, w.y() + 1.6, w.z() + 0.5);
				Gizmos.billboardText(String.valueOf(i + 1), top, TextGizmo.Style.forColorAndCentered(0xFFFFFFFF).withScale(1.5F)).setAlwaysOnTop();
			}
			Vec3 center = new Vec3(w.x() + 0.5, w.y() + 0.5, w.z() + 0.5);
			if (route.showLines() && previous != null) {
				Gizmos.line(previous, center, LINE_COLOR, 2.0F);
			}
			previous = center;
		}
	}
}
