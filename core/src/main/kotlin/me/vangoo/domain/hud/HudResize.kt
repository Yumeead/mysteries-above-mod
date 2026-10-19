package me.vangoo.domain.hud

import kotlin.math.abs
import kotlin.math.roundToInt

enum class HudEdge(val horizontal: Int, val vertical: Int) {
    TOP_LEFT(-1, -1), TOP(0, -1), TOP_RIGHT(1, -1), RIGHT(1, 0),
    BOTTOM_RIGHT(1, 1), BOTTOM(0, 1), BOTTOM_LEFT(-1, 1), LEFT(-1, 0);

    companion object {
        fun at(rect: HudRect, x: Int, y: Int, margin: Int = 3): HudEdge? {
            if (x < rect.x - margin || x > rect.right + margin ||
                y < rect.y - margin || y > rect.bottom + margin) return null
            val horizontal = when {
                abs(x - rect.x) <= margin -> -1
                abs(x - rect.right) <= margin -> 1
                else -> 0
            }
            val vertical = when {
                abs(y - rect.y) <= margin -> -1
                abs(y - rect.bottom) <= margin -> 1
                else -> 0
            }
            return entries.firstOrNull { it.horizontal == horizontal && it.vertical == vertical }
        }
    }
}

data class HudResize(val start: HudRect, val scale: Float, val edge: HudEdge) {
    fun resized(dx: Int, dy: Int, screenWidth: Int, screenHeight: Int): Pair<HudRect, Float> {
        val w = start.width.toFloat()
        val h = start.height.toFloat()
        val ex = edge.horizontal
        val ey = edge.vertical
        val change = when {
            ex == 0 -> dy * ey / h
            ey == 0 -> dx * ex / w
            else -> (dx * ex * w + dy * ey * h) / (w * w + h * h)
        }
        fun room(direction: Int, origin: Int, size: Int, screen: Int): Float = when {
            direction < 0 -> (origin + size).toFloat()
            direction > 0 -> (screen - origin).toFloat()
            else -> minOf(origin + size / 2f, screen - origin - size / 2f) * 2
        }
        val maximum = minOf(HudSettings.MAX_SCALE / scale,
            room(ex, start.x, start.width, screenWidth) / w,
            room(ey, start.y, start.height, screenHeight) / h,
            (screenWidth - 8).coerceAtLeast(1) / w,
            (screenHeight - 8).coerceAtLeast(1) / h).coerceAtLeast(0f)
        val minimum = minOf(HudSettings.MIN_SCALE / scale, maximum)
        val factor = (1f + change).coerceIn(minimum, maximum)
        val width = (w * factor).roundToInt().coerceAtLeast(1)
        val height = (h * factor).roundToInt().coerceAtLeast(1)
        val x = start.x + when {
            ex < 0 -> start.width - width
            ex > 0 -> 0
            else -> (start.width - width) / 2
        }
        val y = start.y + when {
            ey < 0 -> start.height - height
            ey > 0 -> 0
            else -> (start.height - height) / 2
        }
        return HudRect(x, y, width, height).clamped(screenWidth, screenHeight) to (scale * factor)
    }
}
