package me.vangoo.domain.market

import java.util.UUID

/**
 * A counter of breaches of the peace at a gathering. Pure domain: the first breach is a
 * warning, the following ones an expulsion. The application layer carries out the sanction.
 */
class GatheringConduct {

    enum class Sanction { WARN, KICK }

    private val strikes = HashMap<UUID, Int>()

    fun recordViolation(playerId: UUID): Sanction {
        val count = strikes.merge(playerId, 1, Int::plus)!!
        return if (count <= 1) Sanction.WARN else Sanction.KICK
    }

    fun reset() = strikes.clear()
}
