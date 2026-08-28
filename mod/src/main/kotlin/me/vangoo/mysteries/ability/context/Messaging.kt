package me.vangoo.mysteries.ability.context

import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * Текст гравцеві. **Єдиний спосіб** — прямий `sendSystemMessage` поза цим файлом і
 * [me.vangoo.mysteries.ability.runtime.ServerMessaging] не кличеться.
 *
 * Перше, заради чого сервіс існує, — адресація **за UUID**: ціль здібності часто вже
 * офлайн або в іншому вимірі, і шукати її по всіх світах кожна здібність окремо не
 * має. Правило спершу було м'якшим («напряму теж можна»), і саме це його й з'їло:
 * фаза 08 написала півдесятка місць виду `level.server.playerList.getPlayer(id)
 * ?.sendSystemMessage(…)` — тобто вручну переписаний `find` цього ж сервісу.
 *
 * Друге — те, що по цьому рядку колись доведеться пройтись: переклад, глушення на
 * час касту, лог у тесті, пакет замість чату. Перевірка фази 03 («метод потрапляє в
 * контекст, лише якщо додає щось понад один ванільний виклик») тут проходить не
 * стільки арифметикою, скільки тим, що це **точка**, а не розсип.
 *
 * У сесії та в обробнику події їде **сам сервіс**, а не контекст: `Messaging` не
 * тримає нічиєї особи й живе стільки, скільки сервер. Те саме вже зроблено зі
 * `scheduling` і `beyonders`.
 */
interface Messaging {

    fun tell(player: ServerPlayer, message: Component)

    fun actionBar(player: ServerPlayer, message: Component)

    /** Мовчки нічого не робить, якщо гравця немає в мережі. */
    fun tell(playerId: UUID, message: Component)

    fun actionBar(playerId: UUID, message: Component)
}
