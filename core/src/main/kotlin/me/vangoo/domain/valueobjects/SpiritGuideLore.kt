package me.vangoo.domain.valueobjects

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.services.SequenceScaler.ScalingStrategy
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Балансні числа Послідовності 6 шляху Смерті (Провідник духів).
 *
 * Тут лежить лише те, що або залежить від Послідовності, або спільне для кількох файлів
 * (ціна бачить і здібність, і меню; строк трупа — і реєстр, і Воскресіння). Одноразові
 * сталі без скейлу живуть у своїх класах.
 *
 * Посл. 6 — перша для цього тіру, тож усі базові числа задані саме для неї, а [growth]
 * лише піднімає їх для сильніших Послідовностей (на Посл. 6 множник рівно 1.0). Числа
 * слабших тірів — у [SpiritMediumLore], [GravediggerLore], [CorpseCollectorLore].
 */
object SpiritGuideLore {

    /** Послідовність, на якій тір з'являється; усі базові числа задані для неї. */
    private const val BASE_SEQUENCE = 6

    // --- Тіло провідника (PhysicalEnhancement, спільний identity "death_physique") ---
    const val PHYSIQUE_HP_BASE = 8

    // --- Труп на місці смерті (спільне: реєстр LingeringSouls + Воскресіння) ---
    /** Скільки труп лежить на місці смерті (сек). Душа висить 30 хв, тіло псується швидше. */
    const val CORPSE_TTL_SECONDS = 5 * 60

    // --- Мова мертвих (виривання душі) ---
    const val LANGUAGE_COST = 100
    private const val LANGUAGE_COOLDOWN_SECONDS = 20
    private const val LANGUAGE_COOLDOWN_FLOOR_SECONDS = 5
    private const val LANGUAGE_DURATION_SECONDS = 6
    private const val LANGUAGE_RANGE = 20.0
    private const val LANGUAGE_RANGE_CAP = 32.0

    // --- Воскресіння (труп -> слуга) ---
    const val RESURRECTION_COST = 150
    private const val RESURRECTION_COOLDOWN_SECONDS = 80
    private const val RESURRECTION_COOLDOWN_FLOOR_SECONDS = 20

    // --- Почет ---
    /**
     * Стеля почту. Свідоме відхилення від вікіних тисяч — стільки сутностей уб'є сервер.
     * Спільна для всіх трьох входів (Воскресіння / Підкорення / прикликання істот).
     */
    const val RETINUE_CAP = 10

    // --- Підкорення нежиті (площа) ---
    const val SUBJUGATION_COST = 150
    private const val SUBJUGATION_COOLDOWN_SECONDS = 45
    private const val SUBJUGATION_COOLDOWN_FLOOR_SECONDS = 20
    private const val SUBJUGATION_RADIUS = 12.0
    private const val SUBJUGATION_RADIUS_CAP = 24.0

    // --- Домовленість з духами Світу Духів: знайдених у Пеклі, ціна за приєднання до почту
    // (ціни фіксовані: сила істоти й є її ростом) ---
    const val LIVING_SHADOW_COST = 50
    const val SHADOW_PYTHON_COST = 60
    const val DEATH_KNIGHT_COST = 70
    const val LAKE_GODDESS_COST = 80
    private const val PACT_COOLDOWN_SECONDS = 30
    private const val PACT_COOLDOWN_FLOOR_SECONDS = 10

    // --- Обмін духом (щит від душевних ефектів ціною слуги) ---
    const val SPIRIT_SWAP_COST = 110

    /** Скільки слуг з'їдає обмін. */
    const val SPIRIT_SWAP_SERVANT_COST = 1
    private const val SPIRIT_SWAP_WARD_SECONDS = 30
    private const val SPIRIT_SWAP_COOLDOWN_SECONDS = 90
    private const val SPIRIT_SWAP_COOLDOWN_FLOOR_SECONDS = 40

    // --- Зліт (нежить підкидає провідника вгору) ---
    const val SOAR_COST = 15

    /** Антиспам, не сила — тому стала. */
    const val SOAR_COOLDOWN_SECONDS = 20

    // --- Бунт (D3): почет тікає біля сильнішого Беєндера Смерті ---
    const val REBELLION_RADIUS = 24.0
    private const val REBELLION_CHANCE_PER_LEVEL = 0.05

