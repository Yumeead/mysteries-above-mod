package me.vangoo.domain.valueobjects

/**
 * A world position for the persistent undead retinue (Death, Seq. 6) — the world and the
 * coordinates as primitives, like [Waypoint]; the effect layer converts them into a `Location`.
 */
data class RetinuePoint(
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
)
