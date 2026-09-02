package me.vangoo.mysteries.ability

import me.vangoo.domain.abilities.AbilitySpec
import me.vangoo.domain.abilities.AbilityType
import me.vangoo.domain.entities.Beyonder
import me.vangoo.domain.entities.Pathway
import me.vangoo.domain.entities.PathwayGroup
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.domain.valueobjects.Spirituality
import me.vangoo.mysteries.ability.context.Beyonders
import me.vangoo.mysteries.ability.runtime.SessionHost
import me.vangoo.mysteries.schedule.TickScheduler
import net.minecraft.world.entity.Entity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Семантика реєстру сесій — те, що в плагіні тримали 22 однакові `cancel()` і пункт
 * правил «перекаст замінює сесію».
 *
 * Гри тут немає: сесія, яка не чіпає світ, а лише чекає, платить і прибирає за собою,
 * перевіряється прокруткою [TickScheduler]. Те, що потребує живого `ServerPlayer`
 * (гілки [sustain] понад «власника немає»), лишається на ігрову перевірку фази 05.
 */
class SessionHostTest {

    private data class Spec(
        override val identity: AbilityIdentity,
        private val upkeep: Int = 0,
    ) : AbilitySpec {
        override val type = AbilityType.ACTIVE
        override val spiritualityCost = 0
        override fun periodicCost(sequence: Sequence): Int = upkeep
        override fun cooldownSeconds(sequence: Sequence): Int = 0
    }

    private class Roster : Beyonders {
        private val byId = HashMap<UUID, Beyonder>()
        override fun of(id: UUID): Beyonder? = byId[id]
        override fun of(entity: Entity): Beyonder? = null
        override fun isToggled(id: UUID, ability: AbilityIdentity): Boolean = false
        override val online: Collection<Beyonder> get() = byId.values
        fun add(beyonder: Beyonder) {
            byId[beyonder.playerId] = beyonder
        }
    }

    private val errors = mutableListOf<String>()
    private val scheduler = TickScheduler()
    private val roster = Roster()
    private val host = SessionHost(scheduler, roster, players = { null }) { what, _ -> errors += what }

    private val owner: UUID = UUID.randomUUID()
    private val wind = Spec(AbilityIdentity.of("wind"), upkeep = 10)
    private val halo = Spec(AbilityIdentity.of("halo"))

    private fun advance(ticks: Int) = repeat(ticks) { scheduler.advance() }

    private fun beyonderWith(spirituality: Int): Beyonder {
        val pathway = Pathway(
            name = "Tyrant",
            group = PathwayGroup.GodAlmighty,
            sequenceNames = (0..9).map { "S$it" },
            roster = emptyMap(),
        )
        return Beyonder(owner, Sequence.starter(), pathway)
            .apply { setSpirituality(Spirituality.of(spirituality, maxSpirituality)) }
            .also(roster::add)
    }

    // ── Час ─────────────────────────────────────────────────────────────────────

    @Test
    fun `waitTicks resumes exactly on the asked tick`() {
        val woke = mutableListOf<Long>()
        host.session(owner, wind) {
            woke += host.tick
            waitTicks(3)
            woke += host.tick
            waitTicks(1)
            woke += host.tick
        }

        advance(6)

        // Старт — на першому ж тіку, далі рівно +3 і +1. Зайвий тік на пробудження
        // означав би, що продовження корутини поїхало в наступний тік замість поточного.
        assertEquals(listOf(1L, 4L, 5L), woke)
    }

    // ── Реєстр ──────────────────────────────────────────────────────────────────

    @Test
    fun `a finished session leaves the registry on its own`() {
        host.session(owner, wind) { waitTicks(2) }

        advance(1)
        assertTrue(host.isRunning(owner, wind.identity))

        advance(3)
        assertFalse(host.isRunning(owner, wind.identity))
        assertTrue(host.live.isEmpty())
    }

    @Test
    fun `re-casting replaces the session and unwinds the old one`() {
        val log = mutableListOf<String>()

        host.session(owner, wind) {
            try {
                log += "перша пішла"
                waitTicks(100)
            } finally {
                log += "перша прибрала за собою"
            }
        }
        advance(2)

        host.session(owner, wind) {
            log += "друга пішла"
            waitTicks(100)
        }
        advance(2)

        assertEquals(listOf("перша пішла", "перша прибрала за собою", "друга пішла"), log)
        assertEquals(1, host.live.size, "двох сесій однієї здібності бути не може")
    }

