package me.vangoo.domain.services

/**
 * Where a projectile will be, tick by tick, the way the game moves one: it travels its velocity, then
 * the air slows it ([drag] of what is left each tick) and gravity pulls it down. Good for arrows,
 * tridents and thrown things; a fireball steers itself and is only roughly forecast.
 */
object ProjectileForecast {

    /** Vanilla's air drag for arrows and thrown projectiles: they keep this share of their speed each tick. */
    const val AIR_DRAG = 0.99

    /**
     * The first tick (1-based) at which a projectile at ([px], [py], [pz]) moving ([vx], [vy], [vz]) a
     * tick comes within [radius] of the point ([tx], [ty], [tz]), looking [maxTicks] ahead — or `null`
     * if it never does. The segment travelled within a tick is checked, not just its ends: a fast arrow
     * covers three blocks in one.
     */
    fun ticksToReach(
        px: Double, py: Double, pz: Double,
        vx: Double, vy: Double, vz: Double,
        gravity: Double,
        tx: Double, ty: Double, tz: Double,
        radius: Double,
        maxTicks: Int,
        drag: Double = AIR_DRAG,
    ): Int? {
        var x = px; var y = py; var z = pz
        var dx = vx; var dy = vy; var dz = vz
        for (tick in 1..maxTicks) {
            if (segmentDistanceSq(x, y, z, dx, dy, dz, tx, ty, tz) <= radius * radius) return tick
            x += dx; y += dy; z += dz
            dx *= drag; dy = dy * drag - gravity; dz *= drag
        }
        return null
    }

    /** Squared distance from the point to the segment from ([x], [y], [z]) along ([dx], [dy], [dz]). */
    private fun segmentDistanceSq(x: Double, y: Double, z: Double, dx: Double, dy: Double, dz: Double, tx: Double, ty: Double, tz: Double): Double {
        val length = dx * dx + dy * dy + dz * dz
        val t = if (length < 1e-12) 0.0 else (((tx - x) * dx + (ty - y) * dy + (tz - z) * dz) / length).coerceIn(0.0, 1.0)
        val cx = x + dx * t - tx
        val cy = y + dy * t - ty
        val cz = z + dz * t - tz
        return cx * cx + cy * cy + cz * cz
    }
}
