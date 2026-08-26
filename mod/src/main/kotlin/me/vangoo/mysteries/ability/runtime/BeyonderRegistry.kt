package me.vangoo.mysteries.ability.runtime

import me.vangoo.domain.entities.Beyonder
import me.vangoo.mysteries.ability.context.Beyonders
import java.util.UUID

/**
 * Потойбічні, що зараз у грі.
 *
 * Тільки пам'ять: сховище приходить у фазі 09, і саме воно наповнюватиме реєстр на
 * вхід гравця. До того сюди кладе команда — цього досить, щоб хребет касту працював
 * у грі, а не лише в тестах.
 */
class BeyonderRegistry : Beyonders {

    private val byId = LinkedHashMap<UUID, Beyonder>()

    override fun of(id: UUID): Beyonder? = byId[id]

    override val online: Collection<Beyonder> get() = byId.values

    fun register(beyonder: Beyonder) {
        byId[beyonder.playerId] = beyonder
    }

    fun forget(id: UUID): Beyonder? = byId.remove(id)
}
