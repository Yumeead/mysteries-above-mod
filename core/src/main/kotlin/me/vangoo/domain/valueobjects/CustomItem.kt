package me.vangoo.domain.valueobjects

/**
 * Domain Value Object: Represents a custom item definition.
 *
 * NOT a data class: the constructor normalises [lore] (null → empty, otherwise an immutable
 * copy), and `copy()` would go around the normalisation — the same reason as in [Mastery].
 */
class CustomItem(
    val id: String,
    val displayName: String,
    /** Material key as a string (e.g. `MUSIC_DISC_FAR`) — the VO stays free of platform types. */
    val material: String,
    lore: List<String>?,
    val glow: Boolean,
    val customModelData: String?,
) {
    val lore: List<String> = lore?.toList() ?: emptyList()

    init {
        require(material.isNotBlank()) { "Material cannot be blank" }
    }

    /** Check if custom model data is set */
    fun hasCustomModelData(): Boolean = !customModelData.isNullOrEmpty()

    override fun equals(other: Any?): Boolean =
        other is CustomItem &&
            other.id == id &&
            other.displayName == displayName &&
            other.material == material &&
            other.lore == lore &&
            other.glow == glow &&
            other.customModelData == customModelData

    override fun hashCode(): Int =
        listOf(id, displayName, material, lore, glow, customModelData).hashCode()

    override fun toString(): String =
        "CustomItem(id=$id, displayName=$displayName, material=$material, " +
            "lore=$lore, glow=$glow, customModelData=$customModelData)"
}
