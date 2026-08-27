package me.vangoo.mysteries.client.beyonder

import me.vangoo.domain.valueobjects.Spirituality
import me.vangoo.mysteries.net.payload.AbilityRosterPayload
import me.vangoo.mysteries.net.payload.AbilityStatePayload
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import kotlin.math.ceil

/**
 * Усе, що клієнт знає про здібності гравця.
 *
 * Кеш останніх двох пакетів плюс власний лічильник тіків. Потрібен саме кеш: малювати
 * доводиться 60+ разів на секунду, а пакети приходять щонайбільше раз на секунду —
 * щось мусить тримати між ними, і донині на клієнті не тримало нічого.
 *
 * **Нічого не рахує з правил гри.** Ні духовності, ні дозволів, ні того, чи каст
 * можливий: усе це сервер уже вирішив і надіслав. Єдина арифметика тут — відлік
 * кулдауну між пакетами, і та є похідною від надісланого тіку кінця.
 *
 * Стан глобальний, бо глобальний і сам клієнт: гравець тут рівно один.
 */
object ClientAbilityState {

    /**
     * Монотонний лічильник клієнтських тіків.
     *
     * Настінний час не годиться: одиночна гра на паузі стоїть — і сервер, і кулдауни
     * разом із нею, — а `System.currentTimeMillis` цокав би далі й показував готовність
     * раніше, ніж вона настане.
     */
    private var clientTick = 0

    /** Тік клієнта й тік сервера на момент останнього пакета стану. */
    private var anchorClientTick = 0
    private var anchorServerTick = 0
    private var anchored = false

    /** `null` — гравець не Потойбічний. */
    var roster: AbilityRosterPayload.Roster? = null
        private set

    /** `null` — стану ще не надсилали (або гравець не Потойбічний). */
    var spirituality: Spirituality? = null
        private set

    /** Ідентичність обраної здібності; вибір тримає сервер, тут лише його відлуння. */
    var selected: String? = null
        private set

    private val live = HashMap<String, AbilityStatePayload.Live>()

    val isBeyonder: Boolean get() = roster != null

    /** Порядок — той самий, що в `Beyonder.abilities`: стабільний, під м'язову пам'ять. */
    val entries: List<AbilityRosterPayload.Entry> get() = roster?.entries.orEmpty()

    fun entry(identity: String): AbilityRosterPayload.Entry? = entries.firstOrNull { it.identity == identity }

    // ── Вхідні пакети ───────────────────────────────────────────────────────────

    /** Кличеться з клієнтського такту; єдине місце, де цокає [clientTick]. */
    fun tick() {
        clientTick++
    }

    fun accept(payload: AbilityRosterPayload) {
        val fresh = payload.roster
        roster = fresh
        if (fresh == null) {
            // Гравець перестав бути Потойбічним: стану більше не буде, а лишений
            // показував би кулдауни сили, якої вже нема.
            resetState()
            return
        }
        // Здібність могла зникнути разом зі зміною списку — її запис у [live] інакше
        // висів би до виходу зі світу.
        val known = fresh.entries.mapTo(HashSet()) { it.identity }
        live.keys.retainAll(known)
        val chosen = selected
        if (chosen != null && chosen !in known) selected = null
    }

    fun accept(payload: AbilityStatePayload) {
        // Перезаякорення на кожному пакеті: дрейф лічильника не накопичується.
        anchorServerTick = payload.serverTick
        anchorClientTick = clientTick
        anchored = true

        spirituality = payload.spirituality
        selected = payload.selected

        // Знімок повний за задумом: у пакеті лише ті, у кого є що сказати, тож
        // відсутність запису означає «готова, вимкнена, не триває».
        live.clear()
        payload.live.associateByTo(live) { it.identity }
    }

    /** Вихід зі світу. Наступний світ почне з чистого аркуша. */
    fun clear() {
        roster = null
        resetState()
    }

    private fun resetState() {
        spirituality = null
        selected = null
        live.clear()
        anchored = false
    }

    // ── Похідні числа ───────────────────────────────────────────────────────────

    /** Тік сервера, яким він має бути зараз: якір плюс те, що клієнт відцокав сам. */
    fun estimatedServerTick(): Int = anchorServerTick + (clientTick - anchorClientTick)

    fun isToggled(identity: String): Boolean = live[identity]?.toggled == true

    fun isSustaining(identity: String): Boolean = live[identity]?.sustaining == true

    /** Скільки тіків лишилось перепочивати; `0` — здібність готова. */
    fun remainingTicks(identity: String): Int {
        if (!anchored) return 0
        val readyAt = live[identity]?.readyAtTick ?: return 0
        if (readyAt == 0) return 0
        return (readyAt - estimatedServerTick()).coerceAtLeast(0)
    }

    /**
     * Округлення вгору — те саме правило, що й у серверній `CooldownTable`:
     * «лишилось 0 с» при ще не спливлому таймері збиває з пантелику.
     */
    fun remainingSeconds(identity: String): Int {
        val ticks = remainingTicks(identity)
        return if (ticks <= 0) 0 else ceil(ticks.toDouble() / TICKS_PER_SECOND).toInt()
    }

    /**
     * Частка перепочинку, що вже минула: `0` — щойно почався, `1` — готова.
     *
     * Повну тривалість приносить ростер, тож пропорція чесна, а не вгадана з першого
     * побаченого залишку.
     */
    fun cooldownProgress(entry: AbilityRosterPayload.Entry): Float {
        val total = entry.cooldownSeconds * TICKS_PER_SECOND
        if (total <= 0) return 1f
        val remaining = remainingTicks(entry.identity)
        return (1f - remaining.toFloat() / total).coerceIn(0f, 1f)
    }

    /** Підказка, а не заборона: натиснути можна завжди, відмовляє сервер. */
    fun canAfford(entry: AbilityRosterPayload.Entry): Boolean =
        spirituality?.hasSufficient(entry.cost) ?: true
}
