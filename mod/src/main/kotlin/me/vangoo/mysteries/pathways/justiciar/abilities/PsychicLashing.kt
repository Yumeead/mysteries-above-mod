package me.vangoo.mysteries.pathways.justiciar.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

/**
 * Justiciar, Seq. 7 (Interrogator): **Psychic Lashing**.
 *
 * An illusory bolt that whips the mind of the nearest enemy, then leaps to the next one — up to
 * [MAX_BOUNCES] targets — dealing mental damage down the chain.
 */
class PsychicLashing : ActiveAbility(AbilityIdentity.of("justiciar_psychic_lashing")) {

    override val displayName: Component = Component.literal("Психічне Шмагання")

    override val spiritualityCost: Int = 75

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Випускає розряд ілюзорної блискавки в ціль під прицілом (дальність $CAST_RANGE бл.).\n" +
            "▪ Перескакує на найближчих ворогів, до $MAX_BOUNCES цілей\n" +
            "▪ Шкода за влучання: $DAMAGE_PER_HIT",
    )

    override fun contestedTarget(context: CastContext): LivingEntity? = context.targeting.lookingAt(CAST_RANGE)

    override fun cast(context: CastContext): AbilityOutcome {
        var current: LivingEntity? = context.targeting.lookingAt(CAST_RANGE)
            ?: return AbilityOutcome.failed("Немає цілі для атаки")

        val hit = mutableSetOf(context.caster.uuid)
        var origin = context.eye
        var bounces = 0

        while (current != null && bounces < MAX_BOUNCES) {
            strike(context, current, origin)
            hit += current.uuid
            origin = current.eyePosition
            bounces++
            current = nextTarget(context, current.position(), hit)
        }

        return AbilityOutcome.done("Психічне Шмагання вразило $bounces ціл(ей)")
    }

    private fun strike(context: CastContext, target: LivingEntity, from: Vec3) {
        val to = target.eyePosition
        context.level.sendParticles(ParticleTypes.FIREWORK, to.x, to.y, to.z, 10, 0.1, 0.1, 0.1, 0.05)
        context.level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, to.x, to.y, to.z, 5, 0.2, 0.2, 0.2, 0.02)
        context.level.playSound(
            null, target.x, target.y, target.z,
            SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 1.8f,
        )
        target.hurtServer(context.level, context.level.damageSources().indirectMagic(context.caster, context.caster), DAMAGE_PER_HIT)
        target.addEffect(MobEffectInstance(MobEffects.SLOWNESS, 10, 2))
    }

    private fun nextTarget(context: CastContext, center: Vec3, excluded: Set<java.util.UUID>): LivingEntity? {
        val box = net.minecraft.world.phys.AABB.ofSize(center, BOUNCE_RANGE * 2, BOUNCE_RANGE * 2, BOUNCE_RANGE * 2)
        val rangeSquared = BOUNCE_RANGE * BOUNCE_RANGE
        val candidates = context.level.getEntitiesOfClass(LivingEntity::class.java, box) {
            it.uuid !in excluded && it.position().distanceToSqr(center) <= rangeSquared
        }
        return candidates.minByOrNull { it.position().distanceToSqr(center) }
    }

    private companion object {
        const val CAST_RANGE = 12.0
        const val BOUNCE_RANGE = 6.0
        const val MAX_BOUNCES = 4
        const val DAMAGE_PER_HIT = 5.0f
        const val COOLDOWN_SECONDS = 15
    }
}
