package me.vangoo.mysteries.ability.runtime

import me.vangoo.domain.abilities.AbilitySpec
import me.vangoo.domain.entities.Beyonder
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.PendingCast
import me.vangoo.mysteries.ability.context.Beyonders
import me.vangoo.mysteries.ability.context.Cooldowns
import me.vangoo.mysteries.ability.context.Effects
import me.vangoo.mysteries.ability.context.Events
import me.vangoo.mysteries.ability.context.Messaging
import me.vangoo.mysteries.ability.context.Scheduling
import me.vangoo.mysteries.ability.context.Targeting
import net.minecraft.server.level.ServerPlayer

/**
 * Assembles contexts.
 *
 * Not the plugin's `ServiceContainer` and not a factory for a factory's sake: there are seven
 * services in a context, and without this class they would have to be listed at every place
 * that calls an ability — in the cast executor, in the passives' tick, and then at every
 * further entry point.
 *
 * Three of the seven are assembled **for a specific call**: `targeting` looks through this very
 * player's eyes, `events` subscribes on behalf of this very ability, and `effects` knows this
 * very caster's pathway colour. The rest are the same objects for the whole server.
 */
class AbilityContexts(
    private val beyonders: Beyonders,
    private val cooldowns: Cooldowns,
    private val messaging: Messaging,
    private val scheduling: Scheduling,
    private val eventHub: EventHub,
) {

    /** A context with no ticket: a passive's tick, switching a toggle on, a player joining and leaving. */
    fun of(caster: ServerPlayer, beyonder: Beyonder, ability: AbilitySpec): AbilityContext =
        LiveAbilityContext(
            caster, beyonder, ability,
            beyonders, cooldowns, messaging, scheduling,
            LiveTargeting(caster), eventHub.scopeOf(caster.uuid, ability.identity),
            LiveEffects.of(scheduling, beyonder.pathway.name),
        )

    /** The context of one cast: it lives exactly as long as the `cast` call. */
    fun forCast(
        caster: ServerPlayer,
        beyonder: Beyonder,
        ability: AbilitySpec,
        cast: PendingCast,
    ): CastContext =
        LiveCastContext(
            caster, beyonder, ability, cast,
            beyonders, cooldowns, messaging, scheduling,
            LiveTargeting(caster), eventHub.scopeOf(caster.uuid, ability.identity),
            LiveEffects.of(scheduling, beyonder.pathway.name),
        )
}

internal class LiveAbilityContext(
    override val caster: ServerPlayer,
    override val beyonder: Beyonder,
    override val ability: AbilitySpec,
    override val beyonders: Beyonders,
    override val cooldowns: Cooldowns,
    override val messaging: Messaging,
    override val scheduling: Scheduling,
    override val targeting: Targeting,
    override val events: Events,
    override val effects: Effects,
) : AbilityContext

internal class LiveCastContext(
    override val caster: ServerPlayer,
    override val beyonder: Beyonder,
    override val ability: AbilitySpec,
    override val cast: PendingCast,
    override val beyonders: Beyonders,
    override val cooldowns: Cooldowns,
    override val messaging: Messaging,
    override val scheduling: Scheduling,
    override val targeting: Targeting,
    override val events: Events,
    override val effects: Effects,
) : CastContext
