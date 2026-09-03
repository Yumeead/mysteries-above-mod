package me.vangoo.domain.abilities

import me.vangoo.domain.valueobjects.SanityPenalty
import java.util.UUID

/**
 * The lifecycle of one cast: **authorize → ticket → settle**.
 *
 * In the plugin this was a single `Beyonder.useAbility` method that spent the resources
 * immediately, and "deferred" abilities (those waiting for a choice in a menu) had to dodge
 * that. They dodged it with a comment:
 *
 * ```java
 * // CRITICAL: do NOT set the cooldown if the result is deferred!
 * ```
 *
 * Here deferral is simply an **unspent ticket**. A [CastTicket] is issued only by
 * `Beyonder.authorize`, settled only by `Beyonder.settle`, and exactly once. An ability
 * waiting for input keeps the ticket; if the input never comes it never settles, and the
 * player pays nothing. Settling twice is impossible: that is a `check()`, not discipline.
 */
sealed interface CastAuthorization {

    data class Granted(val ticket: CastTicket) : CastAuthorization

    data class Refused(val reason: CastRefusal) : CastAuthorization
}

/** Why `core` refused a cast. Cooldowns live in the mod — they are deliberately absent here. */
sealed interface CastRefusal {

    /** The ability is neither in the pathway nor among the off-pathway ones. */
    data object NotOwned : CastRefusal

    data class InsufficientSpirituality(val required: Int, val available: Int) : CastRefusal

    /** A passive is never cast — the passive manager switches it on. */
    data object NotCastable : CastRefusal
}

/**
 * Permission for one cast. Created only by `Beyonder.authorize`, settled only through
 * `Beyonder.settle`.
 */
class CastTicket internal constructor(
    val spec: AbilitySpec,
    val cost: Int,
    internal val casterId: UUID,
) {
    var isSettled: Boolean = false
        private set

    internal fun consume() {
        check(!isSettled) { "Квиток на ${spec.identity} вже погашено" }
        isSettled = true
    }

    override fun toString(): String = "CastTicket(${spec.identity}, cost=$cost, settled=$isSettled)"
}

/**
 * What exactly was spent when the ticket was settled. A [penalty] of type
 * `SPIRITUALITY_LOSS` has already been applied to the Beyonder; `DAMAGE` and `EXTREME` are
 * effects in the world and the mod applies them.
 */
data class CastReceipt(
    val spentSpirituality: Int,
    val masteryGained: Double,
    val cooldownSeconds: Int,
    val penalty: SanityPenalty,
)
