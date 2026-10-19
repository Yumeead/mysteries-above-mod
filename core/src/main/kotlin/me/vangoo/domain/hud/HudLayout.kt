package me.vangoo.domain.hud

import kotlin.math.abs

data class HudRect(val x: Int, val y: Int, val width: Int, val height: Int) {
    val right get() = x + width
    val bottom get() = y + height
    fun contains(x: Int, y: Int) = x >= this.x && x < right && y >= this.y && y < bottom
    fun overlaps(other: HudRect) = x < other.right && right > other.x && y < other.bottom && bottom > other.y
    fun clamped(screenWidth: Int, screenHeight: Int): HudRect = copy(
        x = x.coerceIn(0, (screenWidth - width).coerceAtLeast(0)),
        y = y.coerceIn(0, (screenHeight - height).coerceAtLeast(0)),
    )
}

data class HudPosition(
    val horizontal: HorizontalAnchor,
    val vertical: VerticalAnchor,
    val offsetX: Int,
    val offsetY: Int,
) {
    fun resolve(width: Int, height: Int, screenWidth: Int, screenHeight: Int): HudRect = HudRect(
        origin(horizontal.ordinal, screenWidth, width) + offsetX.coerceIn(-16384, 16384),
        origin(vertical.ordinal, screenHeight, height) + offsetY.coerceIn(-16384, 16384),
        width, height,
    ).clamped(screenWidth, screenHeight)

    companion object {
        fun of(rect: HudRect, screenWidth: Int, screenHeight: Int): HudPosition {
            val horizontal = HorizontalAnchor.entries[nearest(rect.x, screenWidth, rect.width)]
            val vertical = VerticalAnchor.entries[nearest(rect.y, screenHeight, rect.height)]
            return HudPosition(horizontal, vertical,
                rect.x - origin(horizontal.ordinal, screenWidth, rect.width),
                rect.y - origin(vertical.ordinal, screenHeight, rect.height))
        }

        private fun origin(anchor: Int, screen: Int, size: Int) = when (anchor) {
            0 -> 0
            1 -> (screen - size) / 2
            else -> screen - size
        }

        private fun nearest(value: Int, screen: Int, size: Int) =
            (0..2).minBy { abs(value - origin(it, screen, size)) }
    }
}

object HudLayout {
    fun avoid(rect: HudRect, screenWidth: Int, screenHeight: Int, obstacles: List<HudRect>): HudRect {
        val original = rect.clamped(screenWidth, screenHeight)
        if (obstacles.none(original::overlaps)) return original
        val xs = listOf(original.x, 4, screenWidth - rect.width - 4) +
            obstacles.flatMap { listOf(it.x - rect.width - 4, it.right + 4) }
        val ys = listOf(original.y, 4, screenHeight - rect.height - 4) +
            obstacles.flatMap { listOf(it.y - rect.height - 4, it.bottom + 4) }
        return xs.flatMap { x -> ys.map { y -> original.copy(x = x, y = y).clamped(screenWidth, screenHeight) } }
            .filter { candidate -> obstacles.none(candidate::overlaps) }
            .minByOrNull { abs(it.x - original.x) + abs(it.y - original.y) } ?: original
    }

    fun snap(rect: HudRect, screenWidth: Int, screenHeight: Int, targets: List<HudRect>, distance: Int = 4): HudRect {
        val xs = mutableListOf(4, (screenWidth - rect.width) / 2, screenWidth - rect.width - 4)
        val ys = mutableListOf(4, (screenHeight - rect.height) / 2, screenHeight - rect.height - 4)
        for (target in targets) {
            xs += listOf(target.x, target.right - rect.width, target.x - rect.width - 4,
                target.right + 4, target.x + (target.width - rect.width) / 2)
            ys += listOf(target.y, target.bottom - rect.height, target.y - rect.height - 4,
                target.bottom + 4, target.y + (target.height - rect.height) / 2)
        }
        fun closest(value: Int, choices: List<Int>) = choices.minByOrNull { abs(it - value) }
            ?.takeIf { abs(it - value) <= distance } ?: value
        return rect.copy(x = closest(rect.x, xs), y = closest(rect.y, ys)).clamped(screenWidth, screenHeight)
    }
}
