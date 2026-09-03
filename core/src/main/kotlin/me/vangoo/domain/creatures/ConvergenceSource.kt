package me.vangoo.domain.creatures

/**
 * A resonant source drawn to the nearest kindred Beyonder: a dropped Characteristic, a remnant
 * or a mythic creature. Keyed by pathway + sequence.
 *
 * @property pathway the source's pathway name (compared case-insensitively)
 * @property group the source's pathway group name
 * @property sequence the source's sequence
 */
data class ConvergenceSource(
    val pathway: String?,
    val group: String?,
    val sequence: Int,
    val x: Double,
    val z: Double,
)
