package me.vangoo.domain.valueobjects

/**
 * Kinds of local illusions of the Magician (Seq 7 of the Fool pathway — Illusion Creation).
 *
 * A pure VO: id + spirituality cost. The concrete sounds/particles/smoke live
 * in the pathways layer (`IllusionCreation`).
 */
enum class IllusionKind(val displayName: String, val spiritualityCost: Int) {

    /** A fake explosion — a loud sound + smoke. */
    EXPLOSION("Ілюзія вибуху", 40),

    /** Fake footsteps — quiet sounds near the target. */
    FOOTSTEPS("Ілюзія кроків", 20),

    /** A fake fire — flame particles + crackling. */
    FIRE("Ілюзія пожежі", 30),
}
