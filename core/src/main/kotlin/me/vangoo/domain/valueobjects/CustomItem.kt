package me.vangoo.domain.valueobjects

/**
 * Domain Value Object: Represents a custom item definition.
 *
 * НЕ data class: конструктор нормалізує [lore] (null → порожній, інакше незмінна копія),
 * а `copy()` пройшов би повз нормалізацію — та сама причина, що й у [Mastery].
 */
class CustomItem(
    val id: String,
    val displayName: String,
    /** Ключ матеріалу рядком (напр. `MUSIC_DISC_FAR`) — VO лишається без залежності від платформи. */
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
