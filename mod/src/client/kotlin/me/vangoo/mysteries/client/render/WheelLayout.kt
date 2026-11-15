package me.vangoo.mysteries.client.render

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

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

    /** How far off the ring the arrow card to the other ring stands. */
    const val SWITCH_GAP = 14

    /**
     * The arrow card to the other ring, `[x, y, w, h]` from the centre: off the ring's right side,
     * level with the centre. Always the right: the left of the screen is the side panel's.
     */
    fun switcher(onPage: Int, card: Int): IntArray =
        intArrayOf(radius(onPage) + card / 2 + SWITCH_GAP, -card / 2, card, card)

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

    // ── The arc of sub-choices ──────────────────────────────────────────────────

    /** One row of an open arc: where its label starts and the slice of the band it owns. */
    class Slice(
        /** The label's near end from the centre: its left edge on the right side, its right edge on the left. */
        val x: Int,
        /** The row's top; the row is [ROW] tall. */
        val y: Int,
        /** The band slice level with the row, in degrees clockwise from straight up, [from] below [to]. */
        val from: Double,
        val to: Double,
        /** Where the stem back to the card ends, for the row nearest it when the rows had to slide away. */
        val stem: Double? = null,
    ) {
        val right: Boolean get() = x >= 0

        /** The whole stretch of band the row answers to: its slice and its stem. */
        val start: Double get() = minOf(from, stem ?: from)
        val end: Double get() = maxOf(to, stem ?: to)
    }

    /** One label line; how far past the ring the band runs, and how far past the band the labels start. */
    const val ROW = 11
    const val BAND_GAP = 19
    const val LABEL_GAP = 5

    /** The band's radius: clear of every card, a hovered (grown) one's corners included. */
    fun band(onPage: Int): Int = radius(onPage) + BAND_GAP

    /**
     * Where the sub-choices of a card stand once it is hovered. Names laid along a curve overlap, so
     * each gets a row of its own: the rows stack on the card's height and each is pushed out just far
     * enough to clear the ring at its height — together they curve round the ring as an arc and still
     * never share a line, and none reaches a card. A card on the left half opens to the left, every
     * other to the right. The stack slides to stay within [half] of the centre and on the band; on the
     * right it parts round [avoid] (the arrow card's rows). Each row owns the slice of band level with
     * it; when the rows slid away from the card, the nearest one carries a stem back to it, so the arc
     * always grows out of its card.
     */
    fun arc(cardDx: Int, cardDy: Int, onPage: Int, count: Int, half: Int, avoid: IntRange?): List<Slice> {
        if (count <= 0) return emptyList()
        val band = band(onPage).toDouble()
        val reach = band + LABEL_GAP
        val right = cardDx >= 0
        val tops = stack(count, cardDy, minOf(half, band.toInt() - 1), avoid.takeIf { right })
        val slices = tops.map { top ->
            // The row's edge nearest the centre line is where the ring bulges out furthest.
            val near = if (top < 0 && top + ROW > 0) 0.0 else minOf(abs(top), abs(top + ROW)).toDouble()
            val x = sqrt(reach * reach - near * near).roundToInt()
            val a = bandAngle(top.toDouble(), band, right)
            val b = bandAngle((top + ROW).toDouble(), band, right)
            Slice(if (right) x else -x, top, minOf(a, b), maxOf(a, b))
        }.toMutableList()
        val card = degrees(cardDx.toDouble(), cardDy.toDouble())
        val first = slices.indices.minBy { slices[it].from }
        val last = slices.indices.maxBy { slices[it].to }
        if (card < slices[first].from) slices[first] = slices[first].let { Slice(it.x, it.y, it.from, it.to, stem = card) }
        if (card > slices[last].to) slices[last] = slices[last].let { Slice(it.x, it.y, it.from, it.to, stem = card) }
        return slices
    }

    /**
     * The row under a cursor at ([dx], [dy]), or `-1`. Only past the ring and on the arc's side: inside
     * the ring the cursor points at cards, so going back onto the card, or another, leaves the arc. A
     * row is its whole line, so the label itself can be pointed at; above and below the rows, close to
     * the band, its slices decide, which is how the stretch of band back to the card picks its row.
     */
    fun pickArc(dx: Double, dy: Double, onPage: Int, card: Int, slices: List<Slice>): Int {
        val distance = hypot(dx, dy)
        if (slices.isEmpty() || distance < radius(onPage) + card / 2) return -1
        if ((dx >= 0) != slices.first().right) return -1
        val row = slices.indexOfFirst { dy >= it.y && dy < it.y + ROW }
        if (row >= 0) return row
        if (dy >= slices.minOf { it.y } && dy < slices.maxOf { it.y } + ROW) return -1 // the parting round the arrow card
        if (distance > band(onPage) + LABEL_GAP + ROW) return -1
        val angle = degrees(dx, dy)
        return slices.indexOfFirst { angle >= it.start && angle <= it.end }
    }

    /** The tops of [count] rows centred on [centre] within ±[limit], parted round [avoid] if they would cross it. */
    private fun stack(count: Int, centre: Int, limit: Int, avoid: IntRange?): IntArray {
        val total = count * ROW
        val top = (centre - total / 2).coerceIn(-limit, (limit - total).coerceAtLeast(-limit))
        val plain = IntArray(count) { top + it * ROW }
        if (avoid == null || plain.none { it < avoid.last && it + ROW > avoid.first }) return plain
        var above = plain.count { it + ROW / 2 < (avoid.first + avoid.last) / 2 }
        while (above > 0 && avoid.first - above * ROW < -limit) above--
        while (above < count && avoid.last + (count - above) * ROW > limit) above++
        return IntArray(count) { if (it < above) avoid.first - (above - it) * ROW else avoid.last + (it - above) * ROW }
    }

    /** Where height [y] meets the band, on the right or the left. */
    private fun bandAngle(y: Double, band: Double, right: Boolean): Double {
        val a = Math.toDegrees(acos((-y / band).coerceIn(-1.0, 1.0)))
        return if (right) a else 360.0 - a
    }

    /** Degrees clockwise from straight up, 0 until 360. */
    fun degrees(dx: Double, dy: Double): Double = (Math.toDegrees(atan2(dx, -dy)) + 360.0) % 360.0
}
