package me.vangoo.mysteries.pathways.sun.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sessionRunning
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sun, Seq. 7 (Solar High Priest): **Fire of Light**.
 *
 * A golden holy flame that **stays standing**: a patch on the ground burns for [BURN_SECONDS] s
 * and strikes everything inside it once a second.
 *
 * ## Why this is not a third way of dealing damage
 *
 * The High Priest already has `Сонячне сяйво` (a beam) and `Святе світло` (a pillar), so a third
 * area strike would be the same thing a third time. The difference here is in the **verb**: the
 * beam and the pillar are "to hit", the Fire is "to take ground". It does not kill whoever runs
 * through; it forbids a corridor, a cave mouth, a bridge.
 *
 * Hence the bigger radius, the smaller damage per trigger than the pillar, and the fact that it
 * burns where the High Priest is no longer looking: the flame lives in its own session and does
 * not ask where the caster is now.
 */
class FireOfLight : ActiveAbility(AbilityIdentity.of("sun_fire_of_light")) {

    override val displayName: Component = Component.literal("Вогонь Світла")

    override val spiritualityCost: Int = 110

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Золоте полум'я в точці під прицілом — і воно лишається горіти.\n" +
            "▪ Радіус: ${RADIUS} бл., горить $BURN_SECONDS с\n" +
            "▪ Щосекунди: ${tickDamage(sequence)} по нечисті, знижена по решті\n" +
            "▪ Нечисть у полум'ї спалахує",
    )

    /** A second fire on top of the first puts the first out — a player has one session. */
    override fun canCast(context: CastContext): Boolean = !context.sessionRunning

    override fun cast(context: CastContext): AbilityOutcome {
        val centre = context.caster.pick(MAX_RANGE, 1.0f, false).location
        val mote = ModParticles.mote(context.effects.pathwayColor)
        val hit = tickDamage(context.sequence)
        val beyonders = context.beyonders
        val level = context.level
        val caster = context.caster

        context.session {
            repeat(BURN_SECONDS) {
                blaze(level, centre, mote)
                level.getEntitiesOfClass(
                    LivingEntity::class.java,
                    AABB.ofSize(centre, RADIUS * 2, HEIGHT, RADIUS * 2),
                )
                    .filter { it.position().distanceToSqr(centre.x, it.y, centre.z) <= RADIUS * RADIUS }
                    .forEach { target ->
                        smite(level, caster, beyonders, target, hit)
                        if (target.isProfane(beyonders)) target.igniteForSeconds(FIRE_SECONDS.toFloat())
                    }
                waitTicks(TICKS_PER_SECOND)
            }
        }

        level.playSound(
            null, centre.x, centre.y, centre.z,
            SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.4f, 0.8f,
        )
        return AbilityOutcome.done("Вогонь Світла зайнявся на $BURN_SECONDS с")
    }

    /** The patch burns unevenly: a different pattern every second, otherwise it is a carpet, not a fire. */
    private fun blaze(level: ServerLevel, centre: Vec3, mote: ParticleOptions) {
        repeat(TONGUES) { i ->
            // The clock is the server tick: `Level.gameTime` is gone on 26.2.
            val angle = TAU * i / TONGUES + level.server.tickCount * 0.05
            val r = RADIUS * ((i % 3) + 1) / 3.0
            level.sendParticles(
                mote,
                centre.x + r * cos(angle), centre.y + 0.2, centre.z + r * sin(angle),
                2, 0.1, 0.5, 0.1, 0.02,
            )
        }
        level.sendParticles(
            ParticleTypes.END_ROD,
            centre.x, centre.y + 0.5, centre.z,
            6, RADIUS * 0.4, 0.3, RADIUS * 0.4, 0.01,
        )
    }

    private fun tickDamage(sequence: Sequence): Int =
        scale(BASE_TICK_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        const val MAX_RANGE = 18.0
        const val RADIUS = 4.5
        const val HEIGHT = 4.0
        const val BASE_TICK_DAMAGE = 4
        const val BURN_SECONDS = 7
        const val FIRE_SECONDS = 3
        const val COOLDOWN_SECONDS = 30
        const val TONGUES = 18
        const val TAU = 2.0 * Math.PI
    }
}