    /** Кулдаун Мови мертвих (сек): Посл. 6 = 70, коротшає WEAK, не нижче підлоги. */
    fun languageCooldownSeconds(sequence: Sequence): Int =
        shrink(LANGUAGE_COOLDOWN_SECONDS, LANGUAGE_COOLDOWN_FLOOR_SECONDS, sequence)

    /** Скільки душа лишається поза тілом (сек): Посл. 6 = 5, росте MODERATE. */
    fun languageDurationSeconds(sequence: Sequence): Int =
        (LANGUAGE_DURATION_SECONDS * growth(sequence, ScalingStrategy.MODERATE)).roundToInt()

    /** Дальність Мови мертвих (блоки): Посл. 6 = 20, росте WEAK до стелі 32. */
    fun languageRange(sequence: Sequence): Double =
        min(LANGUAGE_RANGE_CAP, LANGUAGE_RANGE * growth(sequence, ScalingStrategy.WEAK))

    /** Кулдаун Воскресіння (сек): Посл. 6 = 12, коротшає WEAK, не нижче підлоги. */
    fun resurrectionCooldownSeconds(sequence: Sequence): Int =
        shrink(RESURRECTION_COOLDOWN_SECONDS, RESURRECTION_COOLDOWN_FLOOR_SECONDS, sequence)

    /** Кулдаун Підкорення нежиті (сек): Посл. 6 = 45, коротшає WEAK, не нижче підлоги. */
    fun subjugationCooldownSeconds(sequence: Sequence): Int =
        shrink(SUBJUGATION_COOLDOWN_SECONDS, SUBJUGATION_COOLDOWN_FLOOR_SECONDS, sequence)

    /** Кулдаун Домовленості з духами (сек): Посл. 6 = 30, коротшає WEAK, не нижче підлоги. */
    fun pactCooldownSeconds(sequence: Sequence): Int =
        shrink(PACT_COOLDOWN_SECONDS, PACT_COOLDOWN_FLOOR_SECONDS, sequence)

    /** Радіус Підкорення (блоки): Посл. 6 = 12, росте WEAK до стелі 24. */
    fun subjugationRadius(sequence: Sequence): Double =
        min(SUBJUGATION_RADIUS_CAP, SUBJUGATION_RADIUS * growth(sequence, ScalingStrategy.WEAK))

    /** Скільки тримається щит обміну духом (сек): Посл. 6 = 30, росте MODERATE. */
    fun spiritSwapWardSeconds(sequence: Sequence): Int =
        (SPIRIT_SWAP_WARD_SECONDS * growth(sequence, ScalingStrategy.MODERATE)).roundToInt()

    /** Кулдаун обміну духом (сек): Посл. 6 = 90, коротшає WEAK, не нижче підлоги. */
    fun spiritSwapCooldownSeconds(sequence: Sequence): Int =
        shrink(SPIRIT_SWAP_COOLDOWN_SECONDS, SPIRIT_SWAP_COOLDOWN_FLOOR_SECONDS, sequence)

    /**
     * Шанс за секунду, що слуга зрадить провідника біля чужого Беєндера Смерті: 5% за кожну
     * Послідовність переваги суперника. Рівний чи слабший суперник почту не зачіпає (0).
     * Стелі немає й не треба: провідник — Посл. 6, тож розрив не буває більший за 6 (30%).
     */
    fun rebellionChancePerSecond(guide: Sequence, rival: Sequence): Double {
        val levelsStronger = guide.level - rival.level
        if (levelsStronger <= 0) {
            return 0.0
        }
        return REBELLION_CHANCE_PER_LEVEL * levelsStronger
    }

    /** Кулдаун, що коротшає WEAK від бази до підлоги. */
    private fun shrink(baseSeconds: Int, floorSeconds: Int, sequence: Sequence): Int {
        val seconds = baseSeconds / growth(sequence, ScalingStrategy.WEAK)
        return max(floorSeconds.toLong(), seconds.roundToLong()).toInt()
    }

    /** Наскільки сила на заданій Послідовності перевищує силу на Посл. 6 (там рівно 1.0). */
    private fun growth(sequence: Sequence, strategy: ScalingStrategy): Double =
        SequenceScaler.calculateMultiplier(sequence.level, strategy) /
            SequenceScaler.calculateMultiplier(BASE_SEQUENCE, strategy)
}
