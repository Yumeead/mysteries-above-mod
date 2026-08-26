package me.vangoo.mysteries.ability.runtime

import me.vangoo.domain.valueobjects.AbilityIdentity
import java.util.UUID

/**
 * Яку здібність гравець зараз тримає «на руці».
 *
 * Тимчасова форма вибору: справжній — колесо здібностей із фази 11. Але клавіша, що
 * кастує, мусить знати **що** кастувати вже зараз, і тримати цю відповідь на клієнті
 * не можна: клієнт не знає ні списку здібностей, ні того, чи вони ще є.
 *
 * Тільки пам'ять і тільки поки гравець у грі — вибір не переживає ні вихід, ні
 * піднесення (після нього список здібностей інший).
 */
class AbilitySelection {

    private val byPlayer = HashMap<UUID, AbilityIdentity>()

    fun of(player: UUID): AbilityIdentity? = byPlayer[player]

    fun select(player: UUID, ability: AbilityIdentity) {
        byPlayer[player] = ability
    }

    fun forget(player: UUID) {
        byPlayer.remove(player)
    }
}
