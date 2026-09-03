package me.vangoo.domain.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence

/**
 * What an ability **is and what it costs** — the half that knows nothing about Minecraft.
 *
 * The other half — what the ability **does** — is held by `Ability` in the mod: it implements
 * this interface and adds `execute(AbilityContext)`. The seam runs here, and that is exactly
 * why `core` can own a [me.vangoo.domain.entities.Beyonder] with a list of abilities without
 * seeing a single platform type.
 *
 * The plugin had no such boundary: `Ability` pulled in `IAbilityContext`, which pulled in
 * `org.bukkit.Location`, and the whole domain became platform-bound. Here that simply does
 * not compile.
 */
interface AbilitySpec {

    /**
     * The ability's stable key. In the plugin it was derived from the Ukrainian name
     * (`AbilityIdentity.of(getName())`) — here the identity is stated explicitly: it goes
     * into persistence and into packets, and must not travel with the text.
     */
    val identity: AbilityIdentity

    val type: AbilityType

    /** One-off cost of a cast. Passives pay nothing — 0 for them. */
    val spiritualityCost: Int

    /** Upkeep per second for toggles and channels; 0 — nothing is held. */
    fun periodicCost(sequence: Sequence): Int = 0

    fun cooldownSeconds(sequence: Sequence): Int

    /**
     * Multiplier of the owner's incoming sanity loss: 1.0 — no resistance, less — the passive
     * shields the mind. The strongest one is taken, not the product (see `Beyonder`).
     */
    val sanityLossMultiplier: Double get() = 1.0

    /** The same ability from a higher Sequence replaces the weaker version. */
    fun canReplace(other: AbilitySpec): Boolean = identity.canReplace(other.identity)
}
