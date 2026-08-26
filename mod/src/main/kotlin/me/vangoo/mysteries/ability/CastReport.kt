package me.vangoo.mysteries.ability

import me.vangoo.domain.abilities.CastReceipt
import me.vangoo.domain.abilities.CastRefusal
import me.vangoo.domain.valueobjects.SequenceBasedSuccessChance
import net.minecraft.network.chat.Component

/** Чим скінчилась спроба касту. Те, що [AbilityExecutor] віддає тому, хто його покликав. */
sealed interface CastReport {

    /** Здібність відпрацювала; [receipt] є, тільки якщо квиток погашено вже зараз. */
    data class Performed(val outcome: AbilityOutcome, val receipt: CastReceipt?) : CastReport

    data class Refused(val reason: CastRefusal) : CastReport

    data class OnCooldown(val remainingSeconds: Long) : CastReport

    /** Ціль устояла. Кулдаун списано, духовність — ні. */
    data class Resisted(val chance: SequenceBasedSuccessChance) : CastReport

    /** Гравець узагалі не Потойбічний. */
    data object NotBeyonder : CastReport
}

/**
 * Український текст для звіту про каст. Окремо від [AbilityExecutor], щоб той лишався
 * про порядок дій, а не про формулювання.
 */
object CastMessages {

    fun of(report: CastReport): Component? = when (report) {
        is CastReport.NotBeyonder -> Component.literal("Ви не Потойбічний")

        is CastReport.OnCooldown ->
            Component.literal("Перепочинок: ${report.remainingSeconds} с")

        is CastReport.Resisted ->
            Component.literal("Ціль чинила опір! (шанс успіху ${report.chance.getFormattedChance()})")

        is CastReport.Refused -> when (val reason = report.reason) {
            is CastRefusal.NotOwned -> Component.literal("У вас немає цієї здібності")
            is CastRefusal.NotCastable -> Component.literal("Цю силу не викликають навмисно")
            is CastRefusal.InsufficientSpirituality -> Component.literal(
                "Недостатньо духовності: потрібно ${reason.required}, є ${reason.available}",
            )
        }

        // Своє повідомлення здібність формулює сама — переказувати його нема кому.
        is CastReport.Performed -> report.outcome.message
    }
}
