package me.vangoo.domain.valueobjects

/**
 * A saved sea marker (Sea Memory, Tyrant). A pure VO — the world and the coordinates are kept
 * as primitives, the effect layer converts them into a `Location`.
 */
data class Waypoint(
    val name: String,
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
) {
    fun withName(newName: String): Waypoint = copy(name = newName)
}
