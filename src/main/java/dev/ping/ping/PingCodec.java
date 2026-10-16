package dev.ping.ping;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import dev.ping.client.PingConfig;
import dev.ping.client.item.ItemColors;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Wire format for sending a ping to other players who also have the mod.
 *
 * <p>There is no client-to-client channel in Minecraft: a modded client can only ever
 * talk to the server, and a vanilla server will not relay custom payloads. The one
 * path that works on <em>any</em> server, with nothing installed server-side, is chat.
 * So a ping is smuggled through as a chat message with a marker prefix; every modded
 * client recognises it, cancels the chat line, and turns it back into a marker.
 *
 * <p>The payload is Base64 so it survives chat sanitising, word-wrapping and any
 * server that mangles punctuation:
 *
 * <pre>
 *   type(1) | x(2) | y(2) | z(2) | itemIdLen(1) | itemIdBytes(n)
 * </pre>
 *
 * <p>Coordinates are whole metres offset by 32768 to stay unsigned.
 *
 * <p><b>The colour is never carried.</b> It is a pure function of the placing player's
 * UUID for a location marker and of the item itself for an item marker, so both sides
 * work it out from the same inputs instead of trusting a value that could disagree
 * with them. That is also why an item's colour is re-derived rather than sent: the
 * colour of a dyed item comes from per-stack state that does not survive the trip, so
 * the far side can only ever give the receiver the item's natural colour.
 *
 * <p>The item segment is zero length for anything that is not marking an item, which
 * is what keeps a plain location marker down to eight bytes.
 *
 * <p>Attached markers are sent as their current position rather than an entity id,
 * because entity ids are not consistent across clients.
 */
public final class PingCodec {
	/** Token itself. Chosen to be something no human types by accident. */
	private static final String FENCE = "#pp#";

	/** Characters base64url uses, used to find where the payload ends. */
	private static final String BODY_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

	/**
	 * {@code type(1) | x(2) | y(2) | z(2)} — the smallest a valid payload may be. A
	 * clear message is exactly this long, and it is what a placement written by an
	 * earlier build of the mod, with no item segment, looks like.
	 */
	private static final int LENGTH = 7;

	/** Everything up to and including the item id length byte. */
	private static final int HEADER = 8;

	/** Start of the item id — immediately after that fixed prefix. */
	private static final int ITEM_ID = HEADER;

	private static final int COORD_OFFSET = 32768;
	private static final int COORD_RANGE = 65536;

	/** Ceiling on an item id, shared with the code that writes it. */
	private static final int MAX_ITEM_ID = PingItems.MAX_LENGTH;

	/** High bit of the type byte marks a "take my marker down" message. */
	private static final int CLEAR_FLAG = 0x80;

	private PingCodec() {
	}

	/**
	 * True if this text carries a ping.
	 *
	 * <p>Deliberately searches for the fence rather than requiring it at the start. The
	 * component handed to the receive event may be the decorated form the server sent —
	 * {@code "<Player> #pp#..."} — in which case a {@code startsWith} check silently
	 * fails and the raw line shows up in chat.
	 */
	public static boolean isPingMessage(String raw) {
		return raw != null && raw.indexOf(FENCE) >= 0;
	}

	/** Everything after the fence, up to the first character base64url cannot use. */
	private static String body(String raw) {
		if (raw == null) {
			return null;
		}

		int at = raw.indexOf(FENCE);

		if (at < 0) {
			return null;
		}

		int start = at + FENCE.length();
		int end = start;

		while (end < raw.length() && BODY_CHARS.indexOf(raw.charAt(end)) >= 0) {
			end++;
		}

		return end > start ? raw.substring(start, end) : null;
	}

	/** A message telling everyone to drop the sender's marker. */
	public static String encodeClear() {
		byte[] out = new byte[LENGTH];
		out[0] = (byte) CLEAR_FLAG;

		return FENCE + Base64.getUrlEncoder().withoutPadding().encodeToString(out);
	}

	/** True when this is a clear rather than a placement. */
	public static boolean isClear(String raw) {
		byte[] data = payload(raw);

		return data != null && (data[0] & CLEAR_FLAG) != 0;
	}

