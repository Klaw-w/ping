package dev.ping.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;

/**
 * The minimap: terrain, zoom, and the texture everything draws from.
 *
 * <p>Shared by the corner minimap and the full-size map, because they are the same map.
 * The HUD element and the map screen differ in where they put the frame, how big it is and
 * whether they draw the grid; they do not differ in what the world looks like, and
 * duplicating the sampling for them would be two things to keep in step.
 *
 * <p>The split of work matters. {@link #tick} samples the world, on the client tick.
 * {@link #texture} composes and uploads, on the render thread. {@link MinimapRenderer}
 * only draws. Nothing samples terrain inside a frame.
 */
public final class Minimap {
	private final MinimapTerrain terrain = new MinimapTerrain();
	private final MinimapTexture texture = new MinimapTexture();

	private ClientLevel lastLevel;
	private int zoom = MinimapConstants.DEFAULT_ZOOM;

	/** The full-size map's range while it is open, or 0 when it is not. */
	private int screenBlocks;

	/** Samples a slice of the world. Called once per client tick. */
	public void tick(Minecraft client, long now) {
		if (client.level != this.lastLevel) {
			// Markers do not survive a world change, and neither does terrain.
			this.terrain.clear();
			this.lastLevel = client.level;
		}

		if (client.level == null || client.player == null) {
			return;
		}

		this.terrain.tick(client.level, client.player.getX(), client.player.getZ(), this.samplingBlocks(), now);
	}

	public int blocksAcross() {
		return MinimapConstants.zoomBlocks(this.zoom);
	}

	/** Steps the zoom ladder. Positive is one step further out. */
	public void zoomBy(int steps) {
		this.zoom = MinimapConstants.clampZoom(this.zoom + steps);
	}

	/**
	 * The map texture for a view centred here, brought up to date if needed.
	 *
	 * @param now current wall clock, so terrain arriving can be throttled rather than
	 *            rebuilding the image on every frame
	 * @return {@code null} when there is nothing to draw yet
	 */
	public Identifier texture(Minecraft client, double centerX, double centerZ, int blocksAcross, long now) {
		return this.texture.prepare(client, this.terrain, client.level, centerX, centerZ,
				blocksAcross, this.terrain.version(), now);
	}

	/** The biome regions worth naming, each at its own centre. */
	public java.util.List<MinimapTerrain.BiomeLabel> biomeLabels(long now) {
		return this.terrain.biomeLabels(now);
	}

	/**
	 * How much world the terrain cache has to cover right now.
	 *
	 * <p>The full-size map can be zoomed further out than the corner minimap, and it shares
	 * this cache, so while it is open it asks for its own range, and the cache samples
	 * whichever of the two is wider.
	 */
	public void setScreenRange(int blocksAcross) {
		this.screenBlocks = blocksAcross;
	}

	private int samplingBlocks() {
		return Math.max(blocksAcross(), this.screenBlocks);
	}

	/**
	 * The y a marker placed on this column should sit at, or {@link TerrainHeight#UNKNOWN}
	 * when the client cannot tell.
	 *
	 * <p>Exposed for the map screen, which places markers on a column rather than on what
	 * the crosshair is over, and so has to ask the world how high the ground is there.
	 */
	public static int surfaceY(ClientLevel level, int blockX, int blockZ) {
		return TerrainHeight.surfaceY(level, blockX, blockZ);
	}
}
