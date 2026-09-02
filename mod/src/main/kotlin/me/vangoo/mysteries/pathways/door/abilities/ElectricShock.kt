package me.vangoo.mysteries.pathways.door.abilities

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
import net.minecraft.world.phys.Vec3

/**
 * Door, Посл. 8 (Трюкач): **Електричний розряд**.
 *
 * Єдина сила шляху, яка просто б'є. Вона тут потрібна: усе решта на Посл. 8 — втеча,
 * підпал і дим, тобто Трюкач без неї не має чим закінчити бійку, яку сам почав.
 *
 * Розряд іде **зигзагом**: пряма лінія читалась би як промінь, а промінь у цього шляху
 * вже є в Сонця. Зигзаг малюється випадковим відхиленням від осі, а не фізикою — вона
 * тут нічого не додала б.
 */
class ElectricShock : ActiveAbility(AbilityIdentity.of("door_electric_shock")) {

    override val displayName: Component = Component.literal("Електричний розряд")

    override val spiritualityCost: Int = 25

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Розряд у живу істоту під прицілом.\n" +
            "▪ Шкода: ${damage(sequence)}\n" +
            "▪ Дальність ${RANGE.toInt()} бл., перепочинок ${COOLDOWN_SECONDS} с",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val target = context.targeting.lookingAt(RANGE)
            ?: return AbilityOutcome.failed("Немає цілі")

        val to = target.position().add(0.0, target.bbHeight * 0.5, 0.0)
        arc(context, to)

        target.hurtServer(
            context.level,
            context.level.damageSources().indirectMagic(context.caster, context.caster),
            damage(context.sequence).toFloat(),
        )
        context.level.playSound(
            null, to.x, to.y, to.z,
            SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.6f, 2.0f,
        )

        return AbilityOutcome.done("Розряд вразив ${target.name.string}")
    }

    private fun arc(context: CastContext, to: Vec3) {
        val from = context.eye
        val steps = (from.distanceTo(to) / STEP).toInt().coerceIn(1, MAX_STEPS)
        val random = context.level.random
        for (i in 0..steps) {
            val point = from.lerp(to, i.toDouble() / steps).add(
                (random.nextDouble() - 0.5) * JITTER,
                (random.nextDouble() - 0.5) * JITTER,
                (random.nextDouble() - 0.5) * JITTER,
            )
            context.level.sendParticles(
                ParticleTypes.ELECTRIC_SPARK,
                point.x, point.y, point.z,
                2, 0.0, 0.0, 0.0, 0.0,
            )
        }
    }

    private fun damage(sequence: Sequence): Int =
        scale(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        const val RANGE = 6.0
        const val BASE_DAMAGE = 4
        const val COOLDOWN_SECONDS = 8
        const val STEP = 0.4
        const val MAX_STEPS = 40
        const val JITTER = 0.3
    }
}
