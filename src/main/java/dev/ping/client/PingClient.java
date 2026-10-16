package dev.ping.client;

import java.util.Objects;

import dev.ping.client.map.Minimap;
import dev.ping.client.map.PingMapScreen;
import dev.ping.client.map.PingMinimapHud;
import dev.ping.client.net.PingLink;
import dev.ping.client.poi.ClientPois;
import dev.ping.client.render.ChatOffsetHud;
import dev.ping.client.render.HealthShieldHud;
import dev.ping.client.render.MountHealthHud;
import dev.ping.client.render.PingBeamRenderer;
import dev.ping.client.render.PingHud;
import dev.ping.client.settings.PingSettingsScreen;
import dev.ping.ping.PingFeed;
import dev.ping.ping.PingManager;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint.
 *
 * <p>The whole mod is client side. It declares {@code "environment": "client"} so a
 * server never loads it, which also means it cannot relay pings between players on its
 * own: see {@link PingLink} for how it gets around that.
 */
public class PingClient implements ClientModInitializer {
	public static final String MOD_ID = "ping";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final PingManager PINGS = new PingManager();
	private static final PingLink LINK = new PingLink(PINGS);
	private static final PingController CONTROLLER = new PingController(PINGS, LINK);
	private static final Minimap MINIMAP = new Minimap();

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static PingManager pings() {
		return PINGS;
	}

	public static PingLink link() {
		return LINK;
	}

	@Override
	public void onInitializeClient() {
		PingConfig.load();

		// Touch the key bindings so their static initialiser runs during client init,
		// which is the only window where registering a KeyMapping is valid.
		Objects.requireNonNull(PingKeys.PING, "ping key binding");
		Objects.requireNonNull(PingKeys.TOGGLE_SYNC, "ping sync key binding");
		Objects.requireNonNull(PingKeys.PING_MAP, "map key binding");
		Objects.requireNonNull(PingKeys.TOGGLE_MINIMAP, "minimap key binding");
		Objects.requireNonNull(PingKeys.PING_SETTINGS, "settings key binding");

		// The minimap goes in before the markers so a marker is never drawn under it: the
		// map is background, the world markers are the thing being read.
		HudElementRegistry.addLast(id("minimap"), new PingMinimapHud(MINIMAP, PINGS));

		// Right-clicking a banner names the place it stands on.
		ClientPois.register();
		HudElementRegistry.addLast(id("pings"), new PingHud(PINGS));

		// Health and shield. Replacing the vanilla element hands us the original, so the
		// setting can fall back to it instead of removing the element and losing the row.
		HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR, HealthShieldHud::new);

		// The vehicle bar sits above that stack, and falls back to vanilla's mount hearts
		// whenever there is no mount to draw one for.
		HudElementRegistry.replaceElement(VanillaHudElements.MOUNT_HEALTH, MountHealthHud::new);

		// Chat shares the bottom-left corner with the meter, so it is lifted clear of it.
		HudElementRegistry.replaceElement(VanillaHudElements.CHAT, old -> new ChatOffsetHud());

		LINK.register();

		// The beam is world geometry, so it lives in the level render pass rather than the
		// HUD.
		PingBeamRenderer.register();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			long now = System.currentTimeMillis();

			CONTROLLER.tick(client, now);
			MINIMAP.tick(client, now);
			PINGS.tick(now, entityId -> {
				if (client.level == null) {
					return null;
				}

				Entity entity = client.level.getEntity(entityId);

				return entity == null ? null : entity.position();
			});

			postFeed(client);

			// A name whose banner has been broken stops being a name.
			ClientPois.tick(client);

			while (PingKeys.PING_MAP.consumeClick()) {
				if (client.player != null && client.level != null) {
					client.setScreenAndShow(new PingMapScreen(MINIMAP, CONTROLLER, PINGS));
				}
			}

			while (PingKeys.PING_SETTINGS.consumeClick()) {
				if (client.player != null) {
					client.setScreenAndShow(new PingSettingsScreen());
				}
			}

			while (PingKeys.TOGGLE_MINIMAP.consumeClick()) {
				if (client.player != null) {
					client.player.sendOverlayMessage(Component.literal(PingConfig.toggleShowMinimap()
							? "Ping: minimap on"
							: "Ping: minimap off"));
				}
			}
		});

		LOGGER.info("Ping ready");
	}

	/**
	 * Posts queued ping lines into chat.
	 *
	 * <p>The feed is not a second widget beside chat: it <em>is</em> chat. Fortnite's
	 * Message Feed and Minecraft's chat occupy the same corner and do the same job, so
	 * rather than stacking a HUD element on top of the window, each ping becomes a local
	 * chat message and the two stop being two things.
	 *
	 * <p>{@code addClientSystemMessage} is display-only, so this adds no traffic: a ping
	 * still costs exactly one line on the wire, and a received ping still shows a line
	 * without echoing it back.
	 */
	private static void postFeed(Minecraft client) {
		if (!PingConfig.showPingFeed()) {
			return;
		}

		for (PingFeed.Entry entry : PINGS.feed().drain()) {
			// The name wears the marker colour so a feed line and the marker it describes
			// are obviously the same person; the action stays white, as Fortnite's does.
			Component line = Component.literal(entry.author())
					.withStyle(style -> style.withColor(entry.color() & 0xFFFFFF))
					.append(Component.literal(" " + entry.action()).withStyle(style -> style.withColor(0xFFFFFF)));

			client.gui.hud.getChat().addClientSystemMessage(line);
		}
	}
}
