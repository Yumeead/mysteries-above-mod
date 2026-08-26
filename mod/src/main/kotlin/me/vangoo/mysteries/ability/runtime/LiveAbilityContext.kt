package me.vangoo.mysteries.ability.runtime

import me.vangoo.domain.abilities.AbilitySpec
import me.vangoo.domain.entities.Beyonder
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.PendingCast
import me.vangoo.mysteries.ability.context.Beyonders
import me.vangoo.mysteries.ability.context.Cooldowns
import me.vangoo.mysteries.ability.context.Messaging
import me.vangoo.mysteries.ability.context.Scheduling
import me.vangoo.mysteries.ability.context.Targeting
import net.minecraft.server.level.ServerPlayer

/**
 * Складає контексти.
 *
 * Не `ServiceContainer` плагіна й не фабрика заради фабрики: сервісів у контексті п'ять,
 * і без цього класу їх довелося б перелічувати в кожному місці, що кличе здібність —
 * у виконавці касту, у такті пасивок, а далі в кожній наступній точці входу.
 */
class AbilityContexts(
    private val beyonders: Beyonders,
    private val cooldowns: Cooldowns,
    private val messaging: Messaging,
    private val scheduling: Scheduling,
) {

    /** Контекст без квитка: такт пасивки, вмикання тогла, вхід і вихід гравця. */
    fun of(caster: ServerPlayer, beyonder: Beyonder, ability: AbilitySpec): AbilityContext =
        LiveAbilityContext(caster, beyonder, ability, beyonders, cooldowns, messaging, scheduling, LiveTargeting(caster))

    /** Контекст одного касту: живе рівно стільки, скільки триває виклик `cast`. */
    fun forCast(
        caster: ServerPlayer,
        beyonder: Beyonder,
        ability: AbilitySpec,
        cast: PendingCast,
    ): CastContext =
        LiveCastContext(caster, beyonder, ability, cast, beyonders, cooldowns, messaging, scheduling, LiveTargeting(caster))
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
) : CastContext
