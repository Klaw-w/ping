package dev.ping.client.item;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * Works out what colour a marked item should be drawn in.
 *
 * <p>Three rules, in order:
 *
 * <ol>
 *   <li><b>Dyed items</b>: leather armour and anything else carrying a
 *       {@code DYED_COLOR} component uses that exact colour.</li>
 *   <li><b>Known materials and dye-coloured blocks</b>: {@code diamond_ore} is drawn in
 *       diamond blue, not in the average of its own grey rock texture, and
 *       {@code red_bed} is drawn in red rather than in the average of the bed model.
 *       These are the cases where the item's appearance genuinely is not the thing being
 *       called out.</li>
 *   <li><b>Everything else</b>: the average colour of the item's own icon, ignoring
 *       transparent pixels. That is what makes an apple read red and a stick read brown
 *       without anyone having to write a table.</li>
 * </ol>
 *
 * <p>Results are cached per item. Anything that cannot be resolved returns the supplied
 * fallback, so a missing or oddly-shaped texture never leaves a marker colourless.
 */
public final class ItemColors {
	private static final Map<Item, Integer> CACHE = new HashMap<>();

	/** The 16 dye colours, as Minecraft uses them for wool, beds, concrete and so on. */
	private static final Map<String, Integer> DYES = new HashMap<>();

	/** Materials whose own colour is the meaningful one, ignoring their texture. */
	private static final Map<String, Integer> MATERIALS = new HashMap<>();

	/** Affixes stripped before looking a name up in {@link #MATERIALS}. */
	private static final String[] SUFFIXES = {
			"_ore", "_block", "_ingot", "_nugget", "_dust", "_shard", "_gem", "_scrap",
	};

	private static final String[] PREFIXES = { "deepslate_", "nether_", "raw_", "ancient_", "waxed_" };

	static {
		dye("white", 0xF9FFFE);
		dye("orange", 0xF9801D);
		dye("magenta", 0xC74EBD);
		dye("light_blue", 0x3AB3DA);
		dye("yellow", 0xFED83D);
		dye("lime", 0x80C71F);
		dye("pink", 0xF38BAA);
		dye("gray", 0x474F52);
		dye("light_gray", 0x9D9D97);
		dye("cyan", 0x169C9C);
		dye("purple", 0x8932B8);
		dye("blue", 0x3C44AA);
		dye("brown", 0x835432);
		dye("green", 0x5E7C16);
		dye("red", 0xB02E26);
		dye("black", 0x1D1D21);

		material("diamond", 0x4AEDD9);
		material("emerald", 0x17DD62);
		material("gold", 0xFCEE4B);
		material("iron", 0xD8D8D8);
		material("copper", 0xE0774A);
		material("coal", 0x2B2B2B);
		material("redstone", 0xFF3B3B);
		material("lapis", 0x3E6CD9);
		material("amethyst", 0xA05CE0);
		material("quartz", 0xEFE6DD);
		material("netherite", 0x4A4244);
		material("flint", 0x4A4644);
		material("clay", 0xA4A8B8);
		material("glowstone", 0xFFD966);
		material("gunpowder", 0x5A5A5A);
		material("blaze", 0xFFCC33);
		material("slime", 0x7FD65C);
		material("bone", 0xE8E4D8);
		material("string", 0xE8E8E8);
		material("charcoal", 0x2B2B2B);
	}

	private ItemColors() {
	}

	private static void dye(String name, int rgb) {
		DYES.put(name, rgb);
	}

	private static void material(String name, int rgb) {
		MATERIALS.put(name, rgb);
	}

	/**
	 * The colour to draw a marker for this item in.
	 *
	 * @param fallback returned when nothing else can be worked out
	 */
	public static int of(ItemStack stack, int fallback) {
		// Dyed items vary from stack to stack, so they must never come from the shared
		// cache: the check has to happen before the lookup, not after it.
		DyedItemColor dyed = stack.get(DataComponents.DYED_COLOR);

		if (dyed != null) {
			return 0xFF000000 | (dyed.rgb() & 0x00FFFFFF);
		}

		Item item = stack.getItem();
		Integer cached = CACHE.get(item);

		if (cached != null) {
			return cached;
		}

		int resolved = resolve(item, fallback);
		CACHE.put(item, resolved);

		return resolved;
	}

