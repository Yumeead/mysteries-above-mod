package me.vangoo.mysteries.beyonder

import me.vangoo.domain.valueobjects.AbilityIdentity
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * The two windows of a theft are of different lengths, and every bug this class can have lives in
 * the gap between them. Time is passed in explicitly so the test does not wait for it.
 */
@DisplayName("Реєстр крадіжок")
class TheftRegistryTest {

    private val thief = UUID.randomUUID()
    private val victim = UUID.randomUUID()
    private val power = AbilityIdentity.of("victim_power")

    private val hold = 10L * 60 * 1000
    private val suppression = 30L * 60 * 1000
    private val start = 1_000_000L

    @AfterEach
    fun clear() = TheftRegistry.reset()

    @Test
    @DisplayName("Жертва не кличе вкрадене, доки не мине її вікно")
    fun victimIsSuppressedForTheWholeWindow() {
        TheftRegistry.steal(thief, victim, power, hold, suppression, now = start)

        assertTrue(TheftRegistry.isSuppressed(victim, power, start))
        assertTrue(TheftRegistry.isSuppressed(victim, power, start + hold + 1))
        assertFalse(TheftRegistry.isSuppressed(victim, power, start + suppression))
    }

    @Test
    @DisplayName("Пригнічена лише вкрадена сила, а не всі")
    fun onlyTheStolenIdentityIsSuppressed() {
        TheftRegistry.steal(thief, victim, power, hold, suppression, now = start)

        assertFalse(TheftRegistry.isSuppressed(victim, AbilityIdentity.of("another_power"), start))
        assertFalse(TheftRegistry.isSuppressed(UUID.randomUUID(), power, start))
    }

    @Test
    @DisplayName("Підмітання забирає силу рівно один раз, а запис живе до кінця вікна жертви")
    fun sweepRevokesOnceAndKeepsTheRecord() {
        TheftRegistry.steal(thief, victim, power, hold, suppression, now = start)
        val revoked = mutableListOf<TheftRegistry.Theft>()

        TheftRegistry.sweep(start + hold - 1) { revoked += it }
        assertTrue(revoked.isEmpty(), "до строку сила лишається у злодія")

        TheftRegistry.sweep(start + hold) { revoked += it }
        TheftRegistry.sweep(start + hold + 1) { revoked += it }
        assertEquals(1, revoked.size, "забрати можна лише раз")

        assertTrue(TheftRegistry.isSuppressed(victim, power, start + hold + 1), "жертва ще чекає")

        TheftRegistry.sweep(start + suppression) { revoked += it }
        assertNull(TheftRegistry.heldBy(thief), "після вікна жертви запис зникає")
        assertFalse(TheftRegistry.isSuppressed(victim, power, start + suppression))
    }

    @Test
    @DisplayName("Невикористане чекає довго, а перший каст перераховує строк")
    fun theReserveTermIsRecknonedOnFirstUse() {
        val reserve = 6L * 60 * 60 * 1000
        val active = 30L * 60 * 1000
        val theft = TheftRegistry.steal(
            thief, victim, power, hold = reserve, suppression = reserve, now = start, activeHold = active,
        )
        assertEquals(start + reserve, theft.expiresAt, "поки не кликали — чекає резервний строк")

        val firstCall = start + 60_000
        TheftRegistry.markUsed(thief, power, firstCall)
        assertEquals(firstCall + active, theft.expiresAt, "перший каст лишає рівно активний строк")

        TheftRegistry.markUsed(thief, power, firstCall + 120_000)
        assertEquals(firstCall + active, theft.expiresAt, "другий каст нічого не рухає")
    }

    @Test
    @DisplayName("Прометеїв тір резерву не має, тож перший каст нічого не міняє")
    fun withoutAReserveTermTheFirstUseIsANoOp() {
        val theft = TheftRegistry.steal(thief, victim, power, hold, suppression, now = start)
        TheftRegistry.markUsed(thief, power, start + 1000)

        assertEquals(start + hold, theft.expiresAt)
    }

    @Test
    @DisplayName("Слот один: нова крадіжка витісняє попередню")
    fun theSlotIsSingle() {
        TheftRegistry.steal(thief, victim, power, hold, suppression, now = start)
        val second = TheftRegistry.steal(thief, UUID.randomUUID(), AbilityIdentity.of("other"), hold, suppression, now = start)

        assertSame(second, TheftRegistry.heldBy(thief))
    }

    @Test
    @DisplayName("Витрачене вкрадене звільняє і слот, і жертву")
    fun releaseFreesBothSides() {
        TheftRegistry.steal(thief, victim, power, hold, suppression, now = start)
        TheftRegistry.release(thief)

        assertNull(TheftRegistry.heldBy(thief))
        assertFalse(TheftRegistry.isSuppressed(victim, power, start))
    }
}
