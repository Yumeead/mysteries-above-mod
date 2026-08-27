package me.vangoo.mysteries.beyonder

import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Синхронізація стану: гри не піднімає й піднімати не мусить.
 *
 * Насос не знає ні про гравців, ні про пакети — лише про `UUID` і власний годинник.
 * Саме тому найдорожче правило шару («не частіше разу на секунду») перевіряється тут,
 * а не руками на `runClient`.
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
        // Бруднити можна скільки завгодно — це прапорець, а не черга.
        repeat(5) { pump.markDirty(alice) }
        pump.flush()

        assertEquals(listOf(alice), sent)

        // Другий такт без нового бруднення мовчить.
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
        // Бруднення в ту саму секунду: каст списав духовність, і регенерація її повернула.
        pump.markDirty(alice)

        tick += TICKS_PER_SECOND - 1
        pump.flush()
        assertEquals(listOf(alice), sent, "запобіжник пропустив другий пакет у ту саму секунду")

        // Секунда минула — борг віддається.
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

        // Таймер теж забутий: перший же такт після повернення відпрацьовує без затримки.
        pump.markDirty(alice)
        pump.flush()
        assertEquals(listOf(alice), sent)
    }
}
