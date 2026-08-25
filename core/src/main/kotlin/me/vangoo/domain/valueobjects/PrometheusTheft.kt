package me.vangoo.domain.valueobjects

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.services.SequenceScaler.ScalingStrategy
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Балансні числа Послідовності 6 шляху Помилки (Прометей).
 *
 * Одне джерело правди для Крадіжки сили, для Прометеєвого тіру Крадіжки матеріалів і
 * для тіру скарбів у Вищій спостережливості. Чиста математика — без платформи; ефекти
 * живуть у `pathways.error.abilities`.
 *
 * Ключове правило вікі: чим краще розумієш ціль, тим легше вкрасти саме те, що треба.
 * Модельовано через [successChance] — розблоковані рецепти шляху жертви піднімають
 * шанс, сильніша жертва його опускає.
 */
object PrometheusTheft {

    /** Послідовність, на якій тір з'являється; усі базові числа задані для неї. */
    private const val BASE_SEQUENCE = 6

    // --- Крадіжка сили ---
    const val POWER_THEFT_COST = 200
    const val POWER_THEFT_COOLDOWN_SECONDS = 200
    private const val POWER_THEFT_COOLDOWN_FLOOR_SECONDS = 120L
    const val POWER_THEFT_RANGE = 50.0

    /** Скільки вкрадена здібність живе у злодія (реальний час). */
    const val STOLEN_DURATION_MILLIS = 10L * 60_000L

    /** Скільки жертва не може кастувати вкрадену здібність (від моменту крадіжки). */
    const val SUPPRESSION_DURATION_MILLIS = 30L * 60_000L

    /** Одна вкрадена здібність за раз — власний слот, незалежний від `Analysis`. */
    const val MAX_CONCURRENT_STOLEN = 1

    // --- Шанс успіху ---
    private const val BASE_CHANCE = 0.40
    private const val CHANCE_PER_RECIPE = 0.03
    private const val MAX_RECIPE_BONUS = 0.25
    private const val CHANCE_PER_VICTIM_LEVEL = 0.08
    private const val MIN_CHANCE = 0.10
    private const val MAX_CHANCE = 0.95

    // --- Прометеїв тір успадкованих здібностей ---
    /** Дальність Крадіжки матеріалів на Seq 6 (проти 5 на Seq 8/7). */
    const val MATERIAL_THEFT_RANGE = 50.0

    /** Радіус пошуку полум'я для крадіжки вогню (вікі 3b.1) — куб по блоках, тож малий. */
    const val FIRE_THEFT_RADIUS = 10.0

    /** Скільки секунд вогнетривкості дає вкрадене полум'я. */
    const val FIRE_THEFT_RESISTANCE_SECONDS = 20

    /** Радіус чуття цінностей: контейнери, дропи, чужі інвентарі. */
    const val VALUABLES_RADIUS = 50.0

    /** Радіус сканування руди (менший — це кубічний прохід по блоках). */
    const val ORE_SCAN_RADIUS = 16.0

    /** Від скількох цінних одиниць схованка вважається багатою. */
    const val TREASURE_RICH = 10

    /** Від скількох — середньою; менше — дрібниця. */
    const val TREASURE_MEDIUM = 3

    // --- Стійкість розуму ---
    /** Скільки лишається від вхідної втрати глузду під Стійкістю розуму. */
    const val MENTAL_FORTITUDE_SANITY_MULTIPLIER = 0.5

    /** Раз на стільки секунд Стійкість розуму сама відновлює 1 глузду. */
    const val MENTAL_FORTITUDE_RECOVERY_SECONDS = 45

    /** Кулдаун Крадіжки сили (сек): Seq 6 = 300, коротшає MODERATE, не нижче підлоги. */
    fun powerTheftCooldownSeconds(sequence: Sequence): Int {
        val seconds = POWER_THEFT_COOLDOWN_SECONDS * relative(sequence, ScalingStrategy.MODERATE)
        return max(POWER_THEFT_COOLDOWN_FLOOR_SECONDS, seconds.roundToLong()).toInt()
    }

    /**
     * Шанс успішної крадіжки сили.
     *
     * @param unlockedVictimPathwayRecipes скільки рецептів шляху жертви злодій уже відкрив
     * @param victimSequence               Послідовність жертви
     * @return шанс у межах 0.10..0.95
     */
    fun successChance(unlockedVictimPathwayRecipes: Int, victimSequence: Sequence): Double {
        val knowledge = min(
            MAX_RECIPE_BONUS,
            max(0, unlockedVictimPathwayRecipes) * CHANCE_PER_RECIPE,
        )
        val levelsStronger = max(0, BASE_SEQUENCE - victimSequence.level)
        val resistance = levelsStronger * CHANCE_PER_VICTIM_LEVEL
        return (BASE_CHANCE + knowledge - resistance).coerceIn(MIN_CHANCE, MAX_CHANCE)
    }

    /** Чи покаже меню справжню назву здібності жертви (вікі 3a.2). */
    fun revealsAbilityNames(unlockedVictimPathwayRecipes: Int): Boolean =
        unlockedVictimPathwayRecipes > 0

    /** Скільки глузду перетікає від жертви до злодія в слоті Божевілля: Seq 6 = 3, росте WEAK. */
    fun corruptionTransfer(sequence: Sequence): Int =
        (3.0 / relative(sequence, ScalingStrategy.WEAK)).roundToInt()

    /** Відношення сили на Seq 6 до сили на заданій Послідовності (менше 1.0 для сильніших). */
    private fun relative(sequence: Sequence, strategy: ScalingStrategy): Double =
        SequenceScaler.calculateMultiplier(BASE_SEQUENCE, strategy) /
            SequenceScaler.calculateMultiplier(sequence.level, strategy)
}
