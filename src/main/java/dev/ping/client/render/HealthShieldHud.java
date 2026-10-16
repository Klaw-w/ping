package dev.ping.client.render;

import dev.ping.client.PingClient;
import dev.ping.client.PingConfig;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * Fortnite's health and shield meter, drawn the way Fortnite draws it.
 *
 * <p>Two flat bars in the bottom-left, shield above health, each with a white glyph on its
 * left and a white value on its right. The empty part of each bar is a solid dark fill.
 * This is the Chapter 7 design as measured off the capture in {@code docs/FIDELITY.md} —
 * not a Minecraft row wearing Fortnite's colours. There are no hearts here.
 *
 * <p><b>Proportions are measured; the scale is chosen.</b> The capture's absolute
 * resolution is unknown, so what is preserved is the ratios <em>within</em> it: the health
 * bar is 1.58x the shield bar's height, the gap is 0.13x, the bar runs 9.13x the health
 * height across, and the icon column is 0.6x. Those are the four numbers held below.
 *
 * <p><b>The values read on Fortnite's 0-100 scale.</b> Minecraft's health is out of 20 and
 * absorption has no fixed cap, so a raw number would print "20" where Fortnite prints
 * "100". Both bars therefore show the fraction of the bar that is filled, as a percentage
 * — so the number and the fill always agree, and a full bar reads 100 exactly as it does
 * in Fortnite. It is a percentage, not a hit-point count, and it is labelled as one here
 * rather than passed off as the latter.
 *
 * <p><b>Why the vanilla element is replaced.</b> Hearts are the wrong shape for this and a
 * recoloured heart is a Minecraft HUD in green, which is not the thing being asked for.
 * Vanilla's {@code health} element covers the heart row and absorption and nothing else —
 * armour, food, air and mount health are separate elements — so replacing it cannot break
 * them.
 *
 * <p><b>Absorption is the shield.</b> Minecraft spends absorption before health, which is
 * Fortnite's own rule for shield, so the mapping is the real rule and nothing needs
 * intercepting. Overshield, Fortnite's third regenerating layer, has no Minecraft
 * counterpart and is not faked.
 */
public final class HealthShieldHud implements HudElement {
	private static final Identifier SHIELD_ICON = PingClient.id("textures/ping/hud_shield.png");
	private static final Identifier HEALTH_ICON = PingClient.id("textures/ping/hud_health.png");
	private static final int ICON_TEXTURE_SIZE = 128;

	// ---- geometry ---------------------------------------------------------

	/** Bar height. The scale is chosen; everything else is the capture's ratio to this. */
	private static final int HEALTH_HEIGHT = 16;

	/** 1.58x the shield bar's height, measured. */
	private static final int SHIELD_HEIGHT = 10;

	/** 0.13x the health height, measured. */
	private static final int BAR_GAP = 2;

	/** 9.13x the health height, measured. */
	static final int BAR_WIDTH = 146;

	/** 0.6x the health height, measured: the column the glyphs live in. */
	private static final int ICON_COLUMN = 12;

	/** Glyphs sit inside that column at about 0.72x their own bar's height. */
	private static final float ICON_SCALE = 0.72F;

	private static final int ICON_GAP = 4;
	private static final int VALUE_GAP = 6;
	private static final int MARGIN = 5;

	/** The health bar's bottom edge sits this far above the window's, clearing the hotbar. */
	private static final int BASELINE = 24;

	/** Clear space left between the top of the meter and the bottom of chat. */
	private static final int CHAT_GAP = 6;

	/** Width of the block that terminates a filled bar — the yellow tip on health. */
	private static final int HEALTH_CAP_WIDTH = 3;

	/** Width of the bright sliver that terminates a filled shield bar. */
	private static final int SHIELD_EDGE_WIDTH = 2;

	/**
	 * How wide a gradient strip is. The GUI has no horizontal gradient primitive, so each
	 * bar is drawn as columns; two pixels is finer than the eye resolves on a ramp this
	 * shallow and keeps the element to about 75 draws instead of 150.
	 */
	private static final int STRIP = 2;

