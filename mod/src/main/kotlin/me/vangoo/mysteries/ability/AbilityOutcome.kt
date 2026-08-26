package me.vangoo.mysteries.ability

import net.minecraft.network.chat.Component

/**
 * Що здібність зробила. Три стани замість плагінного `AbilityResult` з шістьма
 * прапорцями, з яких `deferred` доводилось не забути перевірити вручну.
 */
sealed interface AbilityOutcome {

    val message: Component?

    /** Ефект стався. [AbilityExecutor] гасить квиток: духовність, засвоєння, кулдаун. */
    data class Done(override val message: Component? = null) : AbilityOutcome

    /**
     * Ефект почався й чекає на гравця — відкрилось меню, триває сесія. Квиток
     * лишається непогашеним, і здібність гасить його сама, коли ефект справді стався.
     * Не дочекалась — не гасить, і гравець нічого не заплатив.
     */
    data class Awaiting(override val message: Component? = null) : AbilityOutcome

    /** Не сталось нічого: немає цілі, не той світ, не той момент. Платити нема за що. */
    data class Failed(override val message: Component) : AbilityOutcome

    companion object {
        fun done(text: String): Done = Done(Component.literal(text))
        fun failed(text: String): Failed = Failed(Component.literal(text))
    }
}
