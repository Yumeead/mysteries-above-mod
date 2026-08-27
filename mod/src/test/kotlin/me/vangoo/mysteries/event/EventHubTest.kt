package me.vangoo.mysteries.event

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.mysteries.ability.context.EVENT_FOREVER
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.ability.runtime.EventHub
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Реєстр підписок. Гри тут немає й не треба: [GameEvent] — власний тип мода, тож
 * подію можна оголосити прямо в тесті, а годинник — прокрутити рукою.
 */
class EventHubTest {

    private class Ping(val payload: String = "") : GameEvent
    private class Pong : GameEvent

    private var tick = 0L
    private val errors = mutableListOf<String>()
    private val hub = EventHub({ tick }, { what, _ -> errors += what })

    private val sailor: UUID = UUID.randomUUID()
    private val bard: UUID = UUID.randomUUID()
    private val scales = AbilityIdentity.of("tyrant_illusory_scales")
    private val water = AbilityIdentity.of("tyrant_restorative_water")

    private fun scope(owner: UUID, ability: AbilityIdentity) = hub.scopeOf(owner, ability)

    @Test
    fun `a handler hears its own event type and no other`() {
        val heard = mutableListOf<String>()
        scope(sailor, scales).on<Ping> { heard += it.payload }

        hub.publish(Ping("перший"))
        hub.publish(Pong())
        hub.publish(Ping("другий"))

        assertEquals(listOf("перший", "другий"), heard)
    }

    @Test
    fun `forgetting one ability leaves the other abilities of the same player alone`() {
        val heard = mutableListOf<String>()
        scope(sailor, scales).on<Ping> { heard += "луска" }
        scope(sailor, water).on<Ping> { heard += "вода" }

        hub.forget(sailor, scales)
        hub.publish(Ping())

        assertEquals(listOf("вода"), heard, "знято чужу підписку — саме та вада плагіна")
    }

    @Test
    fun `forgetting a player takes all of their subscriptions and nobody else's`() {
        val heard = mutableListOf<String>()
        scope(sailor, scales).on<Ping> { heard += "матрос" }
        scope(sailor, water).on<Ping> { heard += "матрос" }
        scope(bard, scales).on<Ping> { heard += "бард" }

        hub.forget(sailor)
        hub.publish(Ping())

        assertEquals(listOf("бард"), heard)
        assertEquals(1, hub.live)
    }

    @Test
    fun `a timed subscription stops on the tick it expires`() {
        val heard = mutableListOf<Long>()
        scope(sailor, scales).on<Ping>(durationTicks = 40) { heard += tick }

        tick = 39
        hub.publish(Ping())
        tick = 40
        hub.publish(Ping())

        assertEquals(listOf(39L), heard)
        assertEquals(0, hub.live, "прострочена підписка має прибратись сама")
    }

    @Test
    fun `a permanent subscription outlives any plausible tick count`() {
        var heard = 0
        scope(sailor, scales).on<Ping>(durationTicks = EVENT_FOREVER) { heard++ }

        tick = Long.MAX_VALUE - 1
        hub.publish(Ping())

        assertEquals(1, heard)
    }

    @Test
    fun `a handler can unsubscribe itself and then hears nothing more`() {
        var heard = 0
        lateinit var handle: me.vangoo.mysteries.ability.context.Subscription
        handle = scope(sailor, scales).on<Ping> {
            heard++
            handle.cancel()
        }

        hub.publish(Ping())
        hub.publish(Ping())

        assertEquals(1, heard)
        assertFalse(handle.isActive)
    }

    @Test
    fun `a handler subscribing mid dispatch does not hear the event that spawned it`() {
        val heard = mutableListOf<String>()
        scope(sailor, scales).on<Ping> {
            heard += "перший"
            scope(sailor, water).on<Ping> { heard += "народжений" }
        }

        hub.publish(Ping())
        assertEquals(listOf("перший"), heard)

        hub.publish(Ping())
        assertEquals(listOf("перший", "перший", "народжений"), heard)
    }

    @Test
    fun `one falling handler neither stops the rest nor stays subscribed`() {
        val heard = mutableListOf<String>()
        scope(sailor, scales).on<Ping> { error("луска впала") }
        scope(bard, water).on<Ping> { heard += "бард" }

        hub.publish(Ping())
        hub.publish(Ping())

        assertEquals(listOf("бард", "бард"), heard)
        assertEquals(1, errors.size, "падіння має бути зафіксоване рівно раз")
        assertEquals(1, hub.live)
    }

    @Test
    fun `clearing drops everything so the next world starts empty`() {
        scope(sailor, scales).on<Ping> { }
        scope(bard, water).on<Pong> { }

        hub.clear()

        assertEquals(0, hub.live)
    }

    @Test
    fun `publishing to nobody is free and harmless`() {
        hub.publish(Ping())
        assertTrue(errors.isEmpty())
    }
}
