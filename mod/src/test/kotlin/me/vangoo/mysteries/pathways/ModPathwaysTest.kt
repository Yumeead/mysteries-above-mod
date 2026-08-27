package me.vangoo.mysteries.pathways

import me.vangoo.mysteries.GameRegistries
import me.vangoo.domain.abilities.AbilityType
import me.vangoo.domain.entities.Beyonder
import me.vangoo.domain.entities.Pathway
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Mastery
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.Ticking
import me.vangoo.mysteries.pathways.sun.Sun
import me.vangoo.mysteries.pathways.tyrant.Tyrant
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Розкладка шляхів: гри не піднімає.
 *
 * Здібності тут **будуються по-справжньому** — це і є перевірка, бо помилка в реєстрації
 * (не та Послідовність, дубльована ідентичність, тип, що не збігається з базовим класом)
 * інакше проявилась би лише в грі, і то не одразу.
 */
class ModPathwaysTest {

    companion object {
        /**
         * Світ тут не піднімається — піднімаються **реєстри**.
         *
         * `ResistanceToHorror` тримає свій перелік дебафів у полі, тобто торкається
         * `MobEffects` уже при завантаженні класу, а той без `Bootstrap` падає
         * `ExceptionInInitializerError`. Розкладати константи по лінивих обгортках
         * заради тесту не варто: `bootStrap()` коштує секунду й нічого, крім реєстрів,
         * не вмикає.
         */
        @JvmStatic
        @BeforeAll
        fun bootstrapRegistries() {
            GameRegistries.bootstrap()
        }
    }

    @Test
    @DisplayName("Індекс імені Послідовності дорівнює її рівню")
    fun sequenceNamesAreIndexedByLevel() {
        assertEquals("Sailor", Tyrant.create().sequenceName(9))
        assertEquals("Seafarer", Tyrant.create().sequenceName(7))
        assertEquals("Tyrant", Tyrant.create().sequenceName(0))
        assertEquals("Bard", Sun.create().sequenceName(9))
        assertEquals("Solar High Priest", Sun.create().sequenceName(7))
    }

    @Test
    @DisplayName("Кожен шлях оголошує всі десять Послідовностей і має здібності")
    fun everyPathwayIsComplete() {
        ModPathways.all.forEach { pathway ->
            repeat(Pathway.SEQUENCE_COUNT) { level ->
                assertTrue(pathway.sequenceName(level).isNotBlank(), "${pathway.name} $level")
            }
            assertTrue(pathway.hasAnyAbility(), "${pathway.name} — заглушка, а не шлях")
        }
    }

    @Test
    @DisplayName("Ідентичності здібностей унікальні по всіх шляхах")
    fun abilityIdentitiesAreUnique() {
        val identities = ModPathways.all
            .flatMap { pathway -> (0 until Pathway.SEQUENCE_COUNT).flatMap(pathway::abilitiesFor) }
            .map { it.identity }

        assertEquals(identities.size, identities.toSet().size, "дубльовані ідентичності: $identities")
    }

    @Test
    @DisplayName("Оголошений тип збігається з базовим класом")
    fun declaredTypeMatchesBaseClass() {
        ModPathways.all
            .flatMap { pathway -> (0 until Pathway.SEQUENCE_COUNT).flatMap(pathway::abilitiesFor) }
            .forEach { spec ->
                when (spec.type) {
                    AbilityType.ACTIVE -> assertTrue(spec is ActiveAbility, "${spec.identity}")
                    AbilityType.PERMANENT_PASSIVE ->
                        assertTrue(spec is PermanentPassiveAbility, "${spec.identity}")

                    else -> Unit
                }
                // Пасивка, що цокає щотіку через недогляд, — найдорожча помилка цього шару.
                if (spec is Ticking) assertTrue(spec.tickPeriodTicks > 0, "${spec.identity}")
            }
    }

    @Test
    @DisplayName("Піднесення відкриває здібності верхніх Послідовностей, не забираючи нижніх")
    fun advancingAccumulatesAbilities() {
        val beyonder = Beyonder(UUID.randomUUID(), Sequence.starter(), Tyrant.create())

        assertEquals(listOf("tyrant_illusory_scales"), beyonder.abilities.map { it.identity.id })

        // 9 → 8 → 7: засвоєння скидається на кожному піднесенні, тож добираємо його двічі.
        repeat(2) {
            beyonder.setMastery(Mastery.max())
            beyonder.advance()
        }

        assertEquals(7, beyonder.sequenceLevel)
        assertNotNull(beyonder.abilityBy(identity("tyrant_illusory_scales")))
        assertNotNull(beyonder.abilityBy(identity("tyrant_restorative_water")))
    }

    @Test
    @DisplayName("Ціна й перепочинок Цілющої Води — ті самі, що в плагіні")
    fun restorativeWaterKeepsItsPrice() {
        val spec = Tyrant.create().abilitiesFor(7).single()

        assertEquals(35, spec.spiritualityCost)
        assertEquals(20, spec.cooldownSeconds(Sequence(7)))
        // Активна здібність не утримується — платить разово.
        assertEquals(0, spec.periodicCost(Sequence(7)))
    }

    @Test
    @DisplayName("Пошук шляху не зважає на регістр, невідомого — немає")
    fun lookupIsCaseInsensitive() {
        assertEquals("Tyrant", ModPathways.byName("tYrAnT")?.name)
        assertNull(ModPathways.byName("Fool"))
    }

    private fun identity(id: String) = AbilityIdentity.of(id)
}
