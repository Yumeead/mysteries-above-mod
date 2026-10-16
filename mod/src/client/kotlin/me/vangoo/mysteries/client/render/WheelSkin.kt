package me.vangoo.mysteries.client.render

import me.vangoo.mysteries.MOD_ID
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * How the ability wheel is dressed. Only the look: geometry, input, state and motion stay in
 * `AbilityWheelScreen` and are the same for every skin.
 *
 * A skin draws three things: the ring behind the cards, a card's body (before the icon, so the
 * icon lies on it), and a card's state marks (after the icon). Every state mark is a flat change
 * of colour or a pixel shape — no glow: hover, selection and cooldown are ordinary states.
 * The only raster halo is baked into [ATMOSPHERIC]'s centre seal, which is decoration, not state.
 */
enum class WheelSkin {

    /** A — restrained pixel occultism: the existing panel, gold corner ticks, a dotted ring. */
    PIXEL {
        override fun ring(graphics: GuiGraphicsExtractor, cx: Int, cy: Int, radius: Int, count: Int, alpha: Float, accent: Int) {
            dots(radius, 4).forEach { (x, y) -> dot(graphics, cx + x, cy + y, UiTheme.ACCENT, alpha * 0.22f) }
        }

        override fun body(graphics: GuiGraphicsExtractor, x: Int, y: Int, alpha: Float) {
            UiTheme.panel(graphics, x, y, CARD, CARD, alpha)
            val tick = UiTheme.argb(UiTheme.ACCENT, alpha)
            for ((cx, cy) in corners(x, y)) graphics.fill(cx, cy, cx + 1, cy + 1, tick)
        }
    },

    /** B — atmospheric occultism: a textured card with corner seals, a heptagram seal at the centre. */
    ATMOSPHERIC {
        override fun ring(graphics: GuiGraphicsExtractor, cx: Int, cy: Int, radius: Int, count: Int, alpha: Float, accent: Int) {
            dots(radius - 14, 3).forEach { (x, y) -> dot(graphics, cx + x, cy + y, UiTheme.ACCENT, alpha * 0.16f) }
            // A rune mark between each pair of cards.
            between(radius, count).forEach { (x, y) ->
                val c = UiTheme.argb(UiTheme.ACCENT, alpha * 0.4f)
                graphics.fill(cx + x, cy + y - 1, cx + x + 1, cy + y + 2, c)
            }
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SEAL, cx - 35, cy - 35, 71, 71, UiTheme.argb(accent, alpha * 0.32f))
        }

