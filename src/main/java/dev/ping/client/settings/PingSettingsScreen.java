package dev.ping.client.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

import com.mojang.blaze3d.platform.InputConstants;

import dev.ping.client.PingConfig;
import dev.ping.client.PingKeys;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import org.lwjgl.glfw.GLFW;

/**
 * The mod's settings, as a screen, rebuilt to a pair of Fortnite captures.
 *
 * <p>Two reference frames were supplied — Fortnite's {@code VIDEO} tab and its {@code AUDIO}
 * tab, both 2000×1125 — and every colour and offset below was read off them with a pixel
 * probe rather than chosen. That is a change of provenance worth stating plainly: the
 * previous revision of this class had none. It borrowed the map screen's chrome, which came
 * off a different capture altogether, and spaced its rows to that screen's rhythm. The map
 * screen is still measured; this one is now measured too, from its own frames.
 *
 * <p><b>The caveat, and it is the usual one.</b> Both captures are of an unknown scale, so
 * <em>ratios within a frame are safe and absolute pixels are not</em>. Everything is
 * therefore stored as a fraction of the window: a measurement in capture pixels only ever
 * appears below as a numerator over 2000 (horizontal) or 1125 (vertical), and is converted
 * at draw time. Nothing was read off one frame and applied at the other's scale, and the two
 * agreed wherever they overlapped — the tab bar, the rail, the buttons and the section
 * headings are pixel-identical in both, which is the only reason the measurements can be
 * treated as one screen's.
 *
 * <p><b>Text is the exception, and it is why the sizes read oddly.</b> Fortnite sets these
 * screens in a typeface this mod does not ship, so the sizes are kept as
 * <em>ratios to the smallest cap height in the capture</em> and applied to
 * Minecraft's own font. The smallest text in the frame is the tab name and the button
 * labels, both 17 capture pixels; that is drawn at 1×, so nothing here except the frame-rate
 * readout (0.82×) is smaller than the game's own glyphs. The same trade is already made in
 * the minimap readouts, for the same reason: Fortnite's 18 px body text is under 6 GUI
 * pixels at a GUI scale of 3, which is below what this font can draw legibly. The ratios
 * survive that, the absolute sizes do not, and the code says which is which.
 *
 * <p>No italic is faked. The capture's headings are oblique and this build's are not: a skew
 * is a guess about the pose stack dressed up as fidelity, and a straight bold cap at the
 * right size reads closer than a broken slant.
 *
 * <p><b>Every line of text is placed by its baseline, and the ascent is the constant that
 * does it.</b> Minecraft anchors a run by the top of its line box, and for this build's font
 * that box is nine pixels: seven of ascent and two of descent ({@code Font.lineHeight} is a
 * hard-coded 9, and {@code GlyphBitmap.getTop} is {@code 7.0F − bearingTop} against a default
 * bearing of 7, so a capital's ink starts exactly on the {@code y} the run is drawn at and
 * its baseline is seven below). That makes a measured ink top a legal {@code y} — every
 * heading, row label and panel line keeps the capture's own ink position — but it also means
 * no two texts at <em>different</em> cap heights share a line
 * unless the smaller is pushed down by the difference in their caps times that ascent, and
 * that a text held by a box stands on the box's centre line rather than hanging from its top
 * edge. Both are {@link #shareBaseline} and {@link #centredTop}. Nothing on this screen is
 * placed by a per-element offset any more: {@code ROW_VALUE_DY}, a two-capture-pixel nudge
 * between the label and its value, is gone, because the two pixels were always the cap
 * difference in disguise and only rounded to the right GUI pixel at the window height they
 * were read at. The label also carried Minecraft's drop shadow where its value did not, and
 * a shadow is one font pixel of extra ink below the baseline — at this screen's sizes that is
 * three device pixels, which is exactly the "few pixels" that made a row look like two lines.
 *
 * <p><b>Every string on this screen goes through {@link #boxedText} or
 * {@link #boxedParagraph}.</b> Fortnite puts each label, value and paragraph in an invisible
 * box: the text is clipped to it, and when it does not fit it scrolls inside rather than
 * spilling onto whatever is next to it. That behaviour is not decoration — the {@code AUDIO}
 * capture's Audio Output Device value is visibly sliced to <em>utput - Headphones (Real</em>
 * by its own box — and the previous revision of this class, which let {@code text} land
 * wherever it liked, ran the description straight into the option list. The box is drawn
 * with {@link GuiGraphicsExtractor#enableScissor}, verified against the mapped jar: the
 * method pushes a {@code ScreenRectangle} onto a {@code ScissorStack} and
 * {@link GuiGraphicsExtractor#disableScissor} pops it, so the pair is balanced and nestable,
 * and it is always disabled on the way out or the rest of the HUD would vanish with it.
 *
 * <p><b>What is drawn, and the three things that are not.</b> The backdrop, the tab bar, the
 * left scroll rail, the section headings, the rows with their tick bars and focus box, the
 * information panel, the two buttons and the frame-rate readout are all the capture's. Left
 * out on purpose:
 *
 * <ul>
 *   <li><b>The tab's icon.</b> The capture's active tab is a glyph on a light slab. Epic's
 *       artwork is not in this repo and freehanding a Fortnite glyph is what
 *       {@code AGENTS.md} forbids, so the slab carries the tab's <em>name</em> instead —
 *       which is what this mod's own map screen already does with its active tab, so the two
 *       screens agree.</li>
 *   <li><b>{@code APPLY}.</b> The capture has three buttons. {@code BACK} and
 *       {@code RESTORE TO DEFAULT} both do something here; {@code APPLY} would not, because
 *       this screen writes through {@link PingConfig} the moment a value changes and there
 *       is nothing left to commit. A button that does nothing is a lie in the shape of a
 *       button, which is the same judgement the map screen's tab strip makes.</li>
 *   <li><b>The solid sliders.</b> Brightness and contrast in the capture are a blue-filled
 *       track with a dark remainder — measured {@code #002A89} at the left of the fill
 *       brightening to {@code #0551EE} at its edge, over a {@code #000545} track that runs
 *       from x 610 to past the scrollbar. Every number here is discrete and stepped, so none
 *       of them uses one; the numbers are recorded here so the next person does not have to
 *       measure them again. A setting with more steps than the tick bar has segments shows
 *       its position as a lit segment, not as a filled track.</li>
 * </ul>
 *
 * <p><b>Two readings that could have gone the other way, resolved.</b> The tab's name is
 * <em>centred</em> under its chip, not left-aligned: measured, the name "VIDEO" spans
 * x 414–485 of a chip at x 403–495, centres 449.5 and 449, and "AUDIO" agrees. And the space
 * around the focused row is real padding rather than a gap — the focus box is 89 capture
 * pixels tall where the row pitch is 96, so one row's box very nearly touches the next.
 *
 * <p><b>Sections, and the one measurement they cost.</b> The captures put their group
 * headings ({@code DISPLAY}, {@code GRAPHICS}, {@code SOUND}, {@code CREATOR OPTIONS}) inline
 * in the list, scrolling with the rows — the {@code AUDIO} frame proves it, since its list
 * has scrolled far enough to show {@code SOUND} mid-list and {@code CREATOR OPTIONS} at the
 * fold. This screen therefore draws each heading as the first line of its group. The cost is
 * the {@code VIDEO} frame's measured gap between its first heading (y 0.1618) and its first
 * row (y 0.2373), 0.0755 where every later row pitches by 0.0853; a list that scrolls cannot
 * keep a special first gap, so this uses the row pitch throughout and the heading sits at its
 * measured place with the first row 0.0098 lower than the frame's.
 *
 * <p><b>The rail, where the two frames disagree.</b> {@code VIDEO}'s rail is a short pill
 * with one dot per row; {@code AUDIO}'s is a full-height track with a thumb, because its list
 * is longer than its window. This list is longer than its window too, so the {@code AUDIO}
 * reading is the one followed: a track at the same x and width, with a thumb whose length is
 * the visible fraction of the list. The {@code VIDEO} dot pattern is what the thumb's track
 * replaces.
 */
public final class PingSettingsScreen extends Screen {
	// ---- the backdrop -----------------------------------------------------

	/**
	 * The backdrop, sampled at three rows of the frame across nine columns each.
	 *
	 * <p>It is not a diagonal ramp, and it is worth knowing before anyone replaces it with
	 * one. The brightest point measured is not a corner but the left edge about a third of
	 * the way down ({@code #0D4E6E}); the darkest is the middle of the frame
	 * ({@code #0F0D1B}); and the bottom-right corner ({@code #0E3264}, a medium navy) is
	 * brighter again than the middle beside it. A single {@code fillGradient} cannot express
	 * that — it is monotonic along one axis — so the field is drawn as vertical strips
	 * between the sampled rows. See {@link #backdrop}.
	 *
	 * <p>Every value is {@code #RRGGBB}; the alpha is added at draw time.
	 */
	private static final float[] PROFILE_X = { 0.0F, 0.10F, 0.20F, 0.30F, 0.40F, 0.50F, 0.80F, 0.90F, 1.0F };

	/** The backdrop along the capture's top edge (y 0 of 1125). */
	private static final int[] PROFILE_TOP = {
			0xFF0D4663, 0xFF0D3F5A, 0xFF0D2D42, 0xFF0E1B2C, 0xFF100F1F,
			0xFF0F0D1B, 0xFF0F0E1E, 0xFF0E142A, 0xFF0F152D
	};

