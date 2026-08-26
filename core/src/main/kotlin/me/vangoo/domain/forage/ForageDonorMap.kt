package me.vangoo.domain.forage

import java.util.Locale

/**
 * Чисте правило мапінгу «оригінальний блок → блок-донор» фореджу. Донор — блок, якого немає
 * в Оверворлді природно; ресурспак малює його «зачарованим». Overrides перемагають дефолти;
 * дефолт обирається за класом цілі (рослина/листя). Імена блоків — рядки (без Minecraft);
 * їхню валідність перевіряє завантажувач конфіга.
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

    /** Донор для оригінального блока; [leaves] — чи належить ціль до листя. */
    fun donorFor(originalMaterial: String?, leaves: Boolean): String =
        overrides[normalize(originalMaterial)]
            ?: if (leaves) defaultLeavesDonor else defaultPlantDonor

    private companion object {
        fun normalize(name: String?): String = name?.trim()?.uppercase(Locale.ROOT) ?: ""
    }
}
