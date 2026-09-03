package me.vangoo.mysteries.ability

import me.vangoo.domain.abilities.CastReceipt
import me.vangoo.domain.abilities.CastTicket
import me.vangoo.domain.entities.Beyonder
import net.minecraft.server.level.ServerPlayer

/**
 * An unfinished cast: the only thing an ability is allowed to outlive the `cast` call with.
 *
 * This replaces the plugin's `AbilityResult.deferred()` together with
 * `AbilityResourceConsumer`. There the invariant was held by a comment — "CRITICAL: do NOT set
 * the cooldown if the result is deferred" — and every deferred ability had to remember to call
 * the consumer by hand. Here there is nothing to forget: never call [complete] and the cast was
 * free, call it twice and a `check()` inside the ticket fails.
 *
 * It does not take the whole context into a session: that carries the identity of one cast and
 * one caster. This object does too, but that is exactly what it is about.
 */
class PendingCast internal constructor(
    private val executor: AbilityExecutor,
    private val caster: ServerPlayer,
    private val beyonder: Beyonder,
    private val ticket: CastTicket,
) {

    val isComplete: Boolean get() = ticket.isSettled

    /**
     * Spends the spirituality, accrues the mastery, sets the cooldown and applies the sanity
     * penalty. Exactly once.
     */
    fun complete(): CastReceipt = executor.settle(caster, beyonder, ticket)

    override fun toString(): String = "PendingCast($ticket)"
}
