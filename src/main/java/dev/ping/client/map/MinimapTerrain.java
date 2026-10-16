package dev.ping.client.map;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * The top-down colour of the world, sampled a chunk at a time.
 *
 * <p>This is the one genuinely new subsystem the minimap needs: everything else is
 * drawing. For every column of a chunk it finds the highest block and takes that block's
 * own {@link MapColor}: the same colours vanilla maps use, which is what makes the map
 * agree with the world it describes instead of being a palette invented here.
 *
 * <p>Sampling is costed deliberately. A 384-block-wide map is 147,456 columns if every
 * texel is read from the world, so instead chunks are sampled once into 16×16 colour
 * arrays and the renderer reads those. Sampling happens on the client tick, a couple of
 * chunks at a time, never inside rendering: a frame must not wait on world lookups, and a
 * map that takes a second to fill in is a map nobody notices filling in.
 *
 * <p>There is no block-update hook. Tiles are re-sampled on {@link
 * MinimapConstants#TILE_TTL_MS} instead, so a build or a hole shows up within a few
 * seconds. That is the trade for staying mixin-free, which this mod is.
 *
 * <p>Heightmap availability is not assumed. Which heightmaps a server ships to the client
 * is server-side policy ({@code Heightmap.Types.sendToClient()} decides), so the best
 * available one is picked at sample time, preferring the surface, and a chunk with none at
 * all simply samples as unknown.
 */
final class MinimapTerrain {
	/** Edge of one sampled chunk, in blocks. */
	private static final int TILE = 16;

	/** Chunk key -> its sampled colours, indexed {@code [z * 16 + x]}. */
	private final Map<Long, Tile> tiles = new HashMap<>();

	/**
	 * Bumped every time a tile is written.
	 *
	 * <p>The renderer uses it to notice that the cache has changed even though the player has
	 * not moved. Without it the map image would only ever be rebuilt when its centre crossed
	 * a block, which means standing still on a fresh world leaves the map permanently
	 * half-empty, and a block broken under your feet never appears.
	 */
	private long version;

	/** The biome regions, recomputed at most once a second. */
	private List<BiomeLabel> labels;
	private long labelsAt;

	/** Everything cached belongs to a world that is no longer loaded. */
	void clear() {
		this.tiles.clear();
		this.labels = null;
		this.version++;
	}

	/** How many times the cache has changed. See {@link #version}. */
	long version() {
		return this.version;
	}

	/**
	 * Samples what the map needs, and remembers what it has already seen.
	 *
	 * <p><b>Discovered terrain is kept.</b> A chunk that has been read keeps its colours
	 * after it unloads, so explored ground accumulates into a map instead of appearing as a
	 * circle that follows the player around. Two things follow from that, and both matter:
	 * a tile outside the loaded world is never re-sampled, because re-sampling it would
	 * replace remembered ground with "unknown"; and the cache is bounded by a cap on how
	 * many tiles it holds rather than by how far away they are.
	 *
	 * @param blocksAcross how much world the map is showing
	 */
	void tick(ClientLevel level, double centerX, double centerZ, int blocksAcross, long now) {
		int centerChunkX = chunkOf((int) Math.floor(centerX));
		int centerChunkZ = chunkOf((int) Math.floor(centerZ));

		// One chunk of slack on top of the visible window, so the edge of the map is
		// filled before it is on screen rather than after.
		int half = blocksAcross / 2 / TILE + 1;
		int budget = MinimapConstants.refreshBudget(half);
		int sampled = 0;

		// Missing tiles first: a map that has just opened should fill before it starts
		// re-reading what it already has. Only loaded chunks are worth sampling: an
		// unloaded one has nothing to say and would only record "unknown" over ground that
		// may already be remembered.
		for (int dz = -half; dz <= half && sampled < budget; dz++) {
			for (int dx = -half; dx <= half && sampled < budget; dx++) {
				int cx = centerChunkX + dx;
				int cz = centerChunkZ + dz;
				long key = key(cx, cz);

				if (this.tiles.containsKey(key) || !loaded(level, cx, cz)) {
					continue;
				}

				this.tiles.put(key, sample(level, cx, cz, now));
				this.version++;
				sampled++;
			}
		}

		// Then the stale ones, walked over what is actually cached rather than over the whole
		// window a second time: the cached set is about the window's size, and this way the
		// scan costs no key allocation.
		//
		// Replacing a value for a key already in a HashMap does not count as a structural
		// change, so writing to the map inside this loop is safe.
		if (sampled < budget) {
			for (Map.Entry<Long, Tile> entry : this.tiles.entrySet()) {
				if (sampled >= budget) {
					break;
				}

				Tile tile = entry.getValue();

				if (now - tile.sampledAt() < MinimapConstants.TILE_TTL_MS) {
					continue;
				}

				long key = entry.getKey();
				int cx = chunkX(key);
				int cz = chunkZ(key);

				// Only the window, and only if the chunk is loaded: remembered ground outside
				// it is left exactly as it was found.
				if (Math.abs(cx - centerChunkX) > half || Math.abs(cz - centerChunkZ) > half
						|| !loaded(level, cx, cz)) {
					continue;
				}

				this.tiles.put(key, sample(level, cx, cz, now));
				this.version++;
				sampled++;
			}
		}

		if (this.tiles.size() > MinimapConstants.MAX_TILES) {
			evictFarthest(centerChunkX, centerChunkZ);
		}
	}

	/** True when this chunk is in the client's world right now. */
	private static boolean loaded(ClientLevel level, int chunkX, int chunkZ) {
		return level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) != null;
	}

	/**
	 * Drops the tiles furthest from the player until the cache is back under its cap.
	 *
	 * <p>A crude eviction, deliberately: the aim is a session that does not grow forever, not
	 * an optimal cache. Anything dropped is simply re-sampled if it is visited again.
	 */
	private void evictFarthest(int centerChunkX, int centerChunkZ) {
		int excess = this.tiles.size() - MinimapConstants.MAX_TILES;
		List<Long> keys = new ArrayList<>(this.tiles.keySet());

		keys.sort((a, b) -> {
			int da = Math.abs(chunkX(a) - centerChunkX) + Math.abs(chunkZ(a) - centerChunkZ);
			int db = Math.abs(chunkX(b) - centerChunkX) + Math.abs(chunkZ(b) - centerChunkZ);

			return Integer.compare(db, da);
		});

		for (int i = 0; i < excess && i < keys.size(); i++) {
			this.tiles.remove(keys.get(i));
		}

		this.version++;
	}

	/** The sampled colours of a chunk, or {@code null} if it has not been sampled. */
	int[] colorsOf(int chunkX, int chunkZ) {
		Tile tile = this.tiles.get(key(chunkX, chunkZ));

		return tile == null ? null : tile.colors();
	}

	/**
	 * The biome regions worth naming, each at its own centre.
	 *
	 * <p>This is what a "POI" is on this map: a name is drawn at the middle of a run of
	 * chunks that share a biome, so the label belongs to the region rather than to the
	 * player. Regions smaller than {@link MinimapConstants#MIN_BIOME_CHUNKS} are dropped:
	 * a one-chunk patch of river bank does not deserve a caption in the middle of the map.
	 *
	 * <p>Cached, and recomputed at most once a second: the cache changes constantly as
	 * terrain loads, and grouping it on every change would be absurd when the answer moves
	 * this slowly.
	 */
	List<BiomeLabel> biomeLabels(long now) {
		if (this.labels != null && now - this.labelsAt < 1000L) {
			return this.labels;
		}

		List<BiomeLabel> found = new ArrayList<>();
		Set<Long> seen = new HashSet<>();

		for (long start : this.tiles.keySet()) {
			if (seen.contains(start) || !worthNaming(start)) {
				continue;
			}

			// Flood fill this region: 4-connected chunks on the same biome name.
			List<Long> region = new ArrayList<>();
			Deque<Long> queue = new ArrayDeque<>();
			boolean complete = true;
			queue.add(start);
			seen.add(start);

			while (!queue.isEmpty()) {
				long key = queue.poll();
				region.add(key);

				int cx = chunkX(key);
				int cz = chunkZ(key);
				long[] around = { key(cx + 1, cz), key(cx - 1, cz), key(cx, cz + 1), key(cx, cz - 1) };

				for (long next : around) {
					if (this.tiles.get(next) == null) {
						// Unexplored ground next to this region: it may be the same biome, and it
						// may stretch the region's true middle somewhere else entirely. Naming it
						// now would put the label off-centre, so the region waits until it is
						// surrounded by ground the client has actually seen.
						complete = false;

						continue;
					}

					if (seen.contains(next) || !sameBiome(start, next)) {
						continue;
					}

					seen.add(next);
					queue.add(next);
				}
			}

			if (!complete || region.size() < MinimapConstants.MIN_BIOME_CHUNKS) {
				continue;
			}

			long sumX = 0L;
			long sumZ = 0L;

			for (long key : region) {
				sumX += chunkX(key) * TILE + TILE / 2L;
				sumZ += chunkZ(key) * TILE + TILE / 2L;
			}

			found.add(new BiomeLabel(sumX / (double) region.size(), sumZ / (double) region.size(),
					this.tiles.get(start).biome()));
		}

		this.labels = found;
		this.labelsAt = now;

		return found;
	}

	private boolean worthNaming(long key) {
		Tile tile = this.tiles.get(key);

		return tile != null && !tile.biome().isEmpty();
	}

	private boolean sameBiome(long a, long b) {
		Tile first = this.tiles.get(a);
		Tile second = this.tiles.get(b);

		return first != null && second != null && first.biome().equals(second.biome());
	}

	/** Samples one chunk into its 16×16 colours. Never returns {@code null}. */
	private static Tile sample(ClientLevel level, int chunkX, int chunkZ, long now) {
		int[] colors = new int[TILE * TILE];
		LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);

		if (chunk == null) {
			// Not loaded here. Not an error: the map shows it as unknown until it is.
			Arrays.fill(colors, MinimapConstants.UNKNOWN_COLOR);

			return new Tile(colors, "", now);
		}

		Heightmap surface = TerrainHeight.of(chunk);
		int originX = chunkX * TILE;
		int originZ = chunkZ * TILE;

		// Which biome this chunk is in, read once at its centre. Stored with the colours so
		// the map can name regions without touching the world again.
		String biome = biomeName(level, originX + TILE / 2, originZ + TILE / 2);

		// One reusable position for the whole chunk. Ten chunks a tick is 2,560 columns, and
		// a fresh BlockPos per column is 2,560 objects a tick for no reason: the block getter
		// does not keep it.
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int z = 0; z < TILE; z++) {
			// Relief shading, as vanilla maps do it: a column is lit or shaded by whether
			// it stands above or below the column to its west.
			//
			// The west neighbour is read across the chunk boundary as well. Carrying a height
			// only *within* a chunk meant every chunk's first column fell back to unshaded,
			// which painted a visible 16-block grid over the whole map, the "invisible grid"
			// a player sees as the map snapping.
			int previous = westHeight(level, surface, chunkX, chunkZ, z);
			boolean hasPrevious = previous != TerrainHeight.UNKNOWN;

			for (int x = 0; x < TILE; x++) {
				if (surface == null) {
					colors[z * TILE + x] = MinimapConstants.UNKNOWN_COLOR;
					continue;
				}

				// getFirstAvailable is the first y *above* the highest matching block, so
				// the block the map shows is one below it.
				int y = surface.getFirstAvailable(x, z) - 1;
				BlockPos at = pos.set(originX + x, y, originZ + z);
				BlockState state = level.getBlockState(at);
				MapColor mapColor = state.getMapColor(level, at);

				if (state.isAir() || mapColor == MapColor.NONE) {
					colors[z * TILE + x] = MinimapConstants.UNKNOWN_COLOR;
					previous = y;
					hasPrevious = true;
					continue;
				}

				MapColor.Brightness brightness = MapColor.Brightness.NORMAL;

				if (hasPrevious) {
					if (y > previous) {
						brightness = MapColor.Brightness.HIGH;
					} else if (y < previous) {
						brightness = MapColor.Brightness.LOW;
					}
				}

				colors[z * TILE + x] = 0xFF000000 | mapColor.calculateARGBColor(brightness);
				previous = y;
				hasPrevious = true;
			}
		}

		return new Tile(colors, biome, now);
	}

	/** The height of the block west of this chunk's first column, or UNKNOWN. */
	private static int westHeight(ClientLevel level, Heightmap surface, int chunkX, int chunkZ, int z) {
		if (surface != null && chunkX > Integer.MIN_VALUE) {
			LevelChunk west = level.getChunkSource().getChunk(chunkX - 1, chunkZ, ChunkStatus.FULL, false);

			if (west != null) {
				Heightmap heightmap = TerrainHeight.of(west);

				if (heightmap != null) {
					return heightmap.getFirstAvailable(TILE - 1, z) - 1;
				}
			}
		}

		return TerrainHeight.UNKNOWN;
	}

	/** The biome at a world column, named the way the game names it. */
	public static String biomeName(ClientLevel level, int blockX, int blockZ) {
		return level.getBiome(new BlockPos(blockX, level.getMinY(), blockZ)).unwrapKey()
				.map(key -> Component.translatable(
						"biome." + key.identifier().getNamespace() + "." + key.identifier().getPath()).getString())
				.orElse("");
	}

	private static long key(long chunkX, long chunkZ) {
		return (chunkX & 0xFFFFFFFFL) | ((chunkZ & 0xFFFFFFFFL) << 32);
	}

	private static int chunkOf(int worldCoordinate) {
		return worldCoordinate >> 4;
	}

	private static int chunkX(long key) {
		return (int) key;
	}

	private static int chunkZ(long key) {
		return (int) (key >> 32);
	}

	/** One chunk's colours, its biome, and when they were read. */
	private record Tile(int[] colors, String biome, long sampledAt) {
	}

	/** A named biome region, at the middle of the region. */
	record BiomeLabel(double x, double z, String name) {
	}
}