	/**
	 * The backdrop at y 500 of 1125 — the row that shows the left of the frame is still
	 * bright halfway down, which a top-to-bottom ramp would not.
	 *
	 * <p>That row is also the row the right-hand option bar sits on, so x 0.60–0.70 of it was
	 * unreadable there. The profile steps from 0.50 straight to 0.80 across the hole rather
	 * than carrying a colour that came off a white pill.
	 */
	private static final int[] PROFILE_MID = {
			0xFF0D4A69, 0xFF0C425C, 0xFF0F2F46, 0xFF0F1C2D, 0xFF100F1E,
			0xFF0F0D1B, 0xFF0F2A55, 0xFF0D2F5D, 0xFF0D3162
	};

	/** The backdrop along the capture's bottom edge (y 1120 of 1125). */
	private static final int[] PROFILE_BOTTOM = {
			0xFF0F1C2D, 0xFF0F1A2C, 0xFF0E1424, 0xFF100F1F, 0xFF0F0D1B,
			0xFF0F0D1B, 0xFF0E2142, 0xFF0C2D58, 0xFF0D3161
	};

	/** Where the middle profile sits, as a fraction of the height. */
	private static final float PROFILE_MID_Y = 0.4444F;

	/** How many vertical strips the backdrop is drawn in. */
	private static final int BACKDROP_STRIPS = 32;

	// ---- the tab bar ------------------------------------------------------

	/** The bar's rounded plate: x 396–1603, y 18–83 of the frame. */
	private static final float BAR_X = 0.1980F;
	private static final float BAR_W = 0.6035F;
	private static final float BAR_Y = 0.0160F;
	private static final float BAR_H = 0.0578F;
	private static final float BAR_RADIUS = 0.0089F;
	private static final int BAR_FILL = 0xFF090C1A;

	/** The active tab: a 100 px slot with a 92 px chip inside it, y 26–74. */
	private static final float TAB_SLOT_X = 0.2000F;
	private static final float TAB_SLOT_W = 0.0500F;
	private static final float CHIP_INSET = 0.0020F;
	private static final float CHIP_Y = 0.0231F;
	private static final float CHIP_H = 0.0436F;
	private static final float CHIP_RADIUS = 0.0098F;
	private static final int CHIP_FILL = 0xFFB8D5E5;
	private static final int CHIP_INK = 0xFF0B1226;

	// ---- the left rail ----------------------------------------------------

	/**
	 * The scroll rail: x 88–133 of the frame, so the track is the {@code VIDEO} pill's own
	 * x and width — 0.0440 to 0.0665 — reused for the {@code AUDIO} track-plus-thumb, which
	 * is what a list longer than its window needs. See the class note on the disagreement.
	 */
	private static final float RAIL_X = 0.0440F;
	private static final float RAIL_W = 0.0225F;
	private static final float RAIL_MIN_THUMB_H = 0.0240F;
	private static final int RAIL_FILL = 0xFF073046;
	private static final int RAIL_THUMB = 0xFFBCC1C5;

	// ---- the heading and the rows ----------------------------------------

	/**
	 * A section heading: ink at x 180, cap 27 px tall. The first line of the list sits here.
	 */
	private static final float HEADING_X = 0.0895F;
	private static final int HEADING_INK = 0xFFFFFFFF;

	/**
	 * The list: heading ink at x 180, row label ink at x 206, and a row pitch of 96 capture
	 * pixels. The list starts at the heading's measured y so the first heading is where the
	 * frame puts it; see the class note on the 0.0755 first gap that a scrolling list cannot
	 * keep.
	 */
	private static final float LIST_Y = 0.1618F;
	private static final float ROW_X = 0.1030F;
	private static final float LIST_PITCH = 0.0853F;

	/**
	 * The tick bar's top, 33 capture pixels below the row's label ink — a third of the row
	 * pitch, so it lands under the value.
	 *
	 * <p>It is kept as the fraction of the window it was measured as, which is this class's
	 * rule for chrome, and not converted to the text's own scale. The consequence is worth
	 * stating rather than tuning away: this build draws the row's text about two and a half
	 * times the share of the window the capture gives it, so the bar sits nearer the value
	 * than Fortnite leaves it, and a label with a descender just touches it. Moving the bar
	 * would replace a measurement with a number chosen to look right, which is the trade
	 * {@code AGENTS.md} asks not to make silently.
	 */
	private static final float ROW_TICK_DY = 0.0293F;
	private static final int LABEL_FOCUS_INK = 0xFFFFFFFF;
	private static final int LABEL_IDLE_INK = 0xFF9AA7AD;
	private static final int VALUE_INK = 0xFFE3E3E5;

	/**
	 * How many list lines fit between the top of the list and the buttons: 0.9253 − 0.1618
	 * over a 0.0853 pitch is 8.9, so eight. The ninth would sit under {@code BACK}.
	 */
	private static final int VISIBLE_LINES = 8;

	/** How much clear space a row's label leaves before the chevron reaches into it. */
	private static final float LABEL_GAP_X = 0.0060F;

	/**
	 * The control column: the tick bar's span, x 654–992, which is also where the value text
	 * is centred.
	 *
	 * <p>Every value in the capture has its ink centre within half a pixel of x 823, and
	 * (654 + 992) / 2 is 823 — so the column is one measurement doing two jobs rather than
	 * two numbers that happen to be near each other. The solid sliders run wider than this,
	 * from x 610, but no row here draws one and the ticks are what the column has to fit.
	 */
	private static final float CONTROL_X = 0.3270F;
	private static final float CONTROL_W = 0.1690F;
	private static final float TICK_H = 0.0080F;
	private static final float TICK_FILL = 0.84F;
	private static final int TICK_ON = 0xFFFFFFFF;
	private static final int TICK_OFF = 0xFF70747D;

	/** The focused row's box: x 170–1048, y 233–322, corner radius 24, stroke 4. */
	private static final float FOCUS_X = 0.0850F;
	private static final float FOCUS_W = 0.4390F;
	private static final float FOCUS_UP = 0.0302F;
	private static final float FOCUS_H = 0.0791F;
	private static final float FOCUS_RADIUS = 0.0213F;
	private static final float FOCUS_STROKE = 0.0036F;

	/**
	 * The focus stroke's colour, and the wrinkle that will not close.
	 *
	 * <p>The stroke's core reads {@code #CCD9FD} down the left of the box and
	 * {@code #A5B6C6} down the right, and both the top and bottom bars are lighter where they
	 * turn than where they run. No single translucent colour over this backdrop produces
	 * that — the algebra does not close, checked channel by channel — so it is either a
	 * gradient stroke or a bevel, and two frames cannot say which. The bluest reading is used
	 * because the capture reads as a light-blue outline and this is what makes it read that
	 * way.
	 */
	private static final int FOCUS_INK = 0xFFC6D6F0;

	/** The focused row's ‹ and ›: 14 × 21, sitting 18 px clear of the tick bar. */
	private static final float CHEVRON_INSET = 0.0090F;
	private static final float CHEVRON_W = 0.0070F;
	private static final float CHEVRON_H = 0.0187F;
	private static final int CHEVRON_INK = 0xFFFFFFFF;

	// ---- the information panel -------------------------------------------

	/** Text at x 1138, wrapped inside the 741 px of column the capture fills. */
	private static final float PANEL_X = 0.5690F;
	private static final float PANEL_W = 0.3705F;
	private static final float PANEL_TITLE_Y = 0.1556F;
	private static final float PANEL_BODY_Y = 0.2124F;
	private static final float PANEL_BODY_PITCH = 0.0307F;

	/**
	 * The body box's own height. It stops 0.0120 of the window above the option list, which
	 * is the whole point of it existing: a paragraph is now clipped (and scrolled) inside a
	 * fixed box, so a long description can never meet the list below it. Before this the
	 * description was split to the panel width and drawn line after line with no ceiling,
	 * and a long one ran into the options — the collision the captures were pulled up to fix.
	 */
	private static final float PANEL_BODY_H = 0.1863F;
	private static final int PANEL_TITLE_INK = 0xFFFFFFFF;
	private static final int PANEL_BODY_INK = 0xFFC1C1C3;

	/** The option list: a 449 × 42 bar, rows pitched 41.5 px, text ink at x 1171. */
	private static final float OPTION_Y = 0.4107F;
	private static final float OPTION_PITCH = 0.0369F;
	private static final float OPTION_BAR_W = 0.2245F;
	private static final float OPTION_BAR_H = 0.0373F;
	private static final float OPTION_TEXT_X = 0.5855F;

	/** The caret on the current option: ink at x 1154, 11 pixels wide and a cap tall. */
	private static final float OPTION_CARET_X = 0.5770F;
	private static final float OPTION_CARET_W = 0.0055F;
	private static final float OPTION_CARET_H = 0.0160F;
	private static final int OPTION_BAR_INK = 0xFFFFFFFF;
	private static final int OPTION_CURRENT_INK = 0xFF041033;
	private static final int OPTION_IDLE_INK = 0xFFFFFFFF;

	/**
	 * How many options the panel lists at once. The capture's longest list is eleven
	 * ({@code Frame Rate Limit}), but that is a value chosen from a small set; a lifetime in
	 * milliseconds has sixty steps and printing them all would bury the panel. Seven is what
	 * fits between the body box and the buttons with room to spare, so a setting with more
	 * steps than that shows a window around the current one.
	 */
	private static final int OPTION_MAX_ROWS = 7;

	// ---- the buttons ------------------------------------------------------

	/** 50 px tall, right edge at x 1912, 33 px apart, 24 px of padding each end. */
	private static final float BUTTON_Y = 0.9253F;
	private static final float BUTTON_H = 0.0444F;
	private static final float BUTTON_RIGHT = 0.9560F;
	private static final float BUTTON_GAP = 0.0165F;
	private static final float BUTTON_PAD = 0.0128F;

