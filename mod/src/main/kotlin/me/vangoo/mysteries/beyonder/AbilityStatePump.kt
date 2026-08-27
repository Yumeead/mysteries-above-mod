package me.vangoo.mysteries.beyonder

import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import java.util.UUID

/**
 * Вирішує **коли** штовхати клієнту стан здібностей.
 *
 * Правило одне: надсилати на зміну, а не на тік. Виконують його дві половини —
 * брудний прапорець і запобіжник:
 *
 * - [markDirty] кличеться звідусіль, де стан справді змінився: каст, тогл, старт і
 *   кінець сесії, зміна вибору, піднесення, реально зросла духовність. Пакета це
 *   ще не породжує.
 * - [flush] раз на секунду відправляє «брудним», і не частіше — гравець, що нічого
 *   не робить і має стелю духовності, не породжує жодного пакета.
 * - [pushNow] обходить секундний такт: після касту бар має впасти в той самий кадр,
 *   а не за 900 мс.
 *
 * **Знімка не будує й гравців не тримає.** [BeyonderStatus] — чистий збирач знімків;
 * дати йому ще й такт означало б дати другу відповідальність, і саме так у плагіні
 * народжувались «сервіси, що роблять усе». Сюди приходить лише `UUID`, а що з ним
 * робити, вирішує [send] — тому цей клас перевіряється без гри зовсім.
 */
class AbilityStatePump(
    private val send: (UUID) -> Unit,
    private val now: () -> Long,
    private val periodTicks: Int = TICKS_PER_SECOND,
) {

    /** `LinkedHashSet`, щоб порядок відправки був той самий, що й порядок бруднення. */
    private val dirty = LinkedHashSet<UUID>()

    private val sentAt = HashMap<UUID, Long>()

    fun markDirty(playerId: UUID) {
        dirty += playerId
    }

    /** Дія гравця: стан летить у цей самий тік, поза чергою і поза запобіжником. */
    fun pushNow(playerId: UUID) {
        dirty -= playerId
        sentAt[playerId] = now()
        send(playerId)
    }

    /**
     * Секундний такт. Кличеться з такту [me.vangoo.mysteries.ability.runtime.AbilityServices]
     * там само, де вже йде регенерація.
     */
    fun flush() {
        if (dirty.isEmpty()) return
        val tick = now()

        val iterator = dirty.iterator()
        while (iterator.hasNext()) {
            val playerId = iterator.next()
            val last = sentAt[playerId]
            // Запобіжник: щойно надісланий стан не дублюється секундним тактом.
            if (last != null && tick - last < periodTicks) continue
            iterator.remove()
            sentAt[playerId] = tick
            send(playerId)
        }
    }

    /** Гравця вже немає на сервері — ані бруднити, ані пам'ятати його більше нема сенсу. */
    fun forget(playerId: UUID) {
        dirty -= playerId
        sentAt.remove(playerId)
    }
}
