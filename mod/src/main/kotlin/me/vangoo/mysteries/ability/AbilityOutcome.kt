package me.vangoo.mysteries.ability

import net.minecraft.network.chat.Component

/**
 * What the ability did. Three states instead of the plugin's `AbilityResult` with six flags,
 * one of which — `deferred` — had to be remembered and checked by hand.
 */
sealed interface AbilityOutcome {

    val message: Component?

    /** The effect happened. [AbilityExecutor] settles the ticket: spirituality, mastery, cooldown. */
    data class Done(override val message: Component? = null) : AbilityOutcome

    /**
     * The effect began and is waiting for the player — a menu opened, a session is running.
     * The ticket stays unsettled and the ability settles it itself once the effect really
     * happens. If the wait comes to nothing it never settles, and the player paid nothing.
     */
    data class Awaiting(override val message: Component? = null) : AbilityOutcome

    /** Nothing happened: no target, the wrong world, the wrong moment. There is nothing to pay for. */
    data class Failed(override val message: Component) : AbilityOutcome

    companion object {
        fun done(text: String): Done = Done(Component.literal(text))
        fun failed(text: String): Failed = Failed(Component.literal(text))
    }
}
