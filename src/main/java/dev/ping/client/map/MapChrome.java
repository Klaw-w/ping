package dev.ping.client.map;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The furniture of the full-size map screen, recreated from a Fortnite capture.
 *
 * <p>Every colour and offset here was measured off that frame (1250×702) and is stored as a
 * fraction of the window, so the layout holds at any size. What Fortnite puts in each slot
 * is Fortnite's; what this build puts in them is recorded on
 * {@link dev.ping.client.map.PingMapScreen} and in {@code docs/FIDELITY.md}, because three
 * of Fortnite's rows have no Minecraft equivalent and pretending otherwise would be worse
 * than showing something real in the same place.
 *
 * <p>The layout, top to bottom: a tab strip top-left, the level badge and XP bar top-right,
 * a squad panel down the left, the map in the middle, a Reset button under it and the
 * control hints down the right.
 */
public final class MapChrome {
	// ---- measured colours -------------------------------------------------

	/** The screen's own backdrop: an opaque blue gradient, no world visible behind it. */
	private static final int BACKGROUND_TOP = 0xFF0B1F76;
	private static final int BACKGROUND_BOTTOM = 0xFF031150;

	private static final int TAB_ACTIVE = 0xFFFFFFFF;
	private static final int TAB_STRIPE = 0xFFFAFF16;
	private static final int TAB_IDLE = 0xFF5DA2F0;

	private static final int PANEL_HEADER = 0xFF1250A7;
	private static final int PANEL_BODY = 0xFF031F51;
	private static final int SECTION = 0xFFBBE8FF;
	private static final int VALUE = 0xFFFFFFFF;
	private static final int ACCENT = 0xFFA0E9FF;

	private static final int BUTTON_FACE = 0xFF0B2A5E;
	private static final int BUTTON_EDGE = 0x80FFFFFF;

	/** The XP bar: a violet-to-blue fill in segments, measured at 29 px segments. */
	private static final int XP_FILL_LEFT = 0xFF8B5CF6;
	private static final int XP_FILL_RIGHT = 0xFF60C8FF;
	private static final int XP_TRACK = 0x66000000;

	// ---- measured geometry, as fractions of the window --------------------

	private static final float TAB_X = 0.030F;
	private static final float TAB_Y = 0.021F;
	private static final float TAB_HEIGHT = 0.043F;
	private static final float TAB_PITCH = 0.080F;
	private static final float TAB_SLANT = 0.008F;

	private static final float LEVEL_X = 0.720F;
	private static final float LEVEL_Y = 0.028F;
	private static final float XP_BAR_X = 0.752F;
	private static final float XP_BAR_Y = 0.043F;
	private static final float XP_BAR_W = 0.226F;
	private static final float XP_BAR_H = 0.014F;

	private static final float PANEL_X = 0.030F;
	private static final float PANEL_Y = 0.161F;
	private static final float PANEL_W = 0.232F;
	private static final float PANEL_HEADER_H = 0.060F;
	private static final float PANEL_ROW = 0.037F;

	private static final float BUTTON_W = 0.104F;
	private static final float BUTTON_H = 0.041F;

	/**
	 * The tab strip's labels.
	 *
	 * <p>Only the map. The capture has Quests, Characters and Accolades alongside it, and an
	 * earlier revision drew all four to match, but three of them led nowhere, and a tab that
	 * does nothing is a lie told in the shape of a button. Matching the capture exactly is
	 * worth less than not pretending.</p>
	 */
	private static final String[] TAB_LABELS = { "MAP" };

	private MapChrome() {
	}

	/** The window-filling blue gradient. */
	public static void background(GuiGraphicsExtractor graphics, int width, int height) {
		graphics.fillGradient(0, 0, width, height, BACKGROUND_TOP, BACKGROUND_BOTTOM);
	}

