package me.vangoo.mysteries.beyonder

import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * State synchronisation: it brings up no game and must not.
 *
 * The pump knows nothing about players or packets — only about a `UUID` and its own clock. That
 * is exactly why the layer's most expensive rule ("no more than once a second") is checked here
 * rather than by hand on `runClient`.
 */
class AbilityStatePumpTest {

    private val sent = mutableListOf<UUID>()
    private var tick = 0L

    private val pump = AbilityStatePump(
        send = { id -> sent += id },
        now = { tick },
    )

    private val alice: UUID = UUID.randomUUID()
    private val bob: UUID = UUID.randomUUID()

    @Test
    @DisplayName("Чистий гравець пакета не породжує")
    fun cleanPlayerSendsNothing() {
        repeat(10) { second ->
            tick = second * TICKS_PER_SECOND.toLong()
            pump.flush()
        }

        assertEquals(emptyList<UUID>(), sent)
    }

    @Test
    @DisplayName("Брудний гравець отримує рівно один пакет за такт")
    fun dirtyPlayerIsFlushedOnce() {
        // Marking dirty can happen any number of times — it is a flag, not a queue.
        repeat(5) { pump.markDirty(alice) }
        pump.flush()

        assertEquals(listOf(alice), sent)

        // A second tick with no new marking stays silent.
        tick += TICKS_PER_SECOND
        pump.flush()
        assertEquals(listOf(alice), sent)
    }

    @Test
    @DisplayName("Дія гравця штовхає стан негайно, не чекаючи такту")
    fun actionPushesImmediately() {
        pump.pushNow(alice)

        assertEquals(listOf(alice), sent)
    }

    @Test
    @DisplayName("Такт не дублює щойно надісланий стан")
    fun flushDoesNotDuplicateFreshPush() {
        pump.pushNow(alice)
        // Marked dirty within the same second: a cast spent spirituality and regeneration returned it.
        pump.markDirty(alice)

        tick += TICKS_PER_SECOND - 1
        pump.flush()
        assertEquals(listOf(alice), sent, "запобіжник пропустив другий пакет у ту саму секунду")

        // A second has passed — the debt is paid.
        tick += 1
        pump.flush()
        assertEquals(listOf(alice, alice), sent)
    }

    @Test
    @DisplayName("Запобіжник рахується окремо для кожного гравця")
    fun throttleIsPerPlayer() {
        pump.pushNow(alice)
        pump.markDirty(alice)
        pump.markDirty(bob)

        pump.flush()

        assertEquals(listOf(alice, bob), sent, "Боб не має чекати через каст Аліси")
    }

    @Test
    @DisplayName("Забутий гравець не отримує нічого і не тримає свого таймера")
    fun forgottenPlayerIsDropped() {
        pump.markDirty(alice)
        pump.forget(alice)
        pump.flush()

        assertEquals(emptyList<UUID>(), sent)

        // The timer is forgotten too: the first tick after coming back runs with no delay.
        pump.markDirty(alice)
        pump.flush()
        assertEquals(listOf(alice), sent)
    }
}
