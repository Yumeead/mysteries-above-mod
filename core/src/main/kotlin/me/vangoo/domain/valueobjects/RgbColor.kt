package me.vangoo.domain.valueobjects

/**
 * Колір як спакований RGB. Існує, щоб `core` міг тримати брендинг шляхів, не знаючи
 * ні про `net.minecraft.util.ARGB`, ні про `TextColor` — мод розпаковує на своєму боці.
 */
@JvmInline
value class RgbColor(val packed: Int) {

    init {
        require(packed in 0..0xFFFFFF) { "RGB out of range: $packed" }
    }

    val red: Int get() = (packed shr 16) and 0xFF
    val green: Int get() = (packed shr 8) and 0xFF
    val blue: Int get() = packed and 0xFF

    override fun toString(): String = "#%06X".format(packed)

    companion object {
        fun of(r: Int, g: Int, b: Int): RgbColor {
            require(r in 0..255 && g in 0..255 && b in 0..255) { "RGB out of range: $r,$g,$b" }
            return RgbColor((r shl 16) or (g shl 8) or b)
        }
    }
}