	/**
	 * The key badge's height, 23 capture pixels — but never less than a line of this font.
	 *
	 * <p>The capture's badge is 23 pixels around a 17-pixel cap, which is three pixels of
	 * padding a side. At this window the 23 capture pixels are about 7 GUI pixels, and the key
	 * drawn at the game's own cap is 7 pixels of ink plus a shadow on its own, so the measured
	 * height is short of the thing it has to hold and used to clip the key to a dark blob. A
	 * line box is the smallest box that cannot do that; {@link #pill} then centres the badge on
	 * the line rather than growing the pill's own pitch, so the row's height is still the
	 * frame's.
	 */
	private static final float BADGE_H = 0.0204F;

	/**
	 * The clear space a key badge leaves on each side of its key, in GUI pixels.
	 *
	 * <p>Read off the capture and converted at the text's scale rather than the window's: the
	 * single-letter {@code A} and {@code R} badges are 24 capture pixels wide around a key of
	 * about 11, so 6.5 capture pixels each side, and the smallest cap in the frame is 17 — 6.5
	 * over 17 of a cap, which at this screen's 1× cap is 2.7, rounded to 3. See
	 * {@link #badgeWidth} for why the badge's own measured width cannot be used instead.
	 */
	private static final int BADGE_TEXT_PAD = 3;

	private static final float BADGE_GAP = 0.0110F;
	private static final int BUTTON_FILL = 0xFF566196;
	private static final int BUTTON_INK = 0xFF02030C;
	private static final int BADGE_FILL = 0xFF0F0D1B;
	private static final int BADGE_INK = 0xFFFFFFFF;

	/** The frame-rate readout, the only thing in the bottom-left: ink at x 80, cap 14 px. */
	private static final float FPS_X = 0.0400F;
	private static final float FPS_Y = 0.9253F;
	private static final int FPS_INK = 0xFF85919F;

	// ---- the decorative arrows -------------------------------------------

	/**
	 * The faint arrows scattered in the backdrop: right-pointing solid triangles.
	 *
	 * <p>Four were measured — a pair at the top-left (a 51 × 53 at x 58, y 517, and a
	 * 13 × 13 at x 114, y 492), a 27 × 27 at x 1733, y 273 read off a zoomed crop of the
	 * top-right, and a 31 × 28 at x 1190, y 919 from a scan of the bottom-middle. The frame
	 * has more of them than are worth enumerating and this is not all of them: it is the set
	 * that was read off the capture rather than a reconstruction of whatever pattern produced
	 * it. Each entry is {@code {centre x, centre y, width, height}} as fractions, and the
	 * silhouette comes from the top-left pair, which is the only one large enough to read
	 * without zooming.
	 */
	private static final float[][] CHEVRONS = {
			{ 0.0420F, 0.4827F, 0.0255F, 0.0471F },
			{ 0.0600F, 0.4427F, 0.0065F, 0.0116F },
			{ 0.8730F, 0.2533F, 0.0135F, 0.0240F },
			{ 0.6025F, 0.8284F, 0.0155F, 0.0249F }
	};

	/**
	 * What an arrow multiplies the backdrop by.
	 *
	 * <p>Measured twice, in two very different parts of the frame: over the bright teal at
	 * the top-left an arrow reads {@code #176289} on {@code #0D4560}, and over the dark navy
	 * at the bottom one reads {@code #15162B} on {@code #0F1022}. The first is a lift of
	 * +10/+29/+41 and the second of +6/+6/+9 — different lifts, but as a <em>ratio</em> both
	 * are the same number, about 1.42. So the arrows are a screen blend, which a 2D GUI has
	 * no mode for; the nearest a plain fill can do is take the local backdrop, multiply it,
	 * and draw the result opaque. The backdrop moves by a few units across any one arrow, so
	 * sampling it once at the arrow's centre costs nothing visible.
	 */
	private static final float CHEVRON_GAIN = 1.42F;

	// ---- text sizes, as ratios of the capture's smallest cap -------------

	/**
	 * The smallest cap height in the capture, in capture pixels: the tab name and the two
	 * button labels, both 17. Every other size on this screen is that element's measured cap
	 * divided by this one, drawn at that multiple of Minecraft's own font — so the ratios are
	 * measured and the absolute size is the game's, which is the only part of the two that
	 * can be honoured at every GUI scale.
	 */
	private static final float CAP_ANCHOR = 17.0F;

	/**
	 * The font's ascent: the distance from the top of Minecraft's line box to the baseline, in
	 * unscaled font pixels. This is the constant every vertical text placement on the screen is
	 * built from.
	 *
	 * <p>Checked against the mapped jar rather than remembered. {@code Font.lineHeight} is 9,
	 * written as a literal in its constructor, and the glyph metrics place a bitmap glyph at
	 * {@code getTop() = 7.0F − bearingTop} with a default bearing of 7 — so a glyph's ink starts
	 * on the {@code y} the run is drawn at, and the baseline is seven below it. Nine minus seven
	 * is the two pixels of descent, which is why {@link #textBox} adds a couple of pixels of
	 * slack under every line it clips. The capture agrees independently: the row labels are
	 * drawn at y 0.1618 of the window and their cap tops land on exactly that pixel row, with a
	 * cap seven font pixels tall.
	 */
	private static final float FONT_ASCENT = 7.0F;

	private static final float CAP_TAB = 17.0F;
	private static final float CAP_BODY = 18.0F;
	private static final float CAP_VALUE = 19.5F;
	private static final float CAP_LABEL = 21.5F;
	private static final float CAP_PANEL_TITLE = 22.0F;
	private static final float CAP_HEADING = 27.0F;
	private static final float CAP_FPS = 14.0F;

	private static final Component TAB_NAME = bold("PING");
	private static final Component BACK = bold("BACK");
	private static final Component RESTORE = bold("RESTORE TO DEFAULT");

	/** How many segments the tick bar draws before it stops subdividing. */
	private static final int TICK_SEGMENTS = 12;

	private static final String ON = "ON";
	private static final String OFF = "OFF";

	// ---- the marquee ------------------------------------------------------

	/**
	 * How long a too-long string rests at the start (and, at the end of its travel, before it
	 * snaps back). 900 ms of stillness to start reading, then a slow slide, then stillness
	 * again, matches the unhurried pace of the captures without ever letting the text run
	 * off its box.
	 */
	private static final int MARQUEE_PAUSE_MS = 900;

	/** How slowly the marquee slides: milliseconds per pixel, so larger is slower. */
	private static final float MARQUEE_MS_PER_PIXEL = 28.0F;

	/** A little empty space to scroll past the far edge, so the last glyph clears it. */
	private static final int MARQUEE_TRAIL = 14;

	// ---- the settings -----------------------------------------------------

	/** The group heading a setting is filed under, drawn as the first line of its group. */
	private enum Group {
		GAMEPLAY("GAMEPLAY"),
		DISPLAY("DISPLAY"),
		MARKERS("MARKERS"),
		BEAM("BEAM");

		private final String heading;

		Group(String heading) {
			this.heading = heading;
		}

		String heading() {
			return this.heading;
		}
	}

	/** How a setting's value is chosen. */
	private enum Kind {
		TOGGLE,
		NUMBER,
		COLOUR
	}

	/**
	 * The rows, in the order they are drawn.
	 *
	 * <p>Each entry holds the config accessors rather than a copy of the value, which is what
	 * makes "read it every frame" the only thing this class can do: the screen and
	 * {@code config/ping.json} cannot disagree, because there is nothing here to disagree
	 * with.
	 *
	 * <p>A number carries its own range and step, and every write passes through
	 * {@link PingConfig}, which clamps a second time against a hand-edited file. A colour is a
	 * number with a fixed palette rather than a free picker: the palette is the set the code
	 * already names — the author's own colour ({@code 0}), the {@code #E8383D} the danger
	 * marker has always been, and the measured Fortnite beam cyan {@code #00B1EF}.
	 */
	private enum Setting {
		CHAT(Group.GAMEPLAY, "Pings Over Chat",
				"Whether a placed ping is broadcast to other players running the mod. Pings "
						+ "reach them through chat — the only channel that works with nothing "
						+ "installed server-side — so with this on, a ping is a real chat message "
						+ "that anyone on the server can see.",
				() -> PingConfig.sendPingsOverChat() ? 1.0D : 0.0D,
				value -> PingConfig.setSendPingsOverChat(value != 0.0D)),

		FEED(Group.GAMEPLAY, "Ping Feed",
				"Whether pinged players are listed in the message feed at the bottom-left of "
						+ "the screen, the way they are in Fortnite.",
				() -> PingConfig.showPingFeed() ? 1.0D : 0.0D,
				value -> PingConfig.setShowPingFeed(value != 0.0D)),

		COOLDOWN(Group.GAMEPLAY, "Mark Cooldown",
				"A short gap after a marker is placed in which the mark button does nothing, "
						+ "which Fortnite documents. Its existence is sourced; the 500 ms length "
						+ "is a tuned estimate, not a measurement. It never swallows the second "
						+ "tap of a double tap, or the danger marker could never be placed.",
				() -> (double) PingConfig.remarkCooldownMs(),
				value -> PingConfig.setRemarkCooldownMs(Math.round(value)),
				0.0D, 2_000.0D, 100.0D, 0, 0.001D, " S"),

		MINIMAP(Group.DISPLAY, "Minimap",
				"Whether the corner minimap is drawn. The map key opens the full-size map "
						+ "either way, and that screen carries its own measurements.",
				() -> PingConfig.showMinimap() ? 1.0D : 0.0D,
				value -> PingConfig.setShowMinimap(value != 0.0D)),

		HEALTH(Group.DISPLAY, "Fortnite Health",
				"Whether health is drawn as Fortnite's green health bar and blue shield bar "
						+ "instead of vanilla red hearts. Off restores the vanilla hearts, and "
						+ "the switch is read every frame, so it takes effect without a restart.",
				() -> PingConfig.fortniteHealth() ? 1.0D : 0.0D,
				value -> PingConfig.setFortniteHealth(value != 0.0D)),

