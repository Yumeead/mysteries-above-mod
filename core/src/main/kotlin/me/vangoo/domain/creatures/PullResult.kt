package me.vangoo.domain.creatures

import java.util.UUID

/**
 * Результат правила тяжіння: до кого тяжіє джерело і наскільки сильно.
 *
 * @property targetId UUID обраного магніта
 * @property strength сила нуджа ∈ (0,1]
 */
data class PullResult(val targetId: UUID, val strength: Double)
