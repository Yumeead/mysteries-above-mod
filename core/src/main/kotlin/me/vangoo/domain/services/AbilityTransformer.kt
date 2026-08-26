package me.vangoo.domain.services

import me.vangoo.domain.abilities.AbilitySpec
import me.vangoo.domain.valueobjects.AbilityIdentity

/**
 * Зшиває старий список здібностей із набором нової Послідовності: та сама
 * ідентичність — заміщення сильнішою версією, нова ідентичність — доповнення.
 * Порядок старого списку зберігається, дублікатів на виході немає.
 */
object AbilityTransformer {

    fun transform(
        current: List<AbilitySpec>,
        incoming: List<AbilitySpec>,
    ): List<AbilitySpec> {
        // Останній переможець: у наборі «усе до поточної Послідовності» сильніша
        // версія йде пізніше, тож саме вона і має лишитись.
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
