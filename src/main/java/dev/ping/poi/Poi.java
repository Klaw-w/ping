package dev.ping.poi;

/**
 * A named location the player made by naming a banner.
 *
 * <p>Minecraft-free on purpose, like {@link dev.ping.ping.PingManager}, so the storage can
 * be reasoned about without a client.
 *
 * <p>The position is stored as block coordinates rather than a floating point position: a
 * named place is a place, and a banner sits on a block. {@code y} is kept only so the name
 * can be shown against the right block; nothing labels by height.
 *
 * @param name      what the player called it, trimmed and non-empty
 * @param x         block x
 * @param y         block y
 * @param z         block z
 * @param dimension the dimension's id, so the Nether's places are not the Overworld's
 */
public record Poi(String name, int x, int y, int z, String dimension) {
	public double distanceSqrTo(double px, double pz) {
		double dx = this.x + 0.5D - px;
		double dz = this.z + 0.5D - pz;

		return dx * dx + dz * dz;
	}

	/** True if this names the same block as {@code other}. */
	public boolean sameSpot(Poi other) {
		return other != null
				&& this.x == other.x()
				&& this.y == other.y()
				&& this.z == other.z()
				&& this.dimension.equals(other.dimension());
	}
}