	/**
	 * Decodes the base64 payload, or {@code null} if it is not a well formed ping.
	 *
	 * <p>Requires the payload to be exactly as long as its own header says it is. A
	 * message clipped by word-wrapping, or by a server that likes to edit punctuation,
	 * fails here instead of being read as a marker somewhere it was not.
	 */
	private static byte[] payload(String raw) {
		String encoded = body(raw);

		if (encoded == null) {
			return null;
		}

		try {
			byte[] data = Base64.getUrlDecoder().decode(encoded.getBytes(StandardCharsets.US_ASCII));

			if (data.length < LENGTH) {
				return null;
			}

			// No item segment is legal at exactly LENGTH: that is the clear message, and
			// the placement payload of an older build. Anything longer has to account for
			// every byte of it.
			return data.length == LENGTH || data.length == HEADER + itemIdLength(data) ? data : null;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** How long the item id in this payload is, in bytes. */
	private static int itemIdLength(byte[] data) {
		return data.length > LENGTH ? data[LENGTH] & 0xFF : 0;
	}

	/** Encodes a marker that is not marking an item. */
	public static String encode(PingType type, Vec3 pos) {
		return encode(type, pos, null);
	}

	/**
	 * Encodes a marker for the wire. The author is carried by the chat line itself and
	 * the colour is re-derived on the receiving end, so neither is here — see
	 * {@link #decode} for what the marker ends up being drawn in.
	 *
	 * @param item the item being marked, or {@code null} when the marker is not one
	 */
	public static String encode(PingType type, Vec3 pos, Item item) {
		byte[] id = itemId(item);
		byte[] out = new byte[HEADER + id.length];

		out[0] = (byte) type.ordinal();
		putShort(out, 1, pos.x);
		putShort(out, 3, pos.y);
		putShort(out, 5, pos.z);
		out[LENGTH] = (byte) id.length;

		if (id.length > 0) {
			System.arraycopy(id, 0, out, ITEM_ID, id.length);
		}

		return FENCE + Base64.getUrlEncoder().withoutPadding().encodeToString(out);
	}

	/**
	 * The item id bytes to write, empty when the item cannot be named.
	 *
	 * <p>An item with no registry id, or an absurdly long one, costs the marker its icon
	 * rather than its existence, so the fallback is a plain pin and never a failed
	 * message.
	 */
	private static byte[] itemId(Item item) {
		String id = PingItems.idOf(item);

		if (id == null || id.length() > MAX_ITEM_ID) {
			return new byte[0];
		}

		byte[] utf8 = id.getBytes(StandardCharsets.UTF_8);

		return utf8.length > MAX_ITEM_ID ? new byte[0] : utf8;
	}

	/**
	 * Decodes a pinged chat line into a marker this client can show.
	 *
	 * <p>The colour is derived on both sides from the same inputs — the author's locator
	 * bar colour, and then the item's own colour for an item marker — which is exactly
	 * the work {@link dev.ping.client.PingController} did when it placed the
	 * original. Paying that cost again here is what makes two clients agree on a marker
	 * without spending payload bytes on a colour both could compute.
	 *
	 * @param raw        the raw chat text
	 * @param author     who sent it
	 * @param authorName their display name
	 * @param now        current wall clock, used to stamp the lifetime
	 */
	public static Optional<Ping> decode(String raw, UUID author, String authorName, long now) {
		byte[] data = payload(raw);

		if (data == null || (data[0] & CLEAR_FLAG) != 0) {
			return Optional.empty();
		}

		PingType[] types = PingType.values();
		PingType type = types[Math.floorMod(data[0], types.length)];
		Vec3 pos = new Vec3(readShort(data, 1), readShort(data, 3), readShort(data, 5));
		int color = PingColors.forType(type, PingColors.of(author));

		Item item = itemOf(data);

		if (item != null) {
			color = ItemColors.of(new ItemStack(item), color);
		}

		long lifetime = type.isThreat() ? PingConfig.threatLifetimeMs() : PingConfig.markerLifetimeMs();

		return Optional.of(new Ping(type, pos, Ping.NO_ENTITY, author, authorName, color, item, now, now + lifetime));
	}

	/**
	 * The item a payload names, or {@code null}. An id this client does not know leaves
	 * the marker as a plain pin instead of dropping it, so a mod only the placing player
	 * has installed still shows them where to look.
	 */
	private static Item itemOf(byte[] data) {
		int length = itemIdLength(data);

		if (length == 0) {
			return null;
		}

		return PingItems.of(new String(data, ITEM_ID, length, StandardCharsets.UTF_8));
	}

	private static void putShort(byte[] out, int at, double value) {
		int encoded = (int) Math.round(value) + COORD_OFFSET;

		if (encoded < 0 || encoded >= COORD_RANGE) {
			encoded = COORD_OFFSET;
		}

		out[at] = (byte) (encoded >> 8);
		out[at + 1] = (byte) encoded;
	}

	private static double readShort(byte[] data, int at) {
		int encoded = ((data[at] & 0xFF) << 8) | (data[at + 1] & 0xFF);

		return encoded - COORD_OFFSET;
	}
}
