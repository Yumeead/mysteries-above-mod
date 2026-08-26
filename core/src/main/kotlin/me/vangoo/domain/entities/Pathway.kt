package me.vangoo.domain.entities

import me.vangoo.domain.abilities.AbilitySpec

/**
 * Шлях: назва, група і по одному набору здібностей на кожну з десяти Послідовностей.
 *
 * Не абстрактний, на відміну від плагіна. Там базовий конструктор кликав
 * `initializeAbilities()` — перевизначений метод із конструктора, тобто підклас
 * наповнював поле, яке ще не існує. Тут набір приходить готовим ззовні, а шляхи в
 * моді — це фабрики, що його збирають.
 */
class Pathway(
    val name: String,
    val group: PathwayGroup,
    sequenceNames: List<String>,
    roster: Map<Int, List<AbilitySpec>> = emptyMap(),
) {

    private val sequenceNames: List<String> = sequenceNames.toList()
    private val roster: Map<Int, List<AbilitySpec>> =
        roster.mapValues { (_, abilities) -> abilities.toList() }

    init {
        require(name.isNotBlank()) { "Pathway name cannot be blank" }
        require(this.sequenceNames.size == SEQUENCE_COUNT) {
            "Pathway $name must declare $SEQUENCE_COUNT sequence names (0-9), " +
                "got ${this.sequenceNames.size}"
        }
        val stray = this.roster.keys.filterNot { it in 0 until SEQUENCE_COUNT }
        require(stray.isEmpty()) { "Pathway $name has abilities on unknown sequences: $stray" }
    }

    /** Канонічна назва Послідовності з новели, напр. `sequenceName(9) == "Матрос"`. */
    fun sequenceName(sequence: Int): String {
        require(sequence in 0 until SEQUENCE_COUNT) { "Sequence out of range: $sequence" }
        return sequenceNames[sequence]
    }

    fun abilitiesFor(sequence: Int): List<AbilitySpec> = roster[sequence].orEmpty()

    /** false — це шлях-заглушка: зілля й характеристики є, здібностей ще немає. */
    fun hasAnyAbility(): Boolean = roster.values.any { it.isNotEmpty() }

    override fun toString(): String = name

    companion object {
        /** Кожен шлях має рівно 10 Послідовностей: 0 — найсильніша, 9 — найслабша. */
        const val SEQUENCE_COUNT: Int = 10
    }
}
