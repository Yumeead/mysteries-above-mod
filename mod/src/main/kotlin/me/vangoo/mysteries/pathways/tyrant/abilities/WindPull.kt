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
import net.minecraft.world.entity.item.ItemEntity

/**
 * Tyrant, Seq. 6 (Wind-Blessed): **Wind Pull**.
 *
 * Air spins around the Wind-Blessed's palm — every dropped item nearby flies straight into it.
 */
class WindPull : ActiveAbility(AbilityIdentity.of("tyrant_wind_pull")) {

    override val displayName: Component = Component.literal("Поклик Вітру")

    override val spiritualityCost: Int = 20

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Закручує повітря навколо долоні — усі кинуті предмети в радіусі злітають до руки.\n" +
            "▪ Радіус: ${radius(sequence)} бл.",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val caster = context.caster
        val radius = radius(context.sequence).toDouble()
        val hand = caster.eyePosition.subtract(0.0, 0.4, 0.0)

        val box = caster.boundingBox.inflate(radius)
        var pulled = 0
        for (item in context.level.getEntitiesOfClass(ItemEntity::class.java, box) { !it.isRemoved }) {
            if (item.position().distanceTo(hand) > radius) continue

            item.setNoPickUpDelay()
            val pull = hand.subtract(item.position()).scale(PULL_FACTOR)
            item.deltaMovement = if (pull.length() > MAX_PULL_SPEED) pull.normalize().scale(MAX_PULL_SPEED) else pull
            trail(context, item.position(), hand)
            pulled++
        }

        if (pulled == 0) {
            return AbilityOutcome.failed("Поблизу немає предметів")
        }

        context.level.playSound(
            null, hand.x, hand.y, hand.z,
            SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 0.9f, 1.5f,
        )
        return AbilityOutcome.done("Предмети злітають до вашої руки")
    }

    private fun trail(context: CastContext, from: net.minecraft.world.phys.Vec3, to: net.minecraft.world.phys.Vec3) {
        val mid = from.add(to).scale(0.5)
        context.level.sendParticles(ParticleTypes.SMALL_GUST, mid.x, mid.y, mid.z, 2, 0.1, 0.1, 0.1, 0.0)
    }

    private fun radius(sequence: Sequence): Int =
        scale(BASE_RADIUS, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        const val BASE_RADIUS = 8
        const val PULL_FACTOR = 0.35
        const val MAX_PULL_SPEED = 1.5
        const val COOLDOWN_SECONDS = 4
    }
}