	/**
	 * The tab strip, with the active tab as a white slab and its yellow stripe.
	 *
	 * @param active index of the lit tab, or -1 for a strip with nothing selected
	 */
	public static void tabs(GuiGraphicsExtractor graphics, Font font, int width, int height, int active) {
		int tabHeight = Math.round(TAB_HEIGHT * height);
		int pitch = Math.round(TAB_PITCH * width);
		int slant = Math.round(TAB_SLANT * width);

		for (int tab = 0; tab < TAB_LABELS.length; tab++) {
			String label = TAB_LABELS[tab];
			int x = Math.round(TAB_X * width) + tab * pitch;
			int textWidth = font.width(label);
			int slabWidth = textWidth + 12;

			if (tab == active) {
				// A parallelogram, drawn a row at a time: a 2D GUI has no shear transform, and
				// at this size a row-by-row slant is indistinguishable from a real one.
				for (int row = 0; row < tabHeight; row++) {
					int inset = slant - Math.round(slant * (row / (float) tabHeight));
					graphics.fill(x + inset, Math.round(TAB_Y * height) + row,
							x + slabWidth - inset, Math.round(TAB_Y * height) + row + 1, TAB_ACTIVE);
				}

				// The yellow stripe sits along the bottom of the slab.
				int stripe = Math.max(2, Math.round(height * 0.009F));
				for (int row = 0; row < stripe; row++) {
					int y = Math.round(TAB_Y * height) + tabHeight - stripe + row;
					graphics.fill(x, y, x + slabWidth, y + 1, TAB_STRIPE);
				}

				graphics.text(font, label, x + 6, Math.round(TAB_Y * height) + (tabHeight - 8) / 2, 0xFF101010, false);
			} else {
				graphics.text(font, label, x + 6, Math.round(TAB_Y * height) + (tabHeight - 8) / 2, TAB_IDLE, true);
			}
		}
	}

	/**
	 * The level badge and XP bar.
	 *
	 * <p>This is the one part of Fortnite's screen that maps onto Minecraft with no
	 * invention at all: the level is the player's experience level, the bar is their
	 * progress towards the next one, and the remaining-XP line is the same number the game
	 * would show. No translation needed, so it is the same information in the same place.
	 */
	static void level(GuiGraphicsExtractor graphics, Minecraft client, Font font, int width, int height) {
		if (client.player == null) {
			return;
		}

		int level = client.player.experienceLevel;
		float progress = client.player.experienceProgress;
		int barX = Math.round(XP_BAR_X * width);
		int barY = Math.round(XP_BAR_Y * height);
		int barW = Math.round(XP_BAR_W * width);
		int barH = Math.max(2, Math.round(XP_BAR_H * height));

		graphics.fill(barX, barY, barX + barW, barY + barH, XP_TRACK);

		// Segmented, as the capture has it: the fill is drawn as blocks with gaps rather than
		// as a solid bar.
		int segments = 10;
		int segment = barW / segments;
		int filled = Math.round(progress * segments);

		for (int i = 0; i < filled; i++) {
			int left = barX + i * segment;
			graphics.fillGradient(left, barY, left + segment - 1, barY + barH, XP_FILL_LEFT, XP_FILL_RIGHT);
		}

		String badge = "LVL " + level;
		String toGo = (client.player.getXpNeededForNextLevel()) + " XP to LVL " + (level + 1);

		graphics.text(font, badge, Math.round(LEVEL_X * width), Math.round(LEVEL_Y * height), VALUE, true);
		graphics.text(font, toGo, barX + barW - font.width(toGo), Math.round((XP_BAR_Y + XP_BAR_H) * height) + 3,
				ACCENT, true);
	}

