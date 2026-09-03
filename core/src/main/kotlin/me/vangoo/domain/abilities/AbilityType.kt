package me.vangoo.domain.abilities

enum class AbilityType {
    /** Requires explicit activation by the player; costs spirituality and leaves a cooldown. */
    ACTIVE,

    /** A passive with a switch — the player turns it on and off. */
    TOGGLEABLE_PASSIVE,

    /** A passive that works at all times, as long as the Beyonder has the ability. */
    PERMANENT_PASSIVE,
    ;

    val isActive: Boolean get() = this == ACTIVE
}
