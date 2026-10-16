package dev.ping.client.poi;

import dev.ping.poi.Poi;
import dev.ping.poi.PoiStore;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Names the place a banner stands on.
 *
 * <p>Opened by right-clicking a banner. The same screen renames and deletes: right-clicking
 * a banner that already has a name fills the box with it and adds a Delete button, so the
 * three things you can do to a named location are all in the place you already went to.
 *
 * <p>The chrome is the same measured palette the map screen uses — the blue gradient, the
 * same white-on-blue text — so a location name is made in a screen that belongs to the rest
 * of the mod rather than to vanilla. The buttons and the text box are vanilla widgets on
 * purpose: a text field is the one control where matching vanilla's feel is worth more than
 * matching a colour scheme, because it is the only thing here the player types into.
 */
public final class PingPoiScreen extends Screen {
	private static final int FIELD_WIDTH = 200;
	private static final int FIELD_HEIGHT = 20;
	private static final int BUTTON_WIDTH = 64;
	private static final int BUTTON_HEIGHT = 20;

	private static final int BACKGROUND_TOP = 0xFF0B1F76;
	private static final int BACKGROUND_BOTTOM = 0xFF031150;
	private static final int TITLE_COLOR = 0xFFFFFFFF;
	private static final int COORDS_COLOR = 0xFFA0E9FF;

	private final String worldKey;
	private final String dimension;
	private final int x;
	private final int y;
	private final int z;
	private final Poi existing;
	private EditBox nameBox;

	public PingPoiScreen(String worldKey, String dimension, int x, int y, int z, Poi existing) {
		super(Component.literal(existing == null ? "Name this location" : "Rename this location"));

		this.worldKey = worldKey;
		this.dimension = dimension;
		this.x = x;
		this.y = y;
		this.z = z;
		this.existing = existing;
	}

	@Override
	protected void init() {
		int centreX = this.width / 2;
		int centreY = this.height / 2;

		this.nameBox = new EditBox(this.font, centreX - FIELD_WIDTH / 2, centreY - 12, FIELD_WIDTH, FIELD_HEIGHT,
				Component.literal("Location name"));
		this.nameBox.setMaxLength(PoiStore.MAX_NAME_LENGTH);
		this.nameBox.setValue(this.existing == null ? "" : this.existing.name());
		this.addRenderableWidget(this.nameBox);
		this.setInitialFocus(this.nameBox);

		int row = centreY + 16;
		int cancelX = centreX - BUTTON_WIDTH / 2;

		this.addRenderableWidget(Button.builder(Component.literal("Save"), button -> this.save())
				.bounds(cancelX - BUTTON_WIDTH - 4, row, BUTTON_WIDTH, BUTTON_HEIGHT).build());
		this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> this.onClose())
				.bounds(cancelX, row, BUTTON_WIDTH, BUTTON_HEIGHT).build());

		if (this.existing != null) {
			this.addRenderableWidget(Button.builder(Component.literal("Delete"), button -> this.delete())
					.bounds(cancelX + BUTTON_WIDTH + 4, row, BUTTON_WIDTH, BUTTON_HEIGHT).build());
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fillGradient(0, 0, this.width, this.height, BACKGROUND_TOP, BACKGROUND_BOTTOM);

		int centreX = this.width / 2;
		String title = this.title.getString();
		String where = "x " + this.x + "   y " + this.y + "   z " + this.z;

		graphics.text(this.font, title, centreX - this.font.width(title) / 2, this.height / 2 - 52, TITLE_COLOR, true);
		graphics.text(this.font, where, centreX - this.font.width(where) / 2, this.height / 2 + 44, COORDS_COLOR, true);

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private void save() {
		String name = PoiStore.cleanName(this.nameBox.getValue());

		if (!name.isEmpty()) {
			PoiStore.put(this.worldKey, new Poi(name, this.x, this.y, this.z, this.dimension));
		}

		this.onClose();
	}

	private void delete() {
		if (this.existing != null) {
			PoiStore.remove(this.worldKey, this.existing);
		}

		this.onClose();
	}
}
