package dev.ping.client.audio;

import dev.ping.client.PingClient;
import dev.ping.ping.PingType;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

/**
 * Ping tones.
 *
 * <p>Fortnite plays a different short tone per mark type. The real files are wired up
 * through {@code assets/ping/sounds.json}; if a file is absent the sound simply
 * does not play and Minecraft logs a missing-sound warning, which is harmless.
 */
public final class PingSounds {
	private PingSounds() {
	}

	public static void play(PingType type) {
		Minecraft client = Minecraft.getInstance();

		// A registry-free event: looks the id up in sounds.json at play time, so the
		// mod does not have to register anything during initialisation.
		SoundEvent event = SoundEvent.createVariableRangeEvent(PingClient.id(type.sound()));

		client.getSoundManager().play(SimpleSoundInstance.forUI(event, 1.0F));
	}
}
