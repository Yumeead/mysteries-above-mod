package me.vangoo.domain.creatures

/**
 * A pure rule: whether this creature may be shown to a player of that sequence.
 *
 * Apex creatures are Sequence 5. They one-shot anyone weaker, and their loot is the
 * ingredients of a Seq. 5 recipe, so a Seq. 9 player needs them neither as a challenge nor as
 * spoils. Hence only someone who has come right up to Seq. 5 ever meets an apex.
 *
 * One rule for every consumer (structure spawn, ambient spawn) — so the gate does not stay in
 * one listener while the creature arrives through another.
 */
object ApexGate {

    /** The player must be at least Seq. 6 (a smaller number = stronger) to meet an apex. */
    const val APEX_MIN_SEQUENCE = 6

    /**
     * @param tier the creature's tier
     * @param playerSequenceLevel the player's sequence, or `null` if he is not a Beyonder
     */
    fun allows(tier: CreatureTier, playerSequenceLevel: Int?): Boolean =
        tier != CreatureTier.APEX || (playerSequenceLevel != null && playerSequenceLevel <= APEX_MIN_SEQUENCE)
}
