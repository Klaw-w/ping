package dev.ping.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import net.minecraft.client.KeyMapping;

import org.lwjgl.glfw.GLFW;

/**
 * Key bindings.
 *
 * <p>26.x renamed Fabric's key binding module to key <em>mapping</em>
 * ({@code fabric-key-mapping-api-v1}), and vanilla {@link KeyMapping} now takes a
 * {@link KeyMapping.Category} rather than the old free-form category string.
 *
 * <p>Default is middle mouse, matching Fortnite. A single press pings; two taps in
 * quick succession place the danger marker.
 */
public final class PingKeys {
	public static final KeyMapping PING = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.ping.ping",
			InputConstants.Type.MOUSE,
			GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
			KeyMapping.Category.MISC
	));

	/**
	 * Toggles whether pings are broadcast over chat.
	 *
	 * <p>Unbound by default, because it is a setting rather than something you press
	 * mid-fight. Bind it if you want to flip it live; the same setting also lives in
	 * {@code config/ping.json}.
	 */
	public static final KeyMapping TOGGLE_SYNC = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.ping.toggle_sync",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_UNKNOWN,
			KeyMapping.Category.MISC
	));

	/**
	 * Opens the full-size map, which is also where a ping is placed by pointing at it
	 * rather than by aiming.
	 *
	 * <p>Default {@code M}, which vanilla leaves free: its own map handling is the held
	 * map item, not a screen.
	 */
	public static final KeyMapping PING_MAP = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.ping.map",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_M,
			KeyMapping.Category.MISC
	));

	/**
	 * Hides and shows the corner minimap. Unbound by default: it is a preference, and the
	 * same setting lives in {@code config/ping.json} so it survives a restart.
	 */
	public static final KeyMapping TOGGLE_MINIMAP = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.ping.toggle_minimap",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_UNKNOWN,
			KeyMapping.Category.MISC
	));

	/**
	 * Opens the settings screen, where the four booleans in {@code config/ping.json} are
	 * flipped without leaving the game.
	 *
	 * <p>Default {@code J}. Measured against the 26.2 jar rather than guessed: vanilla binds
	 * {@code W A S D E F Q T O P L G C X} and the digit row, plus F-keys and the debug
	 * combos, and {@code J} is not among them. {@code P} was the first choice and had to go;
	 * vanilla gives it to {@code key.socialInteractions}, which would have fired alongside
	 * this and made the screen open only by luck.
	 */
	public static final KeyMapping PING_SETTINGS = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.ping.settings",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_J,
			KeyMapping.Category.MISC
	));

	private PingKeys() {
	}
}
