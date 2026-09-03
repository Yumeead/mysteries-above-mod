package me.vangoo.mysteries.ability

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.mysteries.ability.runtime.CooldownTable
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Cooldowns: no game is brought up — the clock here is a stand-in.
 *
 * The main thing proven: [CooldownTable.readyAtTick] and `remainingSeconds` do not drift apart.
 * They are two views of one number — seconds for the server, an absolute tick for the client —
 * and a discrepancy between them is exactly what would give a fill that does not match the digit.
 */
class CooldownTableTest {

    private var tick = 1_000
    private val table = CooldownTable { tick }

    private val alice: UUID = UUID.randomUUID()
    private val water = AbilityIdentity.of("tyrant_restorative_water")
    private val scales = AbilityIdentity.of("tyrant_illusory_scales")

    @Test
    @DisplayName("Готова здібність віддає нуль в обох одиницях")
    fun readyAbilityIsZero() {
        assertEquals(0, table.readyAtTick(alice, water))
        assertEquals(0L, table.remainingSeconds(alice, water))
    }

    @Test
    @DisplayName("Тік готовності абсолютний, а не залишковий")
    fun readyTickIsAbsolute() {
        table.set(alice, water, 20)

        assertEquals(1_000 + 20 * TICKS_PER_SECOND, table.readyAtTick(alice, water))

        // Time passes — the number does not change. That is the point: the client counts down itself.
        tick += 5 * TICKS_PER_SECOND
        assertEquals(1_000 + 20 * TICKS_PER_SECOND, table.readyAtTick(alice, water))
        assertEquals(15L, table.remainingSeconds(alice, water))
    }

    @Test
    @DisplayName("Тік готовності й залишок у секундах не розходяться")
    fun tickAndSecondsAgree() {
        table.set(alice, water, 7)

        repeat(7 * TICKS_PER_SECOND + 5) {
            val readyAtTick = table.readyAtTick(alice, water)
            val seconds = table.remainingSeconds(alice, water)

            if (readyAtTick == 0) {
                assertEquals(0L, seconds, "готова за тіком, але не за секундами (тік $tick)")
            } else {
                // The same rounding up as in the table: the client draws this very digit.
                val expected = (readyAtTick - tick + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND
                assertEquals(expected.toLong(), seconds, "розбіжність на тіку $tick")
            }
            tick++
        }
    }

    @Test
    @DisplayName("Спливлий таймер віддає нуль, а не тік із минулого")
    fun expiredTimerIsZero() {
        table.set(alice, water, 3)
        tick += 3 * TICKS_PER_SECOND

        assertEquals(0, table.readyAtTick(alice, water))
        assertEquals(0L, table.remainingSeconds(alice, water))
    }

    @Test
    @DisplayName("Глобальне замикання віддає один тік усім здібностям одразу")
    fun lockAllShowsSameTickEverywhere() {
        table.lockAll(alice, 10)

        val expected = 1_000 + 10 * TICKS_PER_SECOND
        assertEquals(expected, table.readyAtTick(alice, water))
        assertEquals(expected, table.readyAtTick(alice, scales))
    }

    @Test
    @DisplayName("З двох таймерів перемагає пізніший")
    fun laterTimerWins() {
        table.set(alice, water, 3)
        table.lockAll(alice, 10)

        assertEquals(1_000 + 10 * TICKS_PER_SECOND, table.readyAtTick(alice, water))

        table.set(alice, water, 30)
        assertEquals(1_000 + 30 * TICKS_PER_SECOND, table.readyAtTick(alice, water))
    }

    @Test
    @DisplayName("Забутий гравець лишається без таймерів, включно з глобальним")
    fun forgetClearsEverything() {
        table.set(alice, water, 30)
        table.lockAll(alice, 30)
        table.forget(alice)

        assertEquals(0, table.readyAtTick(alice, water))
    }
}
