package me.vangoo.domain.valueobjects

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Балансні числа Послідовності 5 шляху Помилки (Крадій снів).
 *
 * Одне джерело правди для Концептуальної крадіжки (шість режимів), для Посл-5 тіру
 * Крадіжки сили, для пасивного розвіювання ілюзій і для Личини. Чиста математика — без
 * платформи; ефекти живуть у `pathways.error.abilities`.
 *
 * Числа Посл. 6 лишаються в [PrometheusTheft] і сюди не переїжджають: там — Прометей,
 * тут — Крадій снів. Спільне лише те, що обидва тіри пишуться в один ledger.
 */
object DreamStealerTheft {

    /** Послідовність, на якій тір з'являється; усі базові числа задані для неї. */
    private const val BASE_SEQUENCE = 5

    // --- Спільне для всіх режимів Концептуальної крадіжки ---
    /** Вікі: Theft ... within 80 meters range. */
    const val CONCEPTUAL_RANGE = 80.0
    const val CONCEPTUAL_COST = 200
    const val CONCEPTUAL_COOLDOWN_SECONDS = 5

    /** Один тривалий злам за раз — той самий слот, що й у Прометея. */
    const val MAX_CONCURRENT_STOLEN = 1

    /** Скільки глузду коштує вдала концептуальна крадіжка. */
    const val CONCEPTUAL_CORRUPTION = 1

    // --- Крадіжка сили, тір Посл. 5 (вікі 3b.5) ---
    /** 50 м на Посл. 6 -> 80 м тут. */
    const val POWER_THEFT_RANGE = CONCEPTUAL_RANGE

    /** can be used for half an hour — проти 10 хв Прометея. */
    const val POWER_STOLEN_DURATION_MILLIS = 30L * 60_000L

    /** Жертва не кличе вкрадене вдвічі довше, ніж злодій ним володіє. */
    const val POWER_SUPPRESSION_DURATION_MILLIS = 60L * 60_000L

    /**
     * If ... has not yet used the Stolen power ... it can be kept for a week — тиждень
     * стиснуто до 6 год реального часу: слот увесь цей час зайнятий.
     */
    const val POWER_UNUSED_HOLD_MILLIS = 6L * 60L * 60_000L

    // --- Режим «Сон» ---
    /** Скільки злодій тримає чужий сон (і скільки жертва не може лягти в ліжко). */
    const val DREAM_HOLD_MILLIS = 30L * 60_000L

    /** Скільки триває чужий сон, влитий у третю особу (вікі: infuse scenes within the Dream). */
    const val DREAM_INFUSION_SECONDS = 15

    // --- Режим «Загальні здатності» ---
    /** Ходити / літати / кастувати — забрано на цей строк. */
    const val GENERAL_ABILITY_SECONDS = 20

    // --- Режим «Атака» ---
    /** Вікно, у якому перший вхідний удар поглинається цілком. */
    const val ATTACK_WINDOW_SECONDS = 5

    /** Скільки заряд живе після поглинання, доки злодій не вдарить. */
    const val ATTACK_CHARGE_SECONDS = 10

    /** Стеля поверненої шкоди — щоб зал п'ятдесяткою не поверталась. */
    const val ATTACK_CHARGE_CAP = 20.0

    // --- Режим «Серце» ---
    /** 4 серця = 8.0 одиниць максимального здоров'я, перенесених від жертви до злодія. */
    const val HEART_HEALTH_TRANSFER = 8.0
    const val HEART_DURATION_SECONDS = 60

    /** Жертві лишається щонайменше серце: крадіжка — не вбивство. */
    private const val HEART_MIN_REMAINING = 2.0

    // --- Режим «Ідеал» ---
    /** Частка ПОТОЧНОЇ духовності жертви, що перетікає до злодія. */
    private const val IDEAL_SPIRITUALITY_SHARE = 0.15

    /** Скільки триває апатія (без спринту, без досвіду, слабкість). */
    const val IDEAL_APATHY_SECONDS = 60

