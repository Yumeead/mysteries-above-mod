package me.vangoo.domain.valueobjects

/**
 * Kinds of weapon the Magician makes out of paper (Seq 7 — "Paper as a weapon",
 * the evolution of the Paper Cutter). Paper cost 32…64 depending on power.
 *
 * A pure VO: numbers and a Ukrainian label only. The mapping onto a material,
 * the hit effects and the status effects live in the pathways layer.
 */
enum class PaperWeaponType(
    val displayName: String,
    val paperCost: Int,
    val bonusDamage: Int,
    val knockback: Double,
    val slowTicks: Int,
    /** How many blows the item survives before falling apart. */
    val uses: Int,
) {
    /** Bat — strong knockback, moderate bonus damage. */
    BAT("Паперова бита", 32, 3, 1.4, 0, 8),

    /** Cane — slows the target on hit. */
    CANE("Паперова тростина", 48, 4, 0.2, 100, 6),

    /** Brick — heavy one-off damage. */
    BRICK("Паперова цегла", 64, 8, 0.4, 0, 4),
}
