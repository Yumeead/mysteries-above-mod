package me.vangoo.mysteries.ability.context

import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * Текст гравцеві.
 *
 * `caster.sendSystemMessage(...)` доступний і напряму — цей інтерфейс існує заради
 * адресації **за UUID**: ціль здібності часто вже офлайн або в іншому вимірі, і
 * шукати її по всіх світах кожна здібність окремо не має.
 */
interface Messaging {

    fun tell(player: ServerPlayer, message: Component)

    fun actionBar(player: ServerPlayer, message: Component)

    /** Мовчки нічого не робить, якщо гравця немає в мережі. */
    fun tell(playerId: UUID, message: Component)

    fun actionBar(playerId: UUID, message: Component)
}
