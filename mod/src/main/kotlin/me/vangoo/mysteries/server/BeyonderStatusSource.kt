package me.vangoo.mysteries.server

import me.vangoo.domain.services.SpiritualityCalculator
import me.vangoo.domain.valueobjects.Mastery
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.domain.valueobjects.Spirituality
import me.vangoo.mysteries.net.payload.BeyonderStatusPayload
import net.minecraft.server.level.ServerPlayer

/**
 * Звідки сервер бере стан гравця.
 *
 * ЗАГЛУШКА: справжнього стану ще нема — він приїде з персистентністю. Важлива тут
 * не відповідь, а форма: пакетний шар уже говорить із цим інтерфейсом, тож заміна
 * заглушки на сховище не зачепить ні канали, ні екран.
 */
object BeyonderStatusSource {

    private val spirituality = SpiritualityCalculator()

    fun snapshot(player: ServerPlayer): BeyonderStatusPayload {
        val sequence = Sequence.starter()
        val mastery = Mastery(0.0)
        val maximum = spirituality.calculateMaximumSpirituality(sequence, mastery)
        return BeyonderStatusPayload(
            BeyonderStatusPayload.Beyonder(
                pathwayId = SAMPLE_PATHWAY,
                sequence = sequence,
                spirituality = Spirituality.full(maximum),
            ),
        )
    }

    private const val SAMPLE_PATHWAY = "fool"
}
