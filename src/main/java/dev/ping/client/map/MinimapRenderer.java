package dev.ping.client.map;

import java.util.List;

import dev.ping.client.PingClient;
import dev.ping.client.poi.ClientPois;
import dev.ping.ping.Ping;
import dev.ping.ping.PingColors;
import dev.ping.ping.PingManager;
import dev.ping.poi.Poi;
import dev.ping.poi.PoiStore;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import org.joml.Matrix3x2fStack;

/**
 * Draws a map: terrain, then what is on it.
 *
 * <p>Shared by the corner minimap and the full-size map. The chrome around the map (the
 * readout column and the location label) is drawn by the corner only, because Fortnite's
 * full map screen is a different design, not a bigger copy of this one.
 *
 * <p>The glyphs are the real ones, pulled from a capture at native resolution: the player's
 * pointer, the eliminations skull and the players bust. Colour is not baked into any of
 * them; they are white alpha masks tinted at draw time, so the same asset serves the white
 * readouts and the coloured markers.
 *
 * <p>What the map shows is Fortnite's set: markers in their author colours, item marks with
 * their icon, threat marks as the skull-on-disc Fortnite draws, and a marker past the frame
 * pulled to the edge rather than dropped. What it deliberately does not show is other
 * players, Fortnite hides everyone who has not been marked, and a map that quietly
 * revealed every player in range would be a radar rather than a ping system.
 */
public final class MinimapRenderer {
	// Source sizes, which the blit needs to map UVs. Each is the asset's own pixel size.
	private static final Identifier MARKER_TEXTURE = PingClient.id("textures/ping/marker.png");
	private static final Identifier DISC_TEXTURE = PingClient.id("textures/ping/disc.png");
	private static final Identifier SKULL_TEXTURE = PingClient.id("textures/ping/skull.png");
	private static final Identifier PERSON_TEXTURE = PingClient.id("textures/ping/person.png");
	private static final Identifier ARROW_TEXTURE = PingClient.id("textures/ping/arrow.png");
	private static final Identifier ARROW_HALO_TEXTURE = PingClient.id("textures/ping/arrow_halo.png");

	/**
	 * Source sizes, one per asset, passed to the blit so it can normalise the UVs.
	 *
	 * <p>Each asset is shipped at 4× its extracted size with smoothing, so that drawing it
	 * small averages four samples per output pixel instead of dropping three of them: the
	 * difference between an icon that looks low-resolution and one that looks drawn. The
	 * numbers below are the shipped assets' own dimensions.
	 */
	private static final int LEGACY_SIZE = 128;
	private static final int SKULL_SIZE = 128;
	private static final int PERSON_SIZE = 84;
	private static final int ARROW_SIZE = 64;
	private static final int ARROW_HALO_SIZE = 72;

	/**
	 * The pointer's width over its height. Measured from the extracted artwork (12×16), so
	 * the arrow keeps Fortnite's proportions rather than being drawn as a generic triangle.
	 */
	private static final float ARROW_ASPECT = 12.0F / 16.0F;

	/** How much of the threat disc the skull fills. */
	private static final float THREAT_SKULL_FILL = 0.74F;

	/** The skull's own height over width (27×32 extracted), so it is not squashed square. */
	private static final float SKULL_ASPECT = 32.0F / 27.0F;

	/** The bust's own height over width (20×21 extracted). */
	private static final float PERSON_ASPECT = 21.0F / 20.0F;

	/** Which of the two readouts a row is, top to bottom. */
	public static final int ROW_KILLS = 0;
	public static final int ROW_PLAYERS = 1;

	private MinimapRenderer() {
	}

	/**
	 * Draws the map itself into {@code viewport}.
	 *
	 * @param grid true for the full-size map, which has room for a coordinate grid
	 */
	public static void render(GuiGraphicsExtractor graphics, Minecraft client, Minimap minimap,
			PingManager pings, MapViewport viewport, long now, boolean grid) {
		Identifier texture = minimap.texture(client, viewport.centerX(), viewport.centerZ(),
				(int) Math.round(viewport.blocksAcross()), now);

		drawTerrain(graphics, viewport, texture);

		if (grid) {
			drawGrid(graphics, client, viewport);
		}

		drawAllies(graphics, client, viewport);
		drawBiomeLabels(graphics, client, viewport, minimap.biomeLabels(now));
		drawPoiLabels(graphics, client, viewport);
		drawPings(graphics, client, pings, viewport, now);
		drawPlayer(graphics, client, viewport);
	}

