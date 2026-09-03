package me.vangoo.mysteries.pathways.error.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.grantAttribute
import me.vangoo.mysteries.ability.modifierId
import me.vangoo.mysteries.ability.revokeAttribute
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.beyonder.TheftRegistry
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes

/**
 * Error, Seq. 5 (Dream Stealer): **Heart Theft**.
 *
 * *"They can steal things such as an organ."* Minecraft has no organs, and it has exactly one
 * thing that stands for one: the hearts. Four of them leave the mark and beat for the thief.
 *
 * ## Modifiers, not damage
 *
 * Nothing is hurt here. Both sides get a **transient** modifier on max health — the mark's
 * negative, the thief's positive — so the moment the theft ends the numbers simply are what they
 * were. A transient modifier is never written to the player file, which is why a server crash
 * mid-theft cannot leave anyone permanently short of a heart.
 *
 * Vanilla clamps current health to the new maximum on its own, so a mark at full health drops to
 * their new ceiling and does not die of the theft.
 *
 * ## The session times the effect, the registry holds the slot
 *
 * Two clocks for two different jobs, and deliberately: the session's `finally` gives the hearts
 * back on **every** exit — the term running out, a recast, the thief leaving, the server stopping
 * — while the registry keeps the Dream Stealer's single slot occupied for exactly as long.
 */
class HeartTheft : ActiveAbility(ConceptualTheft.key("heart")) {

    override val displayName: Component = Component.literal("Крадіжка серця")

    override val spiritualityCost: Int = ConceptualTheft.COST

    override fun cooldownSeconds(sequence: Sequence): Int = ConceptualTheft.COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ви виймаєте з цілі частину життя — і вона б'ється у вас.\n" +
            "▪ Ціль під поглядом, до ${ConceptualTheft.RANGE.toInt()} бл.\n" +
            "▪ ${(HEARTS / 2).toInt()} ❤ переходять до вас на ${holdTicks(sequence) / TICKS_PER_SECOND} с\n" +
            "▪ Займає ваш єдиний слот украденого\n" +
            "▪ Відкат ${ConceptualTheft.COOLDOWN_SECONDS} с",
    )

    override fun contestedTarget(context: CastContext): LivingEntity? = ConceptualTheft.mark(context)

    override fun cast(context: CastContext): AbilityOutcome {
        ConceptualTheft.slotTaken(context)?.let { return it }

        val target = ConceptualTheft.mark(context)
            ?: return AbilityOutcome.failed("Наведіть погляд на того, чиє серце берете")
        if (target.maxHealth <= HEARTS + MIN_REMAINDER) {
            return AbilityOutcome.failed("У ${target.name.string} не лишиться на чому триматись")
        }

        val hold = holdTicks(context.sequence)
        val caster = context.caster
        val stolenFrom = modifierId(identity.id, "victim")
        val stolenTo = modifierId(identity.id, "thief")

        target.grantAttribute(Attributes.MAX_HEALTH, stolenFrom, -HEARTS)
        caster.grantAttribute(Attributes.MAX_HEALTH, stolenTo, HEARTS)
        caster.health = caster.health + HEARTS.toFloat()
        ConceptualTheft.hold(context, target, identity, hold.toLong() * MILLIS_PER_TICK)

        context.session {
            try {
                waitTicks(hold)
            } finally {
                target.revokeAttribute(Attributes.MAX_HEALTH, stolenFrom)
                owner?.revokeAttribute(Attributes.MAX_HEALTH, stolenTo)
                TheftRegistry.release(ownerId)
            }
        }

        ConceptualTheft.reach(context, target)
        ConceptualTheft.tell(context, target, "Щось у грудях завмерло — і вас стало менше")
        return AbilityOutcome.done("Серце ${target.name.string} б'ється у вас")
    }

    private fun holdTicks(sequence: Sequence): Int =
        scale(BASE_HOLD_TICKS, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        /** Four hearts, in vanilla health points. */
        const val HEARTS = 8.0
        const val BASE_HOLD_TICKS = 60 * TICKS_PER_SECOND

        /** What must be left standing: a theft is not an execution. */
        const val MIN_REMAINDER = 2.0
        const val MILLIS_PER_TICK = 50L
    }
}
