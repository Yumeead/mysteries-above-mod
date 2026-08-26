package me.vangoo.mysteries.schedule

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Черга тіків. Minecraft тут не потрібен — і в цьому вся суть: годинник мода можна
 * прокрутити на тисячу тіків за мілісекунду й побачити, що саме він зробив.
 */
class TickSchedulerTest {

    private val errors = mutableListOf<String>()
    private val scheduler = TickScheduler { what, _ -> errors += what }

    private fun advance(ticks: Int) = repeat(ticks) { scheduler.advance() }

    @Test
    fun `a delayed task fires on its tick, not before`() {
        val fired = mutableListOf<Long>()
        scheduler.after(3) { fired += scheduler.tick }

        advance(2)
        assertTrue(fired.isEmpty(), "спрацювало раніше строку")

        advance(1)
        assertEquals(listOf(3L), fired)
    }

    @Test
    fun `a zero delay runs on the very next tick`() {
        val fired = mutableListOf<Long>()
        scheduler.after(0) { fired += scheduler.tick }

        advance(1)

        assertEquals(listOf(1L), fired)
    }

    @Test
    fun `a repeating task keeps its period`() {
        val fired = mutableListOf<Long>()
        scheduler.every(periodTicks = 5) { fired += scheduler.tick }

        advance(16)

        assertEquals(listOf(5L, 10L, 15L), fired)
    }

    @Test
    fun `a cancelled task never fires again`() {
        val fired = mutableListOf<Long>()
        val handle = scheduler.every(periodTicks = 2) { fired += scheduler.tick }

        advance(5)
        handle.cancel()
        advance(10)

        assertEquals(listOf(2L, 4L), fired)
        assertFalse(handle.isActive)
    }

    @Test
    fun `work queued from inside a tick runs in that same tick`() {
        // Це і є умова, за якої waitTicks(1) чекає рівно один тік: продовження корутини
        // диспетчиться під час розбору черги й мусить потрапити в цей самий тік.
        val order = mutableListOf<String>()
        scheduler.after(1) {
            order += "перше на тіку ${scheduler.tick}"
            scheduler.submit { order += "друге на тіку ${scheduler.tick}" }
        }

        advance(1)

        assertEquals(listOf("перше на тіку 1", "друге на тіку 1"), order)
    }

    @Test
    fun `one broken task does not stop the rest of the tick`() {
        val fired = mutableListOf<String>()
        scheduler.after(1) { error("пасивка впала") }
        scheduler.after(1) { fired += "друга" }

        advance(1)

        assertEquals(listOf("друга"), fired)
        assertEquals(1, errors.size)
    }

    @Test
    fun `cancelAll empties the queue — a world does not outlive itself`() {
        val fired = mutableListOf<String>()
        scheduler.after(2) { fired += "відкладена" }
        scheduler.every(periodTicks = 1) { fired += "періодична" }

        scheduler.cancelAll()
        advance(10)

        assertTrue(fired.isEmpty())
        assertEquals(0, scheduler.pending)
    }

    @Test
    fun `millis convert to ticks upwards so no delay is instant`() {
        assertEquals(0, millisToTicks(0))
        assertEquals(1, millisToTicks(1))
        assertEquals(1, millisToTicks(50))
        assertEquals(2, millisToTicks(51))
        assertEquals(20, millisToTicks(1_000))
    }
}
