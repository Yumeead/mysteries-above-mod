package me.vangoo.mysteries.server

import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.net.payload.BeyonderStatusPayload
import net.minecraft.server.level.ServerPlayer

/**
 * Звідки сервер бере стан гравця.
 *
 * Більше не заглушка: читає живий реєстр. Порожній реєстр означає, що гравець ще
 * звичайний, — і це та сама відповідь, яку екран уже вміє показувати. Наповнює реєстр
 * сховище (фаза 09) на вхід гравця, а до того — зілля з фази 05.
 */
object BeyonderStatusSource {

    fun snapshot(player: ServerPlayer): BeyonderStatusPayload {
        val beyonder = AbilityServices.beyonders.of(player.uuid)
            ?: return BeyonderStatusPayload(null)

        return BeyonderStatusPayload(
            BeyonderStatusPayload.Beyonder(
                pathwayId = beyonder.pathway.name,
                sequence = beyonder.sequence,
                spirituality = beyonder.spirituality,
            ),
        )
    }
}
