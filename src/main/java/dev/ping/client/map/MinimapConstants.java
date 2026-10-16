package dev.ping.client.map;

/**
 * Tunables for the minimap.
 *
 * <p><b>Most of these are measured, not chosen.</b> They come from a Fortnite capture at
 * 1919×1079 and are stored as fractions of the screen height, so they hold at any
 * resolution or GUI scale. Every number below that says "measured" was read off that frame
 * by sampling pixels along scan lines rather than by eye; see {@code docs/FIDELITY.md} for
 * what is measured and what is still a working value.
 *
 * <p>One deliberate deviation: Fortnite's readout text has a cap height of about 14 px on
 * a 1080p frame, which is roughly 4.7 GUI pixels at Minecraft's GUI scale 3 — smaller than
 * Minecraft's own font can draw legibly. The readouts therefore keep Fortnite's
 * <em>positions and row rhythm</em> but use Minecraft's default font size, so they read
 * slightly larger than the capture. Matching the pixel height would be matching something
 * nobody could read.
 */
public final class MinimapConstants {
	private MinimapConstants() {
	}

	// ---- the frame, measured ---------------------------------------------

	/** The square minimap's edge, as a fraction of screen height. Measured 252/1079. */
	public static final float SIZE_FRACTION = 0.2335F;

	/** Gap above the minimap. Measured 32/1079. */
	public static final float TOP_MARGIN_FRACTION = 0.0297F;

	/** Gap between the minimap and the right edge of the screen. Measured 31/1079. */
	public static final float RIGHT_MARGIN_FRACTION = 0.0287F;

	/**
	 * There is no frame. The map is a bare square: measured with no border, no plate and no
	 * shadow, and with square corners rather than the rounded ones this build used to draw.
	 * The edge is a single hard pixel step from the world to the map.
	 */

	// ---- the readout column, left of the map -----------------------------

	/**
	 * A readout glyph's right edge sits this far left of the map's left edge. Measured 25/1079.
	 *
	 * <p>The column is right-aligned against the map: the glyphs line up on their right
	 * edge, the numbers line up on theirs, further out.
	 */
	public static final float READOUT_GLYPH_GAP_FRACTION = 0.0232F;

	/** A readout number's right edge sits this far left of the map's left edge. Measured 61/1079. */
	public static final float READOUT_TEXT_GAP_FRACTION = 0.0565F;

	/** Vertical pitch between readouts. Measured 36.5/1079. */
	public static final float READOUT_PITCH_FRACTION = 0.0338F;

	/** The first readout's centre sits this far below the map's top edge. Measured 16/1079. */
	public static final float READOUT_FIRST_FRACTION = 0.031F;

	/** Glyph box. Measured 19×22 (skull) and 20×21 (person); one size serves both. */
	public static final float READOUT_GLYPH_FRACTION = 0.0190F;

	public static final int READOUT_COLOR = 0xFFFFFFFF;

	/** The readouts sit on their own varying background, so they carry a shadow. */
	public static final int READOUT_SHADOW = 0xA0000000;

	// ---- the location label, at the map's foot ---------------------------

	/**
	 * The label's cap height. Measured 21.5/1079.
	 *
	 * <p>Fortnite's label names the named location you are standing in. Minecraft has no
	 * named locations, so this build shows the biome instead — the same idea, the same slot.
	 */
	public static final float LABEL_FRACTION = 0.0199F;

	/** The label's black outline. Measured ≈3 px. */
	public static final float LABEL_OUTLINE_FRACTION = 0.0028F;

	/** The label's bottom edge sits this far above the map's bottom. Measured 32/1079. */
	public static final float LABEL_BOTTOM_FRACTION = 0.0297F;

	public static final int LABEL_COLOR = 0xFFFFFFFF;
	public static final int LABEL_OUTLINE_COLOR = 0xFF000000;

	// ---- markers, measured -----------------------------------------------

	/** The player's own arrow, 12×17 measured, drawn slightly larger so it stays readable. */
	public static final float PLAYER_ARROW_FRACTION = 0.0158F;

	/** A teammate's arrow. */
	public static final float ALLY_ARROW_FRACTION = 0.0130F;

	/** The enemy marker's disc. Measured ≈38 px across, which is 3.5% of the screen height. */
	public static final float THREAT_FRACTION = 0.0352F;

	/**
	 * A plain location marker's height on the map.
	 *
	 * <p>Sized to read at a glance next to the threat marker rather than to any measurement:
	 * the capture's own ping marker was never on screen in a frame where its size could be
	 * read. Markers on a map are the thing you are looking for, so they are deliberately
	 * large.
	 */
	public static final float PIN_FRACTION = 0.0300F;

	/**
	 * How much smaller the readout numbers are than Minecraft's font.
	 *
	 * <p>Fortnite's readout text has a 14 px cap height on a 1080p frame, which is about 4.7
	 * GUI pixels at GUI scale 3 — Minecraft's font has a 7 px cap and cannot go smaller and
	 * stay legible, so the text is scaled to close most of the gap without becoming mush.
	 */
	public static final float READOUT_TEXT_SCALE = 0.80F;

	/** The threat marker's disc colour: measured crimson `#D24E49`. */
	public static final int THREAT_COLOR = 0xFFD24E49;

