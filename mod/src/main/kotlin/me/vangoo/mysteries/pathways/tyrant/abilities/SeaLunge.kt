package me.vangoo.mysteries.pathways.tyrant.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * Tyrant, Seq. 9 (Sailor): **Sea Lunge**.
 *
 * The sea's own strength throws the body forward in a burst — everyone in the way is struck and
 * shoved aside. Modest, as befits the weakest Sequence, but it gives a fresh Sailor an active
 * outlet to earn mastery with.
 */
class SeaLunge : ActiveAbility(AbilityIdentity.of("tyrant_sea_lunge")) {

    override val displayName: Component = Component.literal("Морський ривок")

    override val spiritualityCost: Int = 35

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Сила моря кидає вас уперед стрімким ривком.\n" +
            "▪ Шкода на шляху: ${damage(sequence)}, з відкиданням",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val caster = context.caster
        val look = caster.lookAngle.normalize()
        val damage = damage(context.sequence)

        val velocity = look.scale(LUNGE_POWER)
        caster.deltaMovement = Vec3(velocity.x, velocity.y.coerceAtLeast(MIN_LIFT), velocity.z)
        caster.hurtMarked = true

        val box = caster.boundingBox.inflate(HIT_RADIUS, 2.0, HIT_RADIUS)
        for (target in context.level.getEntitiesOfClass(LivingEntity::class.java, box) { it !== caster }) {
            val toTarget = target.position().subtract(caster.position())
            if (toTarget.lengthSqr() < 1.0e-4 || look.dot(toTarget.normalize()) < FACING_DOT) continue

            target.hurtServer(context.level, context.level.damageSources().indirectMagic(caster, caster), damage.toFloat())
            val push = look.scale(KNOCKBACK)
            target.deltaMovement = target.deltaMovement.add(push.x, LIFT, push.z)
            target.hurtMarked = true
        }

        splash(context)
        return AbilityOutcome.done("Ривок моря кинув вас уперед")
    }

    /** A splash-wave at the feet plus a trident-riptide sound: the cast reads as a burst, not a slide. */
    private fun splash(context: CastContext) {
        val feet = context.caster.position()
        context.level.sendParticles(ParticleTypes.SPLASH, feet.x, feet.y + 0.1, feet.z, 24, 1.0, 0.2, 1.0, 0.08)
        context.level.playSound(
            null, feet.x, feet.y, feet.z,
            SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, 1.0f, 1.1f,
        )
    }

    private fun damage(sequence: Sequence): Int =
        scale(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        const val BASE_DAMAGE = 3
        const val LUNGE_POWER = 1.4
        const val MIN_LIFT = 0.4
        const val KNOCKBACK = 1.2
        const val LIFT = 0.35
        const val HIT_RADIUS = 3.0
        /** ~66deg cone ahead of the caster, not a needle. */
        const val FACING_DOT = 0.3
        const val COOLDOWN_SECONDS = 6
    }
}
