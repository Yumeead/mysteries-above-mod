package me.vangoo.domain.forage

import java.util.Locale

/**
 * The pure "original block → donor block" mapping rule for foraging. The donor is a block that
 * does not occur naturally in the Overworld; the resource pack draws it as "enchanted".
 * Overrides beat defaults; the default is chosen by the target's class (plant/leaves). Block
 * names are strings (no Minecraft); the config loader checks that they are valid.
 */
class ForageDonorMap(
    defaultPlantDonor: String?,
    defaultLeavesDonor: String?,
    overrides: Map<String, String>,
) {
    private val defaultPlantDonor: String = normalize(defaultPlantDonor)
    private val defaultLeavesDonor: String = normalize(defaultLeavesDonor)
    private val overrides: Map<String, String> =
        overrides.entries.associate { (k, v) -> normalize(k) to normalize(v) }

    /** The donor for the original block; [leaves] — whether the target belongs to leaves. */
    fun donorFor(originalMaterial: String?, leaves: Boolean): String =
        overrides[normalize(originalMaterial)]
            ?: if (leaves) defaultLeavesDonor else defaultPlantDonor

    private companion object {
        fun normalize(name: String?): String = name?.trim()?.uppercase(Locale.ROOT) ?: ""
    }
}
