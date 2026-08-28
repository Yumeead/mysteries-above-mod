package me.vangoo.mysteries.ability

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import me.vangoo.domain.abilities.AbilitySpec
import me.vangoo.domain.entities.Beyonder
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * Жива сесія: сила, що триває в часі й може згаснути.
 *
 * ## Що це замінює
 *
 * У плагіні таких класів було 22, і кожен ніс той самий каркас вручну: поле
 * `BukkitTask`, лічильник `runs`, ідемпотентний `cancel()`, зняття себе з мапи сесій і
 * `Bukkit.getPlayer(ownerId)` на початку кожного такту. Коментар «cancel ідемпотентний»
 * був справжнім інваріантом — його тримала дисципліна.
 *
 * Тут це корутина. Такт — `waitTicks`, скасування — скасування джоби, прибирання —
 * `finally` в тілі. Написати сесію, яка при вимкненні не зніме накладений ефект, тепер
 * так само важко, як забути `finally` навколо відкритого файлу.
 *
 * ## Ключ — власник плюс здібність
 *
 * Реєстр [me.vangoo.mysteries.ability.context.Scheduling] тримає **одну** сесію на пару
 * «гравець + здібність»: повторний каст гасить попередню сам. Правило «перекаст замінює
 * сесію» в плагіні було пунктом документації, тепер це поведінка реєстру.
 */
class Session internal constructor(
    val owner: UUID,
    val ability: AbilityIdentity,
    private val job: Job,
) {

    val isActive: Boolean get() = job.isActive

    /** Гасить сесію. Ідемпотентно; тіло сесії добіжить свій `finally`. */
    fun stop() {
        job.cancel()
    }

    suspend fun join() {
        job.join()
    }

    override fun toString(): String = "Session($ability, owner=$owner, active=$isActive)"
}

/** Чому [sustain] віддав керування назад. Скасування сюди не потрапляє — воно не повертається. */
enum class SessionEnd {

    /** Власник вийшов зі світу або помер. */
    OWNER_GONE,

    /** Духовності на утримання не стало. */
    EXHAUSTED,

    /** Вийшов заявлений час. */
    EXPIRED,
}

/**
 * Те, що сесія бачить зсередини.
 *
 * Контексту касту тут навмисно немає: [AbilityContext] несе живого `ServerPlayer` і
 * ідентичність одного виклику, а сесія переживає і респавн, і зміну виміру. Замість
 * збереженого гравця — [owner], який щоразу питає список сервера наново.
 */
interface SessionScope : CoroutineScope {

    val ownerId: UUID

    val ability: AbilitySpec

    /**
     * Власник **зараз**. Між тактами не кешувати: після смерті чи переходу між вимірами
     * сервер створює новий `ServerPlayer`, і старий об'єкт мовчки перестає бути тим гравцем.
     * Це найтихіша з помилок цього шару, і саме тому гравця тут не видають назавжди.
     */
    val owner: ServerPlayer?

    val beyonder: Beyonder?

    /** Скільки тіків сесія живе. */
    val ticksAlive: Long

    /** Ціна утримання за такт із [AbilitySpec.periodicCost] для поточної Послідовності. */
    val periodicCost: Int

    suspend fun waitTicks(ticks: Int)

    /** Списує утримання. `false` — духовності не стало, сесія має згаснути. */
    fun payUpkeep(amount: Int = periodicCost): Boolean
}

/**
 * Основний такт сесії — те, чим були всі 22 `BukkitRunnable` плагіна разом.
 *
 * Цокає [body] раз на [periodTicks], тримає утримання й повертає керування, коли сила
 * має згаснути сама: власник пішов, духовність скінчилась або вийшов [durationTicks].
 * Скасування ззовні (перекаст, вихід із гри, зупинка сервера) сюди не повертається —
 * воно летить далі як `CancellationException`, тож **прибирання пишеться в `finally`**,
 * а не після виклику.
 *
 * ```kotlin
 * context.session {
 *     try {
 *         val end = sustain(periodTicks = 2, upkeep = periodicCost) { player ->
 *             trail(player)
 *         }
 *         if (end == SessionEnd.EXHAUSTED) message("Духовність вичерпана — вітер стихає")
 *     } finally {
 *         owner?.removeEffect(MobEffects.SPEED)
 *     }
 * }
 * ```
 */
suspend fun SessionScope.sustain(
    periodTicks: Int = 1,
    upkeep: Int = 0,
    upkeepPeriodTicks: Int = TICKS_PER_SECOND,
    durationTicks: Int = 0,
    body: (ServerPlayer) -> Unit,
): SessionEnd {
    require(periodTicks > 0) { "Такт сесії мусить бути додатним: $periodTicks" }
    require(upkeepPeriodTicks > 0) { "Період утримання мусить бути додатним: $upkeepPeriodTicks" }

    val deadline = if (durationTicks > 0) ticksAlive + durationTicks else Long.MAX_VALUE
    var sinceUpkeep = 0

    while (true) {
        val player = owner ?: return SessionEnd.OWNER_GONE
        body(player)

        if (ticksAlive >= deadline) return SessionEnd.EXPIRED
        waitTicks(periodTicks)
        if (ticksAlive >= deadline) return SessionEnd.EXPIRED

        sinceUpkeep += periodTicks
        if (upkeep > 0) {
            // Платиться **за кожен** період, що минув, а не один раз за такт. Такт сесії
            // буває довшим за період оплати (`SunHalo` б'є раз на дві секунди, а платить
            // щосекунди) — і попередня редакція в такому разі тихо списувала вдвічі менше,
            // ніж оголошено. Лічильник зменшується на сплачене, а не обнуляється: інакше
            // залишок такту губився б.
            val due = upkeepDue(sinceUpkeep, upkeepPeriodTicks)
            sinceUpkeep -= due * upkeepPeriodTicks
            repeat(due) {
                if (!payUpkeep(upkeep)) return SessionEnd.EXHAUSTED
            }
        }
    }
}

/**
 * Скільки разів настала оплата утримання за [sinceUpkeep] накопичених тіків.
 *
 * Окремою функцією саме тому, що тут була вада: `if (sinceUpkeep >= period)` платив рівно
 * один раз, скільки б періодів не вмістилось у такт сесії. Тепер це ділення — і його
 * можна перевірити без гри.
 */
internal fun upkeepDue(sinceUpkeep: Int, upkeepPeriodTicks: Int): Int =
    if (upkeepPeriodTicks <= 0) 0 else sinceUpkeep / upkeepPeriodTicks
