package me.vangoo.mysteries.pathways.sun.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import me.vangoo.mysteries.schedule.TaskHandle
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sun, Seq. 5 (Priest of Light): **Wave of Holiness**.
 *
 * One wave spreading out from the Priest that **treats everyone according to their nature**: it
 * strikes and weakens the unclean, heals everyone else and clears everything harmful off them.
 *
 * ## The pathway's most expensive power, and it is not about damage
 *
 * The damage here is smaller than the High Priest's pillar — because that is not what the wave is
 * for. It saves the party: it clears poison, weakness, bleeding and fire off everyone, tops up
 * health — and at the same time sweeps the surroundings. One button that turns a fight already
 * lost.
 *
 * ## The wave really does spread
 *
 * The effect is applied instantly, and it is visible for [WAVE_TICKS] ticks: a ring travels from
 * the centre to the edge, and by it one reads who was caught. It is drawn by its own job in the
 * scheduler, not by a session: the wave no longer belongs to the caster.
 */
class HolyWave : ActiveAbility(AbilityIdentity.of("sun_holy_wave")) {

    override val displayName: Component = Component.literal("Хвиля святості")

    override val spiritualityCost: Int = 200

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Хвиля святого світла на ${RADIUS.toInt()} бл. навколо вас.\n" +
            "▪ Своїм: +${heal(sequence)} здоров'я і знято все шкідливе\n" +
            "▪ Нечисті: ${damage(sequence)} шкоди й Слабкість на " +
            "${WEAKNESS_TICKS / TICKS_PER_SECOND} с",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val mote = ModParticles.mote(context.effects.pathwayColor)
        wave(context, context.origin, mote)

        var mended = 0
        var struck = 0
        context.targeting.livingNear(RADIUS).forEach { target ->
            when {
                target !== context.caster && target.isProfane(context.beyonders) -> {
                    smite(context.level, context.caster, context.beyonders, target, damage(context.sequence))
                    target.addEffect(MobEffectInstance(MobEffects.WEAKNESS, WEAKNESS_TICKS, 0))
                    struck++
                }
                // The wave saves a party, not a menagerie: a creeper nearby is not healed just
                // because it is not undead.
                target === context.caster || target.isFaithful(context.beyonders) -> {
                    target.heal(heal(context.sequence).toFloat())
                    cleanse(target)
                    mended++
                }
            }
        }

        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.8f, 0.6f,
        )
        return AbilityOutcome.done("Хвиля святості: $mended зцілено, $struck опалено")
    }

    /** A ring travelling from the centre to the edge over [WAVE_TICKS] ticks. */
    private fun wave(context: CastContext, centre: Vec3, mote: ParticleOptions) {
        val level: ServerLevel = context.level
        var step = 0

        lateinit var handle: TaskHandle
        handle = context.scheduling.every(1) {
            if (step >= WAVE_TICKS) return@every handle.cancel()
            val r = RADIUS * (step + 1) / WAVE_TICKS
            val points = (WAVE_POINTS * r / RADIUS).toInt().coerceAtLeast(8)
            repeat(points) { i ->
                val angle = TAU * i / points
                level.sendParticles(
                    mote,
                    centre.x + r * cos(angle), centre.y + 0.5, centre.z + r * sin(angle),
                    1, 0.0, 0.0, 0.0, 0.0,
                )
            }
            step++
        }
        level.sendParticles(ParticleTypes.END_ROD, centre.x, centre.y + 1.0, centre.z, 30, 0.5, 0.5, 0.5, 0.08)
    }

    private fun heal(sequence: Sequence): Int =
        scale(BASE_HEAL, sequence, SequenceScaler.ScalingStrategy.STRONG)

    private fun damage(sequence: Sequence): Int =
        scale(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.STRONG)

    private companion object {
        const val RADIUS = 10.0
        const val BASE_HEAL = 6
        const val BASE_DAMAGE = 6
        const val COOLDOWN_SECONDS = 50
        const val WEAKNESS_TICKS = 3 * TICKS_PER_SECOND
        const val WAVE_TICKS = 14
        const val WAVE_POINTS = 44
        const val TAU = 2.0 * Math.PI
    }
}
