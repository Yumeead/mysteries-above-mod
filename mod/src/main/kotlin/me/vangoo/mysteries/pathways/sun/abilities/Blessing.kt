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
import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.mysteries.ability.context.Fizzle
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.effect.douse
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
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
 * The light is real: a warm light block walks with the Seeker, so the place they stand in is lit
 * for everyone, and the unclean in it smoke and shed embers. No circle is drawn on the ground —
 * where the blessing reaches is read off whom it touches.
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
            return AbilityOutcome.Done()
        }

        val radius = range(context.sequence)
        val burn = undeadDamage(context.sequence)
        val ember = ModParticles.light(EMBER)
        val messaging = context.messaging
        val beyonders = context.beyonders
        // The light block is state in the world, and the ability instance is shared by every
        // player — so its place lives in this Seeker's session, as in `Daytime`.
        var lit: BlockPos? = null
        var beat = 0

        context.session {
            try {
                val end = sustain(periodTicks = LIGHT_PERIOD_TICKS, upkeep = periodicCost) { priest ->
                    lit = carryLight(priest.level(), lit, priest.blockPosition().above(), WARM_LIGHT)
                    if (beat++ % BEATS_PER_SECOND != 0) return@sustain
                    reach(priest, radius).forEach { nearby ->
                        // Three categories, not two: damage to the unclean, healing to one's own,
                        // nothing to the rest of the world. A cow under a blessing is not healed.
                        if (nearby.isProfane(beyonders)) {
                            smite(priest.level(), priest, beyonders, nearby, burn)
                            smoulder(nearby, ember)
                        } else if (nearby.isFaithful(beyonders)) {
                            nearby.addEffect(
                                MobEffectInstance(MobEffects.REGENERATION, MERCY_TICKS, 0, true, false, true),
                            )
                        }
                    }
                }
                if (end == SessionEnd.EXHAUSTED) {
                    messaging.hint(ownerId, Component.literal("Благословення згасло: духовність скінчилась"), Fizzle.EMPTY)
                }
            } finally {
                owner?.let { priest ->
                    lit?.let { douse(priest.level(), it) }
                    extinguish(priest)
                }
            }
        }

        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.2f,
        )
        return AbilityOutcome.Done()
    }

    /** Who is in the circle. Taken anew every second — the circle moves with the Seeker. */
    private fun reach(priest: ServerPlayer, radius: Double): List<LivingEntity> =
        priest.level().getEntitiesOfClass(
            LivingEntity::class.java,
            AABB.ofSize(priest.position(), radius * 2, radius * 2, radius * 2),
        ).filter { it.position().distanceToSqr(priest.position()) <= radius * radius }

    /** The unclean in the light smoulder: smoke, and embers that rise off them and glow. */
    private fun smoulder(target: LivingEntity, ember: ParticleOptions) {
        val level = target.level() as? ServerLevel ?: return
        val random = level.random
        level.sendParticles(
            ParticleTypes.SMOKE, target.x, target.y + target.bbHeight * 0.6, target.z,
            SMOKE, target.bbWidth * 0.4, target.bbHeight * 0.3, target.bbWidth * 0.4, 0.01,
        )
        repeat(EMBERS) {
            level.sendParticles(
                ember,
                target.x + (random.nextDouble() - 0.5) * target.bbWidth,
                target.y + random.nextDouble() * target.bbHeight,
                target.z + (random.nextDouble() - 0.5) * target.bbWidth,
                // `count = 0`: an exact velocity — the ember rises straight, it is not flung.
                0, 0.0, EMBER_RISE, 0.0, 1.0,
            )
        }
    }

    private fun extinguish(priest: ServerPlayer) {
        priest.level().playSound(
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

        /** The light keeps up with a walk; the blessing itself acts once a second. */
        const val LIGHT_PERIOD_TICKS = 5
        const val BEATS_PER_SECOND = TICKS_PER_SECOND / LIGHT_PERIOD_TICKS
        /** Warm rather than blinding: a hearth, not the noon of `Daytime`. */
        const val WARM_LIGHT = 11
        val EMBER = RgbColor(0xFF9A3C)
        const val EMBERS = 3
        const val EMBER_RISE = 0.05
        const val SMOKE = 4
    }
}
