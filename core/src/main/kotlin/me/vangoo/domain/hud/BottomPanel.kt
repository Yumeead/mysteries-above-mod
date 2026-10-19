package me.vangoo.domain.hud

/** Geometry shared by rendering and the editor; hotbar items never move. */
object BottomPanel {
    const val WIDTH = 270

    fun healthBar(settings: HudSettings, maximum: Float): Boolean =
        settings.health == HealthStyle.BAR || settings.compactHealthAbove40 && maximum > 40

    fun orb(width: Int, height: Int, healthRise: Int, armor: Boolean): HudRect =
        HudRect(width / 2 - 133, height - 67 - healthRise.coerceAtLeast(0) -
            if (armor) 8 else 0, 48, 44).clamped(width, height)

    fun ability(width: Int, height: Int, leftHanded: Boolean): HudRect =
        HudRect(width / 2 + if (leftHanded) -136 else 116, height - 21, 20, 20).clamped(width, height)

    fun auxiliary(width: Int, height: Int): HudRect =
        HudRect(width / 2 + 97, height - 57, 37, 27).clamped(width, height)

    fun chatRise(height: Int, chatRight: Int, occupied: List<HudRect>): Int =
        // Top placements must not push the whole chat above the screen.
        occupied.filter { it.x < chatRight && it.right > 0 && it.bottom > height - 100 }.minOfOrNull { it.y }
            ?.let { (height - it - 36).coerceIn(0, (height - 80).coerceAtLeast(0)) } ?: 0
}

/** Counts client ticks, so pauses cannot consume the visibility interval. */
class ResourceNumberDisplay {
    private var previous: Pair<Int, Int>? = null
    private var remaining = 0

    fun tick(value: Int, maximum: Int) {
        if (remaining > 0) remaining--
        val current = value to maximum
        if (previous != null && previous != current) remaining = 50
        previous = current
    }

    fun visible(mode: SpiritualityNumbers, low: Boolean): Boolean =
        low || mode == SpiritualityNumbers.ALWAYS || mode == SpiritualityNumbers.ON_CHANGE && remaining > 0

    fun reset() { previous = null; remaining = 0 }
}

data class AuxiliaryValues(val sanityLoss: Int, val potionDigestion: Int, val unassigned: Int) {
    fun clamped(): AuxiliaryValues = AuxiliaryValues(sanityLoss.coerceIn(0, 10000),
        potionDigestion.coerceIn(0, 10000), unassigned.coerceIn(0, 10000))
    fun asList(): List<Int> = listOf(sanityLoss, potionDigestion, unassigned)
}

fun interface AuxiliaryHudSource { fun snapshot(): AuxiliaryValues }

/** Deliberately unconnected to gameplay; replace the source when the two systems are ready. */
object PreviewAuxiliaryHudSource : AuxiliaryHudSource {
    override fun snapshot() = AuxiliaryValues(5400, 7200, 0)
}

/** Fits a circular aperture to opaque source pixels, keeping the liquid's original bottom row. */
data class OrbMask(val x: Int, val y: Int, val size: Int) {
    fun contains(column: Int, row: Int): Boolean {
        val dx = 2 * (column - x) - size + 1
        val dy = 2 * (row - y) - size + 1
        return column in x until x + size && row in y until y + size && dx * dx + dy * dy <= size * size
    }

    companion object {
        fun fit(full: List<IntArray>, bottom: Int = 49): OrbMask {
            require(full.size > bottom && full.all { it.size >= 50 }) { "Expected the original flask painting canvas" }
            for (size in 28 downTo 8 step 2) {
                val preferred = full.first().size / 2 - 1 - size / 2
                for (x in (13..(49 - size)).sortedBy { kotlin.math.abs(it - preferred) }) {
                    val mask = OrbMask(x, bottom + 1 - size, size)
                    if ((mask.y..bottom).all { row -> (x until x + size).all { column ->
                        !mask.contains(column, row) || full[row][column] ushr 24 == 255
                    } }) return mask
                }
            }
            error("No native circular liquid window fits the source painting")
        }
    }
}
