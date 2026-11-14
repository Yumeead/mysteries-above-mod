package me.vangoo.mysteries.beyonder

import com.mojang.serialization.JsonOps
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
    @DisplayName("Резерв Крадія снів довший за вікно жертви, і запис доживає до кінця резерву")
    fun theReserveOutwaitsTheVictimsWindow() {
        // The Dream Stealer's own terms: 6 h unused, 30 min once called, the victim 1 h without it.
        val reserve = 6L * 60 * 60 * 1000
        val window = 60L * 60 * 1000
        TheftRegistry.steal(thief, victim, power, hold = reserve, suppression = window, now = start, activeHold = 30L * 60 * 1000)
        val revoked = mutableListOf<TheftRegistry.Theft>()

        TheftRegistry.sweep(start + window + 1) { revoked += it }
        assertFalse(TheftRegistry.isSuppressed(victim, power, start + window + 1), "жертва вже при силі")
        assertTrue(revoked.isEmpty() && TheftRegistry.heldBy(thief) != null, "невикористане ще чекає в злодія")

        TheftRegistry.sweep(start + reserve) { revoked += it }
        assertEquals(1, revoked.size, "резерв скінчився — силу забрано")
        assertNull(TheftRegistry.heldBy(thief))
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

    @Test
    @DisplayName("Збережений реєстр після рестарту тримає ті самі строки, що й до нього")
    fun savedRegistrySurvivesARestart() {
        TheftRegistry.steal(thief, victim, power, hold, suppression, now = start, activeHold = 60_000)
        TheftRegistry.markUsed(thief, power, now = start + 5_000)
        TheftRegistry.sweep(now = start + 70_000) { }

        val json = TheftRegistry.CODEC.encodeStart(JsonOps.INSTANCE, TheftRegistry.snapshot()).getOrThrow()
        TheftRegistry.reset()
        TheftRegistry.restore(TheftRegistry.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow())

        val back = TheftRegistry.heldBy(thief)!!
        assertEquals(power, back.identity)
        assertEquals(start + 65_000, back.expiresAt)
        assertEquals(start + 5_000, back.firstUsedAt)
        assertTrue(back.revoked)
        assertTrue(TheftRegistry.isSuppressed(victim, power, start + hold))
    }
}
