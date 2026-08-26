package me.vangoo.domain.rituals

/** Оцінка жертви: скільки духовності повертає спалення предмета на вівтарі. */
object SacrificeAppraiser {

    fun spiritualityFor(kind: SacrificeKind): Int = when (kind) {
        SacrificeKind.PATHWAY_INGREDIENT -> 300
        SacrificeKind.PRECIOUS -> 150
        SacrificeKind.VALUABLE -> 60
        SacrificeKind.TRIFLE -> 10
    }
}
