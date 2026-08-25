package me.vangoo.mysteries.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SequenceTest {

    @Test
    fun `advancing moves toward zero and stops there`() {
        assertEquals(Sequence(8), Sequence(9).advanced())
        assertNull(Sequence.STRONGEST.advanced())
    }

    @Test
    fun `lower level compares as stronger`() {
        assertTrue(Sequence(3) > Sequence(7))
        assertEquals(Sequence.STRONGEST, listOf(Sequence(5), Sequence(0), Sequence(9)).max())
    }

    @Test
    fun `level outside 0 to 9 is rejected`() {
        assertFailsWith<IllegalArgumentException> { Sequence(10) }
        assertFailsWith<IllegalArgumentException> { Sequence(-1) }
    }
}
