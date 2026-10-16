package me.vangoo.mysteries.client.render

import me.vangoo.mysteries.MOD_ID
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB

/**
 * The mod's palette and the four primitives all its surfaces are built from.
 *
 * It exists so that the HUD (M3), the wheel (M4) and the book (M6) look like one mod rather than
 * three. A palette copied three times is where a UI starts to come apart: the very first shade
 * adjustment is made in one file, and the other two are left with the old one.
 *
 * The background is almost black with transparency, the frames are hairlines in the colour of
 * tarnished gold. The pathway colour deliberately does not belong here — it comes from outside
 * and appears in exactly two places in the whole UI: the frame of the card under the cursor and
 * the hairline under the HUD. The occult reads through restraint, not through the number of
 * effects.
 *
 * There is no state here: everything is a pure function of the numbers passed in.
 */
object UiTheme {

    /** The panel background. */
    const val PANEL: Int = 0x0A0A0F

    /** Tarnished gold: frames, the filled part of bars, accents. */
    const val ACCENT: Int = 0xC8A44B

    /** The same gold, muted: the stretch of the bar that is about to be spent. */
    const val ACCENT_DEEP: Int = 0x6E5824

    const val TEXT: Int = 0xEDE8DC

    const val MUTED: Int = 0xB0AAA0

    /** Not enough spirituality, a failed cast — anything that needs attention. */
    const val CRITICAL: Int = 0xC46A55

    /** The unfilled part of a bar and the badge background. */
    const val TRACK: Int = 0x2A2C39

    /** The badge height: a line plus a pixel above and below. */
    const val BADGE_HEIGHT: Int = 11

    private const val BADGE_PADDING = 3
    private const val PANEL_ALPHA = 1.0f
    private const val FRAME_ALPHA = 0.55f
    private const val TRACK_ALPHA = 0.85f
    private const val VEIL_ALPHA = 0.62f

    /** RGB plus a separate opacity. The colours here carry no alpha, so it is multiplied in at the drawing site. */
    fun argb(rgb: Int, alpha: Float): Int =
        ARGB.color(
            (alpha.coerceIn(0f, 1f) * 255f).toInt(),
            (rgb shr 16) and 0xFF,
            (rgb shr 8) and 0xFF,
            rgb and 0xFF,
        )

    /** A panel: a dark background and a hairline gold frame. */
    fun panel(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, alpha: Float) {
        graphics.fill(x, y, x + width, y + height, argb(PANEL, PANEL_ALPHA * alpha))
        graphics.outline(x, y, width, height, argb(ACCENT, FRAME_ALPHA * alpha))
    }

    /** A one-pixel hairline — the only place the pathway colour is let in. */
    fun hairline(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, rgb: Int, alpha: Float) {
        graphics.fill(x, y, x + width, y + 1, argb(rgb, alpha))
    }

    /** A horizontal bar: the track and the fill from left to right. */
    fun bar(
        graphics: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        progress: Float,
        rgb: Int,
        alpha: Float,
    ) {
        graphics.fill(x, y, x + width, y + height, argb(TRACK, TRACK_ALPHA * alpha))
        val filled = (width * progress.coerceIn(0f, 1f)).toInt()
        if (filled > 0) graphics.fill(x, y, x + filled, y + height, argb(rgb, alpha))
    }

    /**
     * A stretch of the bar between two fractions — to mark a range rather than a level.
     *
     * Spirituality draws the selected ability's cost with it: what will be left is visible in
     * advance.
     */
    fun barSlice(
        graphics: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        from: Float,
        to: Float,
        rgb: Int,
        alpha: Float,
    ) {
        val left = x + (width * from.coerceIn(0f, 1f)).toInt()
        val right = x + (width * to.coerceIn(0f, 1f)).toInt()
        if (right <= left) return
        graphics.fill(left, y, right, y + height, argb(rgb, alpha))
    }

