package dev.ping.client.render;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.ping.client.PingClient;
import dev.ping.client.PingConfig;
import dev.ping.ping.Ping;
import dev.ping.ping.PingConstants;
import dev.ping.ping.PingManager;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix3x2fStack;

/**
 * Draws markers.
 *
 * <p>A marker is projected from a world position to the screen, which is also how
 * Fortnite does it: on screen it sits over the spot, and off screen it is pushed to the
 * screen border so you always know which way to look.
 *
 * <p>Registered as a HUD element via {@code HudElementRegistry}. In 26.x HUD code
 * "extracts" draw commands into a render state rather than drawing immediately.
 */
public final class PingHud implements HudElement {
	private static final Identifier MARKER_TEXTURE = PingClient.id("textures/ping/marker.png");
	private static final int MARKER_TEXTURE_SIZE = 128;

	private static final Identifier RING_TEXTURE = PingClient.id("textures/ping/ring.png");
	private static final int RING_TEXTURE_SIZE = 128;

	private static final Identifier DISC_TEXTURE = PingClient.id("textures/ping/disc.png");
	private static final int DISC_TEXTURE_SIZE = 128;

	/** The pin's width-to-height ratio, carried by the artwork itself. */
	private static final float PIN_ASPECT = 0.62F;

	/** Minecraft's default font line height, used to centre the label on its ink. */
	private static final float FONT_LINE_HEIGHT = 9.0F;

	/** The size an item icon is authored at, in GUI pixels. */
	private static final float ITEM_ICON_AUTHORED = 16.0F;

	/** How many chevrons the context block draws. */
	private static final int CHEVRONS = 3;

	/** Milliseconds per chevron cycle in the context block. */
	private static final float CHEVRON_PERIOD = 900.0F;

	private final PingManager pings;

	/** Last line-of-sight answer per marker, so it is not raycast every frame. */
	private final Map<UUID, Occlusion> occlusions = new HashMap<>();

	public PingHud(PingManager pings) {
		this.pings = pings;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();

		if (client.level == null || client.player == null || this.pings.size() == 0) {
			return;
		}

		Camera camera = client.gameRenderer.mainCamera();
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		long now = System.currentTimeMillis();
		Vec3 cameraPos = camera.position();
		float partialTick = delta.getGameTimeDeltaPartialTick(false);

		// Markers have no range limit of their own; the practical bound is how far the
		// world is loaded.
		double visibleRange = renderDistanceBlocks(client) * 1.2D;

		for (Ping ping : this.pings.all()) {
			this.drawMarker(graphics, client, camera, cameraPos, ping, width, height, now, visibleRange, partialTick);
		}

		this.drawContext(graphics, client, camera, width, height, now);
	}

	private void drawMarker(GuiGraphicsExtractor graphics, Minecraft client, Camera camera, Vec3 cameraPos,
			Ping ping, int width, int height, long now, double visibleRange, float partialTick) {
		Vec3 renderPos = interpolated(ping, client, partialTick);
		ScreenProjector.Result projected = ScreenProjector.project(camera, renderPos, width, height);

		if (projected == null) {
			return;
		}

		double distance = cameraPos.distanceTo(renderPos);

		if (distance > visibleRange) {
			return;
		}

		float alpha = ping.fadeAlpha(now);

		if (alpha <= 0.02F) {
			return;
		}

		// Looking at a marker keeps it solid even through terrain; a marker behind
		// terrain that you are NOT looking at drops to a faint ghost so it cannot be
		// mistaken for something in front of you.
		boolean looked = looksAt(client, renderPos);

		if (!looked && this.isOccluded(client, ping, cameraPos, now)) {
			alpha *= (float) PingConfig.occludedAlpha();
		}

		float pinHeight = (float) PingConfig.markerHeightFraction() * height * popScale(ping, now);

		float x = projected.x();
		float y = projected.y();

		if (!projected.onScreen() || projected.behind()) {
			Border border = toBorder(projected, width, height, pinHeight);
			x = border.x();
			y = border.y();
		}

		// Positions are floats but the draw calls take ints, so the fractional part is
		// carried by the transform. Without this every marker snaps to the GUI grid and
		// steps visibly as the camera pans.
		int anchorX = Math.round(x);
		int anchorY = Math.round(y);
		int bodyY = Math.round(anchorY - pinHeight * (float) PingConfig.markerLift());

		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x - anchorX, y - anchorY);

