package me.vangoo.domain.rituals

/** Appraising a sacrifice: how much spirituality burning an item on the altar returns. */
object SacrificeAppraiser {

    fun spiritualityFor(kind: SacrificeKind): Int = when (kind) {
        SacrificeKind.PATHWAY_INGREDIENT -> 300
        SacrificeKind.PRECIOUS -> 150
        SacrificeKind.VALUABLE -> 60
        SacrificeKind.TRIFLE -> 10
    }
}
