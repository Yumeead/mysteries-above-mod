package me.vangoo.domain.valueobjects

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.services.SequenceScaler.ScalingStrategy
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Балансні числа Послідовності 9 шляху Смерті (Трупозбирач).
 *
 * Одне джерело правди для всіх п'яти здібностей тіру: Знання (Нежить) — мітка слабкого
 * місця й розтин трупа, Духовний зір, Бачення духів, Стійкість до трупних аур. Чиста
 * математика — без платформи; ефекти живуть у `pathways.death.abilities`.
 *
 * Посл. 9 — найслабша, тож усі базові числа задані саме для неї, а [growth] лише
 * піднімає їх для сильніших Послідовностей (на Посл. 9 множник рівно 1.0).
 */
object CorpseCollectorLore {

    /** Послідовність, на якій тір з'являється; усі базові числа задані для неї. */
    private const val BASE_SEQUENCE = 9

    // --- Тіло трупозбирача (PhysicalEnhancement) ---
    /** База розрахунку бонусного HP; potion-ефектів тір не дає — тіло холодне, не швидке. */
    const val PHYSIQUE_HP_BASE = 3

    // --- Знання (Нежить): спільні для обох режимів каста ---
    const val KNOWLEDGE_COST = 40
    private const val KNOWLEDGE_COOLDOWN_SECONDS = 12
    private const val KNOWLEDGE_COOLDOWN_FLOOR_SECONDS = 4L

    // --- Режим «мітка слабкого місця» (лише по нежиті) ---
    private const val MARK_RANGE = 20.0
    private const val MARK_RANGE_CAP = 60.0
    private const val MARK_DURATION_SECONDS = 20

    /** Надбавка до урону понад 1.0: Посл. 9 = +35%. */
    private const val MARK_DAMAGE_BONUS = 0.35
    private const val MARK_DAMAGE_MULTIPLIER_CAP = 2.5

    /** Мітка одна на кастера — повторний каст переносить її на нову ціль. */
    const val MAX_CONCURRENT_MARKS = 1

    // --- Режим «розтин трупа» (DEATH-події з CoreProtect) ---
    private const val AUTOPSY_RADIUS = 10.0
    private const val AUTOPSY_RADIUS_CAP = 40.0
    private const val AUTOPSY_WINDOW_SECONDS = 30 * 60
    private const val AUTOPSY_MAX_RECORDS = 5

    // --- Духовний зір (тогл) ---
    private const val SPIRIT_VISION_RANGE = 20.0
    private const val SPIRIT_VISION_RANGE_CAP = 60.0

    /** Періодична ціна тогла за секунду; від Послідовності не залежить. */
    const val SPIRIT_VISION_PERIODIC_COST = 3

    // --- Бачення духів (постійне, безкоштовне) ---
    private const val DEATH_SIGHT_RANGE = 16.0
    private const val DEATH_SIGHT_RANGE_CAP = 48.0

    // --- Похмура присутність ---
    /**
     * Скільки секунд нежить пам'ятає, що трупозбирач ударив першим. Поки пам'ятає — агриться
     * як на будь-кого; коли забуде, похмура присутність знову робить його своїм.
     * Від Послідовності не залежить: це правило поведінки, не сила.
     */
    const val PROVOKE_MEMORY_SECONDS = 10
    private const val GLOOM_RADIUS = 24.0
    private const val GLOOM_RADIUS_CAP = 64.0

    // --- Стійкість до трупних аур ---
    /** У скільки разів швидше згасають Тління й Їдкість: Посл. 9 = вдвічі. */
    private const val DECAY_ACCELERATION = 2.0

    /** Кулдаун Знання (сек): Посл. 9 = 12, коротшає WEAK, не нижче підлоги. */
    fun knowledgeCooldownSeconds(sequence: Sequence): Int {
        val seconds = KNOWLEDGE_COOLDOWN_SECONDS / growth(sequence, ScalingStrategy.WEAK)
        return max(KNOWLEDGE_COOLDOWN_FLOOR_SECONDS, seconds.roundToLong()).toInt()
    }

