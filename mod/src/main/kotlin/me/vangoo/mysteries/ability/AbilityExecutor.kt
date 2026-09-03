package me.vangoo.mysteries.ability

import me.vangoo.domain.abilities.CastAuthorization
import me.vangoo.domain.abilities.CastReceipt
import me.vangoo.domain.abilities.CastRefusal
import me.vangoo.mysteries.beyonder.TheftRegistry
import me.vangoo.domain.abilities.CastTicket
import me.vangoo.domain.entities.Beyonder
import me.vangoo.domain.valueobjects.SanityPenalty
import me.vangoo.domain.valueobjects.SequenceBasedSuccessChance
import me.vangoo.mysteries.MOD_ID
import me.vangoo.mysteries.ability.context.Beyonders
import me.vangoo.mysteries.ability.context.Cooldowns
import me.vangoo.mysteries.ability.context.Messaging
import me.vangoo.mysteries.ability.runtime.AbilityContexts
import me.vangoo.mysteries.event.AbilityCast
import me.vangoo.mysteries.event.GameEvent
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import org.slf4j.LoggerFactory

/**
 * The order of one cast — from the key press to the settlement.
 *
 * In the plugin this order was smeared across three places: `Beyonder.useAbility` took the
 * resources, `Ability.execute` held the hooks and the cooldown, and `AbilityResourceConsumer`
 * did the same again for deferred abilities — and slightly differently. Here it is one, and
 * the difference between an ordinary and a deferred cast comes down to who settles the ticket.
 */
class AbilityExecutor(
    private val beyonders: Beyonders,
    private val cooldowns: Cooldowns,
    private val messaging: Messaging,
    private val contexts: AbilityContexts,
    /**
     * Where to report that a power fired. The mod's bus — the same one damage from the mixin
     * travels on: a cast is as visible from the outside as a blow, and abilities that read it
     * do not care where the event came from.
     */
    private val publish: (GameEvent) -> Unit = {},
) {

    private val log = LoggerFactory.getLogger(MOD_ID)

    fun cast(caster: ServerPlayer, ability: ActiveAbility): CastReport {
        val beyonder = beyonders.of(caster.uuid) ?: return report(caster, CastReport.NotBeyonder)

        val remaining = cooldowns.remainingSeconds(caster.uuid, ability.identity)
        if (remaining > 0) return report(caster, CastReport.OnCooldown(remaining))

        // The one place a stolen power is refused to its owner. Every cast passes here, so no
        // guard is needed at any call site — and sessions the victim already started keep
        // running, because only new casts come through this door.
        if (TheftRegistry.isSuppressed(caster.uuid, ability.identity)) {
            return report(caster, CastReport.Suppressed(TheftRegistry.suppressionSecondsLeft(caster.uuid, ability.identity)))
        }

        // The first call of something stolen starts its real term. Unconditional on purpose: the
        // gate lives inside the record, so nothing here knows what a reserve term is.
        TheftRegistry.markUsed(caster.uuid, ability.identity)

        val ticket = when (val authorization = beyonder.authorize(ability)) {
            is CastAuthorization.Refused -> return report(caster, CastReport.Refused(authorization.reason))
            is CastAuthorization.Granted -> authorization.ticket
        }

        val context = contexts.forCast(
            caster = caster,
            beyonder = beyonder,
            ability = ability,
            cast = PendingCast(this, caster, beyonder, ticket),
        )

        // From here control goes into the ability's code. An exception from there would
        // otherwise simply drift away: Fabric logs it and rethrows, and
        // `BlockableEventLoop.doRunTask` swallows it — meaning the ticket stays unsettled and
        // a broken ability becomes free and endless. The rest of the runtime (`TickScheduler`,
        // `SessionHost`, `EventHub`) already isolates foreign code; this was the only place
        // that did not.
        val outcome = try {
            if (!ability.canCast(context)) {
                // The ticket simply disappears unspent — the player paid nothing.
                return report(caster, CastReport.Refused(CastRefusal.NotCastable))
            }

            contest(context, ability)?.let { chance ->
                // Resistance eats the cooldown but not the spirituality: there was an attempt,
                // there was no effect.
                cooldowns.set(caster.uuid, ability.identity, ability.cooldownSeconds(beyonder.sequence))
                return report(caster, CastReport.Resisted(chance))
            }

            ability.cast(context)
        } catch (error: Exception) {
            log.error("Ability {} threw while casting", ability.identity, error)
            // The same settlement as on resistance: the cooldown is charged, the spirituality
            // is not. Without a cooldown the player would hold the key down and the log would
            // gain a line per tick.
            cooldowns.set(caster.uuid, ability.identity, ability.cooldownSeconds(beyonder.sequence))
            AbilityOutcome.failed("Сила зірвалась")
        }
        val receipt = when (outcome) {
            is AbilityOutcome.Done -> if (context.cast.isComplete) null else context.cast.complete()
            // The ticket stayed with the ability: it will settle it when the effect really happens.
            is AbilityOutcome.Awaiting -> null
            is AbilityOutcome.Failed -> null
        }
        // A failed cast is invisible from the outside — no flash, no sound, so no event either.
        if (outcome !is AbilityOutcome.Failed) publish(AbilityCast(caster, ability))
        return report(caster, CastReport.Performed(outcome, receipt))
    }

    /**
     * Settling the ticket. Called either from here right after [AbilityOutcome.Done], or later
     * by the ability itself through [PendingCast.complete].
     */
    internal fun settle(caster: ServerPlayer, beyonder: Beyonder, ticket: CastTicket): CastReceipt {
        val receipt = beyonder.settle(ticket)
        if (receipt.cooldownSeconds > 0) {
            cooldowns.set(beyonder.playerId, ticket.spec.identity, receipt.cooldownSeconds)
        }
        applyPenalty(caster, receipt.penalty)
        return receipt
    }

    /**
     * `core` has already taken the spirituality loss itself — what is left here are the effects
     * in the world. `EXTREME` currently strikes to kill; the full madness ritual belongs to the
     * phase that brings Rampage.
     */
    private fun applyPenalty(caster: ServerPlayer, penalty: SanityPenalty) {
        when (penalty.type) {
            SanityPenalty.PenaltyType.DAMAGE ->
                caster.hurtServer(caster.level(), sanityDamage(caster), penalty.amount.toFloat())

            SanityPenalty.PenaltyType.EXTREME ->
                caster.hurtServer(caster.level(), sanityDamage(caster), Float.MAX_VALUE)

            SanityPenalty.PenaltyType.SPIRITUALITY_LOSS -> Unit
            SanityPenalty.PenaltyType.NONE -> Unit
        }
    }

    private fun sanityDamage(caster: ServerPlayer): DamageSource =
        caster.damageSources().magic()

    /**
     * The Sequence-based resistance roll. Only another Beyonder resists: an ordinary creature
     * has nothing to put up. Returns the chance if the target held out.
     */
    private fun contest(
        context: CastContext,
        ability: ActiveAbility,
    ): SequenceBasedSuccessChance? {
        val target: LivingEntity = ability.contestedTarget(context) ?: return null
        val defender = beyonders.of(target.uuid) ?: return null

        val chance = SequenceBasedSuccessChance(
            casterSequence = context.beyonder.sequenceLevel,
            targetSequence = defender.sequenceLevel,
        )
        return if (chance.rollSuccess()) null else chance
    }

    private fun report(caster: ServerPlayer, report: CastReport): CastReport {
        CastMessages.of(report)?.let { messaging.actionBar(caster, it) }
        return report
    }
}
