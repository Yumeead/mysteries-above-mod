package me.vangoo.domain.hud

/** Geometry shared by rendering and the editor; hotbar items never move. */
object BottomPanel {
    const val WIDTH = 270

    fun healthBar(settings: HudSettings, maximum: Float): Boolean =
        settings.health == HealthStyle.BAR || settings.compactHealthAbove40 && maximum > 40

    fun orb(width: Int, height: Int, healthRise: Int, armor: Boolean): HudRect =
        HudRect(width / 2 - 133, height - 67 - healthRise.coerceAtLeast(0) -
            if (armor) 8 else 0, 48, 44).clamped(width, height)

    fun orbLabelX(textWidth: Int, originX: Int, screenWidth: Int): Int =
        (21 - textWidth / 2).coerceIn(1 - originX, screenWidth - originX - textWidth - 1)

    fun ability(width: Int, height: Int, leftHanded: Boolean): HudRect {
        val preferred = width / 2 + if (leftHanded) -223 else 116
        val beside = if (preferred >= 1) preferred else width / 2 + 123
        val fits = beside + 86 <= width - 1
        // Narrow viewports need the whole group above the hotbar instead of across its slots.
        return HudRect(if (fits) beside else width - 87, height - if (fits) 21 else 80,
            20, 20).clamped(width, height)
    }

    fun auxiliary(width: Int, height: Int, slot: HudRect = ability(width, height, false)): HudRect =
        HudRect(slot.right + 4, slot.bottom - 42, 62, 42).clamped(width, height)

    fun abilityLabel(width: Int, height: Int, slot: HudRect, textHeight: Int): HudRect =
        HudRect(slot.x, if (slot.y >= textHeight + 24) slot.y - textHeight - 24 else slot.y + 44,
            86, textHeight).clamped(width, height)

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

data class AuxiliaryValues(val sanityLoss: Int, val potionDigestion: Int) {
    fun asList(): List<Int> = listOf(sanityLoss, potionDigestion)
}

/** Every pathway uses Fool's aperture, regardless of transparency at the painting's edge. */
object OrbMask {
    const val x = 17
    const val y = 22
    const val size = 28

    fun contains(column: Int, row: Int): Boolean {
        val dx = 2 * (column - x) - size + 1
        val dy = 2 * (row - y) - size + 1
        return column in x until x + size && row in y until y + size && dx * dx + dy * dy <= size * size
    }
}