		LIFETIME(Group.MARKERS, "Marker Lifetime",
				"45 s is the only lifetime with a real measurement behind it: a player-measured "
						+ "report has a pinged item lasting exactly that long, and notes that a "
						+ "shorter life means somebody picked the item up. Location markers reuse "
						+ "the measured number rather than inventing a second one to disagree "
						+ "with it. An earlier build used two minutes, which read as permanent.",
				() -> (double) PingConfig.markerLifetimeMs(),
				value -> PingConfig.setMarkerLifetimeMs(Math.round(value)),
				5_000.0D, 300_000.0D, 5_000.0D, 0, 0.001D, " S"),

		THREAT_LIFETIME(Group.MARKERS, "Threat Lifetime",
				"How long an enemy or danger marker lives. The one number here with no source "
						+ "behind it: it was chosen to feel right next to the 45 s markers, and "
						+ "is flagged as unsourced rather than presented as measured.",
				() -> (double) PingConfig.threatLifetimeMs(),
				value -> PingConfig.setThreatLifetimeMs(Math.round(value)),
				1_000.0D, 120_000.0D, 1_000.0D, 0, 0.001D, " S"),

		MARKER_SIZE(Group.MARKERS, "Marker Size",
				"The pin's height as a share of the screen height. Measured from Fortnite at "
						+ "2.7%, and a share rather than a pixel count so it holds at every "
						+ "resolution and GUI scale. Below about 1% the pin stops reading as a "
						+ "marker; past 8% it swamps whatever it points at.",
				() -> PingConfig.markerHeightFraction(),
				PingConfig::setMarkerHeightFraction,
				0.005D, 0.10D, 0.001D, 1, 100.0D, "%"),

		MARKER_LIFT(Group.MARKERS, "Marker Lift",
				"How far the pin floats above the marked point, as a fraction of its own "
						+ "height. A tuned choice, not a measured one: Fortnite's vertical "
						+ "anchor could not be pinned down from the captures. At 0.00 the tip "
						+ "sits on the point.",
				PingConfig::markerLift,
				PingConfig::setMarkerLift,
				0.0D, 1.0D, 0.05D, 2, 1.0D, ""),

		OCCLUDED(Group.MARKERS, "Occluded Opacity",
				"How visible a marker stays when it is behind terrain and you are not looking "
						+ "at it. Fortnite shows world markers through everything, so this is a "
						+ "ghost rather than a hide: the earlier 0.10 faded a marker behind a "
						+ "hill until it read as having vanished.",
				PingConfig::occludedAlpha,
				PingConfig::setOccludedAlpha,
				0.0D, 1.0D, 0.05D, 0, 100.0D, "%"),

		THREAT_COLOUR(Group.MARKERS, "Threat Colour",
				"Enemy and danger markers are forced to this colour the way Fortnite's danger "
						+ "marker is red regardless of who placed it. Author Colour lets threats "
						+ "wear the sender's own locator-bar colour like every other marker.",
				() -> (double) PingConfig.threatColour(),
				value -> PingConfig.setThreatColour((int) Math.round(value)),
				new int[] { 0, 0xFFE8383D, 0xFF00B1EF },
				new String[] { "AUTHOR COLOUR", "DANGER RED", "FORTNITE CYAN" }),

		BEAM_COLOUR(Group.BEAM, "Beam Colour",
				"The colour of the world beam over a marker. Fortnite's beam contains no red — "
						+ "it is always blue-cyan regardless of the marker — but this build "
						+ "tints it to the author by default. Marker Colour follows the marker; "
						+ "Fortnite Cyan is the measured #00B1EF.",
				() -> (double) PingConfig.beamColour(),
				value -> PingConfig.setBeamColour((int) Math.round(value)),
				new int[] { 0, 0xFF00B1EF },
				new String[] { "MARKER COLOUR", "FORTNITE CYAN" }),

		BEAM_NEAR(Group.BEAM, "Beam Fade Near",
				"The distance at which the large beam begins to fade in as you back away from "
						+ "a marker. The captures could not settle this pair, so the values are "
						+ "working ones chosen to look right, not measurements.",
				PingConfig::beamFadeNear,
				PingConfig::setBeamFadeNear,
				0.0D, 256.0D, 5.0D, 0, 1.0D, " M"),

		BEAM_FAR(Group.BEAM, "Beam Fade Far",
				"The distance beyond which the large beam is fully visible. Below Beam Fade "
						+ "Near the beam becomes a step rather than a ramp, which is why the "
						+ "band is best given some width.",
				PingConfig::beamFadeFar,
				PingConfig::setBeamFadeFar,
				0.0D, 256.0D, 5.0D, 0, 1.0D, " M");

		private final Group group;
		private final String label;
		private final String description;
		private final DoubleSupplier read;
		private final DoubleConsumer write;
		private final Kind kind;
		private final double min;
		private final double max;
		private final double step;
		private final int decimals;
		private final double displayScale;
		private final String suffix;
		private final int[] palette;
		private final String[] paletteNames;

		Setting(Group group, String label, String description, DoubleSupplier read, DoubleConsumer write) {
			this(group, label, description, read, write, Kind.TOGGLE, 0.0D, 1.0D, 1.0D, 0, 1.0D, "", null, null);
		}

		Setting(Group group, String label, String description, DoubleSupplier read, DoubleConsumer write,
				double min, double max, double step, int decimals, double displayScale, String suffix) {
			this(group, label, description, read, write, Kind.NUMBER, min, max, step, decimals, displayScale,
					suffix, null, null);
		}

		Setting(Group group, String label, String description, DoubleSupplier read, DoubleConsumer write,
				int[] palette, String[] paletteNames) {
			this(group, label, description, read, write, Kind.COLOUR, 0.0D, palette.length - 1.0D, 1.0D, 0, 1.0D,
					"", palette, paletteNames);
		}

		Setting(Group group, String label, String description, DoubleSupplier read, DoubleConsumer write,
				Kind kind, double min, double max, double step, int decimals, double displayScale, String suffix,
				int[] palette, String[] paletteNames) {
			this.group = group;
			this.label = label;
			this.description = description;
			this.read = read;
			this.write = write;
			this.kind = kind;
			this.min = min;
			this.max = max;
			this.step = step;
			this.decimals = decimals;
			this.displayScale = displayScale;
			this.suffix = suffix;
			this.palette = palette;
			this.paletteNames = paletteNames;
		}

		Group group() {
			return this.group;
		}

		String label() {
			return this.label;
		}

		String description() {
			return this.description;
		}

		/** The value the config holds right now, not a copy of it. */
		double value() {
			return this.read.getAsDouble();
		}

		/** How many positions this setting's control has. */
		int count() {
			switch (this.kind) {
				case TOGGLE -> {
					return 2;
				}
				case COLOUR -> {
					return this.palette.length;
				}
				default -> {
					return (int) Math.round((this.max - this.min) / this.step) + 1;
				}
			}
		}

		/** Where the value sits among {@link #count()} positions. */
		int position() {
			switch (this.kind) {
				case TOGGLE -> {
					return this.value() != 0.0D ? 0 : 1;
				}
				case COLOUR -> {
					int found = paletteIndex((int) Math.round(this.value()));

					return found >= 0 ? found : 0;
				}
				default -> {
					return (int) Math.round((this.value() - this.min) / this.step);
				}
			}
		}

		/** Move the value to one of its positions, clamped to the range. */
		void applyPosition(int position) {
			int clamped = Math.max(0, Math.min(count() - 1, position));

			switch (this.kind) {
				case TOGGLE -> this.write.accept(clamped == 0 ? 1.0D : 0.0D);
				case COLOUR -> this.write.accept(this.palette[clamped]);
				default -> this.write.accept(this.min + clamped * this.step);
			}
		}

		/** Step the value one position, staying inside the range. */
		void step(int direction) {
			applyPosition(position() + direction);
		}

		/**
		 * The value the ‹ key moves to. For a boolean that is OFF, which is the reading the
		 * previous revision gave this key ({@code set(false)}); a number simply steps down.
		 */
		void left() {
			if (this.kind == Kind.TOGGLE) {
				applyPosition(1);

				return;
			}

			step(-1);
		}

		/** The value the › key moves to: ON for a boolean, one step up for a number. */
		void right() {
			if (this.kind == Kind.TOGGLE) {
				applyPosition(0);

				return;
			}

			step(1);
		}

		/** What a click or Enter does: a boolean flips, a number advances one step. */
		void advance() {
			if (this.kind == Kind.TOGGLE) {
				applyPosition(1 - position());

				return;
			}

			step(1);
		}

		/** The label of one position, which is what the option list prints. */
		String labelAt(int position) {
			int clamped = Math.max(0, Math.min(count() - 1, position));

			switch (this.kind) {
				case TOGGLE -> {
					return clamped == 0 ? ON : OFF;
				}
				case COLOUR -> {
					return this.paletteNames[clamped];
				}
				default -> {
					return formatValue(this.min + clamped * this.step);
				}
			}
		}

		/** The value as it is printed on the row. */
		String display() {
			switch (this.kind) {
				case TOGGLE -> {
					return this.value() != 0.0D ? ON : OFF;
				}
				case COLOUR -> {
					int found = paletteIndex((int) Math.round(this.value()));

					return found >= 0 ? this.paletteNames[found] : hex((int) Math.round(this.value()));
				}
				default -> {
					return formatValue(this.value());
				}
			}
		}