	// ---- measured colours -------------------------------------------------

	/** The empty part of a bar: a solid fill in the capture, not a transparency. */
	private static final int TRACK = 0xFF211C3D;

	private static final int SHIELD_FROM = 0xFF039AEF;
	private static final int SHIELD_TO = 0xFF0AA9EF;

	/** The bright sliver on the leading edge of a part-filled shield bar. */
	private static final int SHIELD_EDGE = 0xFF6CEBFF;

	private static final int HEALTH_FROM = 0xFF25CA45;
	private static final int HEALTH_TO = 0xFFA9F43E;

	/** The yellow block capping a full health bar. */
	private static final int HEALTH_CAP = 0xFFEEF926;

	/** Poisoned and withered shift the ramp so the status still reads. */
	private static final int POISON_FROM = 0xFF2F9636;
	private static final int POISON_TO = 0xFF7FC24A;
	private static final int WITHER_FROM = 0xFF5E6B45;
	private static final int WITHER_TO = 0xFF9AA46E;

	private static final int VALUE_COLOR = 0xFFFFFFFF;

	/** Both glyphs are white in the capture; nothing tints them differently. */
	private static final int ICON_COLOR = 0xFFF2F5FA;

	/** What a full shield bar represents, matching a full vanilla absorption row. */
	private static final float SHIELD_CAPACITY = 20.0F;

	private final HudElement delegate;

	public HealthShieldHud(HudElement delegate) {
		this.delegate = delegate;
	}

	/**
	 * Height of the vehicle bar that sits above the stack, and the gap below it. Part of the
	 * stack's geometry rather than the vehicle HUD's own business, because
	 * {@link #clearance()} has to know about it.
	 */
	static final int MOUNT_HEIGHT = 10;
	static final int MOUNT_GAP = 2;

	/**
	 * How much room at the bottom of the screen the whole stack needs, including the
	 * mount bar's slot <em>even when there is no mount</em>.
	 *
	 * <p>That reservation is deliberate. Chat is lifted by this number, and lifting it only
	 * while mounted would make the chat block jump every time someone got on or off a horse
	 * — which is worse than twelve pixels of clearance that is sometimes unused.
	 */
	public static int clearance() {
		return BASELINE + HEALTH_HEIGHT + BAR_GAP + SHIELD_HEIGHT + MOUNT_GAP + MOUNT_HEIGHT + CHAT_GAP;
	}

	/** The y of the mount bar's top edge, above the meter. */
	static int mountTop(int guiHeight) {
		return meterTop(guiHeight) - MOUNT_GAP - MOUNT_HEIGHT;
	}

	/** The x the bars start at, shared so a second meter lines up with this one. */
	static int barLeft() {
		return MARGIN + ICON_COLUMN + ICON_GAP;
	}

	/** The y of the meter's top edge — the shield bar's top. */
	static int meterTop(int guiHeight) {
		return guiHeight - BASELINE - HEALTH_HEIGHT - BAR_GAP - SHIELD_HEIGHT;
	}

