package me.vangoo.mysteries.pathways.sun.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.SessionEnd
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sessionRunning
import me.vangoo.mysteries.ability.stopSession
import me.vangoo.mysteries.ability.sustain
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB

/**
 * Sun, Seq. 8 (Light Seeker): **Blessing**.
 *
 * Sustained light: while the Seeker pays, everyone nearby is healed and the unclean nearby
 * smoulder. It goes out when the spirituality runs out or when the Seeker casts a second time.
 *
 * ## A power that goes out on its own is a session, not a toggle
 *
 * `ToggleablePassiveAbility` will not do here: its switch lies **outside** the ability, and
 * there is nothing to reach it with from inside when the tank runs dry. A session, by contrast,
 * decides for itself when to go out, and `sustain` returns the reason.
 *
 * ## The whole price is in the upkeep, not in the cast
 *
 * [spiritualityCost] is zero and [periodicCost] pays. Otherwise switching the blessing off would
 * cost a separate cast — that is, the player would pay to **stop** paying. The three-second
 * cooldown stays: it is against flickering, not against use.
 */
class Blessing : ActiveAbility(AbilityIdentity.of("sun_blessing")) {

    override val displayName: Component = Component.literal("Благословення")

    /** The cast is free: the price of this power is its upkeep, and switching it off must cost nothing. */
    override val spiritualityCost: Int = 0

    /**
     * The upkeep grows with the Sequence — and it must, because **regeneration** grows with it
     * too: 2 points/s at Seq. 8 against 6 at Seq. 5 (and 9 at full mastery). The flat price taken
     * from the plugin turned out at Seq. 5 to be below the regeneration, i.e. the power became
     * free and the spirituality bar crept back up to the ceiling while it was being sustained.
     */
    override fun periodicCost(sequence: Sequence): Int =
        scale(BASE_PERIODIC_COST, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Світло тримається навколо вас: союзники зцілюються, нечисть тліє.\n" +
            "▪ Радіус: ${range(sequence).toInt()} бл.\n" +
            "▪ Нечисті: ${undeadDamage(sequence)} шкоди щосекунди\n" +
            "▪ Витрата: ${periodicCost(sequence)} духовності/с; повторний каст вимикає",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        if (context.sessionRunning) {
            context.stopSession()
            return AbilityOutcome.done("Благословення згасло")
        }

        val radius = range(context.sequence)
        val burn = undeadDamage(context.sequence)
        val mote = ModParticles.mote(context.effects.pathwayColor)
        val messaging = context.messaging
        val beyonders = context.beyonders

        context.session {
            try {
                val end = sustain(periodTicks = TICKS_PER_SECOND, upkeep = periodicCost) { priest ->
                    radiate(priest, mote, radius)
                    reach(priest, radius).forEach { nearby ->
                        // Three categories, not two: damage to the unclean, healing to one's own,
                        // nothing to the rest of the world. A cow under a blessing is not healed.
                        if (nearby.isProfane(beyonders)) {
                            smite(priest.level() as ServerLevel, priest, beyonders, nearby, burn)
                        } else if (nearby.isFaithful(beyonders)) {
                            nearby.addEffect(
                                MobEffectInstance(MobEffects.REGENERATION, MERCY_TICKS, 0, true, false, true),
                            )
                        }
                    }
                }
                if (end == SessionEnd.EXHAUSTED) {
                    messaging.actionBar(ownerId, Component.literal("Благословення згасло: духовність скінчилась"))
                }
            } finally {
                owner?.let { extinguish(it) }
            }
        }

        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.2f,
        )
        return AbilityOutcome.done("Благословення розгорнулось")
    }

    /** Who is in the circle. Taken anew every tick — the circle moves with the Seeker. */
    private fun reach(priest: ServerPlayer, radius: Double): List<LivingEntity> {
        val level = priest.level() as? ServerLevel ?: return emptyList()
        return level.getEntitiesOfClass(
            LivingEntity::class.java,
            AABB.ofSize(priest.position(), radius * 2, radius * 2, radius * 2),
        ).filter { it.position().distanceToSqr(priest.position()) <= radius * radius }
    }

    private fun radiate(priest: ServerPlayer, mote: ParticleOptions, radius: Double) {
        val level = priest.level() as? ServerLevel ?: return
        repeat(HALO_POINTS) { i ->
            val angle = TAU * i / HALO_POINTS
            level.sendParticles(
                mote,
                priest.x + radius * Math.cos(angle), priest.y + 0.3, priest.z + radius * Math.sin(angle),
                1, 0.0, 0.0, 0.0, 0.0,
            )
        }
    }

    private fun extinguish(priest: ServerPlayer) {
        (priest.level() as? ServerLevel)?.playSound(
            null, priest.x, priest.y, priest.z,
            SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.7f, 1.2f,
        )
    }

    private fun range(sequence: Sequence): Double =
        scale(BASE_RANGE, sequence, SequenceScaler.ScalingStrategy.MODERATE).toDouble()

    private fun undeadDamage(sequence: Sequence): Int =
        scale(BASE_UNDEAD_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        const val BASE_RANGE = 10
        const val BASE_PERIODIC_COST = 18
        const val COOLDOWN_SECONDS = 3
        const val BASE_UNDEAD_DAMAGE = 3
        /** A little longer than the tick, so it does not blink between ticks. */
        const val MERCY_TICKS = 30
        const val HALO_POINTS = 20
        const val TAU = 2.0 * Math.PI
    }
}
