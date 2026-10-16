package dev.ping.ping;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.world.phys.Vec3;

/**
 * The live set of markers on this client.
 *
 * <p>Kept free of any Minecraft client types on purpose: the caller ticks it with
 * whatever it managed to resolve, so this class stays trivially testable and does not
 * churn when the render or level APIs move.
 *
 * <p>Fortnite keeps one world marker per player (marking again moves your existing
 * marker rather than adding a second one), so this is keyed by author.
 */
public final class PingManager {
	private final Map<UUID, Ping> byAuthor = new LinkedHashMap<>();

	/**
	 * The corner feed. Recorded here rather than at the call sites because every marker,
	 * placed here or received over chat, arrives through {@link #place}, so this is the
	 * one place that cannot miss one.
	 */
	private final PingFeed feed = new PingFeed();

	/** Places or replaces the marker belonging to {@code ping.author()}. */
	public void place(Ping ping) {
		this.byAuthor.put(ping.author(), ping);
		this.feed.add(ping, ping.createdAt());
	}

	/** The corner feed. */
	public PingFeed feed() {
		return this.feed;
	}

	/** Drops this author's marker, if any. Used when pinging the same spot twice. */
	public void remove(UUID author) {
		this.byAuthor.remove(author);
	}

	public void clear() {
		this.byAuthor.clear();
		this.feed.clear();
	}

	public Collection<Ping> all() {
		return this.byAuthor.values();
	}

	public int size() {
		return this.byAuthor.size();
	}

	public Ping get(UUID author) {
		return this.byAuthor.get(author);
	}

	/**
	 * Advances every marker by one tick.
	 *
	 * @param now             current wall clock
	 * @param resolveEntity   maps an entity id to its current position, or {@code null}
	 *                        if the entity is not (or no longer) known to this client
	 */
	public void tick(long now, EntityPositionResolver resolveEntity) {
		this.byAuthor.values().removeIf(ping -> ping.isExpired(now));

		// An item marker is tied to the dropped item it points at. Once that item is
		// picked up there is nothing left to mark, so the marker goes with it. Location
		// and enemy markers outlive their target and freeze in place instead.
		this.byAuthor.values().removeIf(ping -> ping.isAttached()
				&& ping.type() == PingType.ITEM
				&& resolveEntity.resolve(ping.attachedEntityId()) == null);

		this.byAuthor.replaceAll((author, ping) -> {
			if (!ping.isAttached()) {
				return ping;
			}

			// Enemy markers only track for a while, then freeze in place like Fortnite's do.
			if (now - ping.createdAt() > PingConstants.ATTACH_MS) {
				return ping.detached();
			}

			Vec3 live = resolveEntity.resolve(ping.attachedEntityId());

			if (live == null) {
				// Target left our tracking range. Freeze rather than delete, so the marker
				// still shows where they were last seen.
				return ping.detached();
			}

			return ping.withPos(live);
		});
	}

	/** Resolves an entity id to a live world position. */
	@FunctionalInterface
	public interface EntityPositionResolver {
		Vec3 resolve(int entityId);
	}
}
