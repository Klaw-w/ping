package dev.ping.client.net;

import java.util.UUID;

import dev.ping.client.PingConfig;
import dev.ping.ping.PingCodec;
import dev.ping.ping.PingManager;
import dev.ping.ping.PingType;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Gets a ping from one client to another.
 *
 * <p>Minecraft has no client-to-client channel. A modded client can only talk to the
 * server, and a vanilla server will not relay custom payloads, so the only transport
 * that works everywhere with nothing installed server-side is chat.
 *
 * <p>An outgoing ping is posted as a chat line with a {@code #pp#} marker and a small
 * binary payload — type, position, and the registry id of the item being marked. Every
 * client running the mod recognises that marker, cancels the line so it never reaches
 * the chat HUD, and turns it back into a marker. Clients without the mod see a short
 * line of junk, and the server log records it.
 *
 * <p>Because {@code ALLOW_CHAT} returning false stops the message reaching the HUD, the
 * decode has to happen inside that same handler — the {@code CHAT} event is not fired
 * for cancelled messages.
 *
 * <p>Sending is governed by {@link PingConfig#sendPingsOverChat()}. Incoming pings are
 * always decoded and suppressed regardless, so a modded player's marker still works even
 * if this setting is off here.
 */
public final class PingLink {
	private static final UUID UNKNOWN = new UUID(0L, 0L);

	private final PingManager pings;

	public PingLink(PingManager pings) {
		this.pings = pings;
	}

	public void register() {
		ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, params, timestamp) -> {
			String raw = message.getString();

			if (!PingCodec.isPingMessage(raw)) {
				return true;
			}

			// authlib 7+ turned GameProfile into a record, so these are id()/name().
			UUID author = sender != null ? sender.id() : UNKNOWN;
			String name = sender != null && sender.name() != null ? sender.name() : "Someone";

			if (PingCodec.isClear(raw)) {
				this.pings.remove(author);

				return false;
			}

			PingCodec.decode(raw, author, name, System.currentTimeMillis()).ifPresent(this.pings::place);

			return false;
		});
	}

	/** Broadcasts a marker with no item attached to it. */
	public void send(PingType type, Vec3 pos) {
		this.send(type, pos, (Item) null);
	}

	/** Broadcasts a marker naming the item being marked. */
	public void send(PingType type, Vec3 pos, Item item) {
		if (!PingConfig.sendPingsOverChat()) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		ClientPacketListener connection = client.getConnection();

		if (connection == null) {
			return;
		}

		connection.sendChat(PingCodec.encode(type, pos, item));
	}

	/** Broadcasts a marker for the item in a stack. Convenience for the pick result. */
	public void send(PingType type, Vec3 pos, ItemStack stack) {
		// An empty stack names no item, so it degrades to a plain pin rather than to one
		// named "air" and drawn over with an empty icon.
		this.send(type, pos, stack == null || stack.isEmpty() ? null : stack.getItem());
	}

	/** Tells everyone to drop this player's marker. */
	public void sendClear() {
		if (!PingConfig.sendPingsOverChat()) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		ClientPacketListener connection = client.getConnection();

		if (connection == null) {
			return;
		}

		connection.sendChat(PingCodec.encodeClear());
	}
}
