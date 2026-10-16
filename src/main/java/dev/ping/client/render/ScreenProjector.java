package dev.ping.client.render;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * World to screen projection.
 *
 * <p>Rather than rebuilding a perspective matrix by hand (and getting Minecraft's FOV
 * quirks wrong), this asks the camera for its view-rotation-projection matrix and
 * transforms a camera-relative point through it. That is the same matrix the game
 * itself uses, so markers land exactly where the crosshair says they should.
 *
 * <p>Because the matrix is rotation-only, the input must already have the camera
 * position subtracted from it.
 *
 * <p>Not thread safe by design: it reuses scratch buffers and is only ever called from
 * the client's render thread.
 */
public final class ScreenProjector {
	private static final Matrix4f PROJECTION = new Matrix4f();
	private static final Vector4f SCRATCH = new Vector4f();

	private ScreenProjector() {
	}

	/**
	 * @return screen coordinates, or {@code null} if the point is degenerate
	 */
	public static Result project(Camera camera, Vec3 worldPos, int width, int height) {
		Vec3 relative = worldPos.subtract(camera.position());

		PROJECTION.identity();
		camera.getViewRotationProjectionMatrix(PROJECTION);

		SCRATCH.set((float) relative.x, (float) relative.y, (float) relative.z, 1.0F);
		SCRATCH.mul(PROJECTION);

		float w = SCRATCH.w;

		if (Math.abs(w) < 1.0E-4F) {
			return null;
		}

		float ndcX = SCRATCH.x / w;
		float ndcY = SCRATCH.y / w;
		boolean behind = w < 0.0F;

		// A negative w means the point is behind the camera, and the divide mirrors it.
		// Un-mirror so the caller can still clamp a caret to the correct screen edge.
		if (behind) {
			ndcX = -ndcX;
			ndcY = -ndcY;
		}

		float screenX = (ndcX * 0.5F + 0.5F) * width;
		float screenY = (0.5F - ndcY * 0.5F) * height;
		boolean onScreen = !behind
				&& screenX >= 0.0F && screenX <= width
				&& screenY >= 0.0F && screenY <= height;

		return new Result(screenX, screenY, onScreen, behind);
	}

	/**
	 * @param x        screen x, possibly outside the viewport
	 * @param y        screen y, possibly outside the viewport
	 * @param onScreen true when the marker is genuinely within the viewport
	 * @param behind   true when the point is behind the camera, in which case x/y have
	 *                 been un-mirrored so they still give the right direction to look
	 */
	public record Result(float x, float y, boolean onScreen, boolean behind) {
	}
}
