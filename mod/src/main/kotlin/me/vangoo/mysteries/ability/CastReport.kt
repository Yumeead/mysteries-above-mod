package me.vangoo.mysteries.ability

import me.vangoo.domain.abilities.CastReceipt
import me.vangoo.domain.abilities.CastRefusal
import me.vangoo.domain.valueobjects.SequenceBasedSuccessChance
import net.minecraft.network.chat.Component

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
}
