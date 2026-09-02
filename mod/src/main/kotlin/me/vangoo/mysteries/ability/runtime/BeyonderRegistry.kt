package me.vangoo.mysteries.ability.runtime

import me.vangoo.domain.entities.Beyonder
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.mysteries.ability.context.Beyonders
import java.util.UUID

/**
 * Потойбічні, що зараз у грі.
 *
 * Тільки пам'ять: сховище приходить у фазі 09, і саме воно наповнюватиме реєстр на
 * вхід гравця. До того сюди кладе команда — цього досить, щоб хребет касту працював
 * у грі, а не лише в тестах.
 */
class BeyonderRegistry(
    /**
     * Стан тоглів. Лямбдою, а не посиланням на `PassiveTicker`: той будується **після**
     * реєстру (йому потрібні контексти, яким потрібен реєстр), тож пряме поле замкнуло б
     * коло ініціалізації.
     */
    private val toggles: (UUID, AbilityIdentity) -> Boolean = { _, _ -> false },
) : Beyonders {

    private val byId = LinkedHashMap<UUID, Beyonder>()

    override fun of(id: UUID): Beyonder? = byId[id]

    override fun isToggled(id: UUID, ability: AbilityIdentity): Boolean = toggles(id, ability)

    override val online: Collection<Beyonder> get() = byId.values

    fun register(beyonder: Beyonder) {
        byId[beyonder.playerId] = beyonder
    }

    fun forget(id: UUID): Beyonder? = byId.remove(id)
}