		/**
		 * The positions the option list should show — all of them when the setting has few,
		 * otherwise a window around the current one so the list never grows past
		 * {@link #OPTION_MAX_ROWS}.
		 */
		int[] optionPositions() {
			int total = count();

			if (total <= OPTION_MAX_ROWS) {
				int[] all = new int[total];

				for (int i = 0; i < total; i++) {
					all[i] = i;
				}

				return all;
			}

			int current = Math.max(0, Math.min(total - 1, position()));
			int start = Math.max(0, Math.min(total - OPTION_MAX_ROWS, current - OPTION_MAX_ROWS / 2));
			int[] window = new int[OPTION_MAX_ROWS];

			for (int i = 0; i < OPTION_MAX_ROWS; i++) {
				window[i] = start + i;
			}

			return window;
		}

		private int paletteIndex(int colour) {
			for (int i = 0; i < this.palette.length; i++) {
				if (this.palette[i] == colour) {
					return i;
				}
			}

			return -1;
		}

		private String formatValue(double value) {
			return String.format(Locale.ROOT, "%." + this.decimals + "f", value * this.displayScale) + this.suffix;
		}

		private static String hex(int colour) {
			return String.format(Locale.ROOT, "#%06X", colour & 0xFFFFFF);
		}
	}

	/** {@link Setting#values()} hands out a fresh copy, and the rows are walked every frame. */
	private static final Setting[] SETTINGS = Setting.values();

	/** One line of the list: either a section heading or a setting, never both. */
	private record Line(String heading, int setting) {
		boolean isHeading() {
			return this.heading != null;
		}
	}

	/** The list flattened to lines, each group introduced by its heading. */
	private static final Line[] LINES = buildLines();

	/** The index of the setting the focus box is drawn around. */
	private int focus;

	/** The first line drawn in the window; the list scrolls when the focus moves past the edge. */
	private int scroll;

