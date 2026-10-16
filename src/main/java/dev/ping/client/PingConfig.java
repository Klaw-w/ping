package dev.ping.client;

import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * The mod's settings, stored as JSON in {@code config/ping.json}, and the single source of
 * truth for every value the settings screen writes.
 *
 * <p>It began as a file for four booleans and is now the backing store for the whole screen:
 * the four original toggles plus the marker and beam tunables that used to be {@code static
 * final} in {@code PingConstants}. The booleans' keys are unchanged, so a file already out
 * in the world keeps loading; anything it predates is written back on load with its default
 * rather than silently dropped. A settings file that does not list a setting is a setting
 * nobody can find.
 *
 * <p>The one setting that matters most is {@link #sendPingsOverChat()}. Pings reach other
 * players by being smuggled through chat, which is the only channel that works with nothing
 * installed server-side, but it means a ping is a real chat message, visible in the server
 * log and to anyone who does not have the mod. Turn it off and pings become entirely local.
 *
 * <p><b>Measured versus chosen is preserved here exactly as it was in {@code PingConstants}.</b>
 * The comments below still say which numbers came off a capture and which were tuned to
 * look right; making a value editable does not make it sourced.
 *
 * <p>Every setter clamps to the range the settings screen offers. The screen is not the only
 * writer (the file can be hand-edited), and a lifetime of zero or a marker height of fifty
 * would break the HUD, so the clamp lives at the store, not only at the control.
 */
public final class PingConfig {
	private static final String FILE_NAME = "ping.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	// ---- the four booleans (keys unchanged) ------------------------------

	private static final boolean DEFAULT_SEND_PINGS_OVER_CHAT = true;
	private static final boolean DEFAULT_SHOW_MINIMAP = true;
	private static final boolean DEFAULT_FORTNITE_HEALTH = true;
	private static final boolean DEFAULT_SHOW_PING_FEED = true;

	// ---- marker and beam tunables, moved out of PingConstants ------------

	/** How long a plain location/item marker lives. Measured: 45 s. */
	public static final long DEFAULT_MARKER_LIFETIME_MS = 45_000L;
	public static final long MARKER_LIFETIME_MIN_MS = 5_000L;
	public static final long MARKER_LIFETIME_MAX_MS = 300_000L;

	/** How long an enemy/danger marker lives. Tuned, not measured: 10 s. */
	public static final long DEFAULT_THREAT_LIFETIME_MS = 10_000L;
	public static final long THREAT_LIFETIME_MIN_MS = 1_000L;
	public static final long THREAT_LIFETIME_MAX_MS = 120_000L;

	/** The gap in which the mark button does nothing. Its existence is sourced, its length is not. */
	public static final long DEFAULT_REMARK_COOLDOWN_MS = 500L;
	public static final long REMARK_COOLDOWN_MIN_MS = 0L;
	public static final long REMARK_COOLDOWN_MAX_MS = 2_000L;

	/** How far the pin floats above the point, as a fraction of its own height. Tuned. */
	public static final double DEFAULT_MARKER_LIFT = 0.20D;
	public static final double MARKER_LIFT_MIN = 0.0D;
	public static final double MARKER_LIFT_MAX = 1.0D;

	/** Pin height as a fraction of the screen height. Measured: 2.7%. */
	public static final double DEFAULT_MARKER_HEIGHT_FRACTION = 0.027D;
	public static final double MARKER_HEIGHT_MIN = 0.005D;
	public static final double MARKER_HEIGHT_MAX = 0.10D;

	/** Opacity for a marker behind terrain that is not being looked at. Chosen to stay visible. */
	public static final double DEFAULT_OCCLUDED_ALPHA = 0.55D;
	public static final double OCCLUDED_ALPHA_MIN = 0.0D;
	public static final double OCCLUDED_ALPHA_MAX = 1.0D;

	/** Distance at which the large beam fades in. Captures could not settle this. */
	public static final double DEFAULT_BEAM_FADE_NEAR = 45.0D;
	public static final double DEFAULT_BEAM_FADE_FAR = 55.0D;
	public static final double BEAM_FADE_MIN = 0.0D;
	public static final double BEAM_FADE_MAX = 256.0D;

	/** Forced red for threat markers. {@code 0} means "use the author's own colour". */
	public static final int DEFAULT_THREAT_COLOUR = 0xFFE8383D;

	/** Forced beam colour. {@code 0} means "use the marker's colour". */
	public static final int DEFAULT_BEAM_COLOUR = 0;

	// ---- live values -----------------------------------------------------

	private static boolean sendPingsOverChat = DEFAULT_SEND_PINGS_OVER_CHAT;
	private static boolean showMinimap = DEFAULT_SHOW_MINIMAP;
	private static boolean fortniteHealth = DEFAULT_FORTNITE_HEALTH;
	private static boolean showPingFeed = DEFAULT_SHOW_PING_FEED;

	private static long markerLifetimeMs = DEFAULT_MARKER_LIFETIME_MS;
	private static long threatLifetimeMs = DEFAULT_THREAT_LIFETIME_MS;
	private static long remarkCooldownMs = DEFAULT_REMARK_COOLDOWN_MS;

	private static double markerLift = DEFAULT_MARKER_LIFT;
	private static double markerHeightFraction = DEFAULT_MARKER_HEIGHT_FRACTION;
	private static double occludedAlpha = DEFAULT_OCCLUDED_ALPHA;

	private static double beamFadeNear = DEFAULT_BEAM_FADE_NEAR;
	private static double beamFadeFar = DEFAULT_BEAM_FADE_FAR;

	private static int threatColour = DEFAULT_THREAT_COLOUR;
	private static int beamColour = DEFAULT_BEAM_COLOUR;

	private PingConfig() {
	}

	// ---- the booleans ----------------------------------------------------

	/** Whether a placed ping is also broadcast to other players running the mod. */
	public static boolean sendPingsOverChat() {
		return sendPingsOverChat;
	}

	public static void setSendPingsOverChat(boolean value) {
		if (sendPingsOverChat == value) {
			return;
		}

		sendPingsOverChat = value;
		save();
	}

	public static boolean toggleSendPingsOverChat() {
		setSendPingsOverChat(!sendPingsOverChat);

		return sendPingsOverChat;
	}

	/** Whether the corner minimap is drawn. The map key opens the full map either way. */
	public static boolean showMinimap() {
		return showMinimap;
	}

	public static void setShowMinimap(boolean value) {
		if (showMinimap == value) {
			return;
		}

		showMinimap = value;
		save();
	}

	public static boolean toggleShowMinimap() {
		setShowMinimap(!showMinimap);

		return showMinimap;
	}

	/**
	 * Whether health is drawn as Fortnite's green health / blue shield rather than as
	 * vanilla red hearts. Off restores vanilla's own hearts; the switch is read every
	 * frame, so it takes effect without a restart.
	 */
	public static boolean fortniteHealth() {
		return fortniteHealth;
	}

	public static void setFortniteHealth(boolean value) {
		if (fortniteHealth == value) {
			return;
		}

		fortniteHealth = value;
		save();
	}

	public static boolean toggleFortniteHealth() {
		setFortniteHealth(!fortniteHealth);

		return fortniteHealth;
	}

	/** Whether the ping feed is drawn in the bottom-left corner. */
	public static boolean showPingFeed() {
		return showPingFeed;
	}

	public static void setShowPingFeed(boolean value) {
		if (showPingFeed == value) {
			return;
		}

		showPingFeed = value;
		save();
	}

	public static boolean toggleShowPingFeed() {
		setShowPingFeed(!showPingFeed);

		return showPingFeed;
	}

	// ---- the numbers -----------------------------------------------------

	/** How long a plain location/item marker lives. */
	public static long markerLifetimeMs() {
		return markerLifetimeMs;
	}

	public static void setMarkerLifetimeMs(long value) {
		long clamped = clamp(value, MARKER_LIFETIME_MIN_MS, MARKER_LIFETIME_MAX_MS);

		if (markerLifetimeMs == clamped) {
			return;
		}

		markerLifetimeMs = clamped;
		save();
	}

	/** How long an enemy/danger marker lives. */
	public static long threatLifetimeMs() {
		return threatLifetimeMs;
	}

	public static void setThreatLifetimeMs(long value) {
		long clamped = clamp(value, THREAT_LIFETIME_MIN_MS, THREAT_LIFETIME_MAX_MS);

		if (threatLifetimeMs == clamped) {
			return;
		}

		threatLifetimeMs = clamped;
		save();
	}

	/** The gap in which the mark button does nothing, after a marker has just been placed. */
	public static long remarkCooldownMs() {
		return remarkCooldownMs;
	}

	public static void setRemarkCooldownMs(long value) {
		long clamped = clamp(value, REMARK_COOLDOWN_MIN_MS, REMARK_COOLDOWN_MAX_MS);

		if (remarkCooldownMs == clamped) {
			return;
		}

		remarkCooldownMs = clamped;
		save();
	}

	// ---- the marker's look -----------------------------------------------

	/** How far the pin floats above the marked point, as a fraction of its own height. */
	public static double markerLift() {
		return markerLift;
	}

	public static void setMarkerLift(double value) {
		double clamped = clamp(value, MARKER_LIFT_MIN, MARKER_LIFT_MAX);

		if (markerLift == clamped) {
			return;
		}

		markerLift = clamped;
		save();
	}

	/** Pin height as a fraction of the screen height. */
	public static double markerHeightFraction() {
		return markerHeightFraction;
	}

	public static void setMarkerHeightFraction(double value) {
		double clamped = clamp(value, MARKER_HEIGHT_MIN, MARKER_HEIGHT_MAX);

		if (markerHeightFraction == clamped) {
			return;
		}

		markerHeightFraction = clamped;
		save();
	}

	/** Opacity for a marker that is behind terrain and not being looked at. */
	public static double occludedAlpha() {
		return occludedAlpha;
	}

	public static void setOccludedAlpha(double value) {
		double clamped = clamp(value, OCCLUDED_ALPHA_MIN, OCCLUDED_ALPHA_MAX);

		if (occludedAlpha == clamped) {
			return;
		}

		occludedAlpha = clamped;
		save();
	}

	// ---- the beam --------------------------------------------------------

	/** Distance at which the large beam begins to fade in as you back away. */
	public static double beamFadeNear() {
		return beamFadeNear;
	}

	public static void setBeamFadeNear(double value) {
		double clamped = clamp(value, BEAM_FADE_MIN, BEAM_FADE_MAX);

		if (beamFadeNear == clamped) {
			return;
		}

		beamFadeNear = clamped;
		save();
	}

	/** Distance beyond which the large beam is fully visible. */
	public static double beamFadeFar() {
		return beamFadeFar;
	}

	public static void setBeamFadeFar(double value) {
		double clamped = clamp(value, BEAM_FADE_MIN, BEAM_FADE_MAX);

		if (beamFadeFar == clamped) {
			return;
		}

		beamFadeFar = clamped;
		save();
	}

	// ---- the colour overrides --------------------------------------------

	/** Forced red for enemy/danger markers, or {@code 0} to use the author's own colour. */
	public static int threatColour() {
		return threatColour;
	}

	public static void setThreatColour(int value) {
		if (threatColour == value) {
			return;
		}

		threatColour = value;
		save();
	}

	/** Forced beam colour, or {@code 0} to tint the beam to its marker. */
	public static int beamColour() {
		return beamColour;
	}

	public static void setBeamColour(int value) {
		if (beamColour == value) {
			return;
		}

		beamColour = value;
		save();
	}

	// ---- defaults and persistence ----------------------------------------

	/**
	 * Puts every setting back to the value {@code ping.json} is first written with.
	 *
	 * <p>This is what the screen's RESTORE button calls. It used to mean "set every toggle to
	 * true", which stopped meaning anything once the screen gained numbers, a lifetime has no
	 * {@code true}, so defaults live here, beside the values they restore.
	 */
	public static void resetToDefaults() {
		sendPingsOverChat = DEFAULT_SEND_PINGS_OVER_CHAT;
		showMinimap = DEFAULT_SHOW_MINIMAP;
		fortniteHealth = DEFAULT_FORTNITE_HEALTH;
		showPingFeed = DEFAULT_SHOW_PING_FEED;

		markerLifetimeMs = DEFAULT_MARKER_LIFETIME_MS;
		threatLifetimeMs = DEFAULT_THREAT_LIFETIME_MS;
		remarkCooldownMs = DEFAULT_REMARK_COOLDOWN_MS;

		markerLift = DEFAULT_MARKER_LIFT;
		markerHeightFraction = DEFAULT_MARKER_HEIGHT_FRACTION;
		occludedAlpha = DEFAULT_OCCLUDED_ALPHA;

		beamFadeNear = DEFAULT_BEAM_FADE_NEAR;
		beamFadeFar = DEFAULT_BEAM_FADE_FAR;

		threatColour = DEFAULT_THREAT_COLOUR;
		beamColour = DEFAULT_BEAM_COLOUR;

		save();
	}

	public static void load() {
		Path file = file();

		try {
			if (!Files.exists(file)) {
				// Write the defaults out so there is something to edit.
				save();
				return;
			}

			Data data = GSON.fromJson(Files.readString(file), Data.class);

			if (data != null) {
				sendPingsOverChat = data.sendPingsOverChat;
				showMinimap = data.showMinimap;
				fortniteHealth = data.fortniteHealth;
				showPingFeed = data.showPingFeed;

				// Every numeric key is clamped on the way in as well as on the way out: a
				// hand-edited file is still just a file, and the HUD should not be at its mercy.
				markerLifetimeMs = clamp(data.markerLifetimeMs, MARKER_LIFETIME_MIN_MS, MARKER_LIFETIME_MAX_MS);
				threatLifetimeMs = clamp(data.threatLifetimeMs, THREAT_LIFETIME_MIN_MS, THREAT_LIFETIME_MAX_MS);
				remarkCooldownMs = clamp(data.remarkCooldownMs, REMARK_COOLDOWN_MIN_MS, REMARK_COOLDOWN_MAX_MS);

				markerLift = clamp(data.markerLift, MARKER_LIFT_MIN, MARKER_LIFT_MAX);
				markerHeightFraction = clamp(data.markerHeightFraction, MARKER_HEIGHT_MIN, MARKER_HEIGHT_MAX);
				occludedAlpha = clamp(data.occludedAlpha, OCCLUDED_ALPHA_MIN, OCCLUDED_ALPHA_MAX);

				beamFadeNear = clamp(data.beamFadeNear, BEAM_FADE_MIN, BEAM_FADE_MAX);
				beamFadeFar = clamp(data.beamFadeFar, BEAM_FADE_MIN, BEAM_FADE_MAX);

				threatColour = data.threatColour;
				beamColour = data.beamColour;
			}

			// Written back unconditionally, so a file from an older version gains the keys
			// it is missing instead of silently lacking options that the mod already has.
			save();
		} catch (Exception e) {
			PingClient.LOGGER.warn("Could not read {}, using defaults", file, e);
		}
	}

	private static void save() {
		Path file = file();

		try {
			Files.createDirectories(file.getParent());

			Data data = new Data();
			data.sendPingsOverChat = sendPingsOverChat;
			data.showMinimap = showMinimap;
			data.fortniteHealth = fortniteHealth;
			data.showPingFeed = showPingFeed;

			data.markerLifetimeMs = markerLifetimeMs;
			data.threatLifetimeMs = threatLifetimeMs;
			data.remarkCooldownMs = remarkCooldownMs;

			data.markerLift = markerLift;
			data.markerHeightFraction = markerHeightFraction;
			data.occludedAlpha = occludedAlpha;

			data.beamFadeNear = beamFadeNear;
			data.beamFadeFar = beamFadeFar;

			data.threatColour = threatColour;
			data.beamColour = beamColour;

			Files.writeString(file, GSON.toJson(data));
		} catch (Exception e) {
			PingClient.LOGGER.warn("Could not write {}", file, e);
		}
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	private static long clamp(long value, long min, long max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	/**
	 * Shape of the on-disk file. Field names are the JSON keys.
	 *
	 * <p>The initialisers are the defaults, which is what makes a missing key harmless: Gson
	 * leaves an absent field at its initialiser, so an old file yields the same value the
	 * first launch writes. The four boolean names are the ones already in the wild.
	 */
	private static final class Data {
		private boolean sendPingsOverChat = DEFAULT_SEND_PINGS_OVER_CHAT;
		private boolean showMinimap = DEFAULT_SHOW_MINIMAP;
		private boolean fortniteHealth = DEFAULT_FORTNITE_HEALTH;
		private boolean showPingFeed = DEFAULT_SHOW_PING_FEED;

		private long markerLifetimeMs = DEFAULT_MARKER_LIFETIME_MS;
		private long threatLifetimeMs = DEFAULT_THREAT_LIFETIME_MS;
		private long remarkCooldownMs = DEFAULT_REMARK_COOLDOWN_MS;

		private double markerLift = DEFAULT_MARKER_LIFT;
		private double markerHeightFraction = DEFAULT_MARKER_HEIGHT_FRACTION;
		private double occludedAlpha = DEFAULT_OCCLUDED_ALPHA;

		private double beamFadeNear = DEFAULT_BEAM_FADE_NEAR;
		private double beamFadeFar = DEFAULT_BEAM_FADE_FAR;

		private int threatColour = DEFAULT_THREAT_COLOUR;
		private int beamColour = DEFAULT_BEAM_COLOUR;
	}
}