	private static int resolve(Item item, int fallback) {
		String path = pathOf(item);

		if (path == null) {
			return fallback;
		}

		Integer known = known(path);

		if (known != null) {
			return 0xFF000000 | known;
		}

		Integer average = averageIcon(item, path);

		return average != null ? average : fallback;
	}

	/**
	 * The item's path within its namespace, taken from its translation key, e.g.
	 * {@code item.minecraft.diamond_ore} gives {@code diamond_ore}. Avoids a registry
	 * lookup entirely.
	 *
	 * <p>Block items are named {@code block.minecraft.stone} rather than {@code item.*},
	 * so both prefixes have to be accepted or every block item falls back to the
	 * author's colour.
	 */
	private static String pathOf(Item item) {
		String[] parts = item.getDescriptionId().split("\\.", 3);

		if (parts.length < 3 || (!"item".equals(parts[0]) && !"block".equals(parts[0]))) {
			return null;
		}

		return parts[2];
	}

	private static String namespaceOf(Item item) {
		String[] parts = item.getDescriptionId().split("\\.", 3);

		return parts.length < 3 ? "minecraft" : parts[1];
	}

	/** A colour from the dye or material tables, or {@code null}. */
	private static Integer known(String path) {
		String name = path.toLowerCase(Locale.ROOT);

		// Dye-coloured blocks: red_wool, red_bed, cyan_concrete, light_blue_glazed_terracotta.
		// Longest names first so light_blue is not read as blue.
		for (String colour : DYES.keySet()) {
			if (name.equals(colour + "_dye") || name.startsWith(colour + "_")) {
				return DYES.get(colour);
			}
		}

		// Materials: diamond_ore, deepslate_diamond_ore, raw_iron, iron_ingot, gold_block.
		String trimmed = name;

		for (String prefix : PREFIXES) {
			if (trimmed.startsWith(prefix)) {
				trimmed = trimmed.substring(prefix.length());
			}
		}

		for (String suffix : SUFFIXES) {
			if (trimmed.endsWith(suffix)) {
				trimmed = trimmed.substring(0, trimmed.length() - suffix.length());
			}
		}

		return MATERIALS.get(trimmed);
	}

	/**
	 * The average colour of the item's icon. Item textures live at
	 * {@code textures/item/<path>.png} and block items at {@code textures/block/<path>.png},
	 * so both are tried. That convention is what makes this work without a registry or a
	 * model lookup.
	 */
	private static Integer averageIcon(Item item, String path) {
		Integer fromItems = averageTexture(item, "textures/item/" + path + ".png");

		return fromItems != null ? fromItems : averageTexture(item, "textures/block/" + path + ".png");
	}

	private static Integer averageTexture(Item item, String texturePath) {
		Identifier texture = Identifier.fromNamespaceAndPath(namespaceOf(item), texturePath);
		Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(texture);

		if (resource.isEmpty()) {
			return null;
		}

		NativeImage image = null;

		try (InputStream stream = resource.get().open()) {
			image = NativeImage.read(stream);

			long red = 0L;
			long green = 0L;
			long blue = 0L;
			long count = 0L;

			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					int pixel = image.getPixel(x, y);

					// Transparent pixels are padding, not colour.
					if (ARGB.alpha(pixel) < 8) {
						continue;
					}

					red += ARGB.red(pixel);
					green += ARGB.green(pixel);
					blue += ARGB.blue(pixel);
					count++;
				}
			}

			if (count == 0L) {
				return null;
			}

			return 0xFF000000
					| ((int) (red / count) << 16)
					| ((int) (green / count) << 8)
					| (int) (blue / count);
		} catch (Exception e) {
			// A missing or unreadable texture is not worth a crash; fall back.
			return null;
		} finally {
			if (image != null) {
				image.close();
			}
		}
	}
}
