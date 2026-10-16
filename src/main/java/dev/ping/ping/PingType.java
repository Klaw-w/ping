package dev.ping.ping;

/**
 * The categories of marker the system can place. Mirrors Fortnite's mark types,
 * including the separate "danger" variant that a double-tap of the ping key produces.
 *
 * <p><b>Colour deliberately lives elsewhere.</b> A marker is drawn in its author's
 * locator bar colour, derived from their UUID: see {@link PingColors}. Keeping colour
 * out of this enum means there is exactly one source of truth for it, and adding a new
 * marker type cannot accidentally invent a second palette.
 *
 * <p>Sound names are the drop-in asset slots documented in {@code docs/ASSETS.md}.
 */
public enum PingType {
	/** Plain location marker, "I mean here". */
	NEUTRAL("neutral", "Neutral", "neutral", "marked a location"),
	/** Enemy spotted. */
	ENEMY("enemy", "Enemy Spotted", "enemy", "marked an enemy location"),
	/** Danger / double-tap marker. */
	DANGER("danger", "Watch Out", "enemy", "marked a danger"),
	/** A dropped item, chest or other loot. */
	ITEM("item", "Item Here", "item", "marked an item"),
	/** Something worth defending or grouping on. */
	DEFEND("defend", "Defend This", "neutral", "marked a defend point"),
	/** "Let's go here": the movement order. */
	GOING("going", "Going Here", "neutral", "marked a destination");

	private final String id;
	private final String label;
	private final String sound;
	private final String feedAction;

	PingType(String id, String label, String sound, String feedAction) {
		this.id = id;
		this.label = label;
		this.sound = sound;
		this.feedAction = feedAction;
	}

	public String id() {
		return this.id;
	}

	public String label() {
		return this.label;
	}

	/** Base name of the sound file to play, without extension. */
	public String sound() {
		return this.sound;
	}

	/**
	 * What the feed calls this, in Fortnite's own words: "marked an enemy location",
	 * "marked a destination". Separate from {@link #label()}, which is the marker's own
	 * heading ("Enemy Spotted") and is title case.
	 */
	public String feedAction() {
		return this.feedAction;
	}

	public static PingType byId(String id) {
		for (PingType type : values()) {
			if (type.id.equals(id)) {
				return type;
			}
		}

		return NEUTRAL;
	}

	/** True for the hostile/attention marker family. */
	public boolean isThreat() {
		return this == ENEMY || this == DANGER;
	}
}
