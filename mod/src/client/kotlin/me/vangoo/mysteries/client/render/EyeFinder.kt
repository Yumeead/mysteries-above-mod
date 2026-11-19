package me.vangoo.mysteries.client.render

import kotlin.math.abs

/**
 * Where a skin's eyes are, read off the face itself, so that eyes lit by a power (the Sun's Night Vision, the Justiciar's
 * stare) burn in the eyes the player drew rather than in Steve's.
 *
 * The face is the 8×8 front of the head (skin x 8–15, y 8–15) with the second layer (x 40–47) laid over it, as the game
 * shows it. An eye is a patch that:
 * - is one colour, or close to it (a region of neighbours within [SAME]), and clearly off the skin tone ([SKIN]);
 * - keeps to one half of the face and to the middle rows: hair hangs from the top row, a mouth or a beard sits on the
 *   bottom one, and a visor, glasses or a nose cross the middle;
 * - has its mirror twin on the other half.
 *
 * Of all such pairs the one nearest Steve's eye row is taken, with the pairs touching it (the white and the iris of one
 * eye are two colours). A dark iris on dark skin is barely off the skin tone, so one step from a found eye, along its row
 * and towards the middle, a softer bar ([SOFT]) applies. No shape is assumed: a tall narrow eye, a wide one, one low or
 * high on the face, all come out as they were drawn. Anything doubtful — no pair, a pair far from the eye rows — is
 * `null`, and the caller keeps to Steve's eyes.
 *
 * Pure: it reads pixels through a function and knows nothing of textures. Measured on all eighteen default skins of
 * 26.2 (`EyeFinderTest`): each comes out exactly where Steve's eyes are.
 */
object EyeFinder {

    /** A face cell: column and row within the 8×8 face, 0..7 each. */
    data class Cell(val c: Int, val r: Int)

    /** Steve's eyes, the fallback: row 12 of the skin, x 9–10 and 13–14. */
    val STEVE: Set<Cell> = setOf(Cell(1, 4), Cell(2, 4), Cell(5, 4), Cell(6, 4))

    /**
     * The eyes of the skin whose pixels [argb] gives (skin coordinates, ARGB or ABGR: only the distance between colours
     * matters, and alpha is the top byte either way), or `null` if they cannot be told.
     */
    fun find(argb: (x: Int, y: Int) -> Int): Set<Cell>? {
        val face = Array(SIZE) { r -> IntArray(SIZE) { c -> composite(argb(BASE_X + c, FACE_Y + r), argb(OVER_X + c, FACE_Y + r)) } }
        return find(face)
    }

    /** The same over a face already composed, `face[row][column]`, RGB in the low three bytes. */
    fun find(face: Array<IntArray>): Set<Cell>? {
        val skin = (3..6).flatMap { r -> face[r].toList() }.groupingBy { it and RGB }.eachCount().maxBy { it.value }.key

        val candidates = regions(face).filter { region ->
            val rows = region.map { it.r }
            val cols = region.map { it.c }
            val colour = face[region.first().r][region.first().c]
            0 !in rows && SIZE - 1 !in rows &&
                (cols.max() <= 3 || cols.min() >= 4) &&
                region.size <= MAX_EYE && rows.max() - rows.min() <= MAX_EYE_HEIGHT &&
                distance(colour, skin) > SKIN
        }
        val left = candidates.filter { r -> r.all { it.c <= 3 } }
        val right = candidates.filter { r -> r.all { it.c >= 4 } }
        val pairs = ArrayList<Set<Cell>>()
        for (a in left) {
            val mirror = a.mapTo(HashSet()) { Cell(SIZE - 1 - it.c, it.r) }
            val colourA = face[a.first().r][a.first().c]
            for (b in right) {
                val overlap = mirror.count { it in b }
                if (overlap * 3 >= 2 * maxOf(a.size, b.size) && distance(colourA, face[b.first().r][b.first().c]) <= SAME) pairs += a + b
            }
        }
        if (pairs.isEmpty()) return null

        fun centre(p: Set<Cell>) = p.sumOf { it.r }.toDouble() / p.size
        val best = pairs.minBy { abs(centre(it) - STEVE_ROW) }
        if (centre(best) !in EYE_ROWS) return null
        val eyes = best.toHashSet()
        var grew = true
        while (grew) {
            grew = false
            for (p in pairs) {
                if (eyes.containsAll(p)) continue
                if (p.any { cell -> NEIGHBOURS.any { (dc, dr) -> Cell(cell.c + dc, cell.r + dr) in eyes } }) {
                    eyes += p
                    grew = true
                }
            }
        }
        // One step from an eye, along its row and towards the middle, short of the bridge: a dark iris on dark skin.
        for (cell in eyes.toList()) {
            if (cell.c >= 4) continue
            val next = cell.c + 1
            if (next > 2 || Cell(next, cell.r) in eyes) continue
            val a = face[cell.r][next]
            val b = face[cell.r][SIZE - 1 - next]
            if (distance(a, skin) > SOFT && distance(a, b) <= SAME) {
                eyes += Cell(next, cell.r)
                eyes += Cell(SIZE - 1 - next, cell.r)
            }
        }
        return eyes
    }

    /** Regions of neighbouring cells each within [SAME] of the next and of the region's first. */
    private fun regions(face: Array<IntArray>): List<Set<Cell>> {
        val seen = HashSet<Cell>()
        val out = ArrayList<Set<Cell>>()
        for (r in 0 until SIZE) for (c in 0 until SIZE) {
            val start = Cell(c, r)
            if (!seen.add(start)) continue
            val seed = face[r][c]
            val region = HashSet<Cell>()
            val stack = ArrayDeque(listOf(start))
            while (stack.isNotEmpty()) {
                val at = stack.removeLast()
                region += at
                for ((dc, dr) in NEIGHBOURS) {
                    val n = Cell(at.c + dc, at.r + dr)
                    if (n.c !in 0 until SIZE || n.r !in 0 until SIZE || n in seen) continue
                    val colour = face[n.r][n.c]
                    if (distance(colour, seed) <= SAME && distance(colour, face[at.r][at.c]) <= SAME) {
                        seen += n
                        stack += n
                    }
                }
            }
            out += region
        }
        return out
    }

    /** The second layer over the first, as the game draws them. */
    private fun composite(base: Int, over: Int): Int {
        val a = (over ushr 24 and 0xFF) / 255.0
        fun channel(shift: Int) = ((over shr shift and 0xFF) * a + (base shr shift and 0xFF) * (1 - a)).toInt() shl shift
        return channel(16) or channel(8) or channel(0)
    }

    /** The largest difference of any channel. */
    private fun distance(a: Int, b: Int): Int =
        maxOf(abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)), abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)), abs((a and 0xFF) - (b and 0xFF)))

    private val NEIGHBOURS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

    private const val SIZE = 8
    private const val BASE_X = 8
    private const val OVER_X = 40
    private const val FACE_Y = 8
    private const val RGB = 0xFFFFFF

    /** Neighbours this close are one patch. */
    private const val SAME = 28

    /** A patch this far from the skin tone may be an eye. */
    private const val SKIN = 40

    /** Next to a found eye, this far from the skin tone still counts. */
    private const val SOFT = 16

    private const val MAX_EYE = 6
    private const val MAX_EYE_HEIGHT = 3
    private const val STEVE_ROW = 4.0
    private val EYE_ROWS = 2.0..5.0
}
