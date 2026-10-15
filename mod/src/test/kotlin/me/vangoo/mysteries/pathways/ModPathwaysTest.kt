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
import me.vangoo.mysteries.pathways.door.Door
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
 * The pathway layout: it brings up no game.
 *
 * The abilities here are **really built** — that is the check, because a registration mistake
 * (the wrong Sequence, a duplicated identity, a type that does not match the base class) would
 * otherwise only show up in the game, and not immediately at that.
 */
class ModPathwaysTest {

    companion object {
        /**
         * No world is brought up here — the **registries** are.
         *
         * `ResistanceToHorror` keeps its list of debuffs in a field, i.e. it touches
         * `MobEffects` at class load time, and that fails with an `ExceptionInInitializerError`
         * without `Bootstrap`. Spreading constants over lazy wrappers for a test's sake is not
         * worth it: `bootStrap()` costs a second and switches on nothing but the registries.
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
    @DisplayName("Усередині однієї Послідовності ідентичності унікальні")
    fun abilityIdentitiesAreUniqueWithinSequence() {
        ModPathways.all.forEach { pathway ->
            repeat(Pathway.SEQUENCE_COUNT) { level ->
                val identities = pathway.abilitiesFor(level).map { it.identity }
                assertEquals(
                    identities.size, identities.toSet().size,
                    "дубльовані ідентичності в ${pathway.name}, Посл. $level: $identities",
                )
            }
        }
    }

    @Test
    @DisplayName("Та сама ідентичність на різних Послідовностях — це тір однієї сили")
    fun repeatedIdentityIsAnUpgradeOfTheSameAbility() {
        // The domain is built for this: `AbilityTransformer` treats a repeated identity as
        // "replaced by the stronger version", and `getAllSequencesUpToCurrent()` hands the
        // sequences over 9 → 0, so the stronger tier arrives last and wins. What must not
        // happen is two **different** powers sharing a key — that would be a silent swap.
        ModPathways.all.forEach { pathway ->
            (0 until Pathway.SEQUENCE_COUNT)
                .flatMap(pathway::abilitiesFor)
                .groupBy { it.identity }
                .filterValues { it.size > 1 }
                .forEach { (identity, tiers) ->
                    assertEquals(
                        1, tiers.map { it.javaClass }.toSet().size,
                        "${pathway.name}: $identity носять різні класи — ${tiers.map { it.javaClass.simpleName }}",
                    )
                }
        }
    }

    @Test
    @DisplayName("Спільна ідентичність означає спільний клас, а не збіг імен")
    fun sharedIdentityMeansSharedClass() {
        // The identity is a global key (it travels into saves and packets), so the same one on two
        // pathways is admissible **only** for a genuinely shared ability from `pathways/common`.
        // A copy of `sun_holy_wave` under the same name in another pathway would mean the stronger
        // version of one pathway replaces the other's.
        ModPathways.all
            .flatMap { pathway -> (0 until Pathway.SEQUENCE_COUNT).flatMap(pathway::abilitiesFor) }
            .groupBy { it.identity }
            .filterValues { it.size > 1 }
            .forEach { (identity, specs) ->
                val classes = specs.map { it::class }.toSet()
                assertEquals(1, classes.size, "ідентичність $identity носять різні класи: $classes")
            }
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
                // A passive ticking every tick by oversight is the most expensive mistake in this layer.
                if (spec is Ticking) assertTrue(spec.tickPeriodTicks > 0, "${spec.identity}")
            }
    }

    @Test
    @DisplayName("Піднесення відкриває здібності верхніх Послідовностей, не забираючи нижніх")
    fun advancingAccumulatesAbilities() {
        val beyonder = Beyonder(UUID.randomUUID(), Sequence.starter(), Tyrant.create())

        assertEquals(
            setOf("tyrant_physique", "tyrant_illusory_scales", "tyrant_stance", "tyrant_sea_lunge"),
            beyonder.abilities.map { it.identity.id }.toSet(),
        )

        // 9 → 8 → 7: mastery resets on every ascension, so it is topped up twice.
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
        val spec = Tyrant.create().abilitiesFor(7).single { it.identity == identity("tyrant_restorative_water") }

        assertEquals(35, spec.spiritualityCost)
        assertEquals(20, spec.cooldownSeconds(Sequence(7)))
        // An active ability holds nothing — it pays once.
        assertEquals(0, spec.periodicCost(Sequence(7)))
    }

    @Test
    @DisplayName("Двері заповнюють Посл. 9–5 і мовчать вище")
    fun doorFillsTheSameSequencesAsThePlugin() {
        val door = Door.create()

        assertEquals("Apprentice", door.sequenceName(9))
        assertEquals("Traveler", door.sequenceName(5))
        assertEquals("Door", door.sequenceName(0))

        // The roster is the only place a whole Sequence can silently stay empty: an ability
        // forgotten in the list is caught by neither the compiler nor the rest of the tests.
        assertEquals(listOf(1, 4, 4, 3, 2), (9 downTo 5).map { door.abilitiesFor(it).size })
        assertTrue((4 downTo 0).all { door.abilitiesFor(it).isEmpty() }, "вище Посл. 5 плагін не піднімався")
    }

    @Test
    @DisplayName("Пошук шляху не зважає на регістр, невідомого — немає")
    fun lookupIsCaseInsensitive() {
        assertEquals("Tyrant", ModPathways.byName("tYrAnT")?.name)
        assertNull(ModPathways.byName("Darkness"))
    }

    private fun identity(id: String) = AbilityIdentity.of(id)
}
