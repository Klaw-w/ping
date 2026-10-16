package dev.ping.client.map;

import net.minecraft.world.phys.Vec3;

/**
 * Where a map is on screen, and what stretch of the world it covers.
 *
 * <p>The map is <b>north-up</b>: north is the top of the frame and the player's arrow
 * turns instead of the world. That is Fortnite's mapping, and it is also the only
 * orientation that keeps a marker's screen position a pure translation of its world
 * position, which is what lets the whole map be one texture with pins drawn over it.
 *
 * <p>Note the axes. Minecraft has north at -Z and east at +X, so screen x grows with world
 * x and screen y grows with world z. Getting that backwards mirrors the map, which is
 * exactly the sort of thing that looks plausible until someone walks east.
 *
 * <p>Not a general camera: no rotation, no pitch. A minimap has neither.
 */
public record MapViewport(float x, float y, float size, double centerX, double centerZ, double blocksAcross) {
	/** Screen x for a world x. */
	public float screenX(double worldX) {
		return (float) (this.x + this.size * (0.5D + (worldX - this.centerX) / this.blocksAcross));
	}

	/** Screen y for a world z. */
	public float screenY(double worldZ) {
		return (float) (this.y + this.size * (0.5D + (worldZ - this.centerZ) / this.blocksAcross));
	}

	/** World x under a screen x. */
	public double worldX(double screenX) {
		return this.centerX + (screenX - this.x - this.size * 0.5D) / this.size * this.blocksAcross;
	}

	/** World z under a screen y. */
	public double worldZ(double screenY) {
		return this.centerZ + (screenY - this.y - this.size * 0.5D) / this.size * this.blocksAcross;
	}

	public boolean contains(double screenX, double screenY) {
		return screenX >= this.x && screenX <= this.x + this.size
				&& screenY >= this.y && screenY <= this.y + this.size;
	}

	/** The middle of the frame, in screen pixels. Not the world centre, see {@link #centerX()}. */
	public float screenCenterX() {
		return this.x + this.size * 0.5F;
	}

	/** The middle of the frame, in screen pixels. */
	public float screenCenterY() {
		return this.y + this.size * 0.5F;
	}

	/** The world column under a screen point. The y is left for the caller to place. */
	public Vec3 column(double screenX, double screenY) {
		return new Vec3(this.worldX(screenX), 0.0D, this.worldZ(screenY));
	}

	/**
	 * A viewport centred on a world position.
	 *
	 * <p>The centre is deliberately <b>not</b> snapped to whole blocks. The terrain texture is
	 * composed around the whole-block position under the centre, and the renderer applies the
	 * fractional remainder as a blit offset, so the map pans on every frame instead of
	 * stepping a block at a time.
	 */
	public static MapViewport centredOn(float x, float y, float size, double worldX, double worldZ, double blocksAcross) {
		return new MapViewport(x, y, size, worldX, worldZ, blocksAcross);
	}
}
