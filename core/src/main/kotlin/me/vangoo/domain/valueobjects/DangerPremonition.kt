package me.vangoo.domain.valueobjects

import kotlin.math.max

/**
 * Балансні числа покращеної Інтуїції небезпеки (Seq 9 Провидця шляху Блазня).
 *
 * Усі значення зростають зі силою Beyonder'а (нижча послідовність = потужніше
 * передчуття). Чиста математика — без платформи, тестується юнітами. Ефекти
 * (партикли, glow, ривок) живуть у `pathways.fool.abilities.DangerIntuition`.
 */
object DangerPremonition {

    /** 0 (Seq 9) … 9 (Seq 0). */
    private fun power(sequence: Sequence): Int = 9 - sequence.level

    /**
     * Шанс автоматично ухилитися від удару, що був би смертельним.
     * Seq 9 ≈ 12%, зростає до стелі 40% (ніколи не абсолютний щит).
     */
    fun lethalDodgeChance(sequence: Sequence): Double =
        (0.12 + power(sequence) * 0.03).coerceIn(0.0, 0.40)

    /**
     * Внутрішній кулдаун між авто-ухиленнями (мс). Сильніший Beyonder
     * відновлює передчуття швидше: Seq 9 = 20с … не коротше 6с.
     */
    fun lethalDodgeCooldownMillis(sequence: Sequence): Long =
        max(6_000L, 20_000L - power(sequence) * 1_500L)

    /**
     * Шанс (за одну перевірку) показати натяк на наступну дію ворога,
     * за яким спостерігає Beyonder. Рідкісний прояв: Seq 9 ≈ 4% … стеля 25%.
     */
    fun actionPredictionChance(sequence: Sequence): Double =
        (0.04 + power(sequence) * 0.015).coerceIn(0.0, 0.25)
}
