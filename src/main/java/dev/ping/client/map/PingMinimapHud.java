package dev.ping.client.map;

import dev.ping.client.PingConfig;
import dev.ping.ping.PingManager;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The corner minimap: the map, Fortnite's readout column beside it, and the location label
 * at its foot.
 *
 * <p>Every size and offset here is measured off a Fortnite capture rather than chosen;
 * see {@link MinimapConstants}. The map is a bare square with no frame, sitting in the top
 * right at 23.35% of the screen height, 3% from the top and 2.9% from the right edge.
 *
 * <p>It is not clickable, and that is not a limitation; it is Fortnite's behaviour. The
 * mouse is captured by the camera while you are playing, so there is no cursor to click
 * with; marking from a map happens on the full-size map, which the map key ({@code M} by
 * default) opens.
 */
public final class PingMinimapHud implements HudElement {
	private final Minimap minimap;
	private final PingManager pings;

	/** Reused between frames so the readouts do not allocate. */
	private final int[] values = new int[2];

	public PingMinimapHud(Minimap minimap, PingManager pings) {
		this.minimap = minimap;
		this.pings = pings;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();

		if (!PingConfig.showMinimap() || client.level == null || client.player == null) {
			return;
		}

		// With a menu open the HUD is the backdrop for it. A map floating over an open
		// inventory reads as a rendering bug, so it steps aside.
		if (client.gui.screen() != null) {
			return;
		}

		int screenHeight = graphics.guiHeight();
		float size = MinimapConstants.SIZE_FRACTION * screenHeight;
		float x = graphics.guiWidth()
				- size
				- MinimapConstants.RIGHT_MARGIN_FRACTION * screenHeight;
		float y = MinimapConstants.TOP_MARGIN_FRACTION * screenHeight;

		MapViewport viewport = MapViewport.centredOn(x, y, size,
				client.player.getX(), client.player.getZ(), this.minimap.blocksAcross());

		MinimapRenderer.render(graphics, client, this.minimap, this.pings, viewport,
				System.currentTimeMillis(), false);

		this.values[MinimapRenderer.ROW_KILLS] = PingCounters.kills(client);
		this.values[MinimapRenderer.ROW_PLAYERS] = PingCounters.playersOnline(client);

		MinimapRenderer.drawReadouts(graphics, client, viewport, this.values);
	}

}
