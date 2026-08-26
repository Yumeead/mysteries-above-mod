package me.vangoo.domain

import me.vangoo.domain.valueobjects.RgbColor

/**
 * Єдине джерело правди для кольору шляху: рідина зілля і колір назви.
 *
 * У плагіні колір тексту був `ChatColor` — одним із 16 легасі-кольорів. Тут це
 * звичайний RGB: 26.2 фарбує `Style` повним `TextColor`, тож обмеження в 16 відтінків
 * більше нічого не купує. Значення успадковані від легасі-палітри, щоб брендинг
 * лишився тим самим, що гравці вже бачили.
 */
object PathwayBranding {

    data class Branding(val liquid: RgbColor, val text: RgbColor)

    private val FALLBACK = Branding(RgbColor.of(128, 128, 128), Legacy.GRAY)

    private val table: Map<String, Branding> = buildMap {
        fun put(name: String, r: Int, g: Int, b: Int, text: RgbColor) {
            put(name, Branding(RgbColor.of(r, g, b), text))
        }

        put("Error", 102, 153, 161, Legacy.BLUE)
        put("Visionary", 143, 181, 241, Legacy.GRAY)
        put("Door", 106, 230, 247, Legacy.AQUA)
        put("Justiciar", 235, 195, 142, Legacy.GOLD)
        put("WhiteTower", 118, 134, 225, Legacy.AQUA)
        put("Fool", 162, 110, 215, Legacy.LIGHT_PURPLE)
        put("Sun", 251, 232, 107, Legacy.YELLOW)
        put("Tyrant", 70, 115, 199, Legacy.DARK_AQUA)
        put("HangedMan", 204, 54, 53, Legacy.DARK_RED)
        put("Darkness", 127, 149, 185, Legacy.DARK_GRAY)
        put("Death", 114, 143, 108, Legacy.DARK_GREEN)
        put("TwilightGiant", 232, 120, 99, Legacy.GOLD)
        put("RedPriest", 255, 71, 58, Legacy.RED)
        put("Demoness", 210, 64, 159, Legacy.LIGHT_PURPLE)
        put("Hermit", 124, 98, 179, Legacy.DARK_PURPLE)
        put("Paragon", 243, 145, 75, Legacy.GOLD)
        put("Mother", 60, 183, 153, Legacy.GREEN)
        put("Moon", 255, 96, 104, Legacy.RED)
        put("Abyss", 132, 31, 17, Legacy.BLACK)
        put("Chained", 67, 63, 107, Legacy.DARK_GRAY)
        put("BlackEmperor", 29, 35, 65, Legacy.DARK_BLUE)
        put("WheelOfFortune", 161, 206, 215, Legacy.GRAY)
    }

    /** Імена всіх 22 шляхів — включно з 13 заглушками без здібностей. */
    val names: Set<String> = table.keys

    fun of(pathwayName: String?): Branding = table[pathwayName] ?: FALLBACK

    fun liquidOf(pathwayName: String?): RgbColor = of(pathwayName).liquid

    fun textOf(pathwayName: String?): RgbColor = of(pathwayName).text

    /** RGB легасі-кольорів чату — щоб таблиця вище читалась так само, як у плагіні. */
    private object Legacy {
        val BLACK = RgbColor(0x000000)
        val DARK_BLUE = RgbColor(0x0000AA)
        val DARK_GREEN = RgbColor(0x00AA00)
        val DARK_AQUA = RgbColor(0x00AAAA)
        val DARK_RED = RgbColor(0xAA0000)
        val DARK_PURPLE = RgbColor(0xAA00AA)
        val GOLD = RgbColor(0xFFAA00)
        val GRAY = RgbColor(0xAAAAAA)
        val DARK_GRAY = RgbColor(0x555555)
        val BLUE = RgbColor(0x5555FF)
        val GREEN = RgbColor(0x55FF55)
        val AQUA = RgbColor(0x55FFFF)
        val RED = RgbColor(0xFF5555)
        val LIGHT_PURPLE = RgbColor(0xFF55FF)
        val YELLOW = RgbColor(0xFFFF55)
    }
}
