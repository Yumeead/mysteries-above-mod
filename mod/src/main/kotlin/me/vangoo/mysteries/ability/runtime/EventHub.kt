package me.vangoo.mysteries.ability.runtime

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.mysteries.ability.context.EVENT_FOREVER
import me.vangoo.mysteries.ability.context.Events
import me.vangoo.mysteries.ability.context.Subscription
import me.vangoo.mysteries.event.GameEvent
import java.util.UUID
import kotlin.reflect.KClass

/**
 * Реєстр підписок: хто на що чекає й доки.
 *
 * Спадкоємець плагінного `TemporaryEventManager`, але без його центральної механіки.
 * Там на кожен новий клас події реєструвався динамічний `Listener` через
 * `Bukkit.getPluginManager().registerEvent(...)` — рефлексія була **обов'язкова**, бо
 * події визначає платформа, і їх тисяча. Тут події визначає мод, їх видно у `GameEvent`,
 * і ключем слугує звичайний [KClass] у мапі.
 *
 * ## Три властивості, за які він відповідає
 *
 * 1. **Підписка не переживає свою здібність.** Ключ — пара «власник + здібність»,
 *    тож [forget] знімає рівно те, що належить одному з них, і ніколи чуже.
 * 2. **Строк спливає сам.** Годинник — [now], серверний тік: гра на паузі підписок не
 *    старить. Прибираються вони **ліниво**, під час розсилки, а не окремим завданням у
 *    планувальнику: підписка, чий строк вийшов, не спрацює в жодному разі, тож платити
 *    за неї тіком не треба.
 * 3. **Одна впала здібність не спиняє решту.** Той самий інваріант, що в `PassiveTicker`
 *    і `TickScheduler`, і та сама причина: подія шкоди прилітає всім одразу.
 *
 * Minecraft тут не потрібен — [GameEvent] платформних типів не має, тож реєстр
 * перевіряється звичайним юніт-тестом, як черга тіків і сесії.
 */
class EventHub(
    private val now: () -> Long,
    private val onError: (String, Throwable) -> Unit = { _, _ -> },
) {

    private inner class Entry(
        val owner: UUID,
        val ability: AbilityIdentity,
        val type: KClass<out GameEvent>,
        val expiresAtTick: Long,
        val handler: (GameEvent) -> Unit,
    ) : Subscription {

        private var cancelled = false

        override val isActive: Boolean
            get() = !cancelled && now() < expiresAtTick

        override fun cancel() {
            cancelled = true
        }
    }

    private val byType = HashMap<KClass<out GameEvent>, MutableList<Entry>>()

    // ── Підписка ────────────────────────────────────────────────────────────────

    fun <E : GameEvent> subscribe(
        owner: UUID,
        ability: AbilityIdentity,
        type: KClass<E>,
        durationTicks: Int,
        handler: (E) -> Unit,
    ): Subscription {
        require(durationTicks > 0) { "Строк підписки має бути додатним, а не $durationTicks" }

        val expiresAt =
            if (durationTicks == EVENT_FOREVER) Long.MAX_VALUE else now() + durationTicks

        @Suppress("UNCHECKED_CAST")
        val entry = Entry(owner, ability, type, expiresAt, handler as (GameEvent) -> Unit)
        byType.getOrPut(type) { mutableListOf() } += entry
        return entry
    }

    /** Погляд на реєстр очима однієї здібності одного гравця — те, що бачить контекст. */
    fun scopeOf(owner: UUID, ability: AbilityIdentity): Events = Scope(owner, ability)

    private inner class Scope(
        private val owner: UUID,
        private val ability: AbilityIdentity,
    ) : Events {
        override fun <E : GameEvent> subscribe(
            type: KClass<E>,
            durationTicks: Int,
            handler: (E) -> Unit,
        ): Subscription = this@EventHub.subscribe(owner, ability, type, durationTicks, handler)
    }

    // ── Розсилка ────────────────────────────────────────────────────────────────

    /**
     * Роздає подію всім, хто чекає саме на її тип.
     *
     * Ключ — точний клас події, без обходу ієрархії: `GameEvent` — плоский список
     * фінальних типів, і підписка на «щось спільне» тут просто не має значення.
     *
     * Обробник має право під час розсилки і відписатись, і підписатись — тому список
     * обходиться знімком, а нові підписки цю ж подію вже не бачать.
     */
    fun publish(event: GameEvent) {
        val waiting = byType[event::class] ?: return
        if (waiting.isEmpty()) return

        for (entry in waiting.toList()) {
            if (!entry.isActive) continue
            try {
                entry.handler(event)
            } catch (error: Throwable) {
                onError("Обробник події ${event::class.simpleName} впав: ${entry.ability}", error)
                entry.cancel()
            }
        }

        waiting.removeIf { !it.isActive }
        if (waiting.isEmpty()) byType.remove(event::class)
    }

    // ── Прибирання ──────────────────────────────────────────────────────────────

    /** Знімає підписки однієї здібності: активація наново, втрата здібності. */
    fun forget(owner: UUID, ability: AbilityIdentity) =
        drop { it.owner == owner && it.ability == ability }

    /** Знімає всі підписки гравця: вихід зі світу, втрата статусу Потойбічного. */
    fun forget(owner: UUID) = drop { it.owner == owner }

    /** Зупинка сервера. Інтегрований сервер піднімається знову в тому самому процесі. */
    fun clear() = byType.clear()

    /** Скільки підписок живе зараз — для діагностики й тестів. */
    val live: Int get() = byType.values.sumOf { list -> list.count { it.isActive } }

    private fun drop(matches: (Entry) -> Boolean) {
        val empty = mutableListOf<KClass<out GameEvent>>()
        for ((type, list) in byType) {
            list.removeIf { entry -> matches(entry).also { if (it) entry.cancel() } }
            if (list.isEmpty()) empty += type
        }
        empty.forEach(byType::remove)
    }
}
