package me.vangoo.domain.rituals

import me.vangoo.domain.creatures.CreatureDefinition
import me.vangoo.domain.forage.ForageEntry

/**
 * Звідки береться інгредієнт: із луту істоти чи з фореджу. Джерело Ритуалу одкровення
 * ([RitualType.BESTOWMENT]).
 *
 * Чистий домен: біоми лишаються сирими ключами, а текст натяку складає шар ефектів.
 * Індекс будується раз на старті.
 */
class IngredientSourceIndex(
    creatures: Collection<CreatureDefinition>,
    forageBiomes: Map<String, List<ForageEntry>>,
) {
    enum class Kind { CREATURE, FORAGE }

    /** Рід джерела + біоми, де його шукати (без дублікатів, у порядку конфіга). */
    data class Source(val kind: Kind, val biomes: List<String>)

    private val byIngredient: Map<String, Source> = buildMap {
        // Форедж кладемо першим: істота його перекриває — якщо інгредієнт падає з моба,
        // це головна підказка.
        putAll(index(Kind.FORAGE, forageBiomes.flatMap { (biome, entries) -> entries.map { it.ingredientId to biome } }))
        putAll(
            index(
                Kind.CREATURE,
                creatures.flatMap { creature ->
                    creature.loot.items.flatMap { item ->
                        creature.spawn.naturalBiomes.map { item.itemId to it }
                    }
                },
            ),
        )
    }

    /** Порожньо для інгредієнта, який трапляється лише в скринях (глобальний лут без місця). */
    fun sourceOf(ingredientId: String): Source? = byIngredient[ingredientId]

    private companion object {
        /** Групує пари «інгредієнт → біом» у джерела, зберігаючи порядок і прибираючи дублікати. */
        fun index(kind: Kind, pairs: List<Pair<String, String>>): Map<String, Source> =
            pairs.groupBy({ it.first }, { it.second })
                .mapValues { (_, biomes) -> Source(kind, biomes.distinct()) }
    }
}
