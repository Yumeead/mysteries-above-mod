package me.vangoo.mysteries.pathways

import me.vangoo.domain.entities.Pathway
import me.vangoo.mysteries.pathways.door.Door
import me.vangoo.mysteries.pathways.sun.Sun
import me.vangoo.mysteries.pathways.tyrant.Tyrant
import me.vangoo.mysteries.pathways.whitetower.WhiteTower

/**
 * Шляхи, які мод уміє видати.
 *
 * Спадкоємець плагінного `PathwayManager`, але без його головної вади: там імена
 * Послідовностей жили в `PathwayManager`, а здібності — в самому шляху, тож обидві
 * половини одного шляху редагувались у різних файлах і розходились. Тут шлях
 * складається цілком у своїй фабриці, а цей об'єкт лише тримає список.
 *
 * **Двоє з двадцяти двох.** Фаза 05 переносить рівно три здібності, і більше шляхів
 * тут просто не було б чим наповнити: решта приїде у фазі 08 разом зі своїми
 * здібностями, а тринадцять заглушок — разом із зіллями. Брендинг (кольори рідини й
 * назви) уже знає всі 22 — див. `PathwayBranding`.
 */
object ModPathways {

    private val byName: Map<String, Pathway> =
        listOf(Tyrant.create(), Sun.create(), WhiteTower.create(), Door.create())
            .associateBy { it.name }

    val all: Collection<Pathway> get() = byName.values

    val names: List<String> get() = byName.keys.toList()

    /** Регістр не має значення: імена шляхів їздять у командах, набраних руками. */
    fun byName(name: String): Pathway? =
        byName.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}
