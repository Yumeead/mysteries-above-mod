package me.vangoo.mysteries.pathways.tyrant.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tyrant, Посл. 7 (Мореплавець): **Цілюща Вода**.
 *
 * Мореплавець огортає себе чи союзника, на якого дивиться, живлющою водою — слабка
 * регенерація. Навмисно слабша за зілля, зате миттєва й безкоштовна для матеріалів
 * підтримка в бою.
 */
class RestorativeWater : ActiveAbility(AbilityIdentity.of("tyrant_restorative_water")) {

    override val displayName: Component = Component.literal("Цілюща Вода")

    override val spiritualityCost: Int = 35

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Живлюща вода огортає вас або союзника, на якого ви дивитесь: слабка регенерація " +
            "на ${durationTicks(sequence) / TICKS_PER_SECOND} с. Поступається зіллям, зате миттєва.",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val caster = context.caster
        // Ціль — союзник на лінії зору, інакше сам кастер: промах не має коштувати касту.
        val target: LivingEntity = context.targeting.playerLookingAt(MAX_RANGE) ?: caster

        target.addEffect(
            MobEffectInstance(MobEffects.REGENERATION, durationTicks(context.sequence), 0, false, true, true),
        )

        splash(context.level, target)

        if (target !== caster) {
            context.messaging.actionBar(
                target.uuid,
                Component.literal("Цілюща вода огортає вас — ${caster.name.string}"),
            )
            return AbilityOutcome.done("Ви огорнули ${target.name.string} цілющою водою")
        }
        return AbilityOutcome.done("Цілюща вода огортає вас")
    }

    private fun durationTicks(sequence: Sequence): Int =
        scale(BASE_DURATION_TICKS, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    /**
     * Бризки навколо голови плюс коло крапель під ногами.
     *
     * Геометрія тут місцева й навмисно проста: словник складених фігур — фаза 07, і
     * саме туди ця пара переїде цілком. Заводити для неї службу до того, як фігур
     * стане більше однієї, нема сенсу.
     */
    private fun splash(level: ServerLevel, target: LivingEntity) {
        val eye = target.eyePosition
        level.sendParticles(ParticleTypes.SPLASH, eye.x, eye.y, eye.z, 20, 0.4, 0.4, 0.4, 0.05)

        val feet = target.position()
        repeat(RING_POINTS) { index ->
            val angle = 2.0 * Math.PI * index / RING_POINTS
            level.sendParticles(
                ParticleTypes.FALLING_WATER,
                feet.x + RING_RADIUS * cos(angle),
                feet.y + 0.1,
                feet.z + RING_RADIUS * sin(angle),
                1, 0.0, 0.0, 0.0, 0.0,
            )
        }

        level.playSound(
            null, feet.x, feet.y, feet.z,
            SoundEvents.WATER_AMBIENT, SoundSource.PLAYERS, 1.0f, 1.2f,
        )
    }

    private companion object {
        /** ~4 с регенерації I на Посл. 7; росте з Послідовністю. */
        const val BASE_DURATION_TICKS = 80
        const val MAX_RANGE = 20.0
        const val COOLDOWN_SECONDS = 20
        const val RING_POINTS = 20
        const val RING_RADIUS = 1.2
    }
}
