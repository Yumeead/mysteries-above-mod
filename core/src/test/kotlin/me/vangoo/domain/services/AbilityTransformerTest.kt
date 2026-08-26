package me.vangoo.domain.services

import me.vangoo.domain.abilities.TestAbilitySpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AbilityTransformerTest {

    private fun ids(specs: List<me.vangoo.domain.abilities.AbilitySpec>) =
        specs.map { it.identity.id }

    @Test
    fun `initialization from empty keeps incoming order`() {
        val result = AbilityTransformer.transform(
            current = emptyList(),
            incoming = listOf(TestAbilitySpec.of("a"), TestAbilitySpec.of("b")),
        )
        assertEquals(listOf("a", "b"), ids(result))
    }

    @Test
    fun `same identity is replaced in place, not appended`() {
        val weak = TestAbilitySpec.of("scan_gaze", cost = 10)
        val strong = TestAbilitySpec.of("scan_gaze", cost = 40)

        val result = AbilityTransformer.transform(
            current = listOf(weak, TestAbilitySpec.of("tail")),
            incoming = listOf(strong),
        )

        assertEquals(listOf("scan_gaze", "tail"), ids(result))
        assertEquals(40, result[0].spiritualityCost)
    }

    @Test
    fun `abilities without a replacement survive`() {
        val result = AbilityTransformer.transform(
            current = listOf(TestAbilitySpec.of("old")),
            incoming = listOf(TestAbilitySpec.of("new")),
        )
        assertEquals(listOf("old", "new"), ids(result))
    }

    @Test
    fun `duplicate identity inside incoming collapses to the last version`() {
        val result = AbilityTransformer.transform(
            current = emptyList(),
            incoming = listOf(
                TestAbilitySpec.of("scan_gaze", cost = 10),
                TestAbilitySpec.of("scan_gaze", cost = 40),
            ),
        )
        assertEquals(1, result.size)
        assertEquals(40, result[0].spiritualityCost)
    }

    @Test
    fun `duplicate identity inside current collapses too`() {
        val result = AbilityTransformer.transform(
            current = listOf(TestAbilitySpec.of("dup"), TestAbilitySpec.of("dup")),
            incoming = emptyList(),
        )
        assertEquals(listOf("dup"), ids(result))
    }

    @Test
    fun `empty on both sides yields empty`() {
        assertEquals(emptyList<String>(), ids(AbilityTransformer.transform(emptyList(), emptyList())))
    }
}
