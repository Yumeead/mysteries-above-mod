package me.vangoo.mysteries.ability

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.runtime.AbilityModes
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

class AbilityModesTest {

    private class Door(private val ids: List<String>) : ActiveAbility(AbilityIdentity.of("test_door")) {
        override val displayName: Component = Component.literal("Двері")
        override fun description(sequence: Sequence): Component = Component.literal("")
        override fun modes(sequence: Sequence): List<AbilityMode> = ids.map { AbilityMode(it, Component.literal(it)) }
        override fun cast(context: CastContext): AbilityOutcome = AbilityOutcome.done("")
    }

    private val owner = UUID.randomUUID()
    private val seq = Sequence(5)
    private val modes = AbilityModes()

    @Test
    @DisplayName("Без вибору — перший режим; без режимів — нічого")
    fun defaultsToTheFirst() {
        assertEquals("drag", modes.of(owner, Door(listOf("drag", "purge")), seq)?.id)
        assertNull(modes.of(owner, Door(emptyList()), seq))
    }

    @Test
    @DisplayName("Чужий режим не приймається, свій запам'ятовується")
    fun selectChecksTheList() {
        val door = Door(listOf("drag", "purge"))
        assertFalse(modes.select(owner, door, seq, "fly"))
        assertEquals("drag", modes.of(owner, door, seq)?.id)
        modes.select(owner, door, seq, "purge")
        assertEquals("purge", modes.of(owner, door, seq)?.id)
    }

    @Test
    @DisplayName("Присід гортає по колу, а режим, якого вже немає, падає на перший")
    fun cycleWrapsAndStaleFallsBack() {
        val door = Door(listOf("drag", "purge"))
        assertEquals("purge", modes.cycle(owner, door, seq)?.id)
        assertEquals("drag", modes.cycle(owner, door, seq)?.id)
        modes.select(owner, door, seq, "purge")
        assertEquals("bind", modes.of(owner, Door(listOf("bind")), seq)?.id)
        modes.forget(owner)
        assertEquals("drag", modes.of(owner, door, seq)?.id)
    }
}
