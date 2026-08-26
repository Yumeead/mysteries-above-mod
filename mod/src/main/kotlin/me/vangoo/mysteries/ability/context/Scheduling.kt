package me.vangoo.mysteries.ability.context

import me.vangoo.domain.abilities.AbilitySpec
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.mysteries.ability.Session
import me.vangoo.mysteries.ability.SessionScope
import me.vangoo.mysteries.schedule.TaskHandle
import java.util.UUID

/**
 * Час: відкладена робота й живі сесії.
 *
 * Прямий спадкоємець плагінного `ISchedulingContext`, але без третього його методу.
 * `runAsync` сюди не переїхав навмисно: у Bukkit ним тікали з головного потоку, бо
 * інакше сервер підвисав, і половина викликів робила це «про всяк випадок». Тут будь-яка
 * робота і так на серверному потоці, а справжня фонова — це `withContext(Dispatchers.IO)`
 * усередині сесії, написане явно й видно очима.
 *
 * Годинник — тік сервера. Гра на паузі часу не витрачає.
 */
interface Scheduling {

    /** Скільки тіків сервер відпрацював, відколи піднявся мод. */
    val tick: Long

    /** Один раз, через [delayTicks]. Нуль і менше — цього ж тіку. */
    fun after(delayTicks: Int, block: () -> Unit): TaskHandle

    /** Раз на [periodTicks], уперше — через [delayTicks]. */
    fun every(periodTicks: Int, delayTicks: Int = periodTicks, block: () -> Unit): TaskHandle

    /**
     * Відкриває сесію [ability] для [owner], **гасячи попередню з тим самим ключем**.
     * Перекаст замінює сесію — це поведінка реєстру, а не обов'язок здібності.
     */
    fun session(
        owner: UUID,
        ability: AbilitySpec,
        block: suspend SessionScope.() -> Unit,
    ): Session

    fun sessionOf(owner: UUID, ability: AbilityIdentity): Session?

    fun isRunning(owner: UUID, ability: AbilityIdentity): Boolean =
        sessionOf(owner, ability)?.isActive == true

    /** Гасить одну сесію. `true` — вона була жива. */
    fun stop(owner: UUID, ability: AbilityIdentity): Boolean

    /** Гасить усі сесії гравця: вихід із гри, втрата шляху, смерть. */
    fun stopAll(owner: UUID)

    /** Скільки сесій живе зараз — для діагностики й тестів. */
    val live: Collection<Session>
}
