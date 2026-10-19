package me.vangoo.mysteries.client.render

import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.pow

/** The book's paper and binding; all details sit on the logical GUI pixel grid. */
object GrimoireSkin {
    const val GOLD = 0xB99A61
    const val EDGE = 0x58432D
    const val BURGUNDY = 0x3E1D2A
    private val flecks = HashMap<Long, List<Pair<Int, Int>>>()

    /** Tiny pathway labels need enough contrast against charcoal paper. */
    fun readableAccent(rgb: Int): Int {
        var color = rgb
        fun luminance(value: Int): Double = listOf(16 to .2126, 8 to .7152, 0 to .0722).sumOf { (shift, weight) ->
            val c = ((value shr shift) and 255) / 255.0
            (if (c <= .04045) c / 12.92 else ((c + .055) / 1.055).pow(2.4)) * weight
        }
        while (luminance(color) < .22) {
            val r = (((color shr 16) and 255) + 8).coerceAtMost(255)
            val g = (((color shr 8) and 255) + 8).coerceAtMost(255)
            val b = ((color and 255) + 8).coerceAtMost(255)
            color = (r shl 16) or (g shl 8) or b
        }
        return color
    }

    fun paper(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, cover: Boolean) {
        val base = if (cover) 0x100D11 else 0x191519
        graphics.fill(x, y, x + w, y + h, UiTheme.argb(base, 1f))
        val dots = flecks.getOrPut(w.toLong() shl 32 or h.toLong()) {
            val random = java.util.Random(w * 7919L + h)
            List((w * h / 230).coerceAtLeast(0)) { random.nextInt(w - 4) + 2 to random.nextInt(h - 4) + 2 }
        }
        val ink = UiTheme.argb(if (cover) 0x1B141B else 0x241D23, 1f)
        for ((dx, dy) in dots) graphics.fill(x + dx, y + dy, x + dx + 1, y + dy + 1, ink)
        graphics.outline(x, y, w, h, UiTheme.argb(EDGE, 1f))
        if (cover) {
            graphics.outline(x + 3, y + 3, w - 6, h - 6, UiTheme.argb(0x38282A, 1f))
            graphics.fill(x + 5, y + 8, x + 7, y + h - 8, UiTheme.argb(BURGUNDY, 1f))
        }
        corners(graphics, x + 2, y + 2, w - 4, h - 4, if (cover) GOLD else 0x78603E)
    }

    fun corners(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, rgb: Int) {
        val color = UiTheme.argb(rgb, 1f)
        for (right in listOf(false, true)) for (bottom in listOf(false, true)) {
            val px = if (right) x + w - 1 else x
            val py = if (bottom) y + h - 1 else y
            graphics.fill(if (right) px - 4 else px, py, if (right) px + 1 else px + 5, py + 1, color)
            graphics.fill(px, if (bottom) py - 4 else py, px + 1, if (bottom) py + 1 else py + 5, color)
        }
    }

    fun divider(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int) {
        val mid = x + w / 2
        val dim = UiTheme.argb(EDGE, 1f)
        val gold = UiTheme.argb(GOLD, 1f)
        graphics.fill(x, y, mid - 4, y + 1, dim)
        graphics.fill(mid + 5, y, x + w, y + 1, dim)
        graphics.fill(mid - 1, y - 1, mid + 2, y + 2, gold)
        graphics.fill(mid, y - 2, mid + 1, y + 3, gold)
        graphics.fill(mid - 2, y, mid + 3, y + 1, gold)
    }
}
