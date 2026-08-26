package me.vangoo.domain.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence

/**
 * Чим здібність **є і скільки коштує** — половина, що не знає про Minecraft.
 *
 * Другу половину — що здібність **робить** — тримає `Ability` у моді: вона реалізує цей
 * інтерфейс і додає `execute(AbilityContext)`. Шов проходить тут, і саме тому `core`
 * може володіти [me.vangoo.domain.entities.Beyonder]'ом зі списком здібностей, не
 * бачачи жодного платформного типу.
 *
 * У плагіні цієї межі не було: `Ability` тягнув `IAbilityContext`, той тягнув
 * `org.bukkit.Location`, і весь домен ставав платформним. Тут це просто не збереться.
 */
interface AbilitySpec {

    /**
     * Стабільний ключ здібності. У плагіні він виводився з української назви
     * (`AbilityIdentity.of(getName())`) — тут ідентичність задається явно: вона
     * потрапляє в збереження і в пакети, і не має їздити разом із текстом.
     */
    val identity: AbilityIdentity

    val type: AbilityType

    /** Разова ціна касту. Пасивки не платять — у них 0. */
    val spiritualityCost: Int

    /** Ціна утримання за секунду для тоглів і каналів; 0 — не утримується. */
    fun periodicCost(sequence: Sequence): Int = 0

    fun cooldownSeconds(sequence: Sequence): Int

    /**
     * Множник вхідної втрати глузду для власника: 1.0 — без опору, менше — пасивка
     * захищає розум. Береться найсильніший з усіх, а не добуток (див. `Beyonder`).
     */
    val sanityLossMultiplier: Double get() = 1.0

    /** Та сама здібність, але з вищої Послідовності, заміщає слабшу версію. */
    fun canReplace(other: AbilitySpec): Boolean = identity.canReplace(other.identity)
}
