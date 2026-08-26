package me.vangoo.domain.spells

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Баланс заклинань тестується без сервера — у плагіні `calculateStats` був замкнений
 * у GUI-класі `Spellcasting` і покрити його було неможливо.
 */
internal class SpellRecipeTest {

    @Test
    fun aoeScalesWithPowerAndArea() {
        val r = SpellRecipe.fromBlueprint(
            SpellBlueprint("Гнів", SpellRecipe.Shape.AOE, 2, 3, 0, 0, 0, null),
        )

        assertEquals(8.0, r.damage) // 4 + 2*2.0
        assertEquals(7.5, r.radius) // 3 + 3*1.5
        assertEquals(65, r.spiritualityCost) // 40 + 2*5 + 3*5
        assertEquals(15, r.cooldownSeconds) // max(1, 10-0) + 5
        assertNull(r.buff)
    }

    @Test
    fun costReductionFloorsAtFive() {
        val r = SpellRecipe.fromBlueprint(
            SpellBlueprint("Дешеве", SpellRecipe.Shape.PROJECTILE, 0, 0, 0, 0, 5, null),
        )

        assertEquals(5, r.spiritualityCost) // max(5, 25 - 5*5)
    }

    @Test
    fun buffShapeRequiresBuffType() {
        assertThrows(IllegalArgumentException::class.java) {
            SpellRecipe("X", SpellRecipe.Shape.BUFF, 0.0, 0.0, 0.0, 100, null, 0, 10, 10)
        }
    }

    @Test
    fun blankNameRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SpellRecipe("  ", SpellRecipe.Shape.SELF, 0.0, 0.0, 4.0, 0, null, 0, 10, 10)
        }
    }

    @Test
    fun negativeCostRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SpellRecipe("X", SpellRecipe.Shape.SELF, 0.0, 0.0, 4.0, 0, null, 0, -1, 10)
        }
    }

    @Test
    fun codecRoundTripsDamageSpell() {
        val original = SpellRecipe.fromBlueprint(
            SpellBlueprint("вогняний спис", SpellRecipe.Shape.AOE, 4, 2, 0, 1, 0, null),
        )

        assertEquals(original, SpellCodec.decode(SpellCodec.encode(original)))
    }

    @Test
    fun codecRoundTripsBuffSpell() {
        val original = SpellRecipe.fromBlueprint(
            SpellBlueprint("аура сили", SpellRecipe.Shape.BUFF, 0, 3, 1, 0, 0, SpellRecipe.Buff.STRENGTH),
        )

        val restored = SpellCodec.decode(SpellCodec.encode(original))

        assertEquals(SpellRecipe.Buff.STRENGTH, restored.buff)
        assertEquals(original, restored)
    }

    @Test
    fun codecRoundTripsNameWithUnderscore() {
        val original = SpellRecipe.fromBlueprint(
            SpellBlueprint("спис_вогню", SpellRecipe.Shape.PROJECTILE, 1, 0, 0, 0, 0, null),
        )

        assertEquals("спис_вогню", SpellCodec.decode(SpellCodec.encode(original)).name)
    }

    @Test
    fun encodedIdIsRecognizedAndPlainIsNot() {
        val id = SpellCodec.encode(
            SpellRecipe.fromBlueprint(
                SpellBlueprint("X", SpellRecipe.Shape.TELEPORT, 0, 1, 0, 0, 0, null),
            ),
        )
        assert(SpellCodec.isSerializedSpell(id))
        assert(!SpellCodec.isSerializedSpell("illusory_scales"))
        assert(!SpellCodec.isSerializedSpell(null))
    }

    @Test
    fun decodeRejectsForeignId() {
        assertThrows(IllegalArgumentException::class.java) { SpellCodec.decode("illusory_scales") }
    }

    @Test
    fun decodeRejectsTruncatedPayload() {
        assertThrows(IllegalArgumentException::class.java) { SpellCodec.decode("generated:AOE|1.0") }
    }

    @Test
    fun describeOmitsZeroedFields() {
        val teleport = SpellRecipe.fromBlueprint(
            SpellBlueprint("Крок", SpellRecipe.Shape.TELEPORT, 0, 1, 0, 0, 0, null),
        )

        assertEquals(
            listOf("Тип" to "Телепорт", "Радіус" to "15.0м"),
            SpellCodec.describe(teleport),
        )
    }
}
