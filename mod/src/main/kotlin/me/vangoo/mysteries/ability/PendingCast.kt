package me.vangoo.mysteries.ability

import me.vangoo.domain.abilities.CastReceipt
import me.vangoo.domain.abilities.CastTicket
import me.vangoo.domain.entities.Beyonder
import net.minecraft.server.level.ServerPlayer

/**
 * Незакінчений каст: єдине, що здібності дозволено пережити виклик `cast`.
 *
 * Це заміна плагінного `AbilityResult.deferred()` разом із `AbilityResourceConsumer`.
 * Там інваріант тримався коментарем — «КРИТИЧНО: НЕ встановлювати кулдаун якщо
 * результат deferred» — і кожна відкладена здібність мусила не забути покликати
 * консюмер вручну. Тут забути нічого: не покликав [complete] — каст був безкоштовний,
 * покликав двічі — впав `check()` усередині квитка.
 *
 * Контекст цілком у сесію не бере: він несе ідентичність одного касту й одного
 * кастера. Цей об'єкт — теж, але він рівно про це і є.
 */
class PendingCast internal constructor(
    private val executor: AbilityExecutor,
    private val caster: ServerPlayer,
    private val beyonder: Beyonder,
    private val ticket: CastTicket,
) {

    val isComplete: Boolean get() = ticket.isSettled

    /**
     * Списує духовність, нараховує засвоєння, ставить кулдаун і накладає штраф глузду.
     * Рівно один раз.
     */
    fun complete(): CastReceipt = executor.settle(caster, beyonder, ticket)

    override fun toString(): String = "PendingCast($ticket)"
}