	/**
	 * Biome names, drawn at the middle of each biome.
	 *
	 * <p>Fortnite names locations on its map, at the place itself. This is the same idea with
	 * the only naming Minecraft has: the label belongs to a region of terrain rather than to
	 * the player, so it appears where the biome is and only while that spot is on the map.
	 * A label pinned to the map's own edge told you nothing you could not already see.
	 */
	private static void drawBiomeLabels(GuiGraphicsExtractor graphics, Minecraft client, MapViewport viewport,
			List<MinimapTerrain.BiomeLabel> labels) {
		for (MinimapTerrain.BiomeLabel label : labels) {
			float x = viewport.screenX(label.x());
			float y = viewport.screenY(label.z());

			if (!viewport.contains(x, y) || label.name().isEmpty()) {
				continue;
			}

			drawOutlinedText(graphics, client, label.name(),
					Math.round(x - client.font.width(label.name()) * 0.5F),
					Math.round(y - client.font.lineHeight * 0.5F));
		}
	}

	/**
	 * Named places, each labelled at its own spot on the map.
	 *
	 * <p>Drawn here, beside the biome labels, rather than by either map: both maps show the
	 * same world, so a name drawn by one of them was a name the other silently lost. It goes
	 * through the viewport like everything else on the map, because the full-size map zooms
	 * and pans and the corner map does neither.
	 */
	private static void drawPoiLabels(GuiGraphicsExtractor graphics, Minecraft client, MapViewport viewport) {
		if (client.level == null) {
			return;
		}

		String world = ClientPois.worldKey(client);
		String dimension = ClientPois.dimensionOf(client.level);

		for (Poi poi : PoiStore.all(world)) {
			// A Nether name has no business on an Overworld map at the same coordinates.
			if (!poi.dimension().equals(dimension)) {
				continue;
			}

			float px = viewport.screenX(poi.x() + 0.5D);
			float py = viewport.screenY(poi.z() + 0.5D);

			if (!viewport.contains(px, py)) {
				continue;
			}

			// The same outlined treatment the biome labels get, so a name reads over any
			// terrain instead of only over dark ground.
			drawOutlinedText(graphics, client, poi.name(),
					Math.round(px - client.font.width(poi.name()) * 0.5F),
					Math.round(py - client.font.lineHeight * 0.5F));
		}
	}

