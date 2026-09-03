package me.vangoo.mysteries.pathways.error.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.beyonder.TheftRegistry
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.GameType

/**
 * Error, Seq. 5 (Dream Stealer): **Theft of a General Ability**.
 *
 * *"They can steal certain general abilities, such as the ability to walk, fly, speak."* Not a
 * power — a capacity everyone has and nobody thinks about until it is gone.
 *
 * ## What is taken is what they were doing
 *
 * The plugin offered a submenu of three. Here the mark chooses for the thief without knowing it:
 * whoever is **in the air** loses flight, whoever is **moving** loses their legs, and whoever is
 * standing still loses their voice — which for a Beyonder means their powers stop answering.
 *
 * That is the pathway's own logic rather than a shortcut around a screen: Error takes what it
 * finds in your hands. It also makes the power read differently in every fight, which a menu of
 * three fixed options never would.
 *
 * A screen could still improve this one day — three named capacities inside one power is exactly
 * what phase 11 is for — but it would be a convenience laid on top, not the mechanic itself.
 *
 * ## The thief mirrors what he took
 *
 * The wiki is explicit that the Dream Stealer performs what was taken. Flight taken means flight
 * granted; legs taken mean the thief moves like the mark should have. A stolen voice grants
 * nothing — there is nothing to show — and that is why it lasts as long as the rest.
 *
 * Creative and spectator marks are left alone: their flight is not a capacity, it is a game mode.
 */
class AbilityTheft : ActiveAbility(ConceptualTheft.key("capacity")) {

    private enum class Capacity(val label: String, val loss: String) {
        FLIGHT("політ", "Небо більше вас не тримає"),
        LEGS("ходу", "Ноги перестали слухатись"),
        VOICE("голос", "Слова застрягли — сили не відгукуються"),
    }

    override val displayName: Component = Component.literal("Крадіжка здатності")

    override val spiritualityCost: Int = ConceptualTheft.COST

    override fun cooldownSeconds(sequence: Sequence): Int = ConceptualTheft.COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ви забираєте не силу, а вміння — те, яким ціль саме користується.\n" +
            "▪ Ціль під поглядом, до ${ConceptualTheft.RANGE.toInt()} бл.\n" +
            "▪ У повітрі — політ, у русі — хода, на місці — голос (і сили разом із ним)\n" +
            "▪ Триває ${holdTicks(sequence) / TICKS_PER_SECOND} с; забране працює у вас\n" +
            "▪ Займає ваш єдиний слот украденого\n" +
            "▪ Відкат ${ConceptualTheft.COOLDOWN_SECONDS} с",
    )

    override fun contestedTarget(context: CastContext): LivingEntity? = ConceptualTheft.mark(context)

    override fun cast(context: CastContext): AbilityOutcome {
        ConceptualTheft.slotTaken(context)?.let { return it }

        val target = ConceptualTheft.mark(context)
            ?: return AbilityOutcome.failed("Наведіть погляд на того, у кого берете")

        val what = whatTheyAreUsing(target)
        val hold = holdTicks(context.sequence)
        val caster = context.caster

        take(context, target, what, hold)
        ConceptualTheft.hold(context, target, identity, hold.toLong() * MILLIS_PER_TICK)

        context.session {
            try {
                waitTicks(hold)
            } finally {
                giveBack(target, what)
                owner?.let { stopMirroring(it, what) }
                TheftRegistry.release(ownerId)
            }
        }

        ConceptualTheft.reach(context, target)
        ConceptualTheft.tell(context, target, what.loss)
        return AbilityOutcome.done("Ви забрали ${what.label} у ${target.name.string}")
    }

    /** The mark decides by what they are busy with. */
    private fun whatTheyAreUsing(target: LivingEntity): Capacity = when {
        target.isFallFlying || (target as? ServerPlayer)?.abilities?.flying == true -> Capacity.FLIGHT
        target.deltaMovement.horizontalDistanceSqr() > MOVING -> Capacity.LEGS
        else -> Capacity.VOICE
    }

    private fun take(context: CastContext, target: LivingEntity, what: Capacity, hold: Int) {
        val caster = context.caster
        when (what) {
            Capacity.FLIGHT -> {
                (target as? ServerPlayer)?.takeIf { it.gameMode() == GameType.SURVIVAL }?.let {
                    it.abilities.mayfly = false
                    it.abilities.flying = false
                    it.onUpdateAbilities()
                }
                target.stopFallFlying()
                caster.abilities.mayfly = true
                caster.onUpdateAbilities()
            }

            Capacity.LEGS -> {
                target.addEffect(MobEffectInstance(MobEffects.SLOWNESS, hold, ROOTED, false, true, true))
                caster.addEffect(MobEffectInstance(MobEffects.SPEED, hold, 1, false, true, true))
            }

            Capacity.VOICE ->
                (target as? ServerPlayer)?.let { context.cooldowns.lockAll(it.uuid, hold / TICKS_PER_SECOND) }
        }
    }

    /**
     * Flight is the only capacity that has to be handed back by hand: the other two ride on
     * effects that expire by themselves, and a locked cooldown runs out on its own clock.
     */
    private fun giveBack(target: LivingEntity, what: Capacity) {
        if (what != Capacity.FLIGHT) return
        (target as? ServerPlayer)?.takeIf { it.gameMode() == GameType.SURVIVAL }?.let {
            it.abilities.mayfly = false
            it.onUpdateAbilities()
        }
    }

    private fun stopMirroring(caster: ServerPlayer, what: Capacity) {
        if (what != Capacity.FLIGHT) return
        if (caster.gameMode() == GameType.SURVIVAL) {
            caster.abilities.mayfly = false
            caster.abilities.flying = false
            caster.onUpdateAbilities()
        }
    }

    private fun holdTicks(sequence: Sequence): Int =
        scale(BASE_HOLD_TICKS, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        const val BASE_HOLD_TICKS = 20 * TICKS_PER_SECOND

        /** Slowness at this level is not slowness any more — it is standing still. */
        const val ROOTED = 5
        const val MOVING = 0.01
        const val MILLIS_PER_TICK = 50L
    }
}
