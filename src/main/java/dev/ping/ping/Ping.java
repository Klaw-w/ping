package dev.ping.ping;

import java.util.UUID;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * A single placed marker.
 *
 * <p>A ping is either <em>anchored</em> (a fixed world position) or <em>attached</em>
 * (following a moving entity). Fortnite's enemy marker tracks its target for a while and
 * then stops; we model that with {@link #attachedEntityId} plus {@link #expiresAt}.
 *
 * @param type             what kind of marker this is
 * @param pos              current world position, updated every tick while attached
 * @param attachedEntityId entity id being followed, or {@code -1} for a static ping
 * @param author           who placed it
 * @param authorName       display name of who placed it, for the label
 * @param color            the author's locator bar colour
 * @param item             the item being marked, for item markers; may be {@code null}
 * @param createdAt        wall clock, for the pop-in animation and lifetime maths
 * @param expiresAt        wall clock after which this is dropped
 */
public record Ping(
		PingType type,
		Vec3 pos,
		int attachedEntityId,
		UUID author,
		String authorName,
		int color,
		Item item,
		long createdAt,
		long expiresAt
) {
	/** Entity id meaning "this marker does not follow anything". */
	public static final int NO_ENTITY = -1;

	public boolean isAttached() {
		return this.attachedEntityId != NO_ENTITY;
	}

	public boolean isExpired(long now) {
		return now >= this.expiresAt;
	}

	/** 0.0 at spawn, 1.0 once fully faded in. Drives the pop animation. */
	public float spawnProgress(long now) {
		long age = now - this.createdAt;

		return Math.min(1.0F, age / (float) PingConstants.SPAWN_ANIM_MS);
	}

	/** 1.0 while fresh, falling to 0.0 over the last stretch before expiry. */
	public float fadeAlpha(long now) {
		long remaining = this.expiresAt - now;

		if (remaining >= PingConstants.FADE_MS) {
			return 1.0F;
		}

		return Math.max(0.0F, remaining / (float) PingConstants.FADE_MS);
	}

	/**
	 * The name to show for this marker in the on-screen context block. Left in its natural
	 * capitalisation: Fortnite's own UI is all caps, but Minecraft's font reads badly that
	 * way.
	 */
	public String label() {
		if (this.item != null) {
			return new ItemStack(this.item).getHoverName().getString();
		}

		return this.type.label();
	}

	public Ping withPos(Vec3 newPos) {
		return new Ping(this.type, newPos, this.attachedEntityId, this.author, this.authorName,
				this.color, this.item, this.createdAt, this.expiresAt);
	}

	/** Same marker, no longer following anything. */
	public Ping detached() {
		return new Ping(this.type, this.pos, NO_ENTITY, this.author, this.authorName,
				this.color, this.item, this.createdAt, this.expiresAt);
	}
}
