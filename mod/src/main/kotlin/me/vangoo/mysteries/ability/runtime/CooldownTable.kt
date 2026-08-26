package me.vangoo.mysteries.ability.runtime

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.mysteries.ability.context.Cooldowns
import java.util.UUID
import kotlin.math.ceil

/**
 * Кулдауни в серверних тіках.
 *
 * Годинник — [now], лічильник тіків сервера, а не настінний час. Різниця видима:
 * поставлена на паузу одиночна гра стоїть, і кулдаун разом із нею. `System.currentTimeMillis`
 * цокав би далі, і сила, витрачена перед виходом у меню, поверталась би безкоштовно.
 */
class CooldownTable(private val now: () -> Int) : Cooldowns {

    private data class Key(val playerId: UUID, val identity: AbilityIdentity)

    private val readyAt = HashMap<Key, Int>()
    private val lockedUntil = HashMap<UUID, Int>()

    override fun remainingSeconds(playerId: UUID, identity: AbilityIdentity): Long {
        val tick = now()
        val ready = maxOf(readyAt[Key(playerId, identity)] ?: tick, lockedUntil[playerId] ?: tick)
        return toSeconds(ready - tick)
    }

    override fun set(playerId: UUID, identity: AbilityIdentity, seconds: Int) {
        if (seconds <= 0) return
        readyAt[Key(playerId, identity)] = now() + seconds * TICKS_PER_SECOND
    }

    override fun clear(playerId: UUID, identity: AbilityIdentity) {
        readyAt.remove(Key(playerId, identity))
    }

    override fun lockAll(playerId: UUID, seconds: Int) {
        if (seconds <= 0) return
        val until = now() + seconds * TICKS_PER_SECOND
        lockedUntil.merge(playerId, until, ::maxOf)
    }

    override fun forget(playerId: UUID) {
        readyAt.keys.removeIf { it.playerId == playerId }
        lockedUntil.remove(playerId)
    }

    /** Прибирає таймери, що вже спливли: інакше мапа росте на кожну здібність назавжди. */
    fun prune() {
        val tick = now()
        readyAt.values.removeIf { it <= tick }
        lockedUntil.values.removeIf { it <= tick }
    }

    /** Округлення вгору: «лишилось 0 с» при ще не спливлому таймері збивало б із пантелику. */
    private fun toSeconds(ticks: Int): Long =
        if (ticks <= 0) 0L else ceil(ticks.toDouble() / TICKS_PER_SECOND).toLong()

    private companion object {
        const val TICKS_PER_SECOND = 20
    }
}
