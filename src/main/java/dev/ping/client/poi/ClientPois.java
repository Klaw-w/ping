package dev.ping.client.poi;

import java.util.ArrayList;
import java.util.List;

import dev.ping.poi.Poi;
import dev.ping.poi.PoiStore;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;

/**
 * Named locations on the client: which world they belong to, and the banner that makes one.
 *
 * <p>Right-clicking a banner names the place it stands on. The click is <b>consumed on the
 * client</b> rather than passed to the server, so naming a banner does not also place the
 * block you happen to be holding, the same reason the map key does not fall through to the
 * world. Banners have no use action of their own, so nothing on the server is being
 * suppressed by doing this.
 */
public final class ClientPois {
	private ClientPois() {
	}

	/**
	 * Identifies the world a name belongs to.
	 *
	 * <p>A server and a singleplayer world are different places even at the same
	 * coordinates, and two servers are different places from each other, so the key is the
	 * server's address or the world's name rather than anything positional.
	 */
	public static String worldKey(Minecraft client) {
		ServerData server = client.getCurrentServer();

		if (server != null) {
			return "server:" + server.ip;
		}

		IntegratedServer integrated = client.getSingleplayerServer();

		if (integrated != null) {
			return "world:" + integrated.getWorldData().getLevelName();
		}

		return "unknown";
	}

	public static String dimensionOf(Level level) {
		return level.dimension().identifier().toString();
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !level.isClientSide()) {
				return InteractionResult.PASS;
			}

			BlockPos pos = hit.getBlockPos();

			if (!level.getBlockState(pos).is(BlockTags.BANNERS)) {
				return InteractionResult.PASS;
			}

			Minecraft client = Minecraft.getInstance();
			String world = worldKey(client);
			String dimension = dimensionOf(level);
			Poi existing = PoiStore.at(world, dimension, pos.getX(), pos.getY(), pos.getZ());

			client.setScreenAndShow(new PingPoiScreen(world, dimension, pos.getX(), pos.getY(), pos.getZ(), existing));

			return InteractionResult.SUCCESS;
		});
	}

	/** How often the banners behind the names are re-checked. */
	private static final long SWEEP_INTERVAL_MS = 1000L;

	private static long nextSweep;

	/**
	 * Drops a name whose banner is gone.
	 *
	 * <p>The name is anchored to the banner, so breaking the banner has to break the name.
	 * The check is a sweep rather than a hook on block breaking, because a banner can also
	 * be destroyed by an explosion, by another player, or by a piston; none of which the
	 * person who named it performs, and only a sweep catches all of them.
	 *
	 * <p><b>The loaded check is load-bearing.</b> An unloaded chunk answers "not a banner"
	 * for every block inside it, so judging a name in one deletes every name outside the
	 * render distance the moment it is swept. Only places the client can actually see are
	 * judged.
	 */
	public static void tick(Minecraft client) {
		long now = System.currentTimeMillis();

		if (now < nextSweep) {
			return;
		}

		nextSweep = now + SWEEP_INTERVAL_MS;

		if (client.level == null || client.player == null) {
			return;
		}

		String world = worldKey(client);
		String dimension = dimensionOf(client.level);
		List<Poi> broken = new ArrayList<>();

		for (Poi poi : PoiStore.all(world)) {
			if (!poi.dimension().equals(dimension)) {
				continue;
			}

			BlockPos pos = new BlockPos(poi.x(), poi.y(), poi.z());

			if (!client.level.isLoaded(pos)) {
				continue;
			}

			if (!client.level.getBlockState(pos).is(BlockTags.BANNERS)) {
				broken.add(poi);
			}
		}

		// Removed after the walk rather than during it, so the list being iterated is not
		// also the list being written.
		for (Poi poi : broken) {
			PoiStore.remove(world, poi);
		}
	}
}