	/**
	 * Draws one bar in the meter's style. Shared with the vehicle meter so the two cannot
	 * drift apart, and because the terminator logic below is fiddly enough to be worth
	 * having in exactly one place.
	 */
	static void drawBar(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float fraction,
			int from, int to, int capColor, int capWidth, boolean separator) {
		graphics.fill(x, y, x + width, y + height, TRACK);

		int filled = Math.round(width * Mth.clamp(fraction, 0.0F, 1.0F));

		if (filled <= 0) {
			return;
		}

		int body = capColor == 0 ? filled : Math.max(0, filled - capWidth);

		for (int i = 0; i < body; i += STRIP) {
			int right = Math.min(i + STRIP, body);

			// The ramp is positioned across the whole bar rather than across the fill, so a
			// pixel keeps its colour as the value changes instead of the whole bar shifting.
			graphics.fill(x + i, y, x + right, y + height, lerp(from, to, i / (float) width));
		}

		if (capColor == 0) {
			return;
		}

		if (separator && body > 0) {
			graphics.fill(x + body, y, x + body + 1, y + height, TRACK);
		}

		graphics.fill(x + Math.max(body, filled - capWidth), y, x + filled, y + height, capColor);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (!PingConfig.fortniteHealth()) {
			this.delegate.extractRenderState(graphics, delta);

			return;
		}

		Minecraft client = Minecraft.getInstance();
		Player player = client.player;

		if (player == null || client.level == null || client.gui.hud.isHidden()) {
			return;
		}

		Font font = client.font;
		float health = player.getHealth();
		float maxHealth = Math.max(1.0F, player.getMaxHealth());
		float absorption = player.getAbsorptionAmount();

		int left = MARGIN;
		int barX = left + ICON_COLUMN + ICON_GAP;
		int valueX = barX + BAR_WIDTH + VALUE_GAP;

		int healthBottom = graphics.guiHeight() - BASELINE;
		int healthTop = healthBottom - HEALTH_HEIGHT;
		int shieldTop = healthTop - BAR_GAP - SHIELD_HEIGHT;

		int healthFrom = HEALTH_FROM;
		int healthTo = HEALTH_TO;

		if (player.hasEffect(MobEffects.WITHER)) {
			healthFrom = WITHER_FROM;
			healthTo = WITHER_TO;
		} else if (player.hasEffect(MobEffects.POISON)) {
			healthFrom = POISON_FROM;
			healthTo = POISON_TO;
		}

		// Shield above, health below — Epic's own description of the layout, and the order
		// it reads in: the thing spent first is the thing you look at first.
		this.drawBar(graphics, barX, shieldTop, BAR_WIDTH, SHIELD_HEIGHT, absorption / SHIELD_CAPACITY,
				SHIELD_FROM, SHIELD_TO, SHIELD_EDGE, SHIELD_EDGE_WIDTH, false);
		this.drawIcon(graphics, SHIELD_ICON, left, shieldTop, SHIELD_HEIGHT, ICON_COLOR);
		this.drawValue(graphics, font, absorption / SHIELD_CAPACITY, valueX, shieldTop, SHIELD_HEIGHT);

		this.drawBar(graphics, barX, healthTop, BAR_WIDTH, HEALTH_HEIGHT, health / maxHealth,
				healthFrom, healthTo, HEALTH_CAP, HEALTH_CAP_WIDTH, true);
		this.drawIcon(graphics, HEALTH_ICON, left, healthTop, HEALTH_HEIGHT, ICON_COLOR);
		this.drawValue(graphics, font, health / maxHealth, valueX, healthTop, HEALTH_HEIGHT);
	}

	/** A glyph, squared, sized to its own bar and centred on it. */
	private static void drawIcon(GuiGraphicsExtractor graphics, Identifier texture, int x, int barY, int barHeight,
			int color) {
		int size = Math.max(4, Math.round(barHeight * ICON_SCALE));
		int y = barY + (barHeight - size) / 2;

		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F,
				size, size, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE,
				ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, color);
	}

	/** The value, on Fortnite's 0-100 scale, vertically centred on its bar. */
	private static void drawValue(GuiGraphicsExtractor graphics, Font font, float fraction, int x, int barY,
			int barHeight) {
		int value = Math.round(Mth.clamp(fraction, 0.0F, 1.0F) * 100.0F);
		String text = Integer.toString(value);

		graphics.text(font, text, x, barY + (barHeight - 8) / 2, VALUE_COLOR, true);
	}

	/** A colour ramp between the two ends of a bar. */
	private static int lerp(int from, int to, float t) {
		float k = Mth.clamp(t, 0.0F, 1.0F);

		int a = (int) ((from >>> 24 & 0xFF) + ((to >>> 24 & 0xFF) - (from >>> 24 & 0xFF)) * k);
		int r = (int) ((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * k);
		int g = (int) ((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * k);
		int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * k);

		return a << 24 | r << 16 | g << 8 | b;
	}
}
