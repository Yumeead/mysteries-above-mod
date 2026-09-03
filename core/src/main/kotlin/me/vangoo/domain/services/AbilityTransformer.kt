package me.vangoo.domain.services

import me.vangoo.domain.abilities.AbilitySpec
import me.vangoo.domain.valueobjects.AbilityIdentity

/**
 * Stitches the old ability list together with the new Sequence's set: the same identity means
 * replacement by the stronger version, a new identity means an addition.
 * The order of the old list is preserved and there are no duplicates on the way out.
 */
object AbilityTransformer {

    fun transform(
        current: List<AbilitySpec>,
        incoming: List<AbilitySpec>,
    ): List<AbilitySpec> {
        // Last one wins: in a set of "everything up to the current Sequence" the stronger
        // version comes later, so that is exactly the one that must remain.
        val byIdentity: Map<AbilityIdentity, AbilitySpec> =
            incoming.associateBy { it.identity }

        val result = mutableListOf<AbilitySpec>()
        val taken = mutableSetOf<AbilityIdentity>()

        for (ability in current) {
            if (!taken.add(ability.identity)) continue
            result += byIdentity[ability.identity] ?: ability
        }
        for ((identity, ability) in byIdentity) {
            if (taken.add(identity)) result += ability
        }
        return result
    }
}