		if (ping.item() != null) {
			drawItemMarker(graphics, ping, anchorX, bodyY, height, alpha);
		} else {
			drawIcon(graphics, withAlpha(ping.color(), alpha), anchorX, bodyY, pinHeight);
		}

		if (alpha > 0.3F) {
			drawDistance(graphics, client.font, anchorX, bodyY, pinHeight, distance, alpha);
		}

		pose.popMatrix();
	}

	/**
	 * The item marker: the item's own icon, sitting on a dark disc inside a ring.
	 */
	private static void drawItemMarker(GuiGraphicsExtractor graphics, Ping ping, int x, int y,
			int height, float alpha) {
		int diameter = Math.max(8, Math.round(PingConstants.RING_DIAMETER_FRACTION * height));
		int offset = Math.round(diameter * 0.5F);
		int left = x - offset;
		int top = y - offset;

		blitTinted(graphics, DISC_TEXTURE, DISC_TEXTURE_SIZE, DISC_TEXTURE_SIZE,
				left, top, diameter, diameter, withAlpha(0x000000, alpha * PingConstants.ITEM_BACKDROP_ALPHA));
		blitTinted(graphics, RING_TEXTURE, RING_TEXTURE_SIZE, RING_TEXTURE_SIZE,
				left, top, diameter, diameter, withAlpha(ping.color(), alpha));

		// The item is authored at 16 GUI px, which is about twice the ring's inner size,
		// so it is scaled to fit inside.
		float scale = (diameter * PingConstants.ITEM_ICON_FILL) / ITEM_ICON_AUTHORED;
		float half = ITEM_ICON_AUTHORED * 0.5F;

		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		graphics.item(new ItemStack(ping.item()), Math.round(-half), Math.round(-half));
		pose.popMatrix();
	}

	private static void drawIcon(GuiGraphicsExtractor graphics, int color, int x, int y, float pinHeight) {
		int h = Math.max(1, Math.round(pinHeight));

		// The pin's tail points down, so the icon's bottom edge is anchored to the
		// projected point rather than its centre.
		blitTinted(graphics, MARKER_TEXTURE, MARKER_TEXTURE_SIZE, MARKER_TEXTURE_SIZE,
				x - h / 2, Math.round(y) - h, h, h, color);
	}

	private static void drawDistance(GuiGraphicsExtractor graphics, Font font, int x, int y, float pinHeight,
			double distance, float alpha) {
		String text = Math.round(distance) + "m";

		float pinWidth = pinHeight * PIN_ASPECT;
		int textX = Math.round(x + pinWidth * 0.5F + pinHeight * PingConstants.LABEL_GAP);
		int textY = Math.round(y - pinHeight * PingConstants.LABEL_RISE - FONT_LINE_HEIGHT * 0.5F);

		graphics.text(font, text, textX, textY, withAlpha(0xFFFFFFFF, alpha));
	}

	/**
	 * The block Fortnite shows when you look straight at a marker: what it is, which way
	 * it lies, and how to take it down.
	 */
	private void drawContext(GuiGraphicsExtractor graphics, Minecraft client, Camera camera,
			int width, int height, long now) {
		Ping looked = this.lookedAt(client);

		if (looked == null) {
			return;
		}

		ScreenProjector.Result projected = ScreenProjector.project(camera, looked.pos(), width, height);

		if (projected == null || projected.behind()) {
			return;
		}

		boolean mine = looked.author().equals(client.player.getUUID());
		String title = looked.label();
		String action = mine ? "Cancel marker" : looked.authorName();

		float pinHeight = (float) PingConfig.markerHeightFraction() * height;
		int textWidth = Math.max(client.font.width(title), client.font.width(action));
		int chevronWidth = Math.round(pinHeight * 1.6F);
		int blockWidth = Math.max(textWidth, chevronWidth * 2);

		int x = Math.round(projected.x() + pinHeight * 2.6F);
		int y = Math.round(projected.y() - pinHeight * 4.2F);

		if (x + blockWidth > width - 6) {
			x = Math.round(projected.x() - pinHeight * 2.6F - blockWidth);
		}

		if (y < 6) {
			y = 6;
		}

		// The connector runs from the marker itself out to the block, so the two read as
		// one thing rather than as unrelated overlays.
		drawLine(graphics, projected.x(), projected.y(), x, y + FONT_LINE_HEIGHT * 1.5F,
				withAlpha(looked.color(), 0.75F));

		int lineHeight = (int) FONT_LINE_HEIGHT;
		graphics.text(client.font, title, x, y, 0xFFFFFFFF);
		graphics.fill(x, y + lineHeight + 2, x + blockWidth, y + lineHeight + 4, withAlpha(looked.color(), 1.0F));
		graphics.text(client.font, action, x, y + lineHeight + 8, 0xFFFFFFFF);

		// Chevrons pointing back at the marker, drifting towards it.
		float cycle = (now % (long) CHEVRON_PERIOD) / CHEVRON_PERIOD;
		float drift = cycle * pinHeight * 0.9F;
		int chevronColour = withAlpha(looked.color(), 0.9F);

		for (int i = 0; i < CHEVRONS; i++) {
			float cx = x + textWidth + pinHeight * 0.5F + i * pinHeight * 0.8F - drift;
			float cy = y + lineHeight * 0.5F;
			drawChevron(graphics, cx, cy, pinHeight * 0.34F, chevronColour);
		}
	}

	/** A single ">" arrowhead made of two strokes. */
	private static void drawChevron(GuiGraphicsExtractor graphics, float x, float y, float size, int colour) {
		drawLine(graphics, x, y - size, x + size * 0.7F, y, colour);
		drawLine(graphics, x + size * 0.7F, y, x, y + size, colour);
	}

	/** An arbitrary-angle line, drawn by rotating a thin rectangle onto it. */
	private static void drawLine(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1, int colour) {
		float dx = x1 - x0;
		float dy = y1 - y0;
		float length = (float) Math.sqrt(dx * dx + dy * dy);

		if (length < 1.5F) {
			return;
		}

		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x0, y0);
		pose.rotate((float) Math.atan2(dy, dx));
		graphics.fill(0, -1, Math.round(length), 1, colour);
		pose.popMatrix();
	}

	/** The marker the crosshair is closest to, if it is within the look cone. */
	private Ping lookedAt(Minecraft client) {
		Ping best = null;
		double bestDot = PingConstants.LOOK_CONE_COS;

		for (Ping ping : this.pings.all()) {
			double dot = dotTo(client, ping.pos());

			if (dot > bestDot) {
				bestDot = dot;
				best = ping;
			}
		}

		return best;
	}

	/** True when the crosshair is within the look cone of this position. */
	private static boolean looksAt(Minecraft client, Vec3 target) {
		return dotTo(client, target) >= PingConstants.LOOK_CONE_COS;
	}

	private static double dotTo(Minecraft client, Vec3 target) {
		Vec3 to = target.subtract(client.player.getEyePosition());

		if (to.lengthSqr() < 1.0E-6D) {
			return 0.0D;
		}

		return to.normalize().dot(client.player.getLookAngle());
	}

	/**
	 * The position to draw a marker at.
	 *
	 * <p>A marker that follows an entity stores its position as sampled on the last tick,
	 * at 20 Hz. Drawing that directly makes the marker judder as the camera pans, because
	 * the world is drawn at frame rate and the marker is not. Re-resolving the entity here
	 * with the frame's partial tick puts it back in step.
	 */
	private static Vec3 interpolated(Ping ping, Minecraft client, float partialTick) {
		if (!ping.isAttached() || client.level == null) {
			return ping.pos();
		}

		Entity entity = client.level.getEntity(ping.attachedEntityId());

		if (entity == null) {
			return ping.pos();
		}

		return entity.getPosition(partialTick).add(0.0D, entity.getBbHeight() * 0.6D, 0.0D);
	}

	/**
	 * Where to put a marker that is outside the viewport.
	 *
	 * <p>This scales the marker's <em>projected</em> position out onto the screen border.
	 * That is the whole point: the on-screen path uses the projection matrix, so
	 * computing the off-screen position a different way made the two disagree near the
	 * edges and the marker jumped as it crossed between them. Deriving both from the same
	 * projection keeps it continuous.
	 *
	 * <p>The bounds are inset by the marker's own extent, not just a margin. The pin is
	 * drawn <em>above</em> its anchor, so a marker pinned to the bare top edge had its
	 * whole pin pushed off-screen and simply appeared to be missing.
	 */
	private static Border toBorder(ScreenProjector.Result projected, int width, int height, float pinHeight) {
		float centreX = width * 0.5F;
		float centreY = height * 0.5F;
		float dx = projected.x() - centreX;
		float dy = projected.y() - centreY;

		// Exactly behind the camera gives no direction to work with, so push it downward.
		if (Math.abs(dx) < 1.0E-3F && Math.abs(dy) < 1.0E-3F) {
			dy = 1.0F;
		}

		float halfPinWidth = pinHeight * PIN_ASPECT * 0.5F;
		float spanX = Math.max(4.0F, Math.min(
				centreX - PingConstants.EDGE_MARGIN - halfPinWidth,
				width - PingConstants.EDGE_MARGIN - halfPinWidth - centreX));
		float spanY = Math.max(4.0F, Math.min(
				centreY - PingConstants.EDGE_MARGIN - pinHeight,
				height - PingConstants.EDGE_MARGIN - centreY));

		// Scale the centre -> marker ray until it lands exactly on the border rectangle.
		float scale = Math.min(spanX / Math.abs(dx), spanY / Math.abs(dy));

		return new Border(centreX + dx * scale, centreY + dy * scale);
	}

	/**
	 * A marker's line-of-sight, cached. Raycasting every marker every frame was a visible
	 * cost and the answer does not change meaningfully inside
	 * {@link PingConstants#OCCLUSION_INTERVAL_MS}.
	 */
	private boolean isOccluded(Minecraft client, Ping ping, Vec3 cameraPos, long now) {
		Occlusion cached = this.occlusions.get(ping.author());

		if (cached != null && now - cached.at() < PingConstants.OCCLUSION_INTERVAL_MS) {
			return cached.hidden();
		}

		boolean hidden = false;

		if (client.level != null && client.player != null) {
			BlockHitResult hit = client.level.clip(new ClipContext(
					cameraPos, ping.pos(), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, client.player));
			hidden = hit.getType() != HitResult.Type.MISS;
		}

		this.occlusions.put(ping.author(), new Occlusion(hidden, now));

		return hidden;
	}

	private static void blitTinted(GuiGraphicsExtractor graphics, Identifier texture,
			int texWidth, int texHeight, int x, int y, int width, int height, int color) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F, width, height,
				texWidth, texHeight, texWidth, texHeight, color);
	}

	private static double renderDistanceBlocks(Minecraft client) {
		return Math.max(16, client.options.getEffectiveRenderDistance()) * 16.0D;
	}

	private static float popScale(Ping ping, long now) {
		float t = ping.spawnProgress(now);
		float inverse = 1.0F - t;

		return PingConstants.SPAWN_SCALE + (1.0F - PingConstants.SPAWN_SCALE) * (1.0F - inverse * inverse);
	}

	private static int withAlpha(int argb, float alpha) {
		int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));

		return (argb & 0x00FFFFFF) | (a << 24);
	}

	/** A marker's last known line-of-sight answer. */
	private record Occlusion(boolean hidden, long at) {
	}

	/** A position on the screen border. */
	private record Border(float x, float y) {
	}
}
