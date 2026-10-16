package dev.ping.ping;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * How an item's identity survives the trip to another client.
 *
 * <p>An item marker is only correct if the far side knows <em>what</em> was marked,
 * so the item has to travel with the ping. Only its registry id does — the item itself
 * cannot, and a full {@link net.minecraft.world.item.ItemStack} would carry damage,
 * components and a count, none of which is needed to draw an icon or name a marker.
 *
 * <p>The id is written without the {@code minecraft:} namespace. That prefix is on
 * every vanilla item, and a chat message is not a free medium — every character a
 * client is a character of payload, so the ones that are always the same are the ones
 * worth dropping. Anything modded keeps its own namespace and so still round trips.
 */
public final class PingItems {
	/** Ceiling on how long an id may be, so its length byte and the chat line stay small. */
	public static final int MAX_LENGTH = 100;

	/** Separator between a namespace and a path, as {@link Identifier} uses it. */
	private static final char SEPARATOR = Identifier.NAMESPACE_SEPARATOR;

	/** Prefix elided from vanilla ids, see the class comment. */
	private static final String VANILLA = Identifier.DEFAULT_NAMESPACE + SEPARATOR;

	private PingItems() {
	}

	/**
	 * The registry id of an item, or {@code null} if it has none.
	 *
	 * <p>Returns {@code null} rather than throwing for anything that cannot be named: a
	 * marker is better drawn as a plain pin than not drawn at all.
	 */
	public static String idOf(Item item) {
		if (item == null) {
			return null;
		}

		Identifier id = keyOf(item);

		if (id == null) {
			return null;
		}

		String full = id.toString();

		return full.startsWith(VANILLA) ? full.substring(VANILLA.length()) : full;
	}

	/**
	 * The item an id names, or {@code null} if this client does not know it.
	 *
	 * <p>An unknown id is an ordinary outcome, not a malformed message: it is what a mod
	 * that only the placing player has installed looks like. The marker is still placed,
	 * just without its icon.
	 */
	public static Item of(String id) {
		if (id == null || id.isEmpty() || id.length() > MAX_LENGTH) {
			return null;
		}

		Identifier parsed = Identifier.tryParse(id);

		if (parsed == null) {
			return null;
		}

		return lookup(parsed);
	}

	private static Identifier keyOf(Item item) {
		try {
			return BuiltInRegistries.ITEM.getKey(item);
		} catch (RuntimeException e) {
			// A registry lookup should never throw, but this is keyed on a foreign object:
			// a mod with a broken hashCode() must not take the chat handler down with it.
			return null;
		}
	}

	private static Item lookup(Identifier id) {
		try {
			return BuiltInRegistries.ITEM.getOptional(id).orElse(null);
		} catch (RuntimeException e) {
			return null;
		}
	}
}
