package me.vangoo.domain.hud

enum class SpiritualityMode { FLASK, BAR, OFF }
enum class SpiritualityNumbers { ON_CHANGE, ALWAYS, NEVER }
enum class InterfaceStyle { VANILLA, MOD }
enum class HealthStyle { HEARTS, BAR }
enum class HungerStyle { ICONS, BAR }
enum class HalfHeartStyle { HEIGHT, LEFT }
enum class HudPart { FLASK, BAR, ABILITY, MARIONETTES, AUXILIARY }
enum class HorizontalAnchor { LEFT, CENTER, RIGHT }
enum class VerticalAnchor { TOP, CENTER, BOTTOM }

data class HudSettings(
    val spirituality: SpiritualityMode = SpiritualityMode.FLASK,
    val numbers: SpiritualityNumbers = SpiritualityNumbers.ON_CHANGE,
    val positions: Map<HudPart, HudPosition> = emptyMap(),
    val scales: Map<HudPart, Float> = emptyMap(),
    val flaskBacking: Boolean = false,
    val interfaceStyle: InterfaceStyle = InterfaceStyle.MOD,
    val health: HealthStyle = HealthStyle.HEARTS,
    val hunger: HungerStyle = HungerStyle.ICONS,
    val halfHeart: HalfHeartStyle = HalfHeartStyle.HEIGHT,
    val healthNumbers: SpiritualityNumbers = SpiritualityNumbers.ON_CHANGE,
    val hungerNumbers: SpiritualityNumbers = SpiritualityNumbers.ON_CHANGE,
    val compactHealthAbove40: Boolean = false,
    val panelPositions: Map<HudPart, HudPosition> = emptyMap(),
) {
    fun scale(part: HudPart): Float = validScale(scales[part] ?: 1f)
    val activePositions get() = if (interfaceStyle == InterfaceStyle.MOD) panelPositions else positions
    fun positioned(fresh: Map<HudPart, HudPosition>): HudSettings =
        if (interfaceStyle == InterfaceStyle.MOD) copy(panelPositions = fresh) else copy(positions = fresh)

    companion object {
        const val MIN_SCALE = .5f
        const val MAX_SCALE = 3f

        fun validScale(value: Float): Float = if (value.isFinite()) value.coerceIn(MIN_SCALE, MAX_SCALE) else 1f

        fun migrate(mode: String?, numbers: String?, legacyVisible: Boolean?): HudSettings = HudSettings(
            spirituality = SpiritualityMode.entries.firstOrNull { it.name.equals(mode, true) }
                ?: if (legacyVisible == false) SpiritualityMode.OFF else SpiritualityMode.FLASK,
            numbers = SpiritualityNumbers.entries.firstOrNull { it.name.equals(numbers, true) }
                ?: SpiritualityNumbers.ON_CHANGE,
        )
    }
}
