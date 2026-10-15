package me.vangoo.mysteries.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AbilityGroupTest {

    @Test
    fun bindingMovesBothTheKeyAndTheAbility() {
        val group = AbilityGroup("бій", mutableListOf("a", "b"))
        group.bind("key.keyboard.c", "a")
        group.bind("key.keyboard.v", "a")
        assertNull(group.hotkeys["key.keyboard.c"])
        assertEquals("key.keyboard.v", group.keyOf("a"))

        group.bind("key.keyboard.v", "b")
        assertNull(group.keyOf("a"))
        assertEquals("b", group.hotkeys["key.keyboard.v"])
    }

    @Test
    fun removingFromTheGroupDropsItsKey() {
        val group = AbilityGroup("бій", mutableListOf("a"))
        group.bind("key.keyboard.c", "a")
        group.toggle("a")
        assertFalse(group.contains("a"))
        assertNull(group.keyOf("a"))
        group.toggle("a")
        assertTrue(group.contains("a"))
    }

    @Test
    fun allGroupHoldsEverythingAndCannotBeEdited() {
        val all = AbilityGroup("", null)
        all.toggle("x")
        assertTrue(all.contains("x"))
        assertTrue(all.isAll)
    }
}
