package dev.ping.client;

import java.util.Optional;
import java.util.UUID;

import dev.ping.client.audio.PingSounds;
import dev.ping.client.item.ItemColors;
import dev.ping.client.net.PingLink;
import dev.ping.ping.Ping;
import dev.ping.ping.PingColors;
import dev.ping.ping.PingConstants;
import dev.ping.ping.PingManager;
import dev.ping.ping.PingType;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Turns key presses into pings.
 *
 * <p>This is the "smart ping": what gets marked depends on what the crosshair is over.
 * Looking at a player marks them as an enemy and the marker follows them; looking at a
 * dropped item marks the item and records what it was, so the marker can show its icon;
 * looking at terrain marks the point you are actually aiming at.
 *
 * <p><b>It does not use the vanilla pick result.</b> {@code Minecraft.hitResult} only
 * reaches as far as an arm — about 4.5 blocks — so anything further away read as a miss
 * and fell through to the old "ping the sky" fallback, which put the marker hundreds of
 * blocks away regardless of the terrain in between. Everything here is done with our own
 * long raycast instead.
 */
public final class PingController {
	/**
	 * Extra hitbox inflation on top of each entity's own pick radius. Vanilla inflates by
	 * the pick radius alone when picking under the crosshair, but dropped items report a
	 * radius of zero and would be fiddly to aim at.
	 */
	private static final float PICK_PAD = 0.25F;

	private final PingManager pings;
	private final PingLink link;

	private ClientLevel lastLevel;
	private long lastTap;

	public PingController(PingManager pings, PingLink link) {
		this.pings = pings;
		this.link = link;
	}

	public void tick(Minecraft client, long now) {
		// Markers do not survive a world change.
		if (client.level != this.lastLevel) {
			this.pings.clear();
			this.lastLevel = client.level;
			this.lastTap = 0L;
		}

		if (client.player == null || client.level == null) {
			return;
		}

		// consumeClick() only ever yields presses made with no screen open, so this is
		// inherently GUI-safe. Drain the queue and act once so a stalled frame cannot fire
		// off several pings from one press.
		boolean clicked = false;

		while (PingKeys.PING.consumeClick()) {
			clicked = true;
		}

		if (clicked) {
			this.place(client, now);
		}

		while (PingKeys.TOGGLE_SYNC.consumeClick()) {
			this.reportSync(client, PingConfig.toggleSendPingsOverChat());
		}
	}

	/** Tells the player what the sync setting is now, in the action bar. */
	private static void reportSync(Minecraft client, boolean syncing) {
		client.player.sendOverlayMessage(Component.literal(syncing
				? "Ping: broadcasting over chat"
				: "Ping: local only, nothing sent to chat"));
	}

	private void place(Minecraft client, long now) {
		UUID author = client.player.getUUID();

		// The double tap has to be tested BEFORE the "looking at my own marker" clear.
		// Otherwise the second tap of a double tap sees the marker the first tap just
		// placed, takes it straight back down, and no danger marker is ever created.
		boolean doubleTap = now - this.lastTap <= PingConstants.DOUBLE_TAP_MS;

		// Fortnite throttles the mark button — "a short cooldown before using them
		// repeatedly" — so a spam of presses does not walk the marker and re-cue the tone
		// over and over. The double tap is exempt, or the danger marker could never land.
		if (!doubleTap && this.onCooldown(now)) {
			return;
		}

		if (!doubleTap && this.clearIfLookingAt(client, author)) {
			return;
		}

		Pick pick = this.pick(client);
		this.lastTap = now;

		if (pick == null) {
			// Nothing under the crosshair. Placing a marker for empty sky means dropping
			// one hundreds of blocks away with no relation to what you aimed at, so the
			// press is simply ignored.
			return;
		}

		PingType type;

		if (pick.stack() != null) {
			type = PingType.ITEM;
		} else if (pick.hostile()) {
			type = PingType.ENEMY;
		} else {
			type = PingType.NEUTRAL;
		}

		if (doubleTap) {
			type = PingType.DANGER;
		}

		this.put(client, now, type, pick.pos(), pick.entityId(), pick.stack(), author);
	}

	/**
	 * Places a marker at a position the player picked out rather than aimed at.
	 *
	 * <p>This is the map's route in: on a map there is no crosshair, so neither the
	 * crosshair pick nor the "looking at my own marker" clear applies. Everything
	 * downstream is deliberately identical to a crosshair ping, so a marker placed on the
	 * map is the same object, wears the same colour, and travels over chat the same way.
	 *
	 * @param type what the marker is; the map screen chooses neutral or danger
	 */
	public void placeAt(Minecraft client, long now, Vec3 pos, PingType type) {
		if (client.player == null) {
			return;
		}

		this.lastTap = now;
		this.put(client, now, type, pos, Ping.NO_ENTITY, null, client.player.getUUID());
	}

