package dev.ping.poi;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Named locations, kept in {@code config/ping-locations.json}.
 *
 * <p>Keyed by world, because a named place belongs to the world it was named in: the same
 * coordinates on a different server are a different place, and a singleplayer world's
 * names have no business turning up on a server. The key is decided by the caller (see
 * {@code ClientPois}), and is opaque here.
 *
 * <p>The file is written on every change rather than on shutdown, so a crash cannot lose a
 * location the player just typed.
 */
public final class PoiStore {
	private static final String FILE_NAME = "ping-locations.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Long enough for a descriptive name, short enough not to run off the map. */
	public static final int MAX_NAME_LENGTH = 32;

	private static final Map<String, List<Poi>> BY_WORLD = new LinkedHashMap<>();
	private static boolean loaded;

	private PoiStore() {
	}

	/** Every named place in a world, in the order they were made. */
	public static List<Poi> all(String worldKey) {
		PoiStore.load();

		return BY_WORLD.getOrDefault(worldKey, List.of());
	}

	/** The named place on exactly this block, or {@code null}. */
	public static Poi at(String worldKey, String dimension, int x, int y, int z) {
		for (Poi poi : all(worldKey)) {
			if (poi.x() == x && poi.y() == y && poi.z() == z && poi.dimension().equals(dimension)) {
				return poi;
			}
		}

		return null;
	}

	/**
	 * Adds or renames. A location already on that block is replaced rather than duplicated,
	 * so naming the same banner twice renames it instead of stacking two labels.
	 */
	public static void put(String worldKey, Poi poi) {
		PoiStore.load();

		List<Poi> pois = new ArrayList<>(all(worldKey));
		pois.removeIf(existing -> existing.sameSpot(poi));
		pois.add(poi);
		BY_WORLD.put(worldKey, pois);
		save();
	}

	public static void remove(String worldKey, Poi poi) {
		PoiStore.load();

		List<Poi> pois = new ArrayList<>(all(worldKey));

		if (pois.removeIf(existing -> existing.sameSpot(poi))) {
			BY_WORLD.put(worldKey, pois);
			save();
		}
	}

	/**
	 * The nearest named place within {@code radius} blocks on the same dimension, or
	 * {@code null}.
	 *
	 * <p>Nearest rather than first: two named places can be close together, and the one you
	 * are standing in is the one that should name where you are.
	 */
	public static Poi nearest(String worldKey, String dimension, double px, double pz, double radius) {
		double limit = radius * radius;
		Poi best = null;
		double bestDistance = limit;

		for (Poi poi : all(worldKey)) {
			if (!poi.dimension().equals(dimension)) {
				continue;
			}

			double distance = poi.distanceSqrTo(px, pz);

			if (distance <= bestDistance) {
				bestDistance = distance;
				best = poi;
			}
		}

		return best;
	}

	/** Trims and sanity-checks a typed name. Returns an empty string if it is not usable. */
	public static String cleanName(String raw) {
		if (raw == null) {
			return "";
		}

		String trimmed = raw.trim();

		if (trimmed.isEmpty()) {
			return "";
		}

		return trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed;
	}

	private static void load() {
		if (loaded) {
			return;
		}

		loaded = true;

		try {
			Path file = file();

			if (!Files.exists(file)) {
				return;
			}

			Data data = GSON.fromJson(Files.readString(file), Data.class);

			if (data == null || data.worlds == null) {
				return;
			}

			for (Map.Entry<String, List<Poi>> entry : data.worlds.entrySet()) {
				if (entry.getValue() != null) {
					BY_WORLD.put(entry.getKey(), entry.getValue());
				}
			}
		} catch (Exception e) {
			dev.ping.client.PingClient.LOGGER.warn("Could not read named locations", e);
		}
	}

	private static void save() {
		try {
			Path file = file();
			Files.createDirectories(file.getParent());

			Data data = new Data();
			data.worlds = BY_WORLD;

			Files.writeString(file, GSON.toJson(data));
		} catch (Exception e) {
			dev.ping.client.PingClient.LOGGER.warn("Could not write named locations", e);
		}
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	/** Shape of the on-disk file. */
	private static final class Data {
		private Map<String, List<Poi>> worlds = new LinkedHashMap<>();
	}
}
