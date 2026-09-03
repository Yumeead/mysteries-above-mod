package me.vangoo.domain.creatures

/** A pure rule: whether a point is far enough (horizontally) from the world spawn for creatures to appear. */
object SpawnDistanceGate {

    /**
     * @param dx the X difference from the world spawn point
     * @param dz the Z difference from the world spawn point
     * @param minDistance the minimum horizontal distance in blocks
     * @return true if sqrt(dx^2 + dz^2) >= minDistance
     */
    fun isFarEnough(dx: Double, dz: Double, minDistance: Double): Boolean =
        minDistance <= 0.0 || (dx * dx + dz * dz) >= (minDistance * minDistance)
}
