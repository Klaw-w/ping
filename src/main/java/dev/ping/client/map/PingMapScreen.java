package dev.ping.client.map;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import dev.ping.client.PingController;
import dev.ping.client.PingKeys;
import dev.ping.ping.PingColors;
import dev.ping.ping.PingManager;
import dev.ping.ping.PingType;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The full-size map, recreated from a Fortnite capture.
 *
 * <p>The chrome is Fortnite's, measured off that frame: the blue gradient backdrop, the tab
 * strip, the level badge and XP bar, the squad panel down the left, the map in the middle,
 * Reset, and the control hints down the right. {@link MapChrome} holds those measurements.
 *
 * <p><b>What occupies each of Fortnite's slots, and why.</b> Fortnite's screen reports
 * things a Minecraft client either has or has not:
 *
 * <ul>
 *   <li><b>Level &amp; XP bar</b> — the same thing in both games. The player's experience
 *       level, their progress to the next, and the XP still needed. No invention.</li>
 *   <li><b>Players left</b> — "players online on this server", from the tab list.</li>
 *   <li><b>Storm timer</b> — becomes the in-game clock. Minecraft has no storm, so a
 *       countdown would be a lie; the time of day is real, useful under a map, and sits in
 *       the row Fortnite gives a clock icon.</li>
 *   <li><b>Eliminations</b> — only the local player's count is knowable. The server pushes
 *       stats for the local player alone, and no clientbound packet names a killer, so the
 *       other squad rows cannot be filled in. They are listed with their locator colour and
 *       a dash rather than a fabricated zero.</li>
 *   <li><b>Bars</b> — dropped. It is a Fortnite currency with no Minecraft counterpart, and
 *       inventing one would be inventing data.</li>
 * </ul>
 *
 * <p>Marking is Fortnite's: click or middle-click a spot to mark it, double-click for the
 * danger marker. Dragging pans, which is what the {@code PAN} hint is for, and the map
 * remembers where you dragged it to until you press Reset.
 */
public final class PingMapScreen extends Screen {
	private static final List<String> HINTS = List.of(
			"PAN   Drag",
			"ZOOM OUT   Scroll",
			"ZOOM IN   Scroll",
			"PLACE MARKER   Click",
			"Exit   Esc"
	);

	/** A drag shorter than this is a click that wobbled, not a pan. */
	private static final double DRAG_SLOP = 4.0D;

	/** How far the map can be dragged from the player. */
	private static final double MAX_PAN = 4096.0D;

	private final Minimap minimap;
	private final PingController controller;
	private final PingManager pings;

	/** The screen's own zoom, deliberately independent of the corner minimap's. */
	private int zoom = MinimapConstants.DEFAULT_ZOOM;

	private double panX;
	private double panZ;
	private double pressX;
	private double pressY;
	private boolean dragging;
	private boolean pendingDoubleClick;

	public PingMapScreen(Minimap minimap, PingController controller, PingManager pings) {
		super(Component.literal("Map"));

		this.minimap = minimap;
		this.controller = controller;
		this.pings = pings;
	}

	/**
	 * A map is not a pause screen. Freezing the world to show where the world is makes the
	 * thing it is showing wrong.
	 */
	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void removed() {
		// The map is shut: the terrain cache can go back to sampling only what the corner
		// minimap needs.
		this.minimap.setScreenRange(0);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		Minecraft client = this.minecraft;
		int width = this.width;
		int height = this.height;

		// The map screen can be zoomed further out than the corner minimap, so the terrain
		// cache has to be told what to cover.
		this.minimap.setScreenRange(MinimapConstants.zoomBlocks(this.zoom));

		MapChrome.background(graphics, width, height);
		MapChrome.tabs(graphics, this.font, width, height, 0);
		MapChrome.level(graphics, client, this.font, width, height);
		MapChrome.panel(graphics, this.font, width, height, party(), panelRows(client), "ELIMS", eliminationRows(client));

		MapViewport viewport = viewport();

		MinimapRenderer.render(graphics, client, this.minimap, this.pings, viewport,
				System.currentTimeMillis(), true);
		MapChrome.button(graphics, this.font, MapChrome.resetX(width), MapChrome.resetY(height),
				MapChrome.resetWidth(width), MapChrome.resetHeight(height), "Reset");
		MapChrome.hints(graphics, this.font, width, height, HINTS);

		drawCursor(graphics, viewport, mouseX, mouseY);
	}

	/** A crosshair at the pointer with the coordinate under it. */
	private void drawCursor(GuiGraphicsExtractor graphics, MapViewport viewport, int mouseX, int mouseY) {
		if (!viewport.contains(mouseX, mouseY)) {
			return;
		}

		int arm = 5;
		int colour = MinimapConstants.LABEL_COLOR;

		graphics.fill(mouseX - arm, mouseY, mouseX + arm + 1, mouseY + 1, colour);
		graphics.fill(mouseX, mouseY - arm, mouseX + 1, mouseY + arm + 1, colour);

		String coordinates = Mth.floor(viewport.worldX(mouseX)) + ", " + Mth.floor(viewport.worldZ(mouseY));

		graphics.text(this.font, coordinates, mouseX + arm + 3, mouseY + arm + 2,
				MinimapConstants.GRID_LABEL_COLOR, true);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		this.pressX = event.x();
		this.pressY = event.y();
		this.dragging = false;
		this.pendingDoubleClick = doubleClick;

		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (Math.abs(event.x() - this.pressX) + Math.abs(event.y() - this.pressY) > DRAG_SLOP) {
			this.dragging = true;
		}

		if (!this.dragging) {
			return super.mouseDragged(event, dragX, dragY);
		}

		// The map follows the cursor, so the pan moves against the drag. Scaled by how much
		// world one pixel covers, so a drag feels the same at every zoom.
		MapViewport viewport = viewport();
		double perPixel = viewport.blocksAcross() / viewport.size();

		this.panX = Mth.clamp(this.panX - dragX * perPixel, -MAX_PAN, MAX_PAN);
		this.panZ = Mth.clamp(this.panZ - dragY * perPixel, -MAX_PAN, MAX_PAN);

		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		boolean wasDragging = this.dragging;
		this.dragging = false;

		if (wasDragging) {
			return true;
		}

		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT
				&& event.button() != InputConstants.MOUSE_BUTTON_MIDDLE) {
			return super.mouseReleased(event);
		}

