package me.vangoo.mysteries.ability

import me.vangoo.domain.abilities.CastReceipt
import me.vangoo.domain.abilities.CastRefusal
import me.vangoo.domain.valueobjects.SequenceBasedSuccessChance
import me.vangoo.mysteries.ability.context.Fizzle
import me.vangoo.mysteries.ability.context.Messaging
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

/** How a cast attempt ended. What [AbilityExecutor] hands back to whoever called it. */
sealed interface CastReport {

    /** The ability ran; [receipt] is present only if the ticket was settled right away. */
    data class Performed(val outcome: AbilityOutcome, val receipt: CastReceipt?) : CastReport

    data class Refused(val reason: CastRefusal) : CastReport

    data class OnCooldown(val remainingSeconds: Long) : CastReport

    /** The target held out. The cooldown is charged, the spirituality is not. */
    data class Resisted(val chance: SequenceBasedSuccessChance) : CastReport

    /** Someone stole this power: the owner cannot call it until the window closes. */
    data class Suppressed(val secondsLeft: Long) : CastReport

    /** A Judge's Prohibition stands over the caster and forbids what this power does. Nothing is charged. */
    data object Forbidden : CastReport

    /** The caster is bound ([Restraints]): no power answers them until they are let go. Nothing is charged. */
    data object Restrained : CastReport

    /** The player is not a Beyonder at all. */
    data object NotBeyonder : CastReport
}

/**
 * The Ukrainian text for a cast report. Kept apart from [AbilityExecutor] so that it stays
 * about the order of actions rather than about wording.
 */
object CastMessages {

    fun of(report: CastReport): Component? = when (report) {
        is CastReport.NotBeyonder -> Component.literal("Ви не Потойбічний")

        is CastReport.OnCooldown ->
            Component.literal("Відкат: ${report.remainingSeconds} с")

        is CastReport.Suppressed ->
            Component.literal("Цю силу у вас вкрали — повернеться за ${report.secondsLeft} с")

        is CastReport.Forbidden -> Component.literal("Тут це заборонено")

        is CastReport.Restrained -> Component.literal("Ви скуті")

        is CastReport.Resisted ->
            Component.literal("Ціль чинила опір! (шанс успіху ${report.chance.getFormattedChance()})")

        is CastReport.Refused -> when (val reason = report.reason) {
            is CastRefusal.NotOwned -> Component.literal("У вас немає цієї здібності")
            is CastRefusal.NotCastable -> Component.literal("Цю здібність не застосовують вручну")
            is CastRefusal.InsufficientSpirituality -> Component.literal(
                "Недостатньо духовності: потрібно ${reason.required}, є ${reason.available}",
            )
        }

        // An ability words its own message — there is nobody to paraphrase it.
        is CastReport.Performed -> report.outcome.message
    }

    /**
     * How the refusal shows at the hand. `null` — the report is no refusal: the power happened.
     *
     * Resistance is [Fizzle.NONE] on purpose: the clash is drawn on the **target**, where both
     * sides see it, and a fizzle at the caster's hand would say the power never left it.
     */
    fun fizzleOf(report: CastReport): Fizzle? = when (report) {
        is CastReport.NotBeyonder -> Fizzle.NONE
        is CastReport.OnCooldown -> Fizzle.CLICK
        is CastReport.Suppressed -> Fizzle.SPARK
        is CastReport.Forbidden -> Fizzle.SPARK
        is CastReport.Restrained -> Fizzle.CLICK
        is CastReport.Resisted -> Fizzle.NONE
        is CastReport.Refused -> when (report.reason) {
            is CastRefusal.NotOwned -> Fizzle.NONE
            is CastRefusal.NotCastable -> Fizzle.SPARK
            is CastRefusal.InsufficientSpirituality -> Fizzle.EMPTY
        }
        is CastReport.Performed -> if (report.outcome is AbilityOutcome.Failed) Fizzle.SPARK else null
    }

    /**
     * The one delivery both entry points share: a refusal becomes a fizzle and a hint, never the
     * action bar.
     *
     * A power that **did** happen still words its outcome in the action bar — until its pathway is
     * reworked to show it in the world instead (the Sun goes first). Some of those lines are the
     * answer itself (divination, deduction), and silencing them all at once would take the answer away.
     */
    fun deliver(messaging: Messaging, player: ServerPlayer, report: CastReport) {
        val text = of(report) ?: return
        when (val fizzle = fizzleOf(report)) {
            null -> messaging.actionBar(player, text)
            else -> messaging.hint(player, text, fizzle)
        }
    }
}