	public PingSettingsScreen() {
		super(Component.literal("Ping settings"));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int width = this.width;
		int height = this.height;
		long now = System.currentTimeMillis();

		backdrop(graphics, width, height);
		tabBar(graphics, width, height, now);
		rail(graphics, width, height);
		rows(graphics, width, height, now);
		panel(graphics, width, height, now);
		buttons(graphics, width, height, now);
		readout(graphics, width, height, now);

		// Nothing is registered with init() today, but widget render states only ever run from
		// here — this stays so a widget added later cannot be silently invisible.
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	// ---- the backdrop -----------------------------------------------------

	/**
	 * The window-filling backdrop, as vertical strips.
	 *
	 * <p>Each strip runs one {@code fillGradient} from the sampled top row to the sampled
	 * middle row and a second from there to the bottom row, so two calls reproduce a field
	 * that is not monotonic in either axis on its own. The strip takes its colour at its
	 * centre rather than its edge, which is the difference between a staircase that biases
	 * one way and one that does not.
	 */
	private void backdrop(GuiGraphicsExtractor graphics, int width, int height) {
		int middle = span(PROFILE_MID_Y, height);

		for (int strip = 0; strip < BACKDROP_STRIPS; strip++) {
			int left = strip * width / BACKDROP_STRIPS;
			int right = (strip + 1) * width / BACKDROP_STRIPS;
			float at = (strip + 0.5F) / BACKDROP_STRIPS;

			graphics.fillGradient(left, 0, right, middle, profile(PROFILE_TOP, at), profile(PROFILE_MID, at));
			graphics.fillGradient(left, middle, right, height, profile(PROFILE_MID, at), profile(PROFILE_BOTTOM, at));
		}

		for (float[] chevron : CHEVRONS) {
			int across = Math.max(1, span(chevron[2], width));
			int down = Math.max(1, span(chevron[3], height));

			arrow(graphics, span(chevron[0], width) - across / 2, span(chevron[1], height) - down / 2, across, down,
					gain(backdropAt(chevron[0], chevron[1]), CHEVRON_GAIN));
		}
	}

	/** One sampled row of the backdrop, interpolated between its columns. */
	private static int profile(int[] row, float fraction) {
		int index = 0;

		while (index < PROFILE_X.length - 2 && fraction > PROFILE_X[index + 1]) {
			index++;
		}

		float across = PROFILE_X[index + 1] - PROFILE_X[index];
		float t = across <= 0.0F ? 0.0F : (fraction - PROFILE_X[index]) / across;

		return blend(row[index], row[index + 1], clamp(t));
	}

	/** The backdrop at one point, interpolated across the two gaps the strips are drawn in. */
	private static int backdropAt(float x, float y) {
		int top = profile(PROFILE_TOP, x);
		int middle = profile(PROFILE_MID, x);
		int bottom = profile(PROFILE_BOTTOM, x);

		if (y <= PROFILE_MID_Y) {
			return blend(top, middle, clamp(y / PROFILE_MID_Y));
		}

		return blend(middle, bottom, clamp((y - PROFILE_MID_Y) / (1.0F - PROFILE_MID_Y)));
	}

	// ---- the chrome -------------------------------------------------------

	/**
	 * The tab bar and its one chip.
	 *
	 * <p>The capture's bar is 1207 pixels wide and holds twelve chips on a 100 pixel pitch.
	 * The bar is drawn at its measured span and the chips are not — this mod has one settings
	 * screen, and inventing eleven more would be inventing eleven screens. The chip is
	 * therefore the first slot's, which is exactly where the capture's own active tab sits.
	 */
	private void tabBar(GuiGraphicsExtractor graphics, int width, int height, long now) {
		roundRect(graphics, span(BAR_X, width), span(BAR_Y, height), span(BAR_W, width), span(BAR_H, height),
				span(BAR_RADIUS, height), BAR_FILL);

		int chipX = span(TAB_SLOT_X + CHIP_INSET, width);
		int chipY = span(CHIP_Y, height);
		int chipW = span(TAB_SLOT_W, width) - 2 * span(CHIP_INSET, width);
		int chipH = span(CHIP_H, height);

		roundRect(graphics, chipX, chipY, chipW, chipH, span(CHIP_RADIUS, height), CHIP_FILL);

		// The name is held by the chip, so it centres on the chip's own line rather than
		// hanging from its top edge: the capture's chip is 48 pixels around a 17-pixel cap and
		// its name sits in the middle of that, which is what stops the caps from touching the
		// slab's rim.
		float scale = capScale(CAP_TAB);

		boxedCentred(graphics, TAB_NAME, chipX, chipW, centredTop(chipY, chipH, scale), textBox(CAP_TAB), CHIP_INK,
				scale, false, now);
	}

	/**
	 * The left rail: the list's scrollbar.
	 *
	 * <p>See the class note on the two frames disagreeing. The track covers exactly the
	 * visible list, and the thumb's length is the visible fraction of the whole list, so a
	 * thumb with nowhere to move means the list already fits.
	 */
	private void rail(GuiGraphicsExtractor graphics, int width, int height) {
		int x = span(RAIL_X, width);
		int w = span(RAIL_W, width);
		int trackY = span(LIST_Y, height);
		int trackH = span(LIST_PITCH * VISIBLE_LINES, height);

		roundRect(graphics, x, trackY, w, trackH, w / 2, RAIL_FILL);

		int total = LINES.length;
		int visible = Math.min(VISIBLE_LINES, total);
		int thumbH = Math.min(trackH, Math.max(span(RAIL_MIN_THUMB_H, height), trackH * visible / total));
		int travel = trackH - thumbH;
		int maxScroll = Math.max(1, total - visible);
		int thumbY = trackY + (int) Math.round((double) travel * this.scroll / maxScroll);

		roundRect(graphics, x, thumbY, w, thumbH, w / 2, RAIL_THUMB);
	}

	/**
	 * The rows: a section heading, or a label on the left and its value and tick bar in the
	 * control column. Only the lines inside the scroll window are drawn.
	 */
	private void rows(GuiGraphicsExtractor graphics, int width, int height, long now) {
		int controlX = span(CONTROL_X, width);
		int controlW = span(CONTROL_W, width);
		int labelX = span(ROW_X, width);
		int labelRight = controlX - span(CHEVRON_INSET + CHEVRON_W, width) - span(LABEL_GAP_X, width);
		int labelW = Math.max(1, labelRight - labelX);
		int listY = span(LIST_Y, height);
		int pitch = span(LIST_PITCH, height);

		for (int slot = 0; slot < VISIBLE_LINES; slot++) {
			int line = this.scroll + slot;

			if (line >= LINES.length) {
				break;
			}

			int y = listY + slot * pitch;
			Line entry = LINES[line];

			if (entry.isHeading()) {
				boxedText(graphics, entry.heading(), span(HEADING_X, width), y, labelW, textBox(CAP_HEADING),
						HEADING_INK, now, capScale(CAP_HEADING), false, true);

				continue;
			}

			Setting setting = SETTINGS[entry.setting()];
			boolean focused = entry.setting() == this.focus;
			float labelScale = capScale(CAP_LABEL);
			float valueScale = capScale(CAP_VALUE);

			if (focused) {
				roundOutline(graphics, span(FOCUS_X, width), y - span(FOCUS_UP, height), span(FOCUS_W, width),
						span(FOCUS_H, height), span(FOCUS_RADIUS, height), stroke(height), FOCUS_INK);
			}

			boxedText(graphics, setting.label(), labelX, y, labelW, textBox(CAP_LABEL),
					focused ? LABEL_FOCUS_INK : LABEL_IDLE_INK, now, labelScale, false, true);

			// The value is a smaller cap than its label, so it starts lower for the two to end
			// on one baseline — and the tick bar below it is the row's one chrome element, left
			// where the capture measured it.
			int valueY = shareBaseline(y, labelScale, valueScale);

			boxedCentred(graphics, literal(setting.display()), controlX, controlW, valueY, textBox(CAP_VALUE),
					VALUE_INK, valueScale, true, now);

			// The chevrons belong to the row the keyboard is on, as they do in the capture:
			// they are what says this row's value is the one the arrows will move.
			if (focused) {
				chevrons(graphics, width, height, controlX, controlW, valueY, now);
			}

			bar(graphics, controlX, y + span(ROW_TICK_DY, height), controlW, span(TICK_H, height), setting);
		}
	}

	/** The ‹ and › either side of the focused value. */
	private void chevrons(GuiGraphicsExtractor graphics, int width, int height, int controlX, int controlW, int valueY,
			long now) {
		int chevronW = span(CHEVRON_W, width);
		int chevronH = span(CHEVRON_H, height);

		// Centred on the value's ink rather than on its line box: the box carries two pixels of
		// descent the value never uses, and centring on it hangs the chevrons a pixel and a half
		// below the digits they point at.
		int chevronY = Math.round(valueY + (inkHeight(capScale(CAP_VALUE)) - chevronH) / 2.0F);

		arrowLeft(graphics, controlX - span(CHEVRON_INSET, width) - chevronW, chevronY, chevronW, chevronH,
				CHEVRON_INK);
		arrow(graphics, controlX + controlW + span(CHEVRON_INSET, width), chevronY, chevronW, chevronH, CHEVRON_INK);
	}

	/**
	 * The segmented tick bar under a value.
	 *
	 * <p>The capture uses one of these for every value chosen from a list — three positions
	 * for Window Mode and Rendering Mode, eight for Resolution, eleven for Frame Rate Limit,
	 * two for Vsync and for Sound Quality — with the current position drawn white and the
	 * rest {@code #70747D}. So the count is the number of options and a boolean's is two. The
	 * ratio of a segment to its slot measured 0.81 at three positions, 0.84 at eight and 0.85
	 * at eleven; {@link #TICK_FILL} is the eight-to-eleven reading, because two positions is
	 * nearer those than it is to three.
	 *
	 * <p>A number with more positions than the bar has segments subdivides no further: the
	 * bar draws {@link #TICK_SEGMENTS} segments and lights the one nearest the value's share
	 * of the range, which keeps a sixty-step lifetime from becoming sixty hairline ticks.
	 */
	private void bar(GuiGraphicsExtractor graphics, int x, int y, int width, int height, Setting setting) {
		int total = setting.count();
		int segments = Math.min(total, TICK_SEGMENTS);
		int slot = width / segments;
		int fill = Math.max(1, Math.round(slot * TICK_FILL));
		int active = setting.position();
		int lit = total <= 1 ? 0 : Math.round(active * (segments - 1) / (float) (total - 1));

		for (int index = 0; index < segments; index++) {
			int left = x + index * slot + (slot - fill) / 2;

			graphics.fill(left, y, left + fill, y + height, index == lit ? TICK_ON : TICK_OFF);
		}
	}

	/** The information panel: what the focused setting does, then its options. */
	private void panel(GuiGraphicsExtractor graphics, int width, int height, long now) {
		Setting setting = SETTINGS[this.focus];
		int x = span(PANEL_X, width);

		boxedText(graphics, setting.label(), span(PANEL_X, width), span(PANEL_TITLE_Y, height),
				span(PANEL_W, width), textBox(CAP_PANEL_TITLE), PANEL_TITLE_INK, now, capScale(CAP_PANEL_TITLE),
				false, true);

		boxedParagraph(graphics, setting.description(), x, span(PANEL_BODY_Y, height), span(PANEL_W, width),
				span(PANEL_BODY_H, height), PANEL_BODY_INK, capScale(CAP_BODY), span(PANEL_BODY_PITCH, height), now);

		int[] positions = setting.optionPositions();
		int current = setting.position();

		for (int row = 0; row < positions.length; row++) {
			option(graphics, width, height, row, setting.labelAt(positions[row]), positions[row] == current, now);
		}
	}

	/** One line of the option list. The current one sits on the capture's white bar. */
	private void option(GuiGraphicsExtractor graphics, int width, int height, int row, String label, boolean current,
			long now) {
		int barX = span(PANEL_X, width);
		int barW = span(OPTION_BAR_W, width);
		int barH = span(OPTION_BAR_H, height);
		int y = span(OPTION_Y, height) + row * span(OPTION_PITCH, height);
		float scale = capScale(CAP_BODY);

		// The bar is the row's box, so its line centres on the bar — the capture's option text
		// sits in the middle of its 42-pixel slab, and the current option's own bar is what
		// makes that visible.
		int textY = centredTop(y, barH, scale);

		if (current) {
			graphics.fill(barX, y, barX + barW, y + barH, OPTION_BAR_INK);

			arrow(graphics, span(OPTION_CARET_X, width), y + (barH - span(OPTION_CARET_H, height)) / 2,
					span(OPTION_CARET_W, width), span(OPTION_CARET_H, height), OPTION_CURRENT_INK);
		}

		int textX = span(OPTION_TEXT_X, width);
		int textW = Math.max(1, barX + barW - textX - span(BUTTON_PAD, width));

		if (current) {
			// Dark ink on the white bar reads without a shadow, which the plain helper does not do.
			boxedText(graphics, label, textX, textY, textW, textBox(CAP_BODY), OPTION_CURRENT_INK, now, scale, false,
					false);
		} else {
			boxedText(graphics, label, textX, textY, textW, textBox(CAP_BODY), OPTION_IDLE_INK, now, scale, false, true);
		}
	}

	/**
	 * The two buttons, laid out from the right edge leftwards.
	 *
	 * <p>That is the capture's own rule rather than a convenience. Its {@code RESTORE TO
	 * DEFAULT} and {@code BACK} sit at exactly the same x in both frames, while
	 * {@code APPLY} exists in one — which can only happen if the row is built from the right
	 * and the widths come from the labels.
	 */
	private void buttons(GuiGraphicsExtractor graphics, int width, int height, long now) {
		int y = span(BUTTON_Y, height);
		int pillH = span(BUTTON_H, height);
		int[] row = pillRow(width);

		// row = { back x, back width, restore x, restore width }, right to left.
		pill(graphics, width, height, row[0], y, pillH, row[1], "ESC", BACK, now);
		pill(graphics, width, height, row[2], y, pillH, row[3], "R", RESTORE, now);
	}

	/** The pill row as {@code {BACK x, BACK width, RESTORE x, RESTORE width}}. */
	private int[] pillRow(int width) {
		int back = pillWidth(width, "ESC", BACK);
		int restore = pillWidth(width, "R", RESTORE);
		int backX = span(BUTTON_RIGHT, width) - back;

		return new int[] { backX, back, backX - span(BUTTON_GAP, width) - restore, restore };
	}

	/** A pill is its padding, its key badge, a gap and its label — which is where its width comes from. */
	private int pillWidth(int width, String badge, Component label) {
		float scale = capScale(CAP_TAB);

		return 2 * span(BUTTON_PAD, width) + badgeWidth(badge, scale) + span(BADGE_GAP, width)
				+ Math.round(this.font.width(label) * scale);
	}

	/**
	 * The key badge's width: its key, plus the capture's own padding a side.
	 *
	 * <p>Not the measured badges themselves. The two that exist are 24 capture pixels wide for
	 * the one-letter {@code A} and {@code R} and 46 for {@code ESC} — and the note over them
	 * says what those numbers are: eleven pixels per extra letter <em>of what the key needs at
	 * the capture's own cap height</em>. That derivation does not survive the size substitution
	 * this screen makes everywhere else: the key is drawn at the game's own cap, roughly two and
	 * a half times the share of the window the capture gives it, so a badge held to 24 capture
	 * pixels of window is narrower than the key inside it. It was, and the key was clipped to
	 * the dark blob the capture's badge has no business hiding. The width is therefore the key's
	 * own at this screen's cap and the pill grows to take it, which is the capture's own rule
	 * for the row — its buttons are laid out from the right edge precisely because their widths
	 * come from what they hold.
	 */
	private int badgeWidth(String badge, float scale) {
		return Math.round(this.font.width(badge) * scale) + 2 * BADGE_TEXT_PAD;
	}

	/** One pill: a rounded plate, a dark key badge, and the label in dark caps. */
	private void pill(GuiGraphicsExtractor graphics, int width, int height, int x, int y, int pillH, int pillW,
			String badge, Component label, long now) {
		int pad = span(BUTTON_PAD, width);
		float scale = capScale(CAP_TAB);
		int badgeW = badgeWidth(badge, scale);
		int badgeH = Math.max(span(BADGE_H, height), textBox(CAP_TAB));

		// One line for the whole pill. The key and the label are the same size in two boxes, and
		// a button reads as one thing only if they stand on one baseline: both are placed from
		// the pill's own centred line, and the badge — which is chrome, and is centred on the
		// same ink — is drawn around the key that line puts there.
		int textY = centredTop(y, pillH, scale);
		int badgeY = Math.round(textY + inkHeight(scale) / 2.0F - badgeH / 2.0F);

		roundRect(graphics, x, y, pillW, pillH, pillH / 2, BUTTON_FILL);
		roundRect(graphics, x + pad, badgeY, badgeW, badgeH, stroke(height), BADGE_FILL);
		boxedCentred(graphics, literal(badge), x + pad, badgeW, textY, textBox(CAP_TAB), BADGE_INK, scale, false,
				now);
		boxedText(graphics, label.getString(), x + pad + badgeW + span(BADGE_GAP, width), textY, pillW - pad * 2
				- badgeW - span(BADGE_GAP, width), textBox(CAP_TAB), BUTTON_INK, now, scale, false, false);
	}

	/** The frame-rate readout, which is the one thing the capture puts in that corner. */
	private void readout(GuiGraphicsExtractor graphics, int width, int height, long now) {
		Minecraft client = this.minecraft;

		if (client == null) {
			return;
		}

		// Minecraft publishes one frame count, not Fortnite's minimum and maximum, so the
		// capture's "[119 | 120]" has nothing truthful to put in it and is not drawn. What is
		// kept is where it stands: the readout shares the buttons' corner row, so it is centred
		// on the same 50-pixel band they are and stands on their baseline. It is a smaller cap
		// than a button label, so sharing their baseline rather than their top is the difference
		// between reading as the row's third label and reading as a stray a pixel high.
		float buttonScale = capScale(CAP_TAB);
		float scale = capScale(CAP_FPS);
		int y = shareBaseline(centredTop(span(FPS_Y, height), span(BUTTON_H, height), buttonScale), buttonScale, scale);

		boxedText(graphics, client.getFps() + " FPS", span(FPS_X, width), y, span(PANEL_W, width),
				textBox(CAP_FPS), FPS_INK, now, scale, false, true);
	}

	// ---- input ------------------------------------------------------------

	/**
	 * Hovering a row focuses it, which is the pointer half of the capture's cursor: the focus
	 * box follows whichever row the arrows would move.
	 */
	@Override
	public void mouseMoved(double x, double y) {
		int over = rowAt(x, y);

		if (over >= 0 && over != this.focus) {
			this.focus = over;
			ensureVisible();
		}

		super.mouseMoved(x, y);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		Minecraft client = this.minecraft;

		if (client == null || event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
			return super.mouseClicked(event, doubleClick);
		}

		int[] pillRow = pillRow(this.width);
		int buttonsY = span(BUTTON_Y, this.height);
		int buttonsH = span(BUTTON_H, this.height);

		if (within(pillRow[0], buttonsY, pillRow[1], buttonsH, event.x(), event.y())) {
			this.onClose();

			return true;
		}

		if (within(pillRow[2], buttonsY, pillRow[3], buttonsH, event.x(), event.y())) {
			restore();

			return true;
		}

		// An option in the panel sets that position outright; a row steps to the next one. Both
		// are the capture's behaviour — its rows carry ‹ and › precisely because clicking one
		// moves it — and every write goes through PingConfig, so the file follows immediately.
		int option = optionAt(event.x(), event.y());

		if (option >= 0) {
			SETTINGS[this.focus].applyPosition(option);

			return true;
		}

		int row = rowAt(event.x(), event.y());

		if (row >= 0) {
			this.focus = row;
			ensureVisible();
			SETTINGS[row].advance();

			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY == 0.0D) {
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}

		scrollBy(scrollY > 0.0D ? -1 : 1);

		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (PingKeys.PING_SETTINGS.matches(event)) {
			this.onClose();

			return true;
		}

		switch (event.key()) {
			case GLFW.GLFW_KEY_UP -> {
				this.focus = Math.floorMod(this.focus - 1, SETTINGS.length);
				ensureVisible();

				return true;
			}
			case GLFW.GLFW_KEY_DOWN -> {
				this.focus = (this.focus + 1) % SETTINGS.length;
				ensureVisible();

				return true;
			}
			case GLFW.GLFW_KEY_PAGE_UP -> {
				scrollBy(-VISIBLE_LINES);

				return true;
			}
			case GLFW.GLFW_KEY_PAGE_DOWN -> {
				scrollBy(VISIBLE_LINES);

				return true;
			}
			case GLFW.GLFW_KEY_LEFT -> {
				SETTINGS[this.focus].left();

				return true;
			}
			case GLFW.GLFW_KEY_RIGHT -> {
				SETTINGS[this.focus].right();

				return true;
			}
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
				SETTINGS[this.focus].advance();

				return true;
			}
			default -> {
				// Escape reaches Screen's own handling, which closes.
				return super.keyPressed(event);
			}
		}
	}

