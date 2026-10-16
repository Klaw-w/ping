package dev.ping.ping;

import java.util.UUID;

import dev.ping.client.PingConfig;

import net.minecraft.util.ARGB;

/**
 * Marker colour.
 *
 * <p>This is a Minecraft-meets-Fortnite build, so a marker takes its author's
 * <em>locator bar</em> colour rather than one colour per marker type. That colour is not
 * stored anywhere — vanilla derives it on the fly from the player's UUID:
 *
 * <pre>
 *   ARGB.setBrightness(ARGB.color(255, uuid.hashCode()), 0.9F)
 * </pre>
 *
 * <p>Calling the same two functions means a pip on the locator bar and this player's
 * ping are always the exact same colour, with no palette to keep in sync. It is also
 * why two UUIDs can land on the same colour: only 24 bits of hash survive, so
 * collisions are expected rather than a bug.
 *
 * <p>Because the colour is a pure function of the UUID, it does not need to travel over
 * the wire — the receiving client derives the identical value from the sender's UUID.
 */
public final class PingColors {
	/** Vanilla's locator bar brightness multiplier. */
	private static final float LOCATOR_BRIGHTNESS = 0.9F;

	/** Used when there is no author at all. */
	public static final int FALLBACK = 0xFFFFFFFF;

	private PingColors() {
	}

	/** A player's locator bar colour, as 0xFFRRGGBB. */
	public static int of(UUID uuid) {
		if (uuid == null) {
			return FALLBACK;
		}

		return ARGB.setBrightness(ARGB.color(255, uuid.hashCode()), LOCATOR_BRIGHTNESS);
	}

	/**
	 * The colour a marker of this type should actually be drawn in.
	 *
	 * <p>Enemy and danger markers are forced to red the way Fortnite's danger marker is red
	 * regardless of who placed it — but only while {@link PingConfig#threatColour()} is
	 * non-zero. Set it to {@code 0} and threats wear the author's own colour like every
	 * other marker.
	 */
	public static int forType(PingType type, int authorColor) {
		int override = PingConfig.threatColour();

		if (override != 0 && type.isThreat()) {
			return override;
		}

		return authorColor;
	}
}
