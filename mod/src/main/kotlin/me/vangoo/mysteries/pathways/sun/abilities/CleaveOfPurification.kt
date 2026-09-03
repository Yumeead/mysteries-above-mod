package me.vangoo.mysteries.pathways.sun.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.HolyAffinity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.event.IncomingDamage
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource

/**
 * Sun, Seq. 7 (Solar High Priest): **Cleave of Purification**.
 *
 * Consecrates the weapon for the next few blows: each adds holy damage on top of the ordinary —
 * in full against the unclean, reduced against everyone else.
 *
 * ## The charges live in the cast, not in the ability
 *
 * The counter is an ordinary local variable captured by the event handler. It **cannot** be a
 * class field: there is one ability instance for the whole server, so two High Priests would
 * spend each other's charges. A closure, on the other hand, belongs to a specific cast and dies
 * together with the subscription.
 *
 * The subscription has a term ([DURATION_SECONDS]): charges left unspent burn out on their own —
 * there is nobody to clear them, and `EventHub` removes an expired subscription during dispatch.
 */
class CleaveOfPurification : ActiveAbility(AbilityIdentity.of("sun_cleave_of_purification")) {

    override val displayName: Component = Component.literal("Очищувальний удар")

    override val spiritualityCost: Int = 70

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Освячує зброю в руках.\n" +
            "▪ Наступні ${charges(sequence)} ударів у ближньому бою: +${bonus(sequence)} святої шкоди\n" +
            "▪ По нечисті — повна, по решті — знижена\n" +
            "▪ Невитрачені заряди згорають за $DURATION_SECONDS с",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val casterId = context.caster.uuid
        val beyonders = context.beyonders
        val messaging = context.messaging
        val bonus = bonus(context.sequence)
        var left = charges(context.sequence)

        val blessing = context.events.on<IncomingDamage>(
            durationTicks = DURATION_SECONDS * TICKS_PER_SECOND,
        ) { damage ->
            if (damage.attacker?.uuid != casterId) return@on
            // It is the **weapon** that is consecrated, so melee blows are what count: an arrow
            // or an explosion never touched the blade.
            if (damage.directCause !== damage.attacker) return@on
            if (left <= 0) return@on

            left--
            val extra = bonus * HolyAffinity.damageMultiplier(damage.victim.isProfane(beyonders))
            damage.amount = damage.amount + extra.toFloat()
            flash(damage.victim.level() as? ServerLevel, damage)
            messaging.actionBar(casterId, Component.literal("Освячення: лишилось $left"))
        }

        // The subscription's term will remove it anyway; the handle is only needed to stop it
        // early, and there is nothing to stop here — so it is not kept.
        check(blessing.isActive)

        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.2f,
        )
        return AbilityOutcome.done("Зброю освячено: ${charges(context.sequence)} ударів")
    }

    private fun flash(level: ServerLevel?, damage: IncomingDamage) {
        level ?: return
        val victim = damage.victim
        level.sendParticles(
            ParticleTypes.END_ROD,
            victim.x, victim.y + victim.bbHeight * 0.6, victim.z,
            8, 0.2, 0.2, 0.2, 0.02,
        )
    }

    private fun charges(sequence: Sequence): Int =
        scale(BASE_CHARGES, sequence, SequenceScaler.ScalingStrategy.WEAK)

    private fun bonus(sequence: Sequence): Int =
        scale(BASE_BONUS_DAMAGE, sequence, SequenceScaler.ScalingStrategy.STRONG)

    private companion object {
        const val BASE_CHARGES = 3
        const val BASE_BONUS_DAMAGE = 4
        const val COOLDOWN_SECONDS = 25
        const val DURATION_SECONDS = 30
    }
}
