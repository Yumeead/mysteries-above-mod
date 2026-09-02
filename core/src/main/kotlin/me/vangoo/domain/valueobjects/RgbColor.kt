package me.vangoo.domain.valueobjects

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Колір як спакований RGB. Існує, щоб `core` міг тримати брендинг шляхів, не знаючи
 * ні про `net.minecraft.util.ARGB`, ні про `TextColor` — мод розпаковує на своєму боці.
 *
 * ## Відтінок — один, світлість — різна
 *
 * Фігура, намальована **одним** кольором шляху, читається як пляма фарби: двадцять
 * порошинок однакового синього не мають ні глибини, ні форми. Тому в кольору є
 * [shade] — «той самий колір, трохи темніший або світліший», — і фігури беруть із
 * нього палітру ([shades]) замість того, щоб підмішувати білий чи чорний руками.
 *
 * Працює це для будь-якого шляху, а не лише для синіх Дверей: перетворення йде через
 * **HSV**, тобто відтінок лишається тим самим числом, а рухається лише яскравість
 * (і, у бік світлого, насиченість). Множення каналів RGB, яке напрошується першим,
 * так не вміє: воно годиться, щоб затемнити, але «освітлити» ним означає впертись у
 * межу 255 різними каналами в різний момент — і колір поїде у відтінку.
 */
@JvmInline
value class RgbColor(val packed: Int) {

    init {
        require(packed in 0..0xFFFFFF) { "RGB out of range: $packed" }
    }

    val red: Int get() = (packed shr 16) and 0xFF
    val green: Int get() = (packed shr 8) and 0xFF
    val blue: Int get() = packed and 0xFF

    /**
     * Той самий відтінок, інша світлість.
     *
     * @param amount −1 — до чорного, 0 — цей самий колір, +1 — до білого.
     */
    fun shade(amount: Double): RgbColor {
        require(amount in -1.0..1.0) { "Зсув світлості поза межами [-1, 1]: $amount" }
        if (amount == 0.0) return this

        val r = red / 255.0
        val g = green / 255.0
        val b = blue / 255.0
        val top = maxOf(r, g, b)
        val bottom = minOf(r, g, b)

        val saturation = if (top == 0.0) 0.0 else (top - bottom) / top
        return if (amount < 0) {
            // Темніше — це лише менша яскравість: відтінок і насиченість не рухаються.
            fromHsv(hueOf(r, g, b, top, bottom), saturation, top * (1 + amount))
        } else {
            // Світліше — це шлях до білого, а білий у HSV має нульову насиченість.
            fromHsv(hueOf(r, g, b, top, bottom), saturation * (1 - amount), top + (1 - top) * amount)
        }
    }

    /**
     * Палітра одного відтінку: [count] кольорів від темнішого до світлішого.
     *
     * Саме палітра, а не випадковий зсув на кожну частинку: випадковість дає шум, а
     * впорядкований список дозволяє фігурі покласти світле там, де в неї «світло»
     * (вершина арки, край хвилі), і темне там, де тінь. Різниця між шумом і
     * градієнтом — це різниця між строкатим і об'ємним.
     *
     * @param spread наскільки далеко розходяться краї палітри від цього кольору
     */
    fun shades(count: Int, spread: Double = 0.35): List<RgbColor> {
        require(count > 0) { "Палітра не може бути порожньою" }
        if (count == 1) return listOf(this)
        return List(count) { i -> shade(-spread + 2 * spread * i / (count - 1)) }
    }

    override fun toString(): String = "#%06X".format(packed)

    companion object {
        fun of(r: Int, g: Int, b: Int): RgbColor {
            require(r in 0..255 && g in 0..255 && b in 0..255) { "RGB out of range: $r,$g,$b" }
            return RgbColor((r shl 16) or (g shl 8) or b)
        }

        /** Відтінок у градусах [0, 360). Сірий відтінку не має — для нього нуль. */
        private fun hueOf(r: Double, g: Double, b: Double, top: Double, bottom: Double): Double {
            val span = top - bottom
            if (span < 1.0e-9) return 0.0
            val raw = when (top) {
                r -> (g - b) / span
                g -> 2.0 + (b - r) / span
                else -> 4.0 + (r - g) / span
            } * 60.0
            return if (raw < 0) raw + 360.0 else raw
        }

        private fun fromHsv(hue: Double, saturation: Double, value: Double): RgbColor {
            val s = saturation.coerceIn(0.0, 1.0)
            val v = value.coerceIn(0.0, 1.0)
            val sector = hue / 60.0
            val chroma = v * s
            val second = chroma * (1 - abs(sector % 2.0 - 1))
            val (r, g, b) = when (sector.toInt()) {
                0 -> Triple(chroma, second, 0.0)
                1 -> Triple(second, chroma, 0.0)
                2 -> Triple(0.0, chroma, second)
                3 -> Triple(0.0, second, chroma)
                4 -> Triple(second, 0.0, chroma)
                else -> Triple(chroma, 0.0, second)
            }
            val base = v - chroma
            return of(
                ((r + base) * 255).roundToInt().coerceIn(0, 255),
                ((g + base) * 255).roundToInt().coerceIn(0, 255),
                ((b + base) * 255).roundToInt().coerceIn(0, 255),
            )
        }
    }
}