	/** Puts every setting back to the value {@code ping.json} is first written with. */
	private void restore() {
		PingConfig.resetToDefaults();
	}

	// ---- the list ---------------------------------------------------------

	/** The lines, each group introduced by its heading. */
	private static Line[] buildLines() {
		List<Line> lines = new ArrayList<>();
		Group current = null;

		for (int index = 0; index < SETTINGS.length; index++) {
			Group group = SETTINGS[index].group();

			if (group != current) {
				current = group;
				lines.add(new Line(group.heading(), -1));
			}

			lines.add(new Line(null, index));
		}

		return lines.toArray(new Line[0]);
	}

	/** The line a setting is drawn on. */
	private static int lineOf(int setting) {
		for (int index = 0; index < LINES.length; index++) {
			if (LINES[index].setting() == setting) {
				return index;
			}
		}

		return 0;
	}

	/** Scrolls the window so the focused setting's line is inside it. */
	private void ensureVisible() {
		int line = lineOf(this.focus);
		int maxScroll = Math.max(0, LINES.length - VISIBLE_LINES);

		if (line < this.scroll) {
			this.scroll = line;
		}

		if (line >= this.scroll + VISIBLE_LINES) {
			this.scroll = line - VISIBLE_LINES + 1;
		}

		this.scroll = Math.max(0, Math.min(maxScroll, this.scroll));
	}

	/** Moves the window by a number of lines, clamped to the list. */
	private void scrollBy(int lines) {
		int maxScroll = Math.max(0, LINES.length - VISIBLE_LINES);

		this.scroll = Math.max(0, Math.min(maxScroll, this.scroll + lines));
	}

	/**
	 * The setting under the pointer, or -1.
	 *
	 * <p>The target is the focus box, so the rows tile the way their boxes do — a row does
	 * not own the bare backdrop below its own box. Headings are not targets.
	 */
	private int rowAt(double x, double y) {
		int x0 = span(FOCUS_X, this.width);

		if (x < x0 || x > x0 + span(FOCUS_W, this.width)) {
			return -1;
		}

		int listY = span(LIST_Y, this.height);
		int pitch = span(LIST_PITCH, this.height);

		for (int slot = 0; slot < VISIBLE_LINES; slot++) {
			int line = this.scroll + slot;

			if (line >= LINES.length) {
				break;
			}

			if (LINES[line].isHeading()) {
				continue;
			}

			int top = listY + slot * pitch - span(FOCUS_UP, this.height);

			if (y >= top && y < top + span(FOCUS_H, this.height)) {
				return LINES[line].setting();
			}
		}

		return -1;
	}

	/**
	 * The position of the option under the pointer, or -1. The list belongs to the focused
	 * row alone, and the returned value is an index into that setting's own positions.
	 */
	private int optionAt(double x, double y) {
		int x0 = span(PANEL_X, this.width);

		if (x < x0 || x > x0 + span(OPTION_BAR_W, this.width)) {
			return -1;
		}

		int[] positions = SETTINGS[this.focus].optionPositions();
		int barH = span(OPTION_BAR_H, this.height);

		for (int row = 0; row < positions.length; row++) {
			int top = span(OPTION_Y, this.height) + row * span(OPTION_PITCH, this.height);

			if (y >= top && y < top + barH) {
				return positions[row];
			}
		}

		return -1;
	}

	// ---- geometry ---------------------------------------------------------

	/** A measured fraction of the window, in GUI pixels. */
	private static int span(float fraction, int extent) {
		return Math.round(fraction * extent);
	}

	/** The stroke the capture draws its outlines with, in GUI pixels, never less than one. */
	private static int stroke(int height) {
		return Math.max(1, span(FOCUS_STROKE, height));
	}

	/** The multiple of Minecraft's own font a measured cap height is drawn at. */
	private static float capScale(float measuredCap) {
		return measuredCap / CAP_ANCHOR;
	}

	/** The height one line's ink occupies at a given size — the ascent, not the whole line box. */
	private static float inkHeight(float scale) {
		return FONT_ASCENT * scale;
	}

	/** The height one line of this screen's font occupies at a given size. */
	private float lineHeight(float scale) {
		return this.font.lineHeight * scale;
	}

	/**
	 * The top a run at {@code scale} must be drawn at to stand on the middle of a box's own
	 * line.
	 *
	 * <p>This is what a text <em>held</em> by a box wants, and it is not the same as the box's
	 * top: Minecraft hangs a run from the top of its line box, so a string handed a container's
	 * top edge sits against that edge and leaves the whole padding of the container below
	 * itself. The chip, the key badge, the button label and the option list are all texts held
	 * by a measured box, so all four centre on it; a text whose {@code y} is its own ink
	 * position from the capture — a row label, a heading, a panel line — is drawn at that
	 * position instead and never comes through here.
	 */
	private static int centredTop(int boxTop, int boxHeight, float scale) {
		return Math.round(boxTop + (boxHeight - inkHeight(scale)) / 2.0F);
	}

	/**
	 * The top a run at {@code scale} needs so that it stands on the same baseline as a run of
	 * {@code from} whose top is {@code top}.
	 *
	 * <p>The result is proportional to the ascent and to the difference in the two sizes, which
	 * is the whole point: two lines of different cap heights share a line only by their
	 * baselines, and the gap between their tops is the cap difference times the ascent — not a
	 * number read off a capture and applied as a fraction of the window, which only lands in the
	 * right GUI pixel when the window happens to be the height it was read at. A label and its
	 * value are 21.5 and 19.5 capture pixels of cap, so the value starts 0.82 pixels lower; it
	 * is the same 0.82 at every window size.
	 */
	private static int shareBaseline(int top, float from, float to) {
		return Math.round(top + FONT_ASCENT * (from - to));
	}

	/**
	 * The box a single line of a measured cap height needs to be clipped to without cutting its
	 * ink: a whole line box, plus a pixel either side for the shadow's fringe. It is a clip, not
	 * a placement — the ink inside it is only {@link #inkHeight} tall, and where that ink goes
	 * is {@link #centredTop} or the capture's own {@code y}.
	 */
	private int textBox(float measuredCap) {
		return Math.round(lineHeight(capScale(measuredCap))) + 2;
	}

	private static boolean within(int left, int top, int width, int height, double x, double y) {
		return x >= left && x < left + width && y >= top && y < top + height;
	}

	// ---- drawing primitives ----------------------------------------------