    @Test
    fun `different abilities of one owner live side by side`() {
        host.session(owner, wind) { waitTicks(100) }
        host.session(owner, halo) { waitTicks(100) }

        advance(2)

        assertEquals(2, host.live.size)
        assertTrue(host.isRunning(owner, wind.identity))
        assertTrue(host.isRunning(owner, halo.identity))
    }

    @Test
    fun `stopAll ends every session of one owner`() {
        val stranger = UUID.randomUUID()
        host.session(owner, wind) { waitTicks(100) }
        host.session(owner, halo) { waitTicks(100) }
        host.session(stranger, wind) { waitTicks(100) }
        advance(2)

        host.stopAll(owner)

        assertEquals(1, host.live.size)
        assertTrue(host.isRunning(stranger, wind.identity))
    }

    @Test
    fun `stopping a session runs its finally — this is what cleanup means now`() {
        var removedEffect = false
        host.session(owner, wind) {
            try {
                waitTicks(100)
            } finally {
                removedEffect = true
            }
        }
        advance(2)

        host.stop(owner, wind.identity)
        // Скасування знімає корутину з очікування, а розкрутка стека — це вже робота,
        // і робота диспетчиться в чергу тіку. Тому прибирання відпрацьовує наступним
        // тіком, а не в момент виклику: саме через це зупинка сервера робить іще один
        // прощальний тік (див. AbilityServices.reset).
        advance(1)

        assertTrue(removedEffect, "прибирання в finally мусить відпрацювати на скасуванні")
        assertFalse(host.isRunning(owner, wind.identity))
    }

    // ── Утримання ───────────────────────────────────────────────────────────────

    @Test
    fun `a session drains spirituality until it runs out`() {
        val beyonder = beyonderWith(25)
        var paidBeats = 0

        host.session(owner, wind) {
            while (payUpkeep()) {
                paidBeats++
                waitTicks(1)
            }
        }

        advance(10)

        // 25 духовності, ціна такту 10: двічі заплатив, на третьому не стало.
        assertEquals(2, paidBeats)
        assertEquals(5, beyonder.spirituality.current)
        assertFalse(host.isRunning(owner, wind.identity))
    }

    @Test
    fun `утримання платиться за кожен період, а не раз на такт сесії`() {
        // Такт `SunHalo` — 40 тіків, період оплати — 20: попередня редакція платила один
        // раз за такт, тобто вдвічі менше за оголошене, і сила виходила майже безкоштовною.
        assertEquals(2, upkeepDue(sinceUpkeep = 40, upkeepPeriodTicks = 20))
        assertEquals(1, upkeepDue(sinceUpkeep = 20, upkeepPeriodTicks = 20))
        assertEquals(0, upkeepDue(sinceUpkeep = 19, upkeepPeriodTicks = 20))
        // Залишок не губиться: 25 тіків — це один період і п'ять тіків наперед.
        assertEquals(1, upkeepDue(sinceUpkeep = 25, upkeepPeriodTicks = 20))
    }

    @Test
    fun `sustain gives up when the owner is not in the world`() {
        var end: SessionEnd? = null
        host.session(owner, wind) {
            end = sustain(periodTicks = 1) { /* сюди не дійде: гравця немає */ }
        }

        advance(2)

        assertEquals(SessionEnd.OWNER_GONE, end)
    }

    // ── Стійкість ───────────────────────────────────────────────────────────────

    @Test
    fun `a session that throws does not take its neighbour with it`() {
        var neighbourBeats = 0
        host.session(owner, wind) { error("сесія впала") }
        host.session(owner, halo) {
            repeat(3) {
                neighbourBeats++
                waitTicks(1)
            }
        }

        advance(5)

        assertEquals(3, neighbourBeats)
        assertEquals(1, errors.size)
    }

    @Test
    fun `after shutdown the host still opens sessions — the next world needs them`() {
        host.session(owner, wind) { waitTicks(100) }
        advance(2)

        host.shutdown()
        assertTrue(host.live.isEmpty())

        var restarted = false
        host.session(owner, wind) { restarted = true }
        advance(2)

        assertTrue(restarted, "скасований скоуп не запустив би жодної сесії наступного світу")
    }
}