	/**
	 * White text with a one-pixel black edge.
	 *
	 * <p>Used instead of Minecraft's drop shadow, which draws the whole glyph again one pixel
	 * away, at the readouts' reduced size that fills the counter of a "0" and turns a digit
	 * into a blob. An edge keeps the letterform intact while still reading over any terrain.
	 */
	static void drawOutlinedText(GuiGraphicsExtractor graphics, Minecraft client, String text, int x, int y) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				if (dx != 0 || dy != 0) {
					graphics.text(client.font, text, x + dx, y + dy, MinimapConstants.LABEL_OUTLINE_COLOR, false);
				}
			}
		}

		graphics.text(client.font, text, x, y, MinimapConstants.LABEL_COLOR, false);
	}

	/**
	 * The Fortnite readout column: glyphs and counts, right-aligned against the map's left
	 * edge, drawn outside it.
	 *
	 * <p>No plate behind them. The capture shows the readouts sitting straight on the world,
	 * and the dark area around them in that frame is the world's own rock and sky behind
	 * them, not a panel.
	 *
	 * @param values the count for {@link #ROW_KILLS} and {@link #ROW_PLAYERS}
	 */
	public static void drawReadouts(GuiGraphicsExtractor graphics, Minecraft client, MapViewport viewport, int[] values) {
		Identifier[] glyphs = { SKULL_TEXTURE, PERSON_TEXTURE };
		int[] sizes = { SKULL_SIZE, PERSON_SIZE };
		float screenHeight = graphics.guiHeight();
		float mapLeft = viewport.x();

		float glyphEdge = mapLeft - MinimapConstants.READOUT_GLYPH_GAP_FRACTION * screenHeight;
		float textEdge = mapLeft - MinimapConstants.READOUT_TEXT_GAP_FRACTION * screenHeight;
		float pitch = MinimapConstants.READOUT_PITCH_FRACTION * screenHeight;
		float first = MinimapConstants.READOUT_FIRST_FRACTION * screenHeight;
		float glyphBox = MinimapConstants.READOUT_GLYPH_FRACTION * screenHeight;

		for (int row = 0; row < glyphs.length; row++) {
			float centreY = viewport.y() + first + row * pitch;

			// The glyph is right-aligned on its edge and vertically centred on the row. The
			// skull and the bust are different shapes in the same box (look at them: the
			// skull is wider than it is tall, the bust the other way), so each is drawn at
			// its own aspect inside that box rather than stretched to fill it.
			int width = Math.max(2, Math.round(glyphBox));
			int height = Math.max(2, Math.round(glyphBox * glyphAspect(sizes[row])));
			blit(graphics, glyphs[row], sizes[row],
					Math.round(glyphEdge) - width, Math.round(centreY) - height / 2, width, height, 0xFFFFFFFF);

			String text = Integer.toString(Math.max(0, values[row]));

			// Fortnite's readout text is smaller than Minecraft's font can draw, so it is
			// scaled about its own right edge and mid-line: the anchor stays put and only the
			// glyphs shrink. Drawn with an edge rather than a shadow, because at this size a
			// shadow closes up the hole in a zero.
			int textWidth = client.font.width(text);
			float scale = MinimapConstants.READOUT_TEXT_SCALE;
			float anchorX = textEdge - textWidth;
			// The ink sits below the middle of a text line (a line is 9px tall but the glyphs
			// are about 7) so centring the line leaves the number riding high beside its icon.
			float anchorY = centreY - client.font.lineHeight * 0.5F + 1.0F;

			Matrix3x2fStack pose = graphics.pose();
			pose.pushMatrix();
			pose.translate(anchorX, anchorY);
			pose.scale(scale, scale);

			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					if (dx != 0 || dy != 0) {
						graphics.text(client.font, text, dx, dy, MinimapConstants.LABEL_OUTLINE_COLOR, false);
					}
				}
			}

			graphics.text(client.font, text, 0, 0, MinimapConstants.READOUT_COLOR, false);
			pose.popMatrix();
		}
	}


	/** The map's own pixels. A bare square: no plate, no border, no shadow, square corners. */
	private static void drawTerrain(GuiGraphicsExtractor graphics, MapViewport viewport, Identifier texture) {
		int left = Math.round(viewport.x());
		int top = Math.round(viewport.y());
		int size = Math.round(viewport.size());

		if (texture == null) {
			// No world yet: a flat plate reads as a map that has not loaded, which is what is
			// happening.
			graphics.fill(left, top, left + size, top + size, MinimapConstants.UNKNOWN_COLOR);

			return;
		}

		// The texture is composed around an origin snapped to the *texel* grid. Blitting it at
		// the viewport's position would therefore make the map step by a texel at a time as you
		// walk; the remainder is applied as a pose translation, which is what turns those steps
		// into a smooth pan.
		float shiftX = (float) ((MinimapTexture.snapToTexel(viewport.centerX(), (int) Math.round(viewport.blocksAcross()))
				- viewport.centerX()) / viewport.blocksAcross() * viewport.size());
		float shiftY = (float) ((MinimapTexture.snapToTexel(viewport.centerZ(), (int) Math.round(viewport.blocksAcross()))
				- viewport.centerZ()) / viewport.blocksAcross() * viewport.size());
		int drawX = Math.round(left + shiftX);
		int drawY = Math.round(top + shiftY);

		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(left + shiftX - drawX, top + shiftY - drawY);

		// The 13-argument form, with the source region stated explicitly. The shorter
		// overloads reuse the *drawn* size as the source size, so passing a drawn size
		// smaller than the texture samples a corner of the map, scaled up.
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, drawX, drawY, 0.0F, 0.0F,
				size, size, MinimapConstants.TEXELS, MinimapConstants.TEXELS,
				MinimapConstants.TEXELS, MinimapConstants.TEXELS, 0xFFFFFFFF);

		pose.popMatrix();
	}

	/**
	 * A coordinate grid, as the full-size map has.
	 *
	 * <p>An addition rather than a recreation: Fortnite names locations instead, and
	 * Minecraft has no names to show, so a coordinate is what makes a spot on this map
	 * something that can be read out loud.
	 */
	private static void drawGrid(GuiGraphicsExtractor graphics, Minecraft client, MapViewport viewport) {
		double half = viewport.blocksAcross() * 0.5D;
		double spacing = MinimapConstants.GRID_SPACING;

		double firstX = Math.ceil((viewport.centerX() - half) / spacing) * spacing;
		double firstZ = Math.ceil((viewport.centerZ() - half) / spacing) * spacing;

		for (double worldX = firstX; worldX <= viewport.centerX() + half; worldX += spacing) {
			int screenX = Math.round(viewport.screenX(worldX));
			graphics.fill(screenX, Math.round(viewport.y()), screenX + 1, Math.round(viewport.y() + viewport.size()),
					MinimapConstants.GRID_COLOR);
			graphics.text(client.font, Integer.toString((int) worldX), screenX + 2, Math.round(viewport.y()) + 2,
					MinimapConstants.GRID_LABEL_COLOR, true);
		}

		for (double worldZ = firstZ; worldZ <= viewport.centerZ() + half; worldZ += spacing) {
			int screenY = Math.round(viewport.screenY(worldZ));
			graphics.fill(Math.round(viewport.x()), screenY, Math.round(viewport.x() + viewport.size()), screenY + 1,
					MinimapConstants.GRID_COLOR);
			graphics.text(client.font, Integer.toString((int) worldZ), Math.round(viewport.x()) + 2, screenY + 2,
					MinimapConstants.GRID_LABEL_COLOR, true);
		}
	}

	/** The player's own pointer, drawn where the player is, not at the map's middle. */
	private static void drawPlayer(GuiGraphicsExtractor graphics, Minecraft client, MapViewport viewport) {
		if (client.player == null) {
			return;
		}

		// The player's world position, not the viewport's centre. The map can be dragged, and
		// at that point the centre of the frame is nowhere near the player, drawing the
		// pointer at the centre made it slide around the map on its own.
		drawArrow(graphics, viewport.screenX(client.player.getX()), viewport.screenY(client.player.getZ()),
				MinimapConstants.PLAYER_ARROW_FRACTION * graphics.guiHeight(),
				client.player.getYRot(), MinimapConstants.ARROW_FILL);
	}

	/**
	 * Teammates, in their own colours.
	 *
	 * <p>Allies only. {@code isAlliedTo} is false for everyone on a server with no teams, so
	 * this shows a squad on a squad server and nothing at all in a free-for-all, which is
	 * the behaviour Fortnite has.
	 */
	private static void drawAllies(GuiGraphicsExtractor graphics, Minecraft client, MapViewport viewport) {
		if (client.level == null || client.player == null) {
			return;
		}

		float size = MinimapConstants.ALLY_ARROW_FRACTION * graphics.guiHeight();
		double half = viewport.blocksAcross() * 0.5D;

		for (AbstractClientPlayer other : client.level.players()) {
			if (other == client.player || !client.player.isAlliedTo(other)) {
				continue;
			}

			// Teammates are not clamped to the frame: unlike a marker, whose whole point is
			// to tell you which way to look, a teammate outside the map's range has no
			// direction worth drawing.
			if (Math.abs(other.getX() - viewport.centerX()) > half || Math.abs(other.getZ() - viewport.centerZ()) > half) {
				continue;
			}

			drawArrow(graphics, viewport.screenX(other.getX()), viewport.screenY(other.getZ()), size,
					other.getYRot(), PingColors.of(other.getUUID()));
		}
	}

	/**
	 * The Fortnite pointer: the artwork with its own baked outline, rotated onto the look
	 * vector.
	 *
	 * <p>Two textures are blitted at the same size rather than one being scaled up behind
	 * the other. Scaling a copy to fake an outline is what made the pointer read as a black
	 * blob: on a five-pixel icon, "two pixels bigger" is a forty per cent enlargement, and
	 * the outline swallowed the arrow. The halo asset is grown by exactly one pixel at the
	 * shipped resolution, which is what the game's own marker has.
	 *
	 * <p>The artwork points up: its tip is the topmost pixel and the direction from the
	 * shape's centre of mass to that tip is within a degree of vertical. So it is rotated by
	 * {@link #arrowAngle}, the angle that takes "up" onto the facing. A cursor-shaped arrow
	 * is easy to mistake for a diagonal one, so if the pointer ever reads off by 45°, that
	 * assumption is the thing to check first.
	 */
	private static void drawArrow(GuiGraphicsExtractor graphics, float centreX, float centreY, float height,
			float yaw, int fill) {
		float width = height * ARROW_ASPECT;

		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(centreX, centreY);
		pose.rotate(arrowAngle(yaw));

		blitCentred(graphics, ARROW_HALO_TEXTURE, ARROW_HALO_SIZE, width, height, MinimapConstants.ARROW_OUTLINE);
		blitCentred(graphics, ARROW_TEXTURE, ARROW_SIZE, width, height, fill);

		pose.popMatrix();
	}

	private static void blitCentred(GuiGraphicsExtractor graphics, Identifier texture, int textureSize,
			float width, float height, int color) {
		int w = Math.max(1, Math.round(width));
		int h = Math.max(1, Math.round(height));

		// Both layers are drawn at the same size. The halo's canvas is bigger *because* its
		// shape is one pixel fatter, so matching the drawn size is what makes the outline
		// hug the fill instead of scaling with the canvas.
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, -w / 2, -h / 2, 0.0F, 0.0F,
				w, h, textureSize, textureSize, textureSize, textureSize, color);
	}

	/**
	 * The rotation that takes an up-pointing artwork onto a Minecraft yaw.
	 *
	 * <p>Yaw 0 looks at +Z (south, down the map); yaw 90 looks at -X (west, left). Rotate
	 * maps +x onto {@code (cos, sin)} in screen space, so this is derived once here rather
	 * than four times in four quadrants.
	 */
	private static float arrowAngle(float yaw) {
		double radians = Math.toRadians(yaw);

		return (float) Math.atan2(-Math.sin(radians), -Math.cos(radians));
	}

	/** Every live marker, clamped to the frame if it lies outside it. */
	private static void drawPings(GuiGraphicsExtractor graphics, Minecraft client, PingManager pings,
			MapViewport viewport, long now) {
		// Screen height, not the map's edge: a marker's size is Fortnite's fraction of the
		// screen, and scaling it by the map instead made every marker about four times too
		// small.
		float pinHeight = MinimapConstants.PIN_FRACTION * graphics.guiHeight();

		for (Ping ping : pings.all()) {
			float alpha = ping.fadeAlpha(now);

			if (alpha <= 0.02F) {
				continue;
			}

			// Item marks are deliberately not on the map. Fortnite keeps item pings to the
			// world marker and leaves them off the compass; showing the item icon here as
			// well put loot markers on the map that the game does not draw.
			if (ping.item() != null) {
				continue;
			}

			float worldX = viewport.screenX(ping.pos().x());
			float worldY = viewport.screenY(ping.pos().z());

			// A marker off the map is pulled to the edge rather than hidden. Fortnite does
			// the same, and it is the difference between a map that says "there is a ping"
			// and one that silently loses it.
			float inset = ping.type().isThreat()
					? MinimapConstants.THREAT_FRACTION * graphics.guiHeight() * 0.5F
					: pinHeight * 0.5F;
			float x = clamp(worldX, viewport.x() + inset, viewport.x() + viewport.size() - inset);
			float y = clamp(worldY, viewport.y() + inset, viewport.y() + viewport.size() - inset);

			if (ping.type().isThreat()) {
				drawThreatMarker(graphics, x, y, alpha);
			} else {
				int height = Math.max(4, Math.round(pinHeight));
				blit(graphics, MARKER_TEXTURE, LEGACY_SIZE, Math.round(x) - height / 2,
						Math.round(y) - height, height, height, withAlpha(ping.color(), alpha));
			}
		}
	}

	/**
	 * The threat marker: Fortnite's white skull on a crimson disc.
	 *
	 * <p>Drawn from two assets rather than one, so the disc carries the colour and the skull
	 * stays white on top of it, which is how the capture reads, and which means the marker
	 * still works if the threat colour is ever made configurable.
	 */
	private static void drawThreatMarker(GuiGraphicsExtractor graphics, float x, float y, float alpha) {
		int diameter = Math.max(8, Math.round(MinimapConstants.THREAT_FRACTION * graphics.guiHeight()));

		blit(graphics, DISC_TEXTURE, LEGACY_SIZE, Math.round(x) - diameter / 2, Math.round(y) - diameter / 2,
				diameter, diameter, withAlpha(MinimapConstants.THREAT_COLOR, alpha));

		int skull = Math.max(4, Math.round(diameter * THREAT_SKULL_FILL));
		blit(graphics, SKULL_TEXTURE, SKULL_SIZE, Math.round(x) - skull / 2, Math.round(y) - skull / 2,
				skull, skull, withAlpha(MinimapConstants.THREAT_SKULL_COLOR, alpha));
	}

	/**
	 * The drawn height of a glyph inside its box.
	 *
	 * <p>Both readout glyphs are taller than they are wide (the skull is 27×32 and the bust
	 * 20×21 in the capture) so drawing both square would squash them.
	 */
	private static float glyphAspect(int textureSize) {
		return textureSize == PERSON_SIZE ? PERSON_ASPECT : SKULL_ASPECT;
	}

	private static void blit(GuiGraphicsExtractor graphics, Identifier texture, int textureSize,
			int x, int y, int width, int height, int color) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F, width, height,
				textureSize, textureSize, textureSize, textureSize, color);
	}

	private static float clamp(float value, float minimum, float maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static int withAlpha(int argb, float alpha) {
		int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));

		return (argb & 0x00FFFFFF) | (a << 24);
	}
}
