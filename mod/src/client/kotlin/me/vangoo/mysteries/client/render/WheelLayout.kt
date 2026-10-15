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
}