	/**
	 * The left-hand panel.
	 *
	 * @param header      the party line at the top, where Fortnite says the squad size
	 * @param rows        label/value pairs for the body, in order
	 * @param elims       the elimination block's rows, or empty to omit the block
	 * @param elimsHeader the block's own heading
	 */
	static void panel(GuiGraphicsExtractor graphics, Font font, int width, int height,
			String header, List<Row> rows, String elimsHeader, List<Row> elims) {
		int x = Math.round(PANEL_X * width);
		int y = Math.round(PANEL_Y * height);
		int w = Math.round(PANEL_W * width);
		int headH = Math.round(PANEL_HEADER_H * height);
		int rowH = Math.round(PANEL_ROW * height);
		int cursor = y + headH + rowH / 2;

		// Body first, then the header strip over its top, so one rectangle serves both and the
		// panel cannot end up a pixel short at the bottom.
		int bodyHeight = headH + rowH * (rows.size() * 2 + elims.size() + 4);
		graphics.fill(x, y, x + w, y + bodyHeight, PANEL_BODY);
		graphics.fill(x, y, x + w, y + headH, PANEL_HEADER);
		graphics.text(font, header, x + 8, y + (headH - 8) / 2, VALUE, false);

		cursor = y + headH + rowH / 2;

		for (Row row : rows) {
			graphics.text(font, row.label(), x + 8, cursor, SECTION, true);
			cursor += rowH;

			if (row.value() != null && !row.value().isEmpty()) {
				graphics.text(font, row.value(), x + 8, cursor, VALUE, true);
				cursor += rowH;
			}
		}

		if (elims.isEmpty()) {
			return;
		}

		graphics.text(font, elimsHeader, x + 8, cursor, SECTION, true);
		cursor += rowH;

		for (Row row : elims) {
			// The colour swatch stands where Fortnite draws a character portrait. It is the
			// player's own locator-bar colour, which is what identifies them everywhere else
			// in this mod, so the panel and the map agree about who is who.
			graphics.fill(x + 9, cursor + 1, x + 9 + 12, cursor + 13, row.colour());
			graphics.text(font, row.label(), x + 26, cursor + 3, VALUE, true);

			if (row.value() != null && !row.value().isEmpty()) {
				graphics.text(font, row.value(), x + w - 10 - font.width(row.value()), cursor + 3, VALUE, true);
			}

			cursor += rowH;
		}
	}

	/** A button, drawn in the capture's flat style with a hairline edge. */
	public static void button(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height, String label) {
		graphics.fill(x, y, x + width, y + height, BUTTON_FACE);
		graphics.fill(x, y, x + width, y + 1, BUTTON_EDGE);
		graphics.fill(x, y + height - 1, x + width, y + height, BUTTON_EDGE);
		graphics.fill(x, y, x + 1, y + height, BUTTON_EDGE);
		graphics.fill(x + width - 1, y, x + width, y + height, BUTTON_EDGE);
		graphics.text(font, label, x + (width - font.width(label)) / 2, y + (height - 8) / 2, VALUE, true);
	}

	/** The control hints down the right edge, each a label with a key on it. */
	static void hints(GuiGraphicsExtractor graphics, Font font, int width, int height, List<String> hints) {
		int y = Math.round(0.795F * height);
		int pitch = Math.round(0.037F * height);

		for (String hint : hints) {
			graphics.text(font, hint, Math.round(0.975F * width) - font.width(hint), y, VALUE, true);
			y += pitch;
		}
	}

	/**
	 * The map's rectangle, centred on the window.
	 *
	 * <p>The capture has the map filling most of a 16:9 frame, so its fractions were taken
	 * from there, but placing it by fractions of the window height alone put it near the
	 * top on any window that is not that shape. It is centred instead, at the point the
	 * capture centres it (48.3% across, 54.3% down), and sized to whichever of the two axes
	 * runs out first.
	 */
	static float mapX(int width, int height) {
		return 0.483F * width - mapSize(width, height) * 0.5F;
	}

	static float mapY(int width, int height) {
		return 0.543F * height - mapSize(width, height) * 0.5F;
	}

	static float mapSize(int width, int height) {
		return Math.min(0.429F * width, 0.758F * height);
	}

	static int resetX(int width) {
		return Math.round(0.453F * width);
	}

	static int resetY(int height) {
		return Math.round(0.937F * height);
	}

	static int resetWidth(int width) {
		return Math.round(BUTTON_W * width);
	}

	static int resetHeight(int height) {
		return Math.round(BUTTON_H * height);
	}

	/** A label/value pair in the panel. */
	record Row(String label, String value, int colour) {
		static Row of(String label, String value) {
			return new Row(label, value, VALUE);
		}
	}
}
