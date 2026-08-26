package me.vangoo.mysteries.schedule

import java.util.PriorityQueue
import java.util.concurrent.ConcurrentLinkedQueue

/** Те, що поставлено в чергу і що з неї можна зняти. */
interface TaskHandle {

    val isActive: Boolean

    fun cancel()
}

/**
 * Черга робіт, прив'язана до серверного тіку.
 *
 * ## Навіщо власний планувальник
 *
 * У Bukkit був `Bukkit.getScheduler()`, і плагін висів на ньому 92 викликами. Fabric
 * планувальника не має взагалі — є лише подія кінця тіку. Це той шматок, який лоадер
 * не дає й доводиться написати: сам цикл, черга відкладених робіт і скасування.
 *
 * ## Годинник — тік, а не стінний час
 *
 * Лічильник цокає лише в [advance], тобто рівно тоді, коли цокнув сервер. Поставлена
 * на паузу одиночна гра стоїть — і сесії разом із нею. `System.currentTimeMillis`
 * ішов би далі, і гравець, який вийшов у меню на хвилину, повернувся б у світ, де
 * його тогл усе списав, а вітер уже стих.
 *
 * ## Один зламаний тік не валить решту
 *
 * Виняток усередині роботи ловиться тут і йде в [onError]. У плагіні цей же інваріант
 * тримав `try/catch` у кожному циклі `PassiveAbilityScheduler`, і забути його було
 * питанням часу.
 *
 * Клас **не** потребує Minecraft і саме тому перевіряється звичайним юніт-тестом.
 */
class TickScheduler(private val onError: (String, Throwable) -> Unit = { _, _ -> }) {

    private class Entry(
        val block: () -> Unit,
        var dueTick: Long,
        /** 0 — одноразова робота; більше нуля — період повтору. */
        val periodTicks: Int,
    ) : TaskHandle {

        @Volatile
        private var cancelled = false

        override val isActive: Boolean get() = !cancelled

        override fun cancel() {
            cancelled = true
        }
    }

    private val lock = Any()

    /** Відкладені роботи, найближча — зверху. */
    private val timed = PriorityQueue<Entry>(compareBy { it.dueTick })

    /**
     * Роботи «на зараз». Черга конкурентна навмисно: корутина, що повернулась із
     * `Dispatchers.IO`, диспетчиться сюди з чужого потоку.
     */
    private val immediate = ConcurrentLinkedQueue<Entry>()

    /** Скільки разів цокнув сервер, відколи піднявся планувальник. */
    var tick: Long = 0L
        private set

    /** Виконати якнайшвидше: цього ж тіку, якщо він ще розбирає чергу. */
    fun submit(block: () -> Unit): TaskHandle =
        Entry(block, tick, periodTicks = 0).also { immediate += it }

    /** Один раз, через [delayTicks] тіків. Нуль і менше — цього ж тіку. */
    fun after(delayTicks: Int, block: () -> Unit): TaskHandle {
        if (delayTicks <= 0) return submit(block)
        val entry = Entry(block, tick + delayTicks, periodTicks = 0)
        synchronized(lock) { timed += entry }
        return entry
    }

    /** Раз на [periodTicks] тіків, уперше — через [delayTicks]. */
    fun every(periodTicks: Int, delayTicks: Int = periodTicks, block: () -> Unit): TaskHandle {
        require(periodTicks > 0) { "Період повтору мусить бути додатним: $periodTicks" }
        val entry = Entry(block, tick + maxOf(0, delayTicks), periodTicks)
        synchronized(lock) { timed += entry }
        return entry
    }

    /**
     * Один тік сервера: настали строки — виконати, черга «на зараз» — розібрати.
     *
     * Розбирається до порожньої, а не фіксованою порцією: корутина, що чекала цього
     * тіку, після відновлення часто диспетчить продовження одразу, і воно мусить
     * потрапити в **цей** тік, інакше `waitTicks(1)` мовчки коштувало б два.
     * Стеля [MAX_DRAIN] тут — від зациклення (`while (true) { yield() }`), а не від
     * навантаження.
     */
    fun advance() {
        tick++

        synchronized(lock) {
            while (true) {
                val head = timed.peek() ?: break
                if (head.dueTick > tick) break
                timed.poll()
                if (!head.isActive) continue
                immediate += head
                if (head.periodTicks > 0) {
                    head.dueTick = tick + head.periodTicks
                    timed += head
                }
            }
        }

        var drained = 0
        while (drained < MAX_DRAIN) {
            val entry = immediate.poll() ?: return
            drained++
            if (!entry.isActive) continue
            try {
                entry.block()
            } catch (error: Throwable) {
                onError("Робота планувальника впала на тіку $tick", error)
            }
        }
        onError(
            "Планувальник розібрав $MAX_DRAIN робіт за тік $tick і зупинився — решта поїде наступним",
            IllegalStateException("Черга не порожніє: ${immediate.size} робіт лишилось"),
        )
    }

    /** Зупинка сервера: черга не має пережити світ, у якому її наповнили. */
    fun cancelAll() {
        synchronized(lock) {
            timed.forEach { it.cancel() }
            timed.clear()
        }
        while (true) {
            val entry = immediate.poll() ?: break
            entry.cancel()
        }
    }

    /** Скільки робіт чекає. Для тестів і діагностики. */
    val pending: Int get() = synchronized(lock) { timed.size } + immediate.size

    private companion object {
        const val MAX_DRAIN = 10_000
    }
}