	/**
	 * A filled rounded rectangle, drawn a row at a time.
	 *
	 * <p>The same technique the map screen uses for its slanted tab: a 2D GUI has no arc, and
	 * at these sizes a row-by-row quarter circle is indistinguishable from one. The inset at
	 * a row is {@code r - sqrt(r² - (r - d)²)} for {@code d} rows in from the nearer end,
	 * which is the circle's own equation and reproduces the capture's corner exactly: on the
	 * focus box, whose radius measured 24 capture pixels, it puts the stroke's first ink at
	 * x 188 on the row below the top edge, where the capture has 188.
	 */
	private static void roundRect(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius,
			int colour) {
		int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));

		for (int row = 0; row < height; row++) {
			int inset = cornerInset(row, height, r);

			graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, colour);
		}
	}

	/** The same shape hollow: the capture's focus box is an outline, and the backdrop shows through it. */
	private static void roundOutline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius,
			int thickness, int colour) {
		int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));

		for (int row = 0; row < height; row++) {
			int inset = cornerInset(row, height, r);
			int left = x + inset;
			int right = x + width - inset;

			if (Math.min(row, height - 1 - row) < thickness) {
				graphics.fill(left, y + row, right, y + row + 1, colour);
			} else {
				graphics.fill(left, y + row, left + thickness, y + row + 1, colour);
				graphics.fill(right - thickness, y + row, right, y + row + 1, colour);
			}
		}
	}

	/** How far a rounded corner reaches in at a given row. */
	private static int cornerInset(int row, int height, int radius) {
		int distance = Math.min(row, height - 1 - row);

		if (distance >= radius) {
			return 0;
		}

		int arm = radius - distance;

		return radius - (int) Math.sqrt(radius * radius - arm * arm);
	}

	/** A right-pointing solid triangle: the backdrop's arrows, a chevron and the option caret. */
	private static void arrow(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int colour) {
		if (width <= 0 || height <= 0) {
			return;
		}

		float middle = (height - 1) / 2.0F;

		for (int row = 0; row < height; row++) {
			float reach = 1.0F - Math.abs(row - middle) / (middle + 0.5F);
			int right = x + Math.round(width * Math.max(0.0F, reach));

			if (right > x) {
				graphics.fill(x, y + row, right, y + row + 1, colour);
			}
		}
	}

	/** The same triangle mirrored, which is the stepper's left chevron. */
	private static void arrowLeft(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int colour) {
		if (width <= 0 || height <= 0) {
			return;
		}

		float middle = (height - 1) / 2.0F;

		for (int row = 0; row < height; row++) {
			float reach = 1.0F - Math.abs(row - middle) / (middle + 0.5F);
			int left = x + width - Math.round(width * Math.max(0.0F, reach));

			if (left < x + width) {
				graphics.fill(left, y + row, x + width, y + row + 1, colour);
			}
		}
	}

	// ---- text -------------------------------------------------------------

	/**
	 * One line of text clipped to its own box, and scrolled inside it when it does not fit.
	 *
	 * <p>This is the screen's single entry point for a string: every label, value, heading
	 * and button label goes through it, so nothing can draw outside the box it was given. The
	 * clip is {@link GuiGraphicsExtractor#enableScissor} — verified against the mapped jar,
	 * where it pushes a {@code ScreenRectangle} onto a {@code ScissorStack} and
	 * {@link GuiGraphicsExtractor#disableScissor} pops it — and the pair is always balanced,
	 * including when the text fits and there is nothing to scroll, because a scissor left
	 * enabled would erase the rest of the HUD.
	 *
	 * <p>When the text is wider than the box it becomes a marquee: it rests at the start, then
	 * slides left at a slow, readable pace, wraps, and rests again, all driven from
	 * {@code now} rather than held as state, so two strings in two boxes never fight over a
	 * shared field. {@code centre} centres text that fits — which is what every value in the
	 * capture does — and lets a long one start from the left edge instead of scrolling out of
	 * a position it never held.
	 *
	 * <p>{@code y} is where the line's <em>ink</em> begins, which for this font is also the top
	 * of its line box ({@link #FONT_ASCENT}). A caller with a measured ink position passes it
	 * unchanged; a caller whose text is held by a box passes {@link #centredTop} of that box.
	 * The height is the clip and nothing else — it never moves the text — so handing this
	 * method a container's height and expecting the line to settle into the middle of it does
	 * nothing, which is exactly what four of the held texts on this screen used to be caught by.
	 */
	private void boxedText(GuiGraphicsExtractor graphics, String text, int x, int y, int width, int height,
			int colour, long now, float scale, boolean centre, boolean shadow) {
		if (width <= 0 || height <= 0 || text.isEmpty()) {
			return;
		}

		int textWidth = Math.round(this.font.width(text) * scale);
		int offset = marqueeOffset(textWidth, width, now);
		float drawX = x - offset;

		if (centre && textWidth <= width) {
			drawX = x + (width - textWidth) / 2.0F;
		}

		graphics.enableScissor(x, y, x + width, y + height);
		text(graphics, literal(text), drawX, y, colour, scale, shadow);
		graphics.disableScissor();
	}

	/** {@link #boxedText}, centred horizontally in its box. */
	private void boxedCentred(GuiGraphicsExtractor graphics, Component string, int x, int width, int y, int height,
			int colour, float scale, boolean shadow, long now) {
		boxedText(graphics, string.getString(), x, y, width, height, colour, now, scale, true, shadow);
	}

	/**
	 * A wrapped paragraph clipped to a fixed box, scrolled vertically when it is taller than
	 * the box. This is the description's form: the body box ends above the option list, so no
	 * description, however long, can reach the list — which is the overlap this screen was
	 * rebuilt to remove. The marquee runs top-to-bottom rather than left-to-right because a
	 * paragraph is read in lines.
	 */
	private void boxedParagraph(GuiGraphicsExtractor graphics, String text, int x, int y, int width, int height,
			int colour, float scale, int pitch, long now) {
		List<FormattedCharSequence> lines = this.font.split(literal(text), Math.max(1, Math.round(width / scale)));
		int total = lines.size() * pitch;
		int offset = marqueeOffset(total, height, now);
		float lineY = y - offset;

		graphics.enableScissor(x, y, x + width, y + height);

		for (FormattedCharSequence line : lines) {
			text(graphics, line, x, lineY, colour, scale);
			lineY += pitch;
		}

		graphics.disableScissor();
	}

	/**
	 * How far a too-long string has travelled, in pixels, at this instant.
	 *
	 * <p>The cycle is a rest, a steady slide, and a rest at the far end: at rest the offset is
	 * zero, through the slide it advances one pixel per {@link #MARQUEE_MS_PER_PIXEL}, and
	 * after the slide it sits at the far end until the cycle wraps. With no overflow there is
	 * nothing to move and the answer is always zero, which is how the caller can treat a
	 * short string exactly like a long one.
	 */
	private static int marqueeOffset(int content, int box, long now) {
		if (content <= box || box <= 0) {
			return 0;
		}

		int overflow = content - box + MARQUEE_TRAIL;
		long travel = Math.max(1L, (long) (overflow * MARQUEE_MS_PER_PIXEL));
		long cycle = 2L * MARQUEE_PAUSE_MS + travel;
		long at = Math.floorMod(now, cycle);

		if (at < MARQUEE_PAUSE_MS) {
			return 0;
		}

		at -= MARQUEE_PAUSE_MS;

		if (at < travel) {
			return (int) (at / MARQUEE_MS_PER_PIXEL);
		}

		return overflow;
	}

	/**
	 * Text at a multiple of Minecraft's own size.
	 *
	 * <p>The scale goes through the pose stack because that is where this build's text draw
	 * reads it: {@code GuiGraphicsExtractor.text} snapshots the current matrix into its
	 * {@code GuiTextRenderState}, so text is the one draw call that can be resized without a
	 * texture of its own. The translate is unbaked for the same reason — a fractional origin
	 * would otherwise be rounded away.
	 */
	private void text(GuiGraphicsExtractor graphics, Component string, float x, float y, int colour, float scale,
			boolean shadow) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		graphics.text(this.font, string, 0, 0, colour, shadow);
		graphics.pose().popMatrix();
	}

	/** A wrapped line arrives as a sequence rather than a string, so it needs its own entry. */
	private void text(GuiGraphicsExtractor graphics, FormattedCharSequence line, float x, float y, int colour,
			float scale) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		graphics.text(this.font, line, 0, 0, colour);
		graphics.pose().popMatrix();
	}

	private static Component bold(String string) {
		return Component.literal(string).withStyle(ChatFormatting.BOLD);
	}

	private static Component literal(String string) {
		return Component.literal(string);
	}

	// ---- colour arithmetic ------------------------------------------------

	/** One channel of a linear blend between two packed colours. */
	private static int blend(int from, int to, float t) {
		return channel(from, to, t, 24) << 24 | channel(from, to, t, 16) << 16
				| channel(from, to, t, 8) << 8 | channel(from, to, t, 0);
	}

	private static int channel(int from, int to, float t, int shift) {
		int a = (from >> shift) & 0xFF;
		int b = (to >> shift) & 0xFF;

		return Math.max(0, Math.min(255, Math.round(a + (b - a) * t)));
	}

	/** A colour multiplied by a factor, which is how the backdrop's arrows are made. */
	private static int gain(int colour, float factor) {
		int red = Math.min(255, Math.round(((colour >> 16) & 0xFF) * factor));
		int green = Math.min(255, Math.round(((colour >> 8) & 0xFF) * factor));
		int blue = Math.min(255, Math.round((colour & 0xFF) * factor));

		return 0xFF000000 | red << 16 | green << 8 | blue;
	}

	private static float clamp(float value) {
		return Math.max(0.0F, Math.min(1.0F, value));
	}
}
