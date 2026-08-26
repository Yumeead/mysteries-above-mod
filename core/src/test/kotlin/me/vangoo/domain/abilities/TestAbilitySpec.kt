package me.vangoo.domain.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence

/**
 * Мінімальна здібність для тестів `core`. Реальні здібності живуть у моді й тягнуть
 * Minecraft — сюди їх не занести, та й не треба: правилам достатньо ціни й типу.
 */
data class TestAbilitySpec(
    override val identity: AbilityIdentity,
    override val type: AbilityType = AbilityType.ACTIVE,
    override val spiritualityCost: Int = 0,
    override val sanityLossMultiplier: Double = 1.0,
    private val cooldown: Int = 0,
) : AbilitySpec {

    override fun cooldownSeconds(sequence: Sequence): Int = cooldown

    companion object {
        fun of(
            id: String,
            type: AbilityType = AbilityType.ACTIVE,
            cost: Int = 0,
            cooldown: Int = 0,
            sanityLossMultiplier: Double = 1.0,
        ): TestAbilitySpec = TestAbilitySpec(
            identity = AbilityIdentity.of(id),
            type = type,
            spiritualityCost = cost,
            sanityLossMultiplier = sanityLossMultiplier,
            cooldown = cooldown,
        )
    }
}
