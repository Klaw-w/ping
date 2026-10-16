package dev.ping.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.ping.client.PingClient;
import dev.ping.ping.Ping;
import dev.ping.ping.PingConstants;
import dev.ping.ping.PingManager;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the beam of light that stands at a marked point.
 *
 * <p>This is <b>real world geometry</b>, not a HUD decoration. An earlier version drew the
 * beam as a screen-space quad in the HUD, which meant it had no perspective, was never
 * occluded by terrain, did not clip against the near plane, and visibly slid relative to
 * the world as the camera moved. It has to be a column standing in the world, the way a
 * tower of blocks is.
 *
 * <p>The technique is lifted from vanilla's beacon beam: a hollow column of four quads,
 * submitted as custom geometry into the level's translucent phase. Geometry is submitted
 * in absolute world coordinates (the camera-relative transform is applied later), so the
 * only transform needed here is a translation to the marked point.
 *
 * <p>Vanilla's own {@code BeaconRenderer.submitBeaconBeam} is the reference for the
 * winding and vertex layout; getting the winding wrong makes the column invisible under
 * backface culling, which is enabled on the beacon pipeline.
 */
public final class PingBeamRenderer {
	/**
	 * A column texture rather than the billboard one: the faces of a box all sample the
	 * same horizontal profile, so it is constant across the width with only the vertical
	 * fade the beam needs.
	 */
	private static final Identifier BEAM_TEXTURE = PingClient.id("textures/ping/beam_column.png");

	/** Full brightness, matching what the beacon beam uses. */
	private static final int FULL_BRIGHT = 15728880;

	private PingBeamRenderer() {
	}

	public static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(PingBeamRenderer::collect);
	}

	private static void collect(LevelRenderContext context) {
		Minecraft client = Minecraft.getInstance();

		if (client.level == null || client.player == null) {
			return;
		}

		PingManager pings = PingClient.pings();

		if (pings.size() == 0) {
			return;
		}

		Camera camera = client.gameRenderer.mainCamera();
		Vec3 cameraPos = camera.position();
		float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		long now = System.currentTimeMillis();
		SubmitNodeCollector collector = context.submitNodeCollector();

		// One stack reused across every marker; submitCustomGeometry copies the pose it is
		// handed, so nothing escapes this method.
		PoseStack pose = new PoseStack();

		for (Ping ping : pings.all()) {
			Vec3 base = baseOf(ping, client, partialTick);
			float fade = ping.fadeAlpha(now);

			if (fade <= 0.01F) {
				continue;
			}

			int colour = PingConstants.beamColor(ping.color());

			// The small beam is always present: it is simply too short to make out from any
			// distance, which is why the beam appears to only show up once you back off.
			submitColumn(collector, pose, cameraPos, base, PingConstants.MINI_BEAM_HEIGHT,
					PingConstants.MINI_BEAM_WIDTH * 0.5D, colour, fade);

			// The large one is a long-range signpost and fades in around 50 blocks.
			float bigAlpha = fade * PingConstants.beamAlpha(cameraPos.distanceTo(base));

			if (bigAlpha > 0.01F) {
				submitColumn(collector, pose, cameraPos, base, PingConstants.BEAM_HEIGHT,
						PingConstants.BEAM_WIDTH * 0.5D, colour, bigAlpha);
			}
		}
	}

	/**
	 * Submits one hollow column: four side quads, no caps, drawn in the translucent phase.
	 *
	 * <p>The translation is <b>camera-relative</b>. The level renderer submits everything
	 * that way ({@code LevelRenderer.submitBlockEntities} translates by
	 * {@code blockPos - cameraPos}, and entity submission passes {@code entityPos - cameraPos}),
	 * and the model-view expects it. Absolute world coordinates put the geometry a whole
	 * camera position away from where it belongs.
	 */
	private static void submitColumn(SubmitNodeCollector collector, PoseStack pose, Vec3 cameraPos, Vec3 base,
			double height, double radius, int colour, float alpha) {
		if (height < 0.05D || radius < 0.005D || alpha <= 0.01F) {
			return;
		}

		float r = (float) radius;
		float h = (float) height;
		int argb = ARGB.color(Math.round(Math.min(1.0F, alpha) * 255.0F), colour);

		pose.pushPose();
		pose.translate(base.x - cameraPos.x, base.y - cameraPos.y, base.z - cameraPos.z);

		try {
			collector.submitCustomGeometry(pose, RenderTypes.beaconBeam(BEAM_TEXTURE, true),
					(entry, buffer) -> {
						// Four faces of a square column, wound exactly as the beacon does.
						quad(entry, buffer, argb, h, 0.0F, r, r, 0.0F);
						quad(entry, buffer, argb, h, 0.0F, -r, -r, 0.0F);
						quad(entry, buffer, argb, h, r, 0.0F, 0.0F, -r);
						quad(entry, buffer, argb, h, -r, 0.0F, 0.0F, r);
					});
		} finally {
			// The level renderer validates that its pose stack is balanced, so this must
			// pop even if a submission throws.
			pose.popPose();
		}
	}

	/** One face, from the top edge down and back up, as the beacon winds it. */
	private static void quad(PoseStack.Pose pose, VertexConsumer buffer, int argb, float height,
			float ax, float az, float bx, float bz) {
		vertex(pose, buffer, argb, height, 0.0F, 1.0F, ax, az);
		vertex(pose, buffer, argb, 0.0F, 0.0F, 0.0F, ax, az);
		vertex(pose, buffer, argb, 0.0F, 1.0F, 0.0F, bx, bz);
		vertex(pose, buffer, argb, height, 1.0F, 1.0F, bx, bz);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int argb,
			float y, float u, float v, float x, float z) {
		buffer.addVertex(pose, x, y, z)
				.setColor(argb)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(FULL_BRIGHT)
				.setNormal(pose, 0.0F, 1.0F, 0.0F);
	}

	/**
	 * Where a marker actually is this frame. A marker following an entity stores its
	 * position as sampled on the last tick, so drawing that directly would make the beam
	 * step at 20 Hz while the world runs at frame rate.
	 */
	private static Vec3 baseOf(Ping ping, Minecraft client, float partialTick) {
		if (!ping.isAttached() || client.level == null) {
			return ping.pos();
		}

		Entity entity = client.level.getEntity(ping.attachedEntityId());

		if (entity == null) {
			return ping.pos();
		}

		return entity.getPosition(partialTick).add(0.0D, entity.getBbHeight() * 0.6D, 0.0D);
	}
}
