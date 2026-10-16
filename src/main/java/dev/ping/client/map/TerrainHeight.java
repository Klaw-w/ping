package dev.ping.client.map;

import java.util.List;
import java.util.Map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Reading the surface of a column, without depending on the client having the heightmap.
 *
 * <p>Which heightmaps a client receives is the server's choice: {@code
 * Heightmap.Types.sendToClient()} decides, and it is only the {@code CLIENT}-usage ones, so
 * a client can simply not have the map a caller asks for. Nobody needs to be protected from
 * a crash here: {@code Level.getHeight} handles the gap by priming the missing heightmap on
 * demand from the chunk's block states. What this avoids is <em>paying</em> for that
 * re-scan. A minimap reads a whole chunk's worth of columns, and priming a heightmap
 * because a map was absent is the difference between one lookup and 256.
 *
 * <p>Both the terrain sampler and a marker placed on the map need the same answer and the
 * same safety, so the lookup lives here once.
 *
 * <p>Preferred in the order a top-down map wants to read: the true surface, then anything
 * that blocks motion (water, leaves), then the ocean floor. A chunk with none of them
 * reports itself as unknown rather than guessing, and the caller decides what to do about
 * that, the sampler marks the column unknown, a map-placed marker falls back to the
 * player's own height.
 */
final class TerrainHeight {
	/** {@code getFirstAvailable} for a column with no heightmap at all. */
	static final int UNKNOWN = Integer.MIN_VALUE;

	private static final List<Heightmap.Types> PREFERRED = List.of(
			Heightmap.Types.WORLD_SURFACE,
			Heightmap.Types.MOTION_BLOCKING,
			Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
			Heightmap.Types.OCEAN_FLOOR
	);

	private TerrainHeight() {
	}

	/** The best heightmap a chunk has, or {@code null} if it has none. */
	static Heightmap of(ChunkAccess chunk) {
		for (Heightmap.Types type : PREFERRED) {
			for (Map.Entry<Heightmap.Types, Heightmap> entry : chunk.getHeightmaps()) {
				if (entry.getKey() == type) {
					return entry.getValue();
				}
			}
		}

		return null;
	}

	/**
	 * The y a marker on this column should sit at: the first air position above the
	 * ground, which is what a beam stands on.
	 *
	 * @return {@link #UNKNOWN} when the chunk is not loaded or has no usable heightmap
	 */
	static int surfaceY(ClientLevel level, int blockX, int blockZ) {
		LevelChunk chunk = level.getChunkSource().getChunk(blockX >> 4, blockZ >> 4, ChunkStatus.FULL, false);

		if (chunk == null) {
			return UNKNOWN;
		}

		Heightmap heightmap = of(chunk);

		return heightmap == null ? UNKNOWN : heightmap.getFirstAvailable(blockX & 15, blockZ & 15);
	}
}
