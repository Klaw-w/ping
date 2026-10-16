package dev.ping.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import dev.ping.client.PingConfig;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.Mth;

/**
 * Vanilla chat, lifted clear of the health meter and drawn without its dark plate.
 *
 * <p>Two reasons this replaces vanilla's {@code chat} element:
 *
 * <ul>
 * <li><b>Position.</b> Minecraft's chat and Fortnite's health bar both live in the
 * bottom-left and print over each other. The meter is the one Epic documents a position
 * for, so chat is what moves, which also lands the arrangement Fortnite actually has,
 * the feed sitting above the health bar.</li>
 * <li><b>No plate.</b> Fortnite's feed is plain text with a shadow: no panel, no
 * background box. Minecraft's chat draws a black rectangle behind every line, which is
 * the single most obvious difference between the two. The plate's opacity comes from the
 * game's own {@code textBackgroundOpacity} option, so that option is zeroed for the
 * duration of this draw and put back afterwards. The player's setting is not changed:
 * only what this one call sees, and the restore is in a {@code finally} so a failure
 * mid-draw cannot leave it at zero.</li>
 * </ul>
 *
 * <p>The translation is taken from {@link HealthShieldHud#clearance()} so the two cannot
 * drift apart if the meter is ever resized. Vanilla draws nothing here while chat is
 * focused (that is a screen), and this keeps that condition rather than inventing a
 * second one.
 */
public final class ChatOffsetHud implements HudElement {
	public ChatOffsetHud() {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		ChatComponent chat = client.gui.hud.getChat();

		if (client.player == null || chat.isChatFocused()) {
			return;
		}

		int mouseX = Mth.floor(client.mouseHandler.getScaledXPos(client.getWindow()));
		int mouseY = Mth.floor(client.mouseHandler.getScaledYPos(client.getWindow()));

		// Only lift chat when the meter is actually being drawn. With the setting off,
		// vanilla's hearts are back and there is nothing for chat to clear, leaving the
		// offset on would strand chat in mid-air above a gap.
		float lift = PingConfig.fortniteHealth() ? HealthShieldHud.clearance() : 0.0F;

		graphics.nextStratum();

		// The plate's alpha comes from the game's own background-opacity options, so they
		// are forced to zero for the duration of this draw and put back afterwards. Both
		// are needed: whichever of the two the game consults, it now reads a zero. The
		// player's settings are not changed: only what this one call sees, and the
		// restore is in a finally so a failure mid-draw cannot leave them zeroed.
		OptionInstance<Double> plate = client.options.textBackgroundOpacity();
		OptionInstance<Boolean> chatOnly = client.options.backgroundForChatOnly();
		double previousPlate = plate.get();
		boolean previousChatOnly = chatOnly.get();
		plate.set(0.0D);
		chatOnly.set(false);

		try {
			graphics.pose().pushMatrix();
			graphics.pose().translate(0.0F, -lift);
			chat.extractRenderState(graphics, client.font, client.gui.hud.getGuiTicks(), mouseX, mouseY,
					ChatComponent.DisplayMode.BACKGROUND, false);
			graphics.pose().popMatrix();
		} finally {
			plate.set(previousPlate);
			chatOnly.set(previousChatOnly);
		}
	}
}