	/** The threat marker's skull is white, so it reads against the disc. */
	public static final int THREAT_SKULL_COLOR = 0xFFFFFFFF;

	// ---- the world -------------------------------------------------------

	/**
	 * The map texture's resolution, in texels per side.
	 *
	 * <p>Fixed, and independent of the zoom: zooming changes how many blocks each texel
	 * covers rather than how many texels there are. That keeps the texture allocation and
	 * the per-frame cost flat, which is what makes the map affordable at all — the
	 * alternative, one draw call per texel, is tens of thousands of quads a frame.
	 */
	public static final int TEXELS = 384;

	/**
	 * Blocks across the map at each zoom step, nearest first.
	 *
	 * <p>Chosen so the default sits inside a typical render distance: a map whose edges are
	 * mostly "no data" reads as broken rather than as zoomed out.
	 */
	public static final int[] ZOOM_BLOCKS = { 128, 256, 384, 576 };

	/** Which of {@link #ZOOM_BLOCKS} to start on. */
	public static final int DEFAULT_ZOOM = 1;

	/**
	 * How long a sampled chunk is trusted before it is re-sampled.
	 *
	 * <p>Terrain changes under a player, and this build has no block-update hook, so every
	 * tile is re-read on a timer instead. A few seconds of staleness is invisible on a map
	 * this size and costs a bounded amount of work per tick.
	 */
	public static final long TILE_TTL_MS = 3_000L;

	/**
	 * The shortest gap between two recompositions driven by terrain <em>arriving</em>.
	 *
	 * <p>Movement already recomposes as the centre crosses a block, which is naturally rate
	 * limited. Terrain arriving is not, so without this the map would rebuild its whole
	 * image twenty times a second to show a handful of changed chunks.
	 */
	public static final long COMPOSE_INTERVAL_MS = 100L;

	/** Chunks re-sampled per tick at minimum, however far in the map is zoomed. */
	private static final int MIN_TILES_PER_TICK = 6;

	/** Ceiling on chunks re-sampled per tick, so the widest zoom cannot stall a tick. */
	private static final int MAX_TILES_PER_TICK = 32;

	/** Ticks in one refresh window, at 20 ticks a second. */
	private static final int TICKS_PER_TTL = (int) (TILE_TTL_MS / 50L);

	/** How many chunks beyond the visible window are kept before being dropped. */
	public static final int KEEP_MARGIN_CHUNKS = 3;

	/**
	 * The most chunks the map will remember.
	 *
	 * <p>Explored terrain is kept rather than forgotten, so the cache needs a ceiling: this
	 * is about sixteen thousand chunks, an area about 64×64 chunks across — more ground than
	 * a session covers — at roughly a kilobyte of colours each.
	 */
	public static final int MAX_TILES = 16_384;

	/**
	 * How many chunks a biome region must cover before the map names it.
	 *
	 * <p>Without a floor, every scrap of river bank and beach carries a label and the map
	 * turns into a wall of text. Eight chunks is about 45 by 45 blocks — small enough to
	 * catch real terrain features, large enough that a name means something.
	 */
	public static final int MIN_BIOME_CHUNKS = 8;

	/** Grid line spacing, in blocks, on the full-size map. */
	public static final int GRID_SPACING = 64;

	/** The full-size map's edge, as a fraction of the smaller screen dimension. */
	public static final float SCREEN_FRACTION = 0.84F;

	/**
	 * Colour for a column this client has never seen.
	 *
	 * <p>A neutral grey rather than black, which is Fortnite's fog: ground you have not
	 * visited is greyed out, so the map reads as a chart with unexplored reaches instead of
	 * as a map with holes in it.
	 */
	public static final int UNKNOWN_COLOR = 0xFF6C7278;

	/** The full-size map's backdrop, over the dimmed game. */
	public static final int SCREEN_BACKDROP = 0xC806090C;

	public static final int GRID_COLOR = 0x24FFFFFF;
	public static final int GRID_LABEL_COLOR = 0xFFC8D0D8;

	/** The player's own arrow: white over a dark outline, so it reads on any terrain. */
	public static final int ARROW_FILL = 0xFFFFFFFF;
	public static final int ARROW_OUTLINE = 0xCC0A0E12;

	/**
	 * How many chunks to re-sample this tick, for a window {@code halfChunks} chunks out.
	 *
	 * <p>Derived from the work that actually exists rather than fixed: a window has to be
	 * walked once per {@link #TILE_TTL_MS}, so the budget is the window's size divided by
	 * the ticks in that window. A fixed number cannot be right at both ends of the zoom
	 * ladder.
	 */
	public static int refreshBudget(int halfChunks) {
		int span = 2 * halfChunks + 1;
		int window = span * span;
		int needed = (window + TICKS_PER_TTL - 1) / TICKS_PER_TTL;

		return Math.max(MIN_TILES_PER_TICK, Math.min(MAX_TILES_PER_TICK, needed));
	}

	/** Where a marker sits in the zoom ladder, clamped to it. */
	public static int clampZoom(int index) {
		return Math.max(0, Math.min(ZOOM_BLOCKS.length - 1, index));
	}

	/** How many blocks across the map shows at a zoom step. */
	public static int zoomBlocks(int index) {
		return ZOOM_BLOCKS[clampZoom(index)];
	}
}
