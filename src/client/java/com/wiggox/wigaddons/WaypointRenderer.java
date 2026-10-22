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
				if (Runner.isHunting() && Runner.activeConfig().killHomeSet) {
					var cfg = Runner.activeConfig();
					Gizmos.cuboid(new BlockPos((int) Math.floor(cfg.killHomeX), (int) Math.floor(cfg.killHomeY) - 1, (int) Math.floor(cfg.killHomeZ)), GizmoStyle.stroke(0xFF55FF55, 3.0F));
					Gizmos.billboardText("Hunt home", new Vec3(cfg.killHomeX, cfg.killHomeY + 0.9, cfg.killHomeZ), TextGizmo.Style.forColorAndCentered(0xFF55FF55).withScale(1.2F)).setAlwaysOnTop();
				}
				Runner.FishTimer fishTimer = Runner.fishTimer();
				if (fishTimer != null) {
					Gizmos.billboardText(fishTimer.text(), new Vec3(fishTimer.x(), fishTimer.y(), fishTimer.z()), TextGizmo.Style.forColorAndCentered(fishTimer.pastThreshold() ? 0xFF55FF55 : 0xFFFFFFFF).withScale(0.45F)).setAlwaysOnTop(); // white (green past the Slug Fish time), the size of an ordinary name tag, the same at any distance
				}
				if (HeadScanner.showing) {
					drawHeads();
				}
				if (Runner.isHunting() && WigState.config.killShowTags) {
					drawHuntTags();
				}
				if (WigState.config.followShowTrail) {
					drawTrail();
				}
			} catch (IllegalStateException e) {
				// no gizmo collector at this point of the frame; nothing to draw into
			}
		});
	}

	/** The player heads in the world, through walls with their coordinates, for finding out where a formation's head is. */
	private static void drawHeads() {
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		for (HeadScanner.Head head : HeadScanner.cached(mc)) {
			BlockPos pos = head.pos();
			Gizmos.cuboid(pos, GizmoStyle.stroke(0xFFFF55FF, 3.0F)).setAlwaysOnTop();
			Gizmos.billboardText("head " + pos.getX() + " " + pos.getY() + " " + pos.getZ(), new Vec3(pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5),
				TextGizmo.Style.forColorAndCentered(0xFFFF55FF).withScale(0.6F)).setAlwaysOnTop();
		}
	}

	/** The mod's own name tag (white, like an ordinary one, but drawn over walls) and a box coloured by kind over every mob being hunted. */
	private static void drawHuntTags() {
		net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		for (Runner.HuntMark mark : Runner.huntMarks()) {
			int color = mark.dead() ? 0xFF888888 : mark.target() ? 0xFF55FF55 : mark.special() ? 0xFFFFD700 : 0xFFFF7755;
			double distance = Math.sqrt(player.distanceToSqr(mark.x(), mark.y(), mark.z()));
			// colour codes: the name in the colour of its kind, the distance and notes in greys
			String kind = mark.dead() ? "\u00a77" : mark.target() ? "\u00a7a" : mark.special() ? "\u00a76" : "\u00a7c";
			String text = (mark.special() ? "\u00a76\u00a7l* " : "") + kind + mark.name() + " \u00a7f" + Math.round(distance) + "m"
				+ (mark.dead() ? " \u00a78(dead?)" : !mark.visible() ? " \u00a78(hidden)" : "");
			// the same scale at every distance, like an ordinary name tag: it shrinks with distance in the world
			float scale = (float) WigState.config.killTagScale;
			Gizmos.billboardText(text, new Vec3(mark.x(), mark.eyeY() + 1.2, mark.z()), TextGizmo.Style.forColorAndCentered(0xFFFFFFFF).withScale(scale)).setAlwaysOnTop();
			if (!WigState.config.killShowHitboxes) {
				continue;
			}
			Gizmos.cuboid(new net.minecraft.world.phys.AABB(mark.x() - 0.4, mark.y(), mark.z() - 0.4, mark.x() + 0.4, mark.eyeY() + 0.3, mark.z() + 0.4),
				GizmoStyle.stroke(color, 2.0F)).setAlwaysOnTop();
		}
	}

	private static final int TRAIL_COLOR = 0xFFFFAA00;
	private static final int NEXT_COLOR = 0xFF55FF55;
	private static final int LAST_SEEN_COLOR = 0xFFFF5555;
	private static final int PATH_COLOR = 0xFF55FFFF;

	private static void marker(Vec3 at, double half, int color) {
		Gizmos.cuboid(new net.minecraft.world.phys.AABB(at.x - half, at.y - half, at.z - half, at.x + half, at.y + half, at.z + half),
			GizmoStyle.strokeAndFill(color, 2.0F, (color & 0x00FFFFFF) | 0x99000000)).setAlwaysOnTop();
	}

	/** A dotted line of small cubes; thin line gizmos are unreliable, cuboids always draw. */
	private static void dotted(Vec3 from, Vec3 to, int color) {
		double length = from.distanceTo(to);
		int steps = (int) Math.min(60, Math.max(1, Math.round(length / 0.35)));
		for (int i = 1; i < steps; i++) {
			marker(from.lerp(to, i / (double) steps), 0.04, color);
		}
	}

	/** The trail of the player being followed: where they walked, the point being walked to, and where they were last seen. */
	private static void drawTrail() {
		FollowPlanner.Snapshot trail = Runner.followTrail();
		if (trail == null) {
			return;
		}
		Vec3 previous = null;
		for (double[] point : trail.trail()) {
			Vec3 here = new Vec3(point[0], point[1] + 0.15, point[2]);
			marker(here, 0.12, TRAIL_COLOR);
			if (previous != null) {
				dotted(previous, here, TRAIL_COLOR);
			}
			previous = here;
		}
		if (trail.lastSeen() != null) {
			marker(new Vec3(trail.lastSeen()[0], trail.lastSeen()[1] + 0.15, trail.lastSeen()[2]), 0.25, LAST_SEEN_COLOR);
		}
		if (trail.next() != null) {
			Vec3 next = new Vec3(trail.next()[0], trail.next()[1] + 0.15, trail.next()[2]);
			marker(next, 0.25, NEXT_COLOR);
			net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
			if (player != null) {
				dotted(new Vec3(player.getX(), player.getY() + 0.15, player.getZ()), next, PATH_COLOR);
			}
		}
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
