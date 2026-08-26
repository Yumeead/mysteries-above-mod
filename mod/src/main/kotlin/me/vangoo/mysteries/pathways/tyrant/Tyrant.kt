package me.vangoo.mysteries.pathways.tyrant

import me.vangoo.domain.entities.Pathway
import me.vangoo.domain.entities.PathwayGroup
import me.vangoo.mysteries.pathways.tyrant.abilities.IllusoryScales
import me.vangoo.mysteries.pathways.tyrant.abilities.RestorativeWater

/**
 * Шлях Тирана — море, шторм і вітер.
 *
 * Не клас-нащадок `Pathway`, як у плагіні: там базовий конструктор кликав
 * перевизначений `initializeAbilities()`, тобто підклас наповнював поле, якого ще
 * не існує. Тут шлях — просто значення, а це — фабрика, що його складає.
 *
 * **Наповнений лише на фазу 05.** Із 30 здібностей Тирана перенесено дві — рівно ті,
 * що замикають цикл «випив зілля → скастував». Решта — фаза 08.
 */
object Tyrant {

    const val NAME: String = "Tyrant"

    /** Канон новели; індекс списку **дорівнює** Послідовності: 0 — Тиран, 9 — Матрос. */
    private val SEQUENCE_NAMES = listOf(
        "Tyrant", "God of Thunder", "Calamity", "Sea King", "Calamity Patron",
        "Ocean Songster", "Wind-Blessed", "Seafarer", "Furious One", "Sailor",
    )

    fun create(): Pathway = Pathway(
        name = NAME,
        group = PathwayGroup.GodAlmighty,
        sequenceNames = SEQUENCE_NAMES,
        roster = mapOf(
            9 to listOf(IllusoryScales()),
            7 to listOf(RestorativeWater()),
        ),
    )
}
