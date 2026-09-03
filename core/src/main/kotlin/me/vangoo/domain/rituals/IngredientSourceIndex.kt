package me.vangoo.domain.rituals

import me.vangoo.domain.creatures.CreatureDefinition
import me.vangoo.domain.forage.ForageEntry

/**
 * Where an ingredient comes from: a creature's loot or foraging. The source for the Ritual of
 * Revelation ([RitualType.BESTOWMENT]).
 *
 * Pure domain: biomes stay raw keys, and the effect layer composes the hint text.
 * The index is built once at startup.
 */
class IngredientSourceIndex(
    creatures: Collection<CreatureDefinition>,
    forageBiomes: Map<String, List<ForageEntry>>,
) {
    enum class Kind { CREATURE, FORAGE }

    /** The kind of source + the biomes to look in (no duplicates, in config order). */
    data class Source(val kind: Kind, val biomes: List<String>)

    private val byIngredient: Map<String, Source> = buildMap {
        // Foraging goes first: a creature overrides it — if the ingredient drops from a mob,
        // that is the main hint.
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

    /** Empty for an ingredient found only in chests (global loot with no place of its own). */
    fun sourceOf(ingredientId: String): Source? = byIngredient[ingredientId]

    private companion object {
        /** Groups "ingredient → biome" pairs into sources, keeping the order and dropping duplicates. */
        fun index(kind: Kind, pairs: List<Pair<String, String>>): Map<String, Source> =
            pairs.groupBy({ it.first }, { it.second })
                .mapValues { (_, biomes) -> Source(kind, biomes.distinct()) }
    }
}
