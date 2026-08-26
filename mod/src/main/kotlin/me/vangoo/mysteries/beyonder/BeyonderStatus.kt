package me.vangoo.mysteries.beyonder

import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.BeyonderStatusPayload
import net.minecraft.server.level.ServerPlayer

/**
 * Стан Потойбічного, яким його бачить клієнт.
 *
 * Джерело — живий реєстр. Порожній реєстр означає, що гравець ще звичайний, і це та
 * сама відповідь, яку екран уже вміє показувати. Наповнює реєстр сховище (фаза 09) на
 * вхід гравця, а до того — зілля.
 *
 * [push] кличеться щоразу, коли стан **міг** змінитись: після зілля, після касту, на
 * вхід у світ. Дешевше надіслати зайвий пакет, ніж лишити екран із застарілими числами —
 * саме на цьому в плагіні вічно застрягав бар духовності.
 */
object BeyonderStatus {

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

    fun push(player: ServerPlayer) {
        ModChannels.BEYONDER_STATUS.sendTo(listOf(player), snapshot(player))
    }
}
