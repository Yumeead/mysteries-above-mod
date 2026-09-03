package me.vangoo.domain.creatures

import java.util.UUID

/**
 * The result of the pull rule: which magnet the source is drawn to and how strongly.
 *
 * @property targetId UUID of the chosen magnet
 * @property strength nudge strength ∈ (0,1]
 */
data class PullResult(val targetId: UUID, val strength: Double)
