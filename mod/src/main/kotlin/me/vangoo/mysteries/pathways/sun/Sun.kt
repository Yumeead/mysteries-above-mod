package me.vangoo.mysteries.pathways.sun

import me.vangoo.domain.entities.Pathway
import me.vangoo.domain.entities.PathwayGroup
import me.vangoo.mysteries.pathways.sun.abilities.ResistanceToHorror

/**
 * Шлях Сонця — святе світло, клятви й присуди.
 *
 * **Наповнений лише на фазу 05**: одна пасивка Посл. 7, щоб перевірити другий шлях
 * тієї ж групи — саме між такими зіллям і дозволена зміна шляху. Решта — фаза 08.
 */
object Sun {

    const val NAME: String = "Sun"

    /** Канон новели; індекс списку **дорівнює** Послідовності: 0 — Сонце, 9 — Бард. */
    private val SEQUENCE_NAMES = listOf(
        "Sun", "White Angel", "Light Seeker", "Justice Mentor", "Shadowless",
        "Priest of Light", "Notary", "Solar High Priest", "Light Petitioner", "Bard",
    )

    fun create(): Pathway = Pathway(
        name = NAME,
        group = PathwayGroup.GodAlmighty,
        sequenceNames = SEQUENCE_NAMES,
        roster = mapOf(
            7 to listOf(ResistanceToHorror()),
        ),
    )
}
