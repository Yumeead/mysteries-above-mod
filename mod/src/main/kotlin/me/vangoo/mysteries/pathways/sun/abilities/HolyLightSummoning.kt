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
import net.minecraft.core.BlockPos
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
 * Sun, Seq. 7 (Solar High Priest): **Holy Light**.
 *
 * A pillar of holy fire from the sky into a chosen point. The pathway's strongest single strike —
 * and the only one that **does not work under a roof**.
 *
 * ## The sky is a condition, not decoration
 *
 * The plugin's version drew a pillar of particles anywhere: in a cave, in a bunker, underwater.
 * Here the light really does come from above, so `canSeeSkyFromBelowWater` over the target is a
 * mandatory condition, and the refusal says so outright.
 *
 * That is what the pathway lacked: the Sun had five ways of dealing damage and not one reason to
 * think about **where** you are standing. Now the strongest of them requires going out under the
 * sky, and an opponent has somewhere to hide from it.
 *
 * On 26.2 `Level.canSeeSky` is gone — only `LevelReader.canSeeSkyFromBelowWater` remains.
 */
class HolyLightSummoning : ActiveAbility(AbilityIdentity.of("sun_holy_light_summoning")) {

    override val displayName: Component = Component.literal("Святе світло")

    override val spiritualityCost: Int = 120

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Стовп святого вогню з неба в точку під прицілом.\n" +
            "▪ Шкода: ${damage(sequence)} по нечисті (радіус ${PILLAR_RADIUS} бл.), знижена по решті\n" +
            "▪ Нечисть горить ${FIRE_SECONDS} с\n" +
            "▪ Потрібне відкрите небо над ціллю — під дахом не спрацює",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val hit = context.caster.pick(MAX_RANGE, 1.0f, false).location
        val ground = BlockPos.containing(hit)

        if (!context.level.canSeeSkyFromBelowWater(ground.above())) {
            return AbilityOutcome.failed("Небо над тією точкою закрите — світлу нема звідки прийти")
        }

        val mote = ModParticles.mote(context.effects.pathwayColor)
        descend(context, hit, mote)

        val struck = context.level.getEntitiesOfClass(
            LivingEntity::class.java,
            AABB.ofSize(hit, PILLAR_RADIUS * 2, PILLAR_HEIGHT, PILLAR_RADIUS * 2),
        ).filter { it.position().distanceToSqr(hit.x, it.y, hit.z) <= PILLAR_RADIUS * PILLAR_RADIUS }

        var burned = 0
        struck.forEach { target ->
            smite(context.level, context.caster, context.beyonders, target, damage(context.sequence))
            if (target.isProfane(context.beyonders)) {
                target.igniteForSeconds(FIRE_SECONDS.toFloat())
                burned++
            }
        }

        context.level.playSound(
            null, hit.x, hit.y, hit.z,
            SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.6f, 0.7f,
        )
        return AbilityOutcome.done("Стовп світла впав: ${struck.size} під ним, $burned горить")
    }

    /**
     * The pillar comes down from above over [FALL_TICKS] rather than appearing whole.
     *
     * It is drawn by its own job in the scheduler, not by a session: the pillar does not belong to
     * the caster — it has already fallen and will burn out on its own, even if they leave the game.
     */
    private fun descend(context: CastContext, at: Vec3, mote: ParticleOptions) {
        val level = context.level
        var step = 0

        lateinit var handle: TaskHandle
        handle = context.scheduling.every(1) {
            if (step >= FALL_TICKS) return@every handle.cancel()
            val top = at.y + PILLAR_HEIGHT * (1.0 - step.toDouble() / FALL_TICKS)
            repeat(RING_POINTS) { i ->
                val angle = TAU * i / RING_POINTS + step * SWIRL
                level.sendParticles(
                    mote,
                    at.x + PILLAR_RADIUS * cos(angle), top, at.z + PILLAR_RADIUS * sin(angle),
                    1, 0.0, 0.1, 0.0, 0.0,
                )
            }
            level.sendParticles(ParticleTypes.END_ROD, at.x, top, at.z, 2, 0.1, 0.1, 0.1, 0.01)
            step++
        }
    }

    private fun damage(sequence: Sequence): Int =
        scale(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.STRONG)

    private companion object {
        const val MAX_RANGE = 20.0
        const val PILLAR_HEIGHT = 15.0
        const val PILLAR_RADIUS = 1.6
        const val BASE_DAMAGE = 9
        const val COOLDOWN_SECONDS = 18
        const val FIRE_SECONDS = 5
        const val FALL_TICKS = 12
        const val RING_POINTS = 10
        const val SWIRL = 0.25
        const val TAU = 2.0 * Math.PI
    }
}
