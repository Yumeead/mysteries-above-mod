package me.vangoo.mysteries.client.render

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The wheel's geometry and nothing else: pages, card positions, which card the cursor points at.
 *
 * Kept apart from the screen because it is the one part of the wheel where a silent mistake
 * lives (a 359° sector, a division by zero with one ability) — and it is checked without a pixel.
 * Angles run clockwise from straight up, the way the eye reads a clock face.
 */
object WheelLayout {

    const val PAGE_SIZE = 12

    /** Up to eight cards fit the small ring; nine to twelve need it wider. */
    private const val SMALL_RING = 8
    const val RADIUS = 80
    const val WIDE_RADIUS = 98

    fun pages(count: Int): Int = if (count <= 0) 1 else (count + PAGE_SIZE - 1) / PAGE_SIZE

    /** How many cards stand on [page]. */
    fun countOn(count: Int, page: Int): Int = (count - page * PAGE_SIZE).coerceIn(0, PAGE_SIZE)

    fun radius(onPage: Int): Int = if (onPage <= SMALL_RING) RADIUS else WIDE_RADIUS

    /** The card centre's offset from the wheel centre, `[dx, dy]`. */
    fun offset(index: Int, onPage: Int): IntArray {
        val angle = 2 * PI * index / onPage
        val r = radius(onPage)
        return intArrayOf((sin(angle) * r).roundToInt(), (-cos(angle) * r).roundToInt())
    }

    /**
     * The card index under a cursor at ([dx], [dy]) from the centre, or `-1` inside the dead
     * zone — releasing there casts nothing.
     */
    fun pick(dx: Double, dy: Double, onPage: Int, deadZoneRatio: Float): Int {
        if (onPage <= 0) return -1
        if (hypot(dx, dy) < radius(onPage) * deadZoneRatio) return -1
        val degrees = (Math.toDegrees(atan2(dx, -dy)) + 360.0) % 360.0
        val sector = 360.0 / onPage
        return floor((degrees + sector / 2) / sector).toInt() % onPage
    }

    /**
     * The centre of a card's hour mark: just outside the card, along the line from the wheel's
     * centre through it — the numerals ring the cards the way hours ring a clock face. A card in
     * the lower half carries its price underneath, so its mark steps one more line further out.
     */
    fun hourMark(cardDx: Int, cardDy: Int, card: Int, lineHeight: Int): IntArray {
        val length = hypot(cardDx.toDouble(), cardDy.toDouble()).takeIf { it > 0 } ?: return intArrayOf(0, -card)
        val ux = cardDx / length
        val uy = cardDy / length
        val reach = card / 2 + lineHeight / 2 + 3 + if (uy > 0.5) lineHeight else 0
        return intArrayOf((cardDx + ux * reach).roundToInt(), (cardDy + uy * reach).roundToInt())
    }

    // ── Modes ───────────────────────────────────────────────────────────────────

    /** Height of one mode pill, the gap between pills, and the gap between the card and its column. */
    const val MODE_HEIGHT = 12
    const val MODE_GAP = 2
    const val MODE_OFFSET = 6

    /**
     * Where the mode pills of a card stand: a column beside the card, on the side away from the
     * centre (right for the right half and the top and bottom cards, left otherwise), centred on
     * the card's height. A column rather than an arc because the pills carry names, and names
     * laid along an arc overlap. Each rectangle is `[x, y, w, h]` relative to the wheel centre.
     */
    fun modeColumn(cardDx: Int, cardDy: Int, card: Int, widths: IntArray): List<IntArray> {
        val total = widths.size * MODE_HEIGHT + (widths.size - 1).coerceAtLeast(0) * MODE_GAP
        val top = cardDy - total / 2
        val right = cardDx >= 0
        return widths.mapIndexed { i, w ->
            val x = if (right) cardDx + card / 2 + MODE_OFFSET else cardDx - card / 2 - MODE_OFFSET - w
            intArrayOf(x, top + i * (MODE_HEIGHT + MODE_GAP), w, MODE_HEIGHT)
        }
    }

    /** The pill under the cursor, or `-1`. */
    fun pickMode(dx: Double, dy: Double, pills: List<IntArray>): Int =
        pills.indexOfFirst { (x, y, w, h) -> dx >= x && dx < x + w && dy >= y - MODE_GAP && dy < y + h + MODE_GAP }

    /**
     * Whether the cursor is still on its way from the card to its pills: inside the box that
     * holds both, with a margin. While it is, the card under the angle must not change — the
     * column usually stands across the neighbour's sector, and reaching for a mode would
     * otherwise drop the card the modes belong to.
     */
    fun inCorridor(dx: Double, dy: Double, cardDx: Int, cardDy: Int, card: Int, pills: List<IntArray>): Boolean {
        if (pills.isEmpty()) return false
        val margin = MODE_OFFSET
        val left = minOf(cardDx - card / 2, pills.minOf { it[0] }) - margin
        val right = maxOf(cardDx + card / 2, pills.maxOf { it[0] + it[2] }) + margin
        val top = minOf(cardDy - card / 2, pills.first()[1]) - margin
        val bottom = maxOf(cardDy + card / 2, pills.last()[1] + MODE_HEIGHT) + margin
        return dx in left.toDouble()..right.toDouble() && dy in top.toDouble()..bottom.toDouble()
    }
}