    /** Дальність прицілу мітки (блоки): Посл. 9 = 20, росте WEAK до стелі 60. */
    fun markRange(sequence: Sequence): Double =
        min(MARK_RANGE_CAP, MARK_RANGE * growth(sequence, ScalingStrategy.WEAK))

    /** Скільки мітка тримається (сек): Посл. 9 = 20, росте MODERATE. */
    fun markDurationSeconds(sequence: Sequence): Int =
        (MARK_DURATION_SECONDS * growth(sequence, ScalingStrategy.MODERATE)).roundToInt()

    /**
     * Множник урону кастера по міченій нежиті: Посл. 9 = 1.35, надбавка росте WEAK
     * до стелі 2.5. По не-нежиті мітка не ставиться взагалі — множника тут немає.
     */
    fun markDamageMultiplier(sequence: Sequence): Double {
        val bonus = MARK_DAMAGE_BONUS * growth(sequence, ScalingStrategy.WEAK)
        return min(MARK_DAMAGE_MULTIPLIER_CAP, 1.0 + bonus)
    }

    /** Радіус читання місця смерті (блоки): Посл. 9 = 10, росте WEAK до стелі 40. */
    fun autopsyRadius(sequence: Sequence): Double =
        min(AUTOPSY_RADIUS_CAP, AUTOPSY_RADIUS * growth(sequence, ScalingStrategy.WEAK))

    /** Як давно могла статись смерть, щоб слід іще читався (сек): Посл. 9 = 30 хв, росте MODERATE. */
    fun autopsyWindowSeconds(sequence: Sequence): Int =
        (AUTOPSY_WINDOW_SECONDS * growth(sequence, ScalingStrategy.MODERATE)).roundToInt()

    /** Скільки смертей показує один розтин: Посл. 9 = 5, росте WEAK. */
    fun autopsyMaxRecords(sequence: Sequence): Int =
        (AUTOPSY_MAX_RECORDS * growth(sequence, ScalingStrategy.WEAK)).roundToInt()

    /**
     * Радіус, у якому похмура присутність змушує нежить забути вже взяту ціль (блоки):
     * Посл. 9 = 24, росте WEAK до стелі 64.
     */
    fun gloomRadius(sequence: Sequence): Double =
        min(GLOOM_RADIUS_CAP, GLOOM_RADIUS * growth(sequence, ScalingStrategy.WEAK))

    /** Радіус Духовного зору (блоки): Посл. 9 = 20, росте WEAK до стелі 60. */
    fun spiritVisionRange(sequence: Sequence): Double =
        min(SPIRIT_VISION_RANGE_CAP, SPIRIT_VISION_RANGE * growth(sequence, ScalingStrategy.WEAK))

    /** Радіус постійного Бачення духів (блоки): Посл. 9 = 16, росте WEAK до стелі 48. */
    fun deathSightRange(sequence: Sequence): Double =
        min(DEATH_SIGHT_RANGE_CAP, DEATH_SIGHT_RANGE * growth(sequence, ScalingStrategy.WEAK))

    /**
     * У скільки разів швидше згасають Тління (Wither) й Їдкість (Poison) на кастері:
     * Посл. 9 = 2.0, росте WEAK. Холод (Freezing) до цієї математики не входить —
     * імунітет до нього абсолютний з першої ж Послідовності.
     */
    fun decayAcceleration(sequence: Sequence): Double =
        DECAY_ACCELERATION * growth(sequence, ScalingStrategy.WEAK)

    /**
     * Скільки зайвих тіків Тління/Їдкості з'їдається за кожну секунду носіння ефекту.
     * Прискорення 2.0 означає «за секунду згорає дві» — тобто мінус ще 20 тіків понад
     * ванільний хід годинника.
     */
    fun decayExtraTicksPerSecond(sequence: Sequence): Int =
        (20 * (decayAcceleration(sequence) - 1.0)).roundToInt()

    /** Наскільки сила на заданій Послідовності перевищує силу на Посл. 9 (там рівно 1.0). */
    private fun growth(sequence: Sequence, strategy: ScalingStrategy): Double =
        SequenceScaler.calculateMultiplier(sequence.level, strategy) /
            SequenceScaler.calculateMultiplier(BASE_SEQUENCE, strategy)
}
