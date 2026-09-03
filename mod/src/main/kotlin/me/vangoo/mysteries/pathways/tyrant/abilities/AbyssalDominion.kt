package me.vangoo.mysteries.pathways.tyrant.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.event.IncomingDamage
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource

/**
 * Tyrant, Seq. 5 (Ocean Songster): **Abyssal Dominion**.
 *
 * Underwater, the Songster fears no Beyonder below a demigod: the water smothers blows against
 * them and strengthens their own.
 */
class AbyssalDominion : PermanentPassiveAbility(AbilityIdentity.of("tyrant_abyssal_dominion")) {

    override val displayName: Component = Component.literal("Володіння Безоднею")

    override fun description(sequence: Sequence): Component = Component.literal(
        "Поки ви у воді, безодня — ваша.\n" +
            "▪ Вхідна шкода: -${((1 - INCOMING_MULTIPLIER) * 100).toInt()}%\n" +
            "▪ Ваші удари: +${((MELEE_MULTIPLIER - 1) * 100).toInt()}%",
    )

    override fun onActivate(context: AbilityContext) {
        val owner = context.caster.uuid

        context.events.on<IncomingDamage> { damage ->
            if (damage.isCancelled) return@on
            val victim = damage.victim

            if (victim.uuid == owner) {
                if (!victim.isInWater) return@on
                damage.scale(INCOMING_MULTIPLIER)
                shield(damage)
                return@on
            }

            val attacker = damage.attacker ?: return@on
            if (attacker.uuid != owner || !attacker.isInWater) return@on
            damage.scale(MELEE_MULTIPLIER)
        }
    }

    /** The water smothers the blow: a ring in the pathway colour, bubbles and a muffled splash. */
    private fun shield(damage: IncomingDamage) {
        val victim = damage.victim
        val level = victim.level() as? net.minecraft.server.level.ServerLevel ?: return
        val at = victim.position()
        level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, at.x, at.y + 1.0, at.z, 12, 0.4, 0.6, 0.4, 0.02)
        level.playSound(
            null, at.x, at.y, at.z,
            SoundEvents.AMBIENT_UNDERWATER_EXIT, SoundSource.PLAYERS, 0.7f, 0.7f,
        )
    }

    private companion object {
        const val INCOMING_MULTIPLIER = 0.65
        const val MELEE_MULTIPLIER = 1.4
    }
}