		if (overReset(event.x(), event.y())) {
			this.panX = 0.0D;
			this.panZ = 0.0D;

			return true;
		}

		MapViewport viewport = viewport();

		if (!viewport.contains(event.x(), event.y())) {
			return true;
		}

		place(viewport.column(event.x(), event.y()), this.pendingDoubleClick);

		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY == 0.0D) {
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}

		// Scroll up zooms in, which is towards the near end of the ladder.
		this.zoom = MinimapConstants.clampZoom(this.zoom + (scrollY > 0.0D ? -1 : 1));

		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (PingKeys.PING_MAP.matches(event)) {
			this.onClose();

			return true;
		}

		return super.keyPressed(event);
	}

	private boolean overReset(double x, double y) {
		int left = MapChrome.resetX(this.width);
		int top = MapChrome.resetY(this.height);

		return x >= left && x <= left + MapChrome.resetWidth(this.width)
				&& y >= top && y <= top + MapChrome.resetHeight(this.height);
	}

	/** Marks the column under the cursor. */
	private void place(Vec3 column, boolean danger) {
		Minecraft client = this.minecraft;

		if (client.level == null || client.player == null) {
			return;
		}

		int x = Mth.floor(column.x());
		int z = Mth.floor(column.z());
		int y = Minimap.surfaceY(client.level, x, z);

		if (y == TerrainHeight.UNKNOWN) {
			// No heightmap for that column, which usually means it is outside the loaded
			// world. The marker still belongs on the right spot, so it takes the player's own
			// height rather than being refused.
			y = Mth.floor(client.player.getY());
		}

		this.controller.placeAt(client, System.currentTimeMillis(),
				new Vec3(x + 0.5D, y, z + 0.5D), danger ? PingType.DANGER : PingType.NEUTRAL);
	}

	/** The map as it is right now: centred where it has been dragged to. */
	private MapViewport viewport() {
		float size = MapChrome.mapSize(this.width, this.height);
		double centerX = (this.minecraft.player == null ? 0.0D : this.minecraft.player.getX()) + this.panX;
		double centerZ = (this.minecraft.player == null ? 0.0D : this.minecraft.player.getZ()) + this.panZ;

		return new MapViewport(MapChrome.mapX(this.width, this.height), MapChrome.mapY(this.width, this.height), size,
				centerX, centerZ, MinimapConstants.zoomBlocks(this.zoom));
	}

	/** Fortnite's header is the party size. Yours is counted the way the mod counts allies. */
	private String party() {
		Minecraft client = this.minecraft;

		if (client.level == null || client.player == null) {
			return "SOLO";
		}

		int team = 1;

		for (AbstractClientPlayer other : client.level.players()) {
			if (other != client.player && client.player.isAlliedTo(other)) {
				team++;
			}
		}

		return switch (team) {
			case 1 -> "SOLO";
			case 2 -> "DUOS";
			case 3 -> "TRIOS";
			default -> "SQUADS";
		};
	}

	private List<MapChrome.Row> panelRows(Minecraft client) {
		return List.of(
				MapChrome.Row.of("TIME", clock(client)),
				MapChrome.Row.of("PLAYERS ONLINE", Integer.toString(PingCounters.playersOnline(client)))
		);
	}

	/**
	 * The elimination block.
	 *
	 * <p>Your row is real. Everyone else's is a dash, because it has to be: the server sends
	 * statistics for the local player only, and no clientbound packet carries a killer, so
	 * there is no honest way to know another player's count from here.
	 */
	private List<MapChrome.Row> eliminationRows(Minecraft client) {
		List<MapChrome.Row> rows = new ArrayList<>();

		if (client.player == null) {
			return rows;
		}

		rows.add(new MapChrome.Row(client.player.getName().getString(),
				Integer.toString(PingCounters.kills(client)), PingColors.of(client.player.getUUID())));

		ClientLevel level = client.level;

		if (level == null) {
			return rows;
		}

		for (AbstractClientPlayer other : level.players()) {
			if (other == client.player || rows.size() >= 4) {
				continue;
			}

			rows.add(new MapChrome.Row(other.getName().getString(), "—", PingColors.of(other.getUUID())));
		}

		return rows;
	}

	/**
	 * The in-game clock.
	 *
	 * <p>Minecraft's day is 24,000 ticks with tick 0 at 06:00, so the time is the clock's
	 * position in the day plus six hours. (26.x replaced the old raw day counter with named
	 * world clocks, which is why this reads a clock rather than a day-time field.)
	 */
	private static String clock(Minecraft client) {
		if (client.level == null) {
			return "--:--";
		}

		long dayTime = client.level.getDefaultClockTime();
		long hours = Math.floorMod(dayTime / 1000L + 6L, 24L);
		long minutes = Math.floorMod(dayTime, 24000L) % 1000L * 60L / 1000L;

		return String.format("%02d:%02d", hours, minutes);
	}
}
