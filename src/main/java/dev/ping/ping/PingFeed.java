package dev.ping.ping;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The ping feed: the short "X marked an enemy location" lines Fortnite puts in the corner.
 *
 * <p>Deliberately Minecraft-free, like {@link PingManager}, so the wording and the de-duping
 * can be reasoned about without a client. The client drains this once a tick and turns each
 * line into a chat message — the feed and chat are one thing here, not two, so this is a
 * queue rather than something that holds state on screen.
 *
 * <p>Fortnite calls this the Message Feed and reuses it for eliminations; this is only the
 * marker half of it. The wording is Fortnite's own, taken from the strings published for
 * Squad Comms — "marked an enemy location", "marked a destination" — rather than invented
 * here.
 */
public final class PingFeed {
	/**
	 * A ping can arrive twice: once when it is placed locally and again a moment later when
	 * the server echoes the chat message back to its sender. Identical lines inside this
	 * window are the same event, not two.
	 */
	private static final long DUPLICATE_WINDOW_MS = 400L;

	private final Deque<Entry> pending = new ArrayDeque<>();
	private Entry last;

	/** Queues a marker event. A duplicate of the one just before it is dropped. */
	public void add(Ping ping, long now) {
		String author = ping.authorName();
		String action = ping.type().feedAction();

		if (this.last != null
				&& this.last.author().equals(author)
				&& this.last.action().equals(action)
				&& now - this.last.at() < DUPLICATE_WINDOW_MS) {
			return;
		}

		Entry entry = new Entry(author, ping.color(), action, now);
		this.last = entry;
		this.pending.addLast(entry);
	}

	/** Takes everything queued since the last call, oldest first. */
	public List<Entry> drain() {
		List<Entry> out = new ArrayList<>(this.pending);
		this.pending.clear();

		return out;
	}

	public void clear() {
		this.pending.clear();
		this.last = null;
	}

	/**
	 * One line.
	 *
	 * @param author the player whose ping it was
	 * @param color  that player's marker colour, so the name matches their marker
	 * @param action what they did, in Fortnite's own words
	 * @param at     when it was placed, for de-duping
	 */
	public record Entry(String author, int color, String action, long at) {
	}
}
