package dev.ping.client.map;

import com.mojang.blaze3d.platform.NativeImage;

import dev.ping.client.PingClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * The map's pixels, as a texture the GUI can blit in one call.
 *
 * <p>The map is drawn by reading {@link MinimapTerrain}'s sampled colours into a
 * {@link NativeImage} and uploading it. That is the whole trick behind it being cheap: one
 * blit per frame, rather than one fill per map pixel — a 384-wide map drawn as individual
 * quads would be 147,456 of them, every frame, which is not a thing to do to a render
 * thread.
 *
 * <p>Recomposition is quantised to whole blocks. A texel's world position only changes
 * when the map's centre crosses a block boundary, so walking across a chunk rebuilds the
 * image a few hundred times rather than every frame, and the terrain is stable under the
 * markers drawn on top of it.
 *
 * <p>The texture is created once, on first use, and lives for the session. It is worth
 * being precise about why that is safe, because the obvious reading is wrong: {@code
 * TextureManager.reload} only re-schedules reloadable textures, and a {@code DynamicTexture}
 * is not one, so F3+T does not drop this. The manager closes it at shutdown, after the
 * render loop has ended. There is therefore no re-registration path to write, and — since
 * {@code AbstractTexture.getTexture()} throws rather than returning {@code null} once its
 * GPU texture has been released — no null check that could stand in for one.
 */
final class MinimapTexture {
	private static final Identifier ID = PingClient.id("minimap");

	private final int size = MinimapConstants.TEXELS;

	private DynamicTexture texture;
	private ClientLevel lastLevel;
	private double centerX = Double.NaN;
	private double centerZ = Double.NaN;
	private int blocksAcross = -1;
	private long version = -1L;
	private long composedAt;

	/**
	 * The texture's origin, snapped to the texel grid rather than to whole blocks.
	 *
	 * <p>This is what makes the map pan smoothly. Snapping to blocks meant a step of
	 * `blocksAcross / TEXELS` texels — 1.5 of them at the default zoom — so the sampling phase
	 * alternated as the centre moved and the terrain shimmered on its own. Snapping to the
	 * texel grid makes every step exactly one texel, which the blit offset then cancels
	 * exactly, leaving a residual movement of a fraction of a pixel.
	 *
	 * <p>Shared with the renderer, which has to subtract the same value to place the blit.
	 */
	static double snapToTexel(double world, int blocksAcross) {
		double perTexel = blocksAcross / (double) MinimapConstants.TEXELS;

		return Math.floor(world / perTexel) * perTexel;
	}

	/**
	 * Brings the texture up to date for a map centred here, and returns its id.
	 *
	 * @param version the terrain cache's change counter, so newly sampled chunks are drawn
	 * @param now     current wall clock
	 * @return {@code null} when there is no world to draw, in which case the caller draws
	 * its own backdrop rather than blitting nothing
	 */
	Identifier prepare(Minecraft client, MinimapTerrain terrain, ClientLevel level,
			double centerX, double centerZ, int blocksAcross, long version, long now) {
		if (level == null) {
			return null;
		}

		if (level != this.lastLevel) {
			// A new world: whatever is in the image is from a place we are no longer in.
			this.lastLevel = level;
			this.centerX = Double.NaN;
		}

		DynamicTexture target = ensure(client);

		if (target == null) {
			return null;
		}

		double snappedX = MinimapTexture.snapToTexel(centerX, blocksAcross);
		double snappedZ = MinimapTexture.snapToTexel(centerZ, blocksAcross);

		// The map has moved, or the zoom changed: rebuild, however recently it was rebuilt.
		boolean moved = snappedX != this.centerX || snappedZ != this.centerZ || blocksAcross != this.blocksAcross;

		// Terrain arrived, but the view did not move. Rebuilding the whole image for every
		// chunk the cache refilled would be twenty rebuilds a second; this is the throttle.
		boolean fresh = version != this.version && now - this.composedAt >= MinimapConstants.COMPOSE_INTERVAL_MS;

		if (!moved && !fresh) {
			return ID;
		}

		this.centerX = snappedX;
		this.centerZ = snappedZ;
		this.blocksAcross = blocksAcross;
		this.version = version;
		this.composedAt = now;

		// Composed around the snapped centre, not the raw one: the viewport the caller draws
		// with is snapped too, and a texture built around a different origin would sit up to
		// a block away from the markers drawn on top of it.
		compose(terrain, target.getPixels(), snappedX, snappedZ, blocksAcross);
		target.upload();

		return ID;
	}

	/** Writes every texel of the map for a window of {@code blocksAcross} blocks. */
	private void compose(MinimapTerrain terrain, NativeImage image, double centerX, double centerZ, int blocksAcross) {
		double blocksPerTexel = blocksAcross / (double) this.size;
		double originX = centerX - blocksAcross * 0.5D;
		double originZ = centerZ - blocksAcross * 0.5D;

		// Consecutive texels are usually in the same chunk, so the chunk's colours are
		// looked up once and reused. Without this the compose is one hash lookup per texel.
		int[] colors = null;
		int lastChunkX = Integer.MIN_VALUE;
		int lastChunkZ = Integer.MIN_VALUE;

		for (int z = 0; z < this.size; z++) {
			int worldZ = (int) Math.floor(originZ + (z + 0.5D) * blocksPerTexel);
			int chunkZ = worldZ >> 4;

			for (int x = 0; x < this.size; x++) {
				int worldX = (int) Math.floor(originX + (x + 0.5D) * blocksPerTexel);
				int chunkX = worldX >> 4;

				if (chunkX != lastChunkX || chunkZ != lastChunkZ) {
					colors = terrain.colorsOf(chunkX, chunkZ);
					lastChunkX = chunkX;
					lastChunkZ = chunkZ;
				}

				image.setPixel(x, z, colors == null
						? MinimapConstants.UNKNOWN_COLOR
						: colors[(worldZ & 15) * 16 + (worldX & 15)]);
			}
		}
	}

	/** The registered texture, creating it once if it is not there yet. */
	private DynamicTexture ensure(Minecraft client) {
		if (this.texture != null) {
			return this.texture;
		}

		try {
			DynamicTexture created = new DynamicTexture("ping minimap", this.size, this.size, false);

			client.getTextureManager().register(ID, created);
			this.texture = created;

			return created;
		} catch (RuntimeException e) {
			PingClient.LOGGER.warn("Could not allocate the minimap texture", e);

			return null;
		}
	}
}
