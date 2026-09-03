package me.vangoo.domain.valueobjects

/**
 * One servant of the undead retinue (Death, Seq. 6), described well enough for the effect
 * layer (`UndeadRetinue.spawnFromRecord`) to restore it after a relog/restart or after being
 * shown from the shadows.
 *
 * [typeOrTag] is a vanilla `EntityType` (RESURRECTED/SUBJUGATED) or the internal id of a
 * MythicMobs creature of the Spirit World (PACT). `SUBJUGATED` lives in this list for the
 * sake of hiding/showing within ONE session — such records are deliberately never written to
 * disk (surviving a relog/restart), because subjugated undead is an arbitrary vanilla mob
 * with no identity of its own.
 */
data class RetinueServant(
    val kind: Kind,
    val typeOrTag: String,
    val customName: String?,
    val at: RetinuePoint,
) {
    enum class Kind { RESURRECTED, SUBJUGATED, PACT }
}
