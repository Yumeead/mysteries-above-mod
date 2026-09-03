package me.vangoo.mysteries.pathways.error.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.Ticking
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.event.MobTargeting
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.ai.gossip.GossipType
import net.minecraft.world.entity.animal.golem.IronGolem
import net.minecraft.world.entity.npc.villager.Villager
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Error, Seq. 8 (Swindler): **Swindler's Charm**.
 *
 * A trait, not a power to press: the Swindler's mere presence disarms. A hostile creature may
 * fail to take them as a target at all, an iron golem never takes them, and villagers trade with
 * them more willingly.
 *
 * ## It stopped being an active ability
 *
 * In the plugin `SwindlerCharm` was an `ActiveAbility` that merged three wiki entries into one
 * class — its own comments labelled them "Effect 1/2/3". The wiki calls Charm a **Trait**, so
 * here it is a passive, and the other two entries left for classes of their own (`Eloquence`,
 * `ThoughtMisdirection`). Nothing was lost: a trait you have to cast is not a trait.
 *
 * ## Aggro is denied at the moment of acquisition, not swept up afterwards
 *
 * [MobTargeting] arrives from the mod's third mixin, so denial happens **before** the creature
 * has a target at all. The plugin cancelled a Bukkit event and then called `setTarget(null)` on
 * top, because by the time the handler ran the target was sometimes already in place.
 *
 * ## Reputation is planted by presence, not by a right click
 *
 * The plugin wrote the villager's gossip when the player right-clicked them — i.e. the charm
 * worked on the click. Here the tick plants it on every villager within [CHARM_RANGE], so the
 * charm works on the **village**: the Swindler walks through and the prices are already lower.
 * Vanilla does the arithmetic itself and shows it only to this player, exactly as before.
 */
class SwindlerCharm :
    PermanentPassiveAbility(AbilityIdentity.of("error_swindler_charm")),
    Ticking {

    /** Gossip is state to plant, not a moment to catch — so it is planted rarely. */
    override val tickPeriodTicks: Int = 2 * TICKS_PER_SECOND

    override val displayName: Component = Component.literal("Шарм Афериста")

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ваша чарівність обеззброює — вам не треба нічого робити.\n" +
            "▪ Вороже створіння за ${CHARM_RANGE.toInt()} бл. з шансом " +
            "${percent(denialChance(sequence))}% не може взяти вас на приціл\n" +
            "▪ Залізні големи байдужі до вас завжди\n" +
            "▪ Селяни поруч торгують дешевше — знижка близько ${percent(discount(sequence))}%",
    )

    override fun onActivate(context: AbilityContext) {
        val caster = context.caster
        val level = context.level
        val effects = context.effects
        val sequence = context.sequence

        context.events.on<MobTargeting> { event ->
            if (event.target !== caster) return@on
            if (event.mob.distanceToSqr(caster) > CHARM_RANGE * CHARM_RANGE) return@on
            if (event.mob !is IronGolem && Random.nextDouble() >= denialChance(sequence)) return@on

            event.deny()
            level.sendParticles(
                ModParticles.mote(effects.pathwayColor),
                event.mob.x, event.mob.eyeY + 0.4, event.mob.z,
                4, 0.25, 0.25, 0.25, 0.0,
            )
        }
    }

    /**
     * Plants goodwill in every villager within reach.
     *
     * Vanilla caps `MAJOR_POSITIVE` itself, and the value does not spread between villagers, so
     * the charm stays the Swindler's personal affair rather than a rumour about them.
     */
    override fun tick(context: AbilityContext) {
        val caster = context.caster
        val goodwill = (discount(context.sequence) * GOSSIP_SCALE).roundToInt()
        context.level.getEntitiesOfClass(Villager::class.java, caster.boundingBox.inflate(CHARM_RANGE))
            .forEach { villager ->
                val gossips = villager.gossips
                if (gossips.getReputation(caster.uuid) { it == GossipType.MAJOR_POSITIVE } >= goodwill) return@forEach
                gossips.add(caster.uuid, GossipType.MAJOR_POSITIVE, goodwill)
                context.level.playSound(
                    null, villager.x, villager.y, villager.z,
                    SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.4f, 1.2f,
                )
            }
    }

    private fun denialChance(sequence: Sequence): Double =
        scale(BASE_DENIAL_PERCENT, sequence, SequenceScaler.ScalingStrategy.WEAK) / 100.0

    private fun discount(sequence: Sequence): Double =
        (scale(BASE_DISCOUNT_PERCENT, sequence, SequenceScaler.ScalingStrategy.WEAK) / 100.0)
            .coerceAtMost(MAX_DISCOUNT)

    private fun percent(fraction: Double): Int = (fraction * 100).roundToInt()

    private companion object {
        const val CHARM_RANGE = 16.0
        const val BASE_DENIAL_PERCENT = 25
        const val BASE_DISCOUNT_PERCENT = 20
        const val MAX_DISCOUNT = 0.30

        /** Vanilla reads gossip in whole points; a discount fraction becomes one. */
        const val GOSSIP_SCALE = 100
    }
}