    /**
     * The cooldown over the icon: a veil from the bottom up that recedes as readiness grows.
     *
     * [remaining] is the share of the cooldown still left: `1` — it just started, `0` — ready.
     */
    fun cooldownVeil(graphics: GuiGraphicsExtractor, x: Int, y: Int, size: Int, remaining: Float, alpha: Float) {
        val covered = (size * remaining.coerceIn(0f, 1f)).toInt()
        if (covered <= 0) return
        graphics.fill(x, y + size - covered, x + size, y + size, argb(PANEL, VEIL_ALPHA * alpha))
    }

    fun badgeWidth(font: Font, text: Component): Int = font.width(text) + BADGE_PADDING * 2

    private val SPIRIT: Identifier = Identifier.fromNamespaceAndPath(MOD_ID, "spirit/droplet")

    /** The same drop, emptied and cracked: "cannot afford" as a shape, not only a colour. */
    private val DRAINED: Identifier = Identifier.fromNamespaceAndPath(MOD_ID, "spirit/drained")
    private const val SPIRIT_WIDTH = 5
    private const val SPIRIT_HEIGHT = 7
    private const val SPIRIT_ADVANCE = SPIRIT_WIDTH + 2

    fun priceWidth(font: Font, text: Component, spirit: Boolean): Int =
        (if (spirit) SPIRIT_ADVANCE else 0) + font.width(text)

    /**
     * A price line: a drop and the number. The drop is a pixel sprite, not a `✦` glyph — the glyph
     * comes from the unicode fallback font and lands blurred and off the digits' baseline. When the
     * price cannot be paid, the drop is drawn empty and cracked and the number turns red.
     */
    fun price(graphics: GuiGraphicsExtractor, font: Font, text: Component, critical: Boolean, spirit: Boolean, x: Int, y: Int, alpha: Float) {
        var left = x
        if (spirit) {
            if (critical) {
                shadowed(graphics, DRAINED, left, y, SPIRIT_WIDTH, SPIRIT_HEIGHT, 0xFFFFFF, alpha)
            } else {
                shadowed(graphics, SPIRIT, left, y, SPIRIT_WIDTH, SPIRIT_HEIGHT, ACCENT, alpha)
            }
            left += SPIRIT_ADVANCE
        }
        graphics.text(font, text, left, y, argb(if (critical) CRITICAL else MUTED, alpha))
    }

    /** A sprite with the font's shadow: the same shape a pixel down-right at a quarter brightness. */
    private fun shadowed(graphics: GuiGraphicsExtractor, id: Identifier, x: Int, y: Int, w: Int, h: Int, tint: Int, alpha: Float) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, id, x + 1, y + 1, w, h, argb(ARGB.scaleRGB(tint, 0.25f), alpha))
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, id, x, y, w, h, argb(tint, alpha))
    }

    fun priceBadgeWidth(font: Font, text: Component, spirit: Boolean): Int =
        priceWidth(font, text, spirit) + BADGE_PADDING * 2

    /** [price] on a badge backing. */
    fun priceBadge(graphics: GuiGraphicsExtractor, font: Font, text: Component, critical: Boolean, spirit: Boolean, x: Int, y: Int, alpha: Float) {
        graphics.fill(x, y, x + priceBadgeWidth(font, text, spirit), y + BADGE_HEIGHT, argb(TRACK, TRACK_ALPHA * alpha))
        price(graphics, font, text, critical, spirit, x + BADGE_PADDING, y + 2, alpha)
    }

    /** A badge: a backing and the text. A cost, a slot key caption, a page counter. */
    fun badge(
        graphics: GuiGraphicsExtractor,
        font: Font,
        text: Component,
        x: Int,
        y: Int,
        rgb: Int,
        alpha: Float,
    ) {
        graphics.fill(x, y, x + badgeWidth(font, text), y + BADGE_HEIGHT, argb(TRACK, TRACK_ALPHA * alpha))
        graphics.text(font, text, x + BADGE_PADDING, y + 2, argb(rgb, alpha))
    }
}