	/**
	 * The shared tail of every placement: colour it, stamp its life, show it, send it.
	 *
	 * @param stack the marked item, or {@code null} for a marker that is not an item mark
	 */
	private void put(Minecraft client, long now, PingType type, Vec3 pos, int entityId,
			ItemStack stack, UUID author) {
		int color = PingColors.forType(type, PingColors.of(author));
		Item item = null;

		if (stack != null && !stack.isEmpty()) {
			// An item marker is coloured by the item itself, not by who called it out.
			color = ItemColors.of(stack, color);
			item = stack.getItem();
		}

		long lifetime = type.isThreat() ? PingConfig.threatLifetimeMs() : PingConfig.markerLifetimeMs();
		Ping ping = new Ping(type, pos, entityId, author, client.player.getName().getString(),
				color, item, now, now + lifetime);

		// One marker per player: marking again moves your existing marker.
		this.pings.place(ping);
		PingSounds.play(type);
		this.link.send(type, pos, item);
	}

	/** True while the mark button has just been used and nothing should be placed. */
	private boolean onCooldown(long now) {
		return this.lastTap != 0L && now - this.lastTap < PingConfig.remarkCooldownMs();
	}

	/**
	 * What the crosshair is actually over, out to the render distance.
	 *
	 * @return the nearest hit, or {@code null} if the ray reaches nothing
	 */
	private Pick pick(Minecraft client) {
		Vec3 from = client.player.getEyePosition();
		Vec3 to = from.add(client.player.getLookAngle().scale(renderDistanceBlocks(client)));

		BlockHitResult blockHit = client.level.clip(new ClipContext(
				from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, client.player));

		double blockDistance = blockHit.getType() == HitResult.Type.MISS
				? Double.MAX_VALUE
				: from.distanceTo(blockHit.getLocation());

		EntityHit entity = pickEntity(client, from, to, blockDistance);

		if (entity != null) {
			if (entity.entity() instanceof ItemEntity itemEntity) {
				// Copy the stack: the original is the live entity's and will change when
				// the item is picked up.
				return new Pick(itemEntity.position(), itemEntity.getId(), itemEntity.getItem().copy(), false);
			}

			Entity hit = entity.entity();
			Vec3 pos = new Vec3(hit.getX(), hit.getY() + hit.getBbHeight() * 0.6D, hit.getZ());

			return new Pick(pos, hit.getId(), null, isHostile(client, hit));
		}

		if (blockHit.getType() != HitResult.Type.MISS) {
			return new Pick(blockHit.getLocation(), Ping.NO_ENTITY, null, false);
		}

		return null;
	}

	/** The closest pickable entity the ray passes through, within {@code maxDistance}. */
	private static EntityHit pickEntity(Minecraft client, Vec3 from, Vec3 to, double maxDistance) {
		AABB search = client.player.getBoundingBox().inflate(1.0D).expandTowards(to.subtract(from));
		EntityHit best = null;

		for (Entity entity : client.level.getEntities(client.player, search)) {
			// ItemEntity reports itself as unpickable, because in vanilla you pick items
			// up by walking over them rather than by aiming at them. A manual raycast
			// still has to be able to hit them.
			if (!entity.isPickable() && !(entity instanceof ItemEntity)) {
				continue;
			}

			// Inflate by the entity's own pick radius, which is what vanilla's own
			// crosshair picking does, plus a small fixed pad: this is a deliberate
			// crosshair pick rather than an incidental projectile sweep, and dropped
			// items report a pick radius of zero.
			Optional<Vec3> hit = entity.getBoundingBox()
					.inflate(entity.getPickRadius() + PICK_PAD)
					.clip(from, to);

			if (hit.isEmpty()) {
				continue;
			}

			double distance = from.distanceTo(hit.get());

			if (distance <= maxDistance && (best == null || distance < best.distance())) {
				best = new EntityHit(entity, distance);
			}
		}

		return best;
	}

	/**
	 * If the crosshair is on this player's own marker, remove it and report that we dealt
	 * with the press. Fortnite clears a marker by marking the same spot again.
	 */
	private boolean clearIfLookingAt(Minecraft client, UUID author) {
		Ping mine = this.pings.get(author);

		if (mine == null) {
			return false;
		}

		Vec3 toMarker = mine.pos().subtract(client.player.getEyePosition());

		if (toMarker.lengthSqr() < 1.0E-6D) {
			return false;
		}

		if (toMarker.normalize().dot(client.player.getLookAngle()) < PingConstants.UNPING_COS) {
			return false;
		}

		this.pings.remove(author);
		this.lastTap = 0L;
		PingSounds.play(PingType.NEUTRAL);
		this.link.sendClear();

		return true;
	}

	/** How far a ping ray reaches. The only real bound is how far the world is loaded. */
	private static double renderDistanceBlocks(Minecraft client) {
		return Math.max(16, client.options.getEffectiveRenderDistance()) * 16.0D;
	}

	/** True for another player who is not on our side. */
	private static boolean isHostile(Minecraft client, Entity entity) {
		if (!(entity instanceof Player other) || other == client.player) {
			return false;
		}

		return !client.player.isAlliedTo(other);
	}

	/** What the crosshair is over. */
	private record Pick(Vec3 pos, int entityId, ItemStack stack, boolean hostile) {
	}

	/** An entity the ray passed through, and where. */
	private record EntityHit(Entity entity, double distance) {
	}
}
