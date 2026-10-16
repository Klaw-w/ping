package dev.ping.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.stats.Stats;

/**
 * The two numbers Fortnite puts beside the minimap.
 *
 * <p>Both are read from data the client already has, rather than tracked here. That matters
 * more than it sounds: a hand-rolled kill counter cannot be made correct on the client,
 * because no clientbound packet carries the killer — not the death packet, not entity
 * removal. The server's own statistic is the only honest source, and the client holds a
 * live copy of it.
 */
final class PingCounters {
	private PingCounters() {
	}

	/**
	 * Players on this server, from the tab list.
	 *
	 * <p>Uses the <em>listed</em> set rather than everything the server has ever announced,
	 * which is what vanilla's own player list draws. It includes the local player: Fortnite's
	 * count is everyone in the match including you, and a squad of one reads 1, not 0.
	 */
	static int playersOnline(Minecraft client) {
		if (client.getConnection() == null) {
			return 0;
		}

		return client.getConnection().getListedOnlinePlayers().size();
	}

	/**
	 * The local player's kills in this world.
	 *
	 * <p>{@code minecraft:player_kills}, read from the player's own stats. The server keeps
	 * that authoritative and pushes changes to the client, so this is the same number the
	 * statistics screen shows — and it is per world or server profile rather than per
	 * session, which is what "kills in that world" means.
	 *
	 * <p>A player-kills-only counter reads zero in singleplayer and on any server with no
	 * other players. That is Fortnite's meaning of the readout, kept deliberately: it is
	 * accurate rather than lively.
	 */
	static int kills(Minecraft client) {
		if (client.player == null) {
			return 0;
		}

		return client.player.getStats().getValue(Stats.CUSTOM, Stats.PLAYER_KILLS);
	}
}
