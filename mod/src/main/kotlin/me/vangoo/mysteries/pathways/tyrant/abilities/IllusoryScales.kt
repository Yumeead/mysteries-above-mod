package me.vangoo.mysteries.pathways.tyrant.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.event.IncomingDamage
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import kotlin.math.roundToInt

/**
 * Tyrant, Seq. 9 (Sailor): **Illusory Scales**.
 *
 * Scales hidden under the skin make the Sailor slippery and hard to the touch, like a fish.
 * Mechanically it is damage resistance, but only while the body is in water, where the scales
 * "come alive". On land there is no effect.
 *
 * ## The first ability built on events (phase 06)
 *
 * Before the seam appeared, the scales were **polling**: twice a second they checked for water
 * and applied a vanilla `RESISTANCE`. That gave three flaws, and all three went away with the tick.
 *
 * | before | after |
 * |---|---|
 * | the effect showed in the potion list and could be removed by someone else's milk | nothing is put on the player |
 * | leaving the water still left the resistance up for half a second | the water is checked at the moment of the blow |
 * | `RESISTANCE I` is exactly 20 %, vanilla has no other numbers | any number, here [REDUCTION] |
 *
 * A whole `removeOwnScales` method went away too: it existed only to tell **our own** applied
 * resistance from a potion the player had drunk, because the plugin cleared the effect
 * indiscriminately.
 *
 * ## The subscription holds a UUID, not a player
 *
 * The handler lives longer than one tick, and a `ServerPlayer` does not: after a death the
 * server creates a new object. So only [AbilityContext.caster]`.uuid` is taken from the context,
 * and the victim is read from the event. The same rule as in `SessionScope.owner`, only here it
 * comes even cheaper — the event carries the victim anyway.
 */
class IllusoryScales : PermanentPassiveAbility(AbilityIdentity.of("tyrant_illusory_scales")) {

    override val displayName: Component = Component.literal("Ілюзорна луска")

    override fun description(sequence: Sequence): Component = Component.literal(
        "Прихована під шкірою луска робить вас слизьким і твердим, мов риба. " +
            "У воді вона зрізає ${(REDUCTION * 100).roundToInt()}% вхідної шкоди.",
    )

    override fun onActivate(context: AbilityContext) {
        val owner = context.caster.uuid

        context.events.on<IncomingDamage> { damage ->
            val victim = damage.victim
            if (victim.uuid != owner) return@on
            // The scales "come alive" only in water — and that is checked at the moment of the
            // blow, not on the last tick before it.
            if (!victim.isInWater) return@on

            damage.scale(1.0 - REDUCTION)
            shimmer(victim)
        }
    }

    /** The scales flashed as they smothered a blow. While there is no blow, they are not seen at all. */
    private fun shimmer(victim: LivingEntity) {
        val level = victim.level() as? ServerLevel ?: return
        level.sendParticles(
            ParticleTypes.BUBBLE,
            victim.x, victim.y + victim.bbHeight / 2.0, victim.z,
            12, 0.35, 0.45, 0.35, 0.02,
        )
    }

    private companion object {
        /**
         * The share of damage cut off. Twenty per cent is exactly what the vanilla
         * `RESISTANCE I` gave in the previous revision: the mechanic changed, the balance did not.
         *
         * It deliberately does not grow with the Sequence: in the plugin the scales did not grow
         * either, and a Sailor who has risen gets stronger versions of the resistance as separate
         * abilities.
         */
        const val REDUCTION = 0.20
    }
}
