package me.vangoo.mysteries.pathways.door.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sustain
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * Door, Seq. 8 (Trickster): **Vanishing Trick**.
 *
 * The Trickster is yanked backwards, and where they were standing a moment ago a veil remains.
 * Whoever walks into it bogs down.
 *
 * ## The dash is velocity, not a teleport
 *
 * [net.minecraft.world.entity.Entity.hurtMarked] is the same flag vanilla pushes a player with in
 * an explosion: without it the server would change the velocity on its side while the client
 * noticed nothing and pulled the player back on the next tick. A teleport instead of a dash would
 * eat the main thing: a dash can be interrupted by a wall, a teleport cannot.
 *
 * ## The veil stays in place rather than travelling with the Trickster
 *
 * That is why the centre is remembered once, at cast time, and the session draws exactly there. A
 * veil tied to the caster would be smoke on their collar — i.e. it would hide nothing.
 */
class EscapeTrick : ActiveAbility(AbilityIdentity.of("door_escape_trick")) {

    override val displayName: Component = Component.literal("Трюк зі зникненням")

    override val spiritualityCost: Int = 35

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Вас смикає назад, а на вашому місці лишається завіса диму.\n" +
            "▪ Завіса тримається ${SMOKE_TICKS / TICKS_PER_SECOND} с у радіусі ${SMOKE_RADIUS} бл.\n" +
            "▪ Хто в ній — грузне (Сповільнення II)\n" +
            "▪ Відкат ${COOLDOWN_SECONDS} с",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val centre = context.origin.add(0.0, 1.0, 0.0)
        val level = context.level

        // The dash backwards. The vertical component is small and constant: without it the player
        // simply scrapes along the ground, with a large one they take off and take fall damage.
        val back = Vec3(-context.look.x, 0.0, -context.look.z).let {
            if (it.lengthSqr() < 1.0e-6) Vec3(0.0, 0.0, 0.0) else it.normalize().scale(PULL_STRENGTH)
        }
        context.caster.setDeltaMovement(back.x, LIFT, back.z)
        context.caster.hurtMarked = true

        puff(level, centre)
        level.playSound(
            null, centre.x, centre.y, centre.z,
            SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.4f, 0.6f,
        )
        level.playSound(
            null, centre.x, centre.y, centre.z,
            SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.8f,
        )

        context.session {
            sustain(periodTicks = SMOKE_PERIOD_TICKS, durationTicks = SMOKE_TICKS) { trickster ->
                val world = trickster.level() as? ServerLevel ?: return@sustain
                world.sendParticles(
                    ParticleTypes.LARGE_SMOKE,
                    centre.x, centre.y, centre.z,
                    25, 1.3, 0.6, 1.3, 0.0,
                )
                world.getEntitiesOfClass(
                    LivingEntity::class.java,
                    AABB.ofSize(centre, SMOKE_RADIUS * 2, SMOKE_RADIUS * 2, SMOKE_RADIUS * 2),
                ) { it !== trickster && it.position().distanceToSqr(centre) <= SMOKE_RADIUS * SMOKE_RADIUS }
                    .forEach { it.addEffect(MobEffectInstance(MobEffects.SLOWNESS, MIRE_TICKS, 1)) }
            }
        }

        return AbilityOutcome.done("Вас там більше немає")
    }

    /** The initial burst: four vanilla smokes, because one reads as a campfire rather than a veil. */
    private fun puff(level: ServerLevel, centre: Vec3) {
        level.sendParticles(ParticleTypes.LARGE_SMOKE, centre.x, centre.y, centre.z, 120, 1.2, 1.0, 1.2, 0.02)
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, centre.x, centre.y, centre.z, 80, 0.8, 1.5, 0.8, 0.01)
        level.sendParticles(ParticleTypes.SQUID_INK, centre.x, centre.y, centre.z, 45, 0.7, 0.6, 0.7, 0.0)
        level.sendParticles(ParticleTypes.DUST_PLUME, centre.x, centre.y, centre.z, 60, 1.5, 0.8, 1.5, 0.0)
    }

    private companion object {
        const val PULL_STRENGTH = 1.3
        const val LIFT = 0.25
        const val SMOKE_RADIUS = 2.5
        const val COOLDOWN_SECONDS = 10
        const val SMOKE_TICKS = 3 * TICKS_PER_SECOND
        const val SMOKE_PERIOD_TICKS = 5
        /** A little longer than the tick, so it does not let go between ticks. */
        const val MIRE_TICKS = 20
    }
}
