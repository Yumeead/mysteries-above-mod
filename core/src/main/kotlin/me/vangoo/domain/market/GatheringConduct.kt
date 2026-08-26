package me.vangoo.domain.market

import java.util.UUID

/**
 * Лічильник порушень спокою на зборах. Чистий домен: перше порушення — попередження,
 * наступні — вигнання. Санкцію виконує шар застосунку.
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
