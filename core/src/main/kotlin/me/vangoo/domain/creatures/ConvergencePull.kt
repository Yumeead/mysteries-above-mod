package me.vangoo.domain.creatures

import kotlin.math.sqrt

/**
 * The pure rule of the Law of Convergence (the counterpart of [CreatureSelector]). Picks which
 * resonant Beyonder a source is drawn to, and how strongly. Deterministic and stateless.
 *
 * Resonance: the same pathway (strong) or the same pathway group (neighbour). The magnet is
 * chosen by the weight `resonanceWeight × seqMultiplier / (1 + distance)` (the largest weight;
 * ties broken by the closest, then by the smaller UUID). The nudge strength
 * `strength = resonanceWeight × seqMultiplier / MAX_SCORE` ∈ (0,1] does NOT depend on the
 * distance — a needed essence pulls just as noticeably across the whole radius.
 */
class ConvergencePull {

    fun computePull(
        source: ConvergenceSource?,
        beyonders: Collection<ResonantBeyonder>?,
        radius: Double,
    ): PullResult? {
        if (source == null || beyonders.isNullOrEmpty()) return null
        val r2 = radius * radius

        var best: ResonantBeyonder? = null
        var bestWeight = -1.0
        var bestDistSq = Double.MAX_VALUE
        var bestScore = 0.0

        for (b in beyonders) {
            val resonance = resonanceWeight(source, b)
            if (resonance <= 0.0) continue

            val dx = b.x - source.x
            val dz = b.z - source.z
            val distSq = dx * dx + dz * dz
            if (distSq > r2) continue

            val score = resonance * seqMultiplier(source, b)
            val weight = score / (1.0 + sqrt(distSq))

            val better = weight > bestWeight ||
                (weight == bestWeight && distSq < bestDistSq) ||
                (weight == bestWeight && distSq == bestDistSq && (best == null || b.id < best.id))
            if (better) {
                best = b
                bestWeight = weight
                bestDistSq = distSq
                bestScore = score
            }
        }

        return best?.let { PullResult(it.id, bestScore / MAX_SCORE) }
    }

    private fun resonanceWeight(s: ConvergenceSource, b: ResonantBeyonder): Double {
        if (s.pathway == null || b.pathway == null) return 0.0
        if (s.pathway.equals(b.pathway, ignoreCase = true)) return SAME_PATHWAY_WEIGHT
        if (s.group != null && b.group != null && s.group.equals(b.group, ignoreCase = true)) {
            return NEIGHBOR_WEIGHT
        }
        return 0.0
    }

    private fun seqMultiplier(s: ConvergenceSource, b: ResonantBeyonder): Double = when (s.sequence) {
        b.sequenceLevel - 1 -> NEXT_NEEDED_MULT
        b.sequenceLevel -> CURRENT_MULT
        else -> 1.0
    }

    private companion object {
        const val SAME_PATHWAY_WEIGHT = 2.0
        const val NEIGHBOR_WEIGHT = 1.0
        const val NEXT_NEEDED_MULT = 4.0 // source.sequence == beyonder.seq - 1
        const val CURRENT_MULT = 2.0 // source.sequence == beyonder.seq
        const val MAX_SCORE = SAME_PATHWAY_WEIGHT * NEXT_NEEDED_MULT // 8.0
    }
}
