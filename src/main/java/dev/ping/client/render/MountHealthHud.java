package dev.ping.client.render;

import dev.ping.client.PingConfig;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Fortnite's vehicle health bar: one orange bar sitting above the player's shield bar,
 * replacing vanilla's own mount health.
 *
 * <p><b>Why vanilla's mount hearts are replaced.</b> Minecraft draws a mount's health as a
 * second row of hearts beneath the player's own, which is the wrong shape for this HUD for
 * the same reason the player's row was replaced — a recoloured heart is a Minecraft HUD in
 * orange, not the thing being asked for. Fortnite instead draws a single separate bar:
 * the wiki's <i>Health (Battle Royale)</i> page records that riding a vehicle draws a
 * separate <b>orange</b> health bar above the shield bar. That fact is documentation, not a
 * measurement, and it is recorded as such in {@code docs/FIDELITY.md}.
 *
 * <p><b>Position and width are shared with the player meter, not re-derived.</b> The bar
 * starts at the same x as health and shield ({@link HealthShieldHud#barLeft()}), runs the
 * same width ({@link HealthShieldHud#BAR_WIDTH}), and floats {@link #GAP} GUI pixels above
 * the meter's top edge ({@link HealthShieldHud#meterTop(int)}), so the three bars read as
 * one stack and cannot drift apart if the meter is ever resized. The height is
 * <b>chosen</b> — the brief allows 8-10 GUI px and no capture fixes it — and is set equal to
 * the shield bar's height so the upper two bars match.
 *
 * <p><b>The orange is chosen, not measured.</b> The wiki says "orange" and gives no hex, so
 * the ramp below cannot be a measurement and is not labelled as one. It is picked to sit in
 * the same family as the meter's own ramps: a deeper orange on the left running to a lighter
 * one on the right, with a brighter tip where the fill ends. Treat it as a working value
 * chosen to look right, exactly as {@code docs/FIDELITY.md} asks.
 *
 * <p><b>The value is a percentage on Fortnite's 0-100 scale.</b> Minecraft health is out of
 * 20, so a raw number would print "20" where Fortnite prints "100". The number printed is
 * the fraction of the bar that is filled, rounded, so the digits and the fill always agree —
 * the same convention, and the same reasoning, as {@link HealthShieldHud}. It is not a
 * hit-point count.
 *
 * <p><b>Delegation keeps vanilla exact.</b> The row only exists for a mount that would get
 * one, and the guard is vanilla's own: {@code Hud.getPlayerVehicleWithHealth} returns the
 * player's vehicle only when it {@code instanceof LivingEntity}, and
 * {@code Hud.getVehicleMaxHearts} computes {@code (int)(maxHealth + 0.5) / 2}, which is zero
 * exactly when {@code maxHealth <= 1}. So "a {@code LivingEntity} with {@code maxHealth > 1}"
 * below is that condition written out. When there is no such mount, the previous element is
 * invoked rather than skipped, so vanilla's behaviour is preserved to the pixel. Null player
 * or level and a hidden HUD return without drawing, matching {@link HealthShieldHud}.
 */
public final class MountHealthHud implements HudElement {
	// ---- geometry ---------------------------------------------------------

	/** The gap {@link HealthShieldHud} puts between a bar and its value, matched here. */
	private static final int VALUE_GAP = 6;

	// ---- chosen colours ---------------------------------------------------

	/**
	 * The vehicle ramp. <b>Chosen, not measured</b> — the wiki says orange and gives no hex,
	 * so these are picked to read as Fortnite orange beside the shield blue and health green.
	 */
	private static final int VEHICLE_FROM = 0xFFF26A0C;
	private static final int VEHICLE_TO = 0xFFFFA015;

	/** A brighter tip where the fill ends, in the meter's own manner. Chosen, not measured. */
	private static final int VEHICLE_CAP = 0xFFFFC93C;

	private static final int VEHICLE_CAP_WIDTH = 3;

	private static final int VALUE_COLOR = 0xFFFFFFFF;

	private final HudElement delegate;

	public MountHealthHud(HudElement delegate) {
		this.delegate = delegate;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		// With the Fortnite meter turned off, the player's hearts are back and a lone orange
		// bar above them would be half a HUD. Hand the whole row back so the setting moves
		// both together, the way ChatOffsetHud reads the same flag.
		if (!PingConfig.fortniteHealth()) {
			this.delegate.extractRenderState(graphics, delta);

			return;
		}

		Minecraft client = Minecraft.getInstance();
		Player player = client.player;

		if (player == null || client.level == null || client.gui.hud.isHidden()) {
			return;
		}

		Entity vehicle = player.getVehicle();

		// Vanilla's guard, narrowed to the two conditions that actually decide a bar:
		// a LivingEntity mount whose max health yields at least one heart. Anything else —
		// walking, a boat, a mount with a single hit point — has no bar to draw, so vanilla
		// is asked to draw nothing rather than being skipped outright.
		if (!(vehicle instanceof LivingEntity mount) || mount.getMaxHealth() <= 1.0F) {
			this.delegate.extractRenderState(graphics, delta);

			return;
		}

		float health = mount.getHealth();
		float maxHealth = Math.max(1.0F, mount.getMaxHealth());
		float fraction = health / maxHealth;

		int barX = HealthShieldHud.barLeft();
		int barY = HealthShieldHud.mountTop(graphics.guiHeight());

		HealthShieldHud.drawBar(graphics, barX, barY, HealthShieldHud.BAR_WIDTH, HealthShieldHud.MOUNT_HEIGHT,
				fraction, VEHICLE_FROM, VEHICLE_TO, VEHICLE_CAP, VEHICLE_CAP_WIDTH, true);

		// The value, on Fortnite's 0-100 scale, vertically centred on the bar and set just
		// right of it — the placement HealthShieldHud.drawValue uses for the other two bars.
		Font font = client.font;
		int value = Math.round(Mth.clamp(fraction, 0.0F, 1.0F) * 100.0F);

		graphics.text(font, Integer.toString(value), barX + HealthShieldHud.BAR_WIDTH + VALUE_GAP,
				barY + (HealthShieldHud.MOUNT_HEIGHT - 8) / 2, VALUE_COLOR, true);
	}
}
