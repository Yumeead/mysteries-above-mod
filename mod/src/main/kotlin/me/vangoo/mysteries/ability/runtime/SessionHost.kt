package me.vangoo.mysteries.ability.runtime

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.vangoo.domain.abilities.AbilitySpec
import me.vangoo.domain.entities.Beyonder
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.mysteries.ability.Session
import me.vangoo.mysteries.ability.SessionScope
import me.vangoo.mysteries.ability.context.Beyonders
import me.vangoo.mysteries.ability.context.Scheduling
import me.vangoo.mysteries.schedule.ServerTickDispatcher
import me.vangoo.mysteries.schedule.TaskHandle
import me.vangoo.mysteries.schedule.TickScheduler
import me.vangoo.mysteries.schedule.MILLIS_PER_TICK
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * Реєстр живих сесій поверх [TickScheduler].
 *
 * Одна сесія на пару «гравець + здібність». Новий каст тієї самої здібності гасить
 * попередню сесію **до** того, як запустить нову: у плагіні це був пункт правил
 * («перекаст замінює сесію: спершу зняти й скасувати стару»), і кожна з 22 сесій
 * виконувала його вручну — або не виконувала.
 *
 * Скоуп — [SupervisorJob]: сесія, що впала з винятком, не тягне за собою решту. Виняток
 * логується як помилка одного гравця, а не як падіння всього шару.
 */
class SessionHost(
    private val scheduler: TickScheduler,
    private val beyonders: Beyonders,
    private val players: (UUID) -> ServerPlayer?,
    private val onError: (String, Throwable) -> Unit = { _, _ -> },
) : Scheduling {

    private val dispatcher = ServerTickDispatcher(scheduler)
    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(dispatcher + supervisor + CoroutineName("mysteries-sessions"))

    private data class Key(val owner: UUID, val ability: AbilityIdentity)

    private val sessions = LinkedHashMap<Key, Session>()

    override val tick: Long get() = scheduler.tick

    override fun after(delayTicks: Int, block: () -> Unit): TaskHandle =
        scheduler.after(delayTicks, block)

    override fun every(periodTicks: Int, delayTicks: Int, block: () -> Unit): TaskHandle =
        scheduler.every(periodTicks, delayTicks, block)

    override fun session(
        owner: UUID,
        ability: AbilitySpec,
        block: suspend SessionScope.() -> Unit,
    ): Session {
        val key = Key(owner, ability.identity)
        sessions.remove(key)?.stop()

        val startedAt = scheduler.tick
        // LAZY, бо тіло може добігти до кінця синхронно, і тоді знімати з реєстру
        // довелось би те, чого туди ще не поклали.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            LiveSessionScope(this, owner, ability, startedAt).block()
        }
        val session = Session(owner, ability.identity, job)
        sessions[key] = session
        job.invokeOnCompletion { error ->
            if (sessions[key] === session) sessions.remove(key)
            if (error != null && error !is CancellationException) {
                onError("Сесія ${ability.identity} гравця $owner впала", error)
            }
        }
        job.start()
        return session
    }

    override fun sessionOf(owner: UUID, ability: AbilityIdentity): Session? =
        sessions[Key(owner, ability)]

    override fun stop(owner: UUID, ability: AbilityIdentity): Boolean {
        val session = sessions.remove(Key(owner, ability)) ?: return false
        val wasActive = session.isActive
        session.stop()
        return wasActive
    }

    override fun stopAll(owner: UUID) {
        sessions.keys.filter { it.owner == owner }
            .mapNotNull { sessions.remove(it) }
            .forEach { it.stop() }
    }

    override val live: Collection<Session> get() = sessions.values.toList()

    /**
     * Зупинка сервера: жодна сесія не має пережити світ, у якому почалась.
     *
     * Гасяться діти, а не сам скоуп: одиночна гра піднімає й гасить інтегрований сервер
     * усередині того самого процесу, а скасований [SupervisorJob] більше ніколи не
     * запустить корутину — наступний світ лишився б без сесій зовсім.
     */
    fun shutdown() {
        sessions.values.toList().forEach { it.stop() }
        sessions.clear()
        supervisor.cancelChildren()
    }

    private inner class LiveSessionScope(
        delegate: CoroutineScope,
        override val ownerId: UUID,
        override val ability: AbilitySpec,
        private val startedAt: Long,
    ) : SessionScope, CoroutineScope by delegate {

        override val owner: ServerPlayer? get() = players(ownerId)

        override val beyonder: Beyonder? get() = beyonders.of(ownerId)

        override val ticksAlive: Long get() = scheduler.tick - startedAt

        override val periodicCost: Int
            get() = beyonder?.let { ability.periodicCost(it.sequence) } ?: 0

        override suspend fun waitTicks(ticks: Int) {
            if (ticks > 0) delay(ticks * MILLIS_PER_TICK)
        }

        override fun payUpkeep(amount: Int): Boolean =
            beyonder?.payUpkeep(amount) ?: false
    }
}
