package dev.ping.ping;

import dev.ping.client.PingConfig;

/**
 * Tunables for the whole system.
 *
 * <p>Screen-space sizes are expressed as <em>fractions of the screen height</em> rather
 * than pixel counts, so they hold at any resolution or GUI scale. The numbers come from
 * measuring Fortnite captures against the HUD as a reference; see {@code docs/FIDELITY.md}.
 *
 * <p>The values a player can change on the settings screen are no longer here: they live in
 * {@link PingConfig}, which is the store the screen writes. What remains is the fixed
 * scaffolding — animation lengths, hit-test cosines, texture dimensions — plus the two beam
 * helpers, which read their tunables from {@code PingConfig} at call time.
 */
public final class PingConstants {
	private PingConstants() {
	}

	/** How long an enemy marker keeps following its target before it goes static. */
	public static final long ATTACH_MS = 5_000L;

	/** Fade-out window at the end of a marker's life. */
	public static final long FADE_MS = 1_000L;

	/** Pop-in animation length. */
	public static final long SPAWN_ANIM_MS = 160L;

	// ---- the pin ---------------------------------------------------------

	/** How far the pin shrinks at the very start of its pop-in animation. */
	public static final float SPAWN_SCALE = 0.62F;

	/**
	 * How far off screen a marker goes before it is clamped to the edge, in pixels. Kept
	 * small: too much inset and markers visibly detach from the screen edge.
	 */
	public static final float EDGE_MARGIN = 10.0F;

	/**
	 * How long to cache a marker's line-of-sight result. Raycasting every marker every
	 * frame was a visible cost; the answer does not change meaningfully in 120 ms.
	 */
	public static final long OCCLUSION_INTERVAL_MS = 120L;

	/** Double-tap window for the danger marker, in milliseconds. */
	public static final long DOUBLE_TAP_MS = 320L;

	// ---- the distance label ----------------------------------------------

	/** Label offsets, as fractions of the pin height. See docs/FIDELITY.md. */
	public static final float LABEL_GAP = 0.103F;
	public static final float LABEL_RISE = 0.138F;

	// ---- item markers ----------------------------------------------------

	/** Ring diameter as a fraction of the screen height. */
	public static final float RING_DIAMETER_FRACTION = 0.040F;

	/** How much of the ring's diameter the item icon fills. */
	public static final float ITEM_ICON_FILL = 0.52F;

	/** Opacity of the disc drawn behind an item icon so it reads against any background. */
	public static final float ITEM_BACKDROP_ALPHA = 0.75F;

	// ---- the beams -------------------------------------------------------

	/**
	 * The two beams.
	 *
	 * <p>Both are columns standing in <b>world space</b> at the marked point, not
	 * billboards. That distinction is the whole point: a vertical world line converges on
	 * a vanishing point, so under a pitched camera it appears <em>slanted</em> rather than
	 * straight up the screen, and it foreshortens as you look up and down. Drawing it as
	 * an axis-aligned stripe looked wrong at any camera angle other than level.
	 *
	 * <p>The small one is always present — it is simply too short to make out at range,
	 * which is why it looks like the beam only appears once you back off. The large one is
	 * a long-range signpost and only fades in past {@link PingConfig#beamFadeNear()}.
	 */
	public static final double MINI_BEAM_HEIGHT = 0.75D;
	public static final double MINI_BEAM_WIDTH = 0.22D;

	/** The large, long-range beam. Girth is this value across; tune here. */
	public static final double BEAM_HEIGHT = 128.0D;
	public static final double BEAM_WIDTH = 1.5D;

	/**
	 * The beam texture carries the taper, the vertical fade and the core-versus-glow
	 * split baked in, so drawing a beam is a single tinted blit.
	 */
	public static final int BEAM_TEXTURE_WIDTH = 128;
	public static final int BEAM_TEXTURE_HEIGHT = 512;

	/**
	 * Opacity of the large beam at a given distance, from {@link PingConfig}'s fade band.
	 */
	public static float beamAlpha(double distance) {
		double near = PingConfig.beamFadeNear();
		double far = PingConfig.beamFadeFar();

		if (far <= near) {
			// A near at or past the far is a degenerate band; the honest reading is a step.
			return distance >= far ? 1.0F : 0.0F;
		}

		if (distance <= near) {
			return 0.0F;
		}

		if (distance >= far) {
			return 1.0F;
		}

		return (float) ((distance - near) / (far - near));
	}

	/** The colour a beam should be drawn in. */
	public static int beamColor(int markerColor) {
		int fixed = PingConfig.beamColour();

		return fixed != 0 ? fixed : markerColor;
	}

	// ---- interaction -----------------------------------------------------

	/**
	 * How closely you must be looking at your own marker for the ping key to <em>remove</em>
	 * it instead of placing a new one. Stored as a cosine to avoid a trigonometry call per
	 * check. {@code 0.9990} is about 2.5 degrees.
	 */
	public static final double UNPING_COS = 0.9990D;

	/**
	 * How closely you must be looking at a marker for it to count as "the one you are
	 * looking at" — drawn solid through terrain, and showing its context block. Slightly
	 * wider than {@link #UNPING_COS}. About 4 degrees.
	 */
	public static final double LOOK_CONE_COS = 0.9976D;
}
