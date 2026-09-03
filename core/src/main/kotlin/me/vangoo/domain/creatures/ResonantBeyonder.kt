package me.vangoo.domain.creatures

import java.util.UUID

/**
 * A magnet candidate for the Law of Convergence: an online Beyonder a resonant source may be
 * drawn to.
 *
 * @property id the player's UUID
 * @property pathway the pathway name (compared case-insensitively)
 * @property group the pathway group name (for "neighbour" resonance)
 * @property sequenceLevel the current sequence level (9 = weakest, 0 = strongest)
 */
data class ResonantBeyonder(
    val id: UUID,
    val pathway: String?,
    val group: String?,
    val sequenceLevel: Int,
    val x: Double,
    val z: Double,
)