        override fun body(graphics: GuiGraphicsExtractor, x: Int, y: Int, alpha: Float) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ATMOS_CARD, x, y, CARD, CARD, UiTheme.argb(0xFFFFFF, alpha))
        }
    },

    /** C — tarot: a portrait card inside the same square cell, the pathway emblem as its suit. */
    TAROT {
        override val left: Int = 3
        override val right: Int get() = 21

        override fun ring(graphics: GuiGraphicsExtractor, cx: Int, cy: Int, radius: Int, count: Int, alpha: Float, accent: Int) {
            between(radius, count).forEach { (x, y) -> star(graphics, cx + x, cy + y, UiTheme.argb(UiTheme.ACCENT, alpha * 0.45f)) }
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, WHEEL, cx - 23, cy - 23, 47, 47, UiTheme.argb(accent, alpha * 0.28f))
        }

        override fun body(graphics: GuiGraphicsExtractor, x: Int, y: Int, alpha: Float) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, TAROT_CARD, x, y, CARD, CARD, UiTheme.argb(0xFFFFFF, alpha))
        }

        override fun selectedMark(graphics: GuiGraphicsExtractor, x: Int, y: Int, color: Int) {
            // The crest in the top band lights up.
            graphics.fill(x + 10, y + 1, x + 14, y + 3, color)
        }

        override fun emblem(graphics: GuiGraphicsExtractor, x: Int, y: Int, pathway: String?, rgb: Int, alpha: Float) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, emblemOf(pathway), x + 10, y + 19, 5, 5, UiTheme.argb(rgb, alpha))
        }
    };

    /** The visible card's horizontal extent within the 24-pixel cell: the tarot card is narrower. */
    open val left: Int = 0
    open val right: Int get() = CARD

    abstract fun ring(graphics: GuiGraphicsExtractor, cx: Int, cy: Int, radius: Int, count: Int, alpha: Float, accent: Int)

    abstract fun body(graphics: GuiGraphicsExtractor, x: Int, y: Int, alpha: Float)

    /**
     * The state marks, drawn over the icon. Hover is the pathway-coloured outline (as before);
     * selection is a solid pixel mark; a card the player cannot afford gets a critical-red frame.
     */
    fun marks(graphics: GuiGraphicsExtractor, x: Int, y: Int, hovered: Boolean, selected: Boolean, critical: Boolean, alpha: Float, accent: Int) {
        val w = right - left
        if (critical && !hovered) graphics.outline(x + left, y, w, CARD, UiTheme.argb(UiTheme.CRITICAL, alpha * 0.8f))
        if (selected) selectedMark(graphics, x, y, UiTheme.argb(UiTheme.TEXT, alpha))
        if (hovered) graphics.outline(x + left - 1, y - 1, w + 2, CARD + 2, UiTheme.argb(accent, alpha))
    }

    /** A short bar in the gap under the icon. */
    protected open fun selectedMark(graphics: GuiGraphicsExtractor, x: Int, y: Int, color: Int) {
        graphics.fill(x + 9, y + 21, x + 15, y + 22, color)
    }

    /** The pathway emblem on the card; only the tarot carries one, as its suit. */
    open fun emblem(graphics: GuiGraphicsExtractor, x: Int, y: Int, pathway: String?, rgb: Int, alpha: Float) = Unit

    fun next(): WheelSkin = entries[(ordinal + 1) % entries.size]

    protected companion object {
        const val CARD = 24

        val ATMOS_CARD: Identifier = sprite("atmos_card")
        val SEAL: Identifier = sprite("atmos_seal")
        val TAROT_CARD: Identifier = sprite("tarot_card")
        val WHEEL: Identifier = sprite("tarot_wheel")
        private val EMBLEMS = setOf("sun", "tyrant", "whitetower", "door", "error", "visionary", "justiciar", "fool", "death")

        fun sprite(name: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, "wheel/$name")

        fun emblemOf(pathway: String?): Identifier =
            sprite("emblem/" + (pathway?.lowercase()?.takeIf { it in EMBLEMS } ?: "default"))

        /** Pixel offsets of a dotted ring, one dot per [spacing] pixels of arc. Cached: the radius takes two values. */
        private val rings = HashMap<Long, List<IntArray>>()

        fun dots(radius: Int, spacing: Int): List<IntArray> = rings.getOrPut(radius.toLong() shl 8 or spacing.toLong()) {
            val n = (2 * PI * radius / spacing).toInt()
            List(n) { i ->
                val t = 2 * PI * i / n
                intArrayOf((sin(t) * radius).roundToInt(), (-cos(t) * radius).roundToInt())
            }.distinctBy { it[0] * 4096 + it[1] }
        }

        /** Points on the ring halfway between neighbouring cards. */
        fun between(radius: Int, count: Int): List<IntArray> =
            if (count < 2) emptyList() else List(count) { i ->
                val t = 2 * PI * (i + 0.5) / count
                intArrayOf((sin(t) * radius).roundToInt(), (-cos(t) * radius).roundToInt())
            }

        fun dot(graphics: GuiGraphicsExtractor, x: Int, y: Int, rgb: Int, alpha: Float) =
            graphics.fill(x, y, x + 1, y + 1, UiTheme.argb(rgb, alpha))

        fun corners(x: Int, y: Int): List<IntArray> =
            listOf(intArrayOf(x + 1, y + 1), intArrayOf(x + CARD - 2, y + 1), intArrayOf(x + 1, y + CARD - 2), intArrayOf(x + CARD - 2, y + CARD - 2))

        /** A five-pixel cross star. */
        fun star(graphics: GuiGraphicsExtractor, x: Int, y: Int, color: Int) {
            graphics.fill(x - 1, y, x + 2, y + 1, color)
            graphics.fill(x, y - 1, x + 1, y + 2, color)
        }
    }
}
