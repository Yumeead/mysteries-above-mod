package me.vangoo.mysteries.schedule

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Delay
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.InternalCoroutinesApi
import kotlin.coroutines.CoroutineContext

/**
 * Диспетчер, для якого «потік» — це серверний тік.
 *
 * Корутина, запущена тут, прокидається всередині [TickScheduler.advance], тобто на
 * головному потоці сервера. Тому здібності в сесії дозволено чіпати світ напряму:
 * ніякого `runTask(plugin, …)` навколо кожного дотику, як було в плагіні.
 *
 * ## Чому реалізовано ще й [Delay]
 *
 * Без нього `delay()` пішов би через власний фоновий таймер корутин — стінний час.
 * Одиночна гра на паузі стоїть, а такий `delay` цокав би далі, і сесія прокинулась би
 * з боргом у тіках. З [Delay] **усяке** очікування міряється тіками: `delay(1.seconds)`
 * — це рівно 20 тіків сервера, скільки б реального часу вони не зайняли.
 *
 * Ціна — опт-ін на [InternalCoroutinesApi]: інтерфейс [Delay] позначено внутрішнім.
 * Він стабільний роками й ним живуть усі мод-диспетчери; альтернатива — лишити
 * пастку, у яку вступить перший же `delay()` замість [waitTicks].
 */
@OptIn(InternalCoroutinesApi::class)
class ServerTickDispatcher(private val scheduler: TickScheduler) : CoroutineDispatcher(), Delay {

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        scheduler.submit(block::run)
    }

    override fun scheduleResumeAfterDelay(
        timeMillis: Long,
        continuation: CancellableContinuation<Unit>,
    ) {
        val handle = scheduler.after(millisToTicks(timeMillis)) {
            // resumeWith, а не resume(value, onCancellation): продовження диспетчиться
            // назад сюди ж, тобто прокидається в тому самому тіку, у якому настав строк.
            continuation.resumeWith(Result.success(Unit))
        }
        continuation.invokeOnCancellation { handle.cancel() }
    }

    override fun invokeOnTimeout(
        timeMillis: Long,
        block: Runnable,
        context: CoroutineContext,
    ): DisposableHandle {
        val handle = scheduler.after(millisToTicks(timeMillis)) { block.run() }
        return DisposableHandle { handle.cancel() }
    }

    override fun toString(): String = "ServerTick"
}
