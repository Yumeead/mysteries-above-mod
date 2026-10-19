package me.vangoo.domain.hud

import kotlin.math.PI

/** A damped pendulum driven by camera yaw; rendering interpolates its fixed-tick samples. */
class TagMotion {
    private var yaw: Float? = null
    private var previous = 0f
    private var angle = 0f
    private var velocity = 0f

    fun tick(cameraYaw: Float, walking: Float = 0f, reducedMotion: Boolean = false) {
        require(cameraYaw.isFinite() && walking.isFinite())
        val oldYaw = yaw
        yaw = cameraYaw
        if (reducedMotion || oldYaw == null) {
            previous = 0f; angle = 0f; velocity = 0f
            return
        }
        val delta = ((cameraYaw - oldYaw + 180f) % 360f + 360f) % 360f - 180f
        // Positive screen rotation sends the hanging end left when the camera turns right.
        val target = (delta * .012f + walking).coerceIn(-LIMIT, LIMIT)
        previous = angle
        repeat(8) {
            val dt = .05f / 8
            velocity += (196f * (target - angle) - 18f * velocity) * dt
            angle += velocity * dt
            if (angle !in -LIMIT..LIMIT) {
                angle = angle.coerceIn(-LIMIT, LIMIT)
                velocity = 0f
            }
        }
    }

    fun angle(partialTick: Float): Float = previous +
        (angle - previous) * partialTick.coerceIn(0f, 1f)

    fun reset() { yaw = null; previous = 0f; angle = 0f; velocity = 0f }

    companion object {
        const val LIMIT = (PI / 10).toFloat()
    }
}

/** The first painted row is the attachment, rather than the centre of the hanging ornament. */
data class TagAttachment(val x: Float, val y: Float) {
    companion object {
        fun find(width: Int, height: Int, opaque: (Int, Int) -> Boolean): TagAttachment {
            for (y in 0 until height) {
                val columns = (0 until width).filter { x -> opaque(x, y) }
                if (columns.isNotEmpty()) return TagAttachment((columns.first() + columns.last() + 1) / 2f, y + .5f)
            }
            error("The tag artwork has no attachment pixels")
        }
    }
}