    // --- Розшифрування ілюзій, тір Посл. 5 ---
    /** Через скільки ворожа ілюзія на самому Крадії снів сама розсипається. */
    const val ILLUSION_DISPEL_SECONDS = 2

    // --- Личина ---
    const val DISGUISE_COST = 140
    const val DISGUISE_COOLDOWN_SECONDS = 600
    const val DISGUISE_DURATION_MILLIS = 10L * 60_000L

    /** Личина коштує глузду дорожче за звичайну крадіжку — це обряд, не рух руки. */
    const val DISGUISE_CORRUPTION = 3

    /** Множник шкоди від вкраденої відповіді на молитву, доки личина тримається. */
    const val DISGUISE_DAMAGE_MULTIPLIER = 1.25

    /** Скільки триває викриття: провалений обряд лишає злодія без здібностей. */
    const val DISGUISE_EXPOSURE_SECONDS = 500

    /** Скільки запалених свічок треба вівтарю Личини (як і в Ритуальній магії). */
    const val DISGUISE_CANDLES_REQUIRED = 3

    private const val DISGUISE_BASE_CHANCE = 0.90
    private const val DISGUISE_CHANCE_PER_ACCESS = 0.06
    private const val DISGUISE_MIN_CHANCE = 0.25

    // --- Шанс успіху ---
    private const val BASE_CHANCE = 0.50
    private const val CHANCE_PER_RECIPE = 0.03
    private const val MAX_RECIPE_BONUS = 0.25
    private const val CHANCE_PER_VICTIM_LEVEL = 0.08
    private const val MIN_CHANCE = 0.10
    private const val MAX_CHANCE = 0.95

    /**
     * Шанс успішної концептуальної крадіжки — та сама форма, що в Прометея, але з базою
     * Посл. 5: знання шляху жертви піднімає, сильніша жертва опускає.
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

    /**
     * Скільки духовності справді перетікає в режимі «Ідеал».
     *
     * Два затиски. Перший — беремо 15 % від ПОТОЧНОЇ духовності жертви, а не від
     * максимуму: висушена жертва не має бути кращою ціллю за повну. Другий — не більше,
     * ніж злодію бракує до власного максимуму: надлишок згорає, тож союзника не можна
     * доїти як батарейку.
     *
     * @param victimCurrentSpirituality поточна духовність жертви
     * @param thiefMissingSpirituality  скільки злодію бракує до максимуму
     * @return перенесена кількість, ніколи не від'ємна
     */
    fun idealSpiritualityTransfer(
        victimCurrentSpirituality: Int,
        thiefMissingSpirituality: Int,
    ): Int {
        val taken = (max(0, victimCurrentSpirituality) * IDEAL_SPIRITUALITY_SHARE).roundToInt()
        return min(taken, max(0, thiefMissingSpirituality))
    }

    /**
     * Скільки максимального здоров'я справді переходить у режимі «Серце».
     *
     * У слабкої жертви беруть менше: після крадіжки їй лишається хоча б одне серце,
     * інакше режим був би просто вбивством із затримкою.
     *
     * @param victimMaxHealth поточний максимум здоров'я жертви
     * @return перенесена кількість, ніколи не від'ємна
     */
    fun heartTransfer(victimMaxHealth: Double): Double =
        max(0.0, min(HEART_HEALTH_TRANSFER, victimMaxHealth - HEART_MIN_REMAINING))

    /**
     * Шанс, що Личина витримає. Вікі: the higher the intelligence of the corresponding True
     * Deity, the greater the probability for the Disguise to fail — божеств у грі немає, тож
     * за розум править вага самої церкви: скільки шляхів вона тримає під собою.
     *
     * @param pathwayAccesses скільки шляхів у домені церкви (`Institution.accesses()`)
     * @return шанс у межах 0.25..0.90
     */
    fun disguiseSuccessChance(pathwayAccesses: Int): Double {
        val penalty = max(0, pathwayAccesses) * DISGUISE_CHANCE_PER_ACCESS
        return (DISGUISE_BASE_CHANCE - penalty).coerceIn(DISGUISE_MIN_CHANCE, DISGUISE_BASE_CHANCE)
    }
}
