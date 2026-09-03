package me.vangoo.mysteries.pathways.tyrant.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.schedule.TaskHandle
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/**
 * Tyrant, Seq. 5 (Ocean Songster): **Slick Water**.
 *
 * Spills sticky, frictionless water: whoever steps into the patch slides on their own momentum
 * and cannot push off to jump.
 */
class SlickWater : ActiveAbility(AbilityIdentity.of("tyrant_slick_water")) {

    override val displayName: Component = Component.literal("Слизька Вода")

    override val spiritualityCost: Int = 60

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Розливає липку воду без тертя в радіусі ${RADIUS.toInt()} бл. на ${DURATION_TICKS / 20} с: " +
            "усе живе там з'їжджає за власним рухом і не може стрибнути.",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val hit = context.caster.pick(MAX_RANGE, 1.0f, false)
        if (hit.type != HitResult.Type.BLOCK) {
            return AbilityOutcome.failed("Немає поверхні, щоб розлити воду")
        }
        val center = (hit as net.minecraft.world.phys.BlockHitResult).blockPos.let {
            Vec3(it.x + 0.5, it.y + 1.0, it.z + 0.5)
        }

        context.level.sendParticles(ParticleTypes.SPLASH, center.x, center.y, center.z, 20, RADIUS, 0.2, RADIUS, 0.05)
        context.level.sendParticles(ParticleTypes.DRIPPING_WATER, center.x, center.y, center.z, 12, RADIUS * 0.5, 0.1, RADIUS * 0.5, 0.0)
        context.level.playSound(null, center.x, center.y, center.z, SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 1.2f, 0.6f)
        context.level.playSound(null, center.x, center.y, center.z, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 1.0f, 0.7f)

        var tick = 0
        lateinit var handle: TaskHandle
        handle = context.scheduling.every(1) {
            tick++
            if (tick > DURATION_TICKS) return@every handle.cancel()

            if (tick % 5 == 0) {
                context.level.sendParticles(ParticleTypes.DRIPPING_WATER, center.x, center.y, center.z, 12, RADIUS / 2, 0.2, RADIUS / 2, 0.0)
            }

            val box = AABB(center.x - RADIUS, center.y - 1.0, center.z - RADIUS, center.x + RADIUS, center.y + 1.0, center.z + RADIUS)
            for (target in context.level.getEntitiesOfClass(LivingEntity::class.java, box)) {
                target.addEffect(MobEffectInstance(MobEffects.JUMP_BOOST, 10, JUMP_BLOCK_AMPLIFIER))

                val velocity = target.deltaMovement
                val horizontal = Vec3(velocity.x, 0.0, velocity.z)
                if (horizontal.lengthSqr() < 1.0e-4) continue
                var slide = horizontal.scale(SLIDE_GAIN)
                if (slide.length() > SLIDE_CAP) slide = slide.normalize().scale(SLIDE_CAP)
                target.deltaMovement = velocity.add(slide)
            }
        }

        return AbilityOutcome.done("Слизька вода розлилась довкола")
    }

    private companion object {
        const val RADIUS = 4.0
        const val MAX_RANGE = 20.0
        const val SLIDE_GAIN = 0.45
        const val SLIDE_CAP = 1.1
        const val DURATION_TICKS = 200
        const val COOLDOWN_SECONDS = 25
        /** The vanilla idiom for "no jump at all". */
        const val JUMP_BLOCK_AMPLIFIER = 128
    }
}
