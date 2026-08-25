package me.vangoo.domain.valueobjects

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.services.SequenceScaler.ScalingStrategy
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Балансні числа Послідовності 7 шляху Смерті (Медіум Духів).
 *
 * Одне джерело правди для тіру: Сприйняття духів (пасивка), Прикликання духів (Ілюзорне око,
 * Морозна тінь, Земляний дух), Голос мертвих (дві гілки допиту) і Трупна личина. Чиста
 * математика — без платформи; ефекти живуть у `pathways.death.abilities`.
 *
 * Посл. 7 — перша для цього тіру, тож усі базові числа задані саме для неї, а [growth]
 * лише піднімає їх для сильніших Послідовностей (на Посл. 7 множник рівно 1.0). Числа
 * слабших тірів живуть окремо, у [GravediggerLore] і [CorpseCollectorLore].
 */
object SpiritMediumLore {

    /** Послідовність, на якій тір з'являється; усі базові числа задані для неї. */
    private const val BASE_SEQUENCE = 7

    // --- Тіло медіума (PhysicalEnhancement, спільний identity "death_physique") ---
    /** База бонусного HP. Свідомо мала надбавка понад Посл. 8 (6): вікі каже not significant. */
    const val PHYSIQUE_HP_BASE = 7

    // --- Сприйняття духів (постійна пасивка, безкоштовна) ---
    private const val PERCEPTION_RADIUS = 24.0
    private const val PERCEPTION_RADIUS_CAP = 48.0

    // --- Голос мертвих: душа, що затрималась на місці смерті ---
    /**
     * Скільки душа висить на місці смерті (сек). Від Послідовності НЕ залежить: це строк самої
     * душі, а не сила медіума. Не персиститься — рестарт її стирає.
     */
    const val SOUL_TTL_SECONDS = 30 * 60

    // --- Прикликання духів: спільна ціна не задається, кожен дух має свою ---

    // --- Ілюзорне око (розвідка) ---
    const val ILLUSORY_EYE_COST = 30
    private const val ILLUSORY_EYE_COOLDOWN_SECONDS = 40
    private const val ILLUSORY_EYE_COOLDOWN_FLOOR_SECONDS = 15
    private const val ILLUSORY_EYE_DURATION_SECONDS = 15
    private const val ILLUSORY_EYE_RADIUS = 48.0
    private const val ILLUSORY_EYE_RADIUS_CAP = 64.0

    // --- Морозна тінь (домен холоду + крижаний обладунок + коса) ---
    const val FROST_SHADOW_COST = 90
    private const val FROST_COOLDOWN_SECONDS = 50
    private const val FROST_COOLDOWN_FLOOR_SECONDS = 30
    private const val FROST_DURATION_SECONDS = 12

    /** Радіус домену росте MODERATE саме щоб дійти до вікіних 10-20 meter range. */
    private const val FROST_RADIUS = 10.0
    private const val FROST_RADIUS_CAP = 20.0
    private const val FROST_TICK_DAMAGE = 1.0
    private const val FROST_TICK_DAMAGE_CAP = 3.0

    /** Надбавка до урону морозної коси понад 1.0: Посл. 7 = +40%. */
    private const val SCYTHE_DAMAGE_BONUS = 0.4
    private const val SCYTHE_DAMAGE_MULTIPLIER_CAP = 2.5

    // --- Земляний дух (затягування в землю; коштує крові) ---
    const val EARTH_SPIRIT_COST = 80

    /** Ціна кров'ю в HP (2 = серце). Стала: кров — це кров, сильнішому медіуму її не менше. */
    const val EARTH_SPIRIT_BLOOD_HP = 4
    private const val EARTH_COOLDOWN_SECONDS = 55
    private const val EARTH_COOLDOWN_FLOOR_SECONDS = 20
    private const val EARTH_HOLD_SECONDS = 5

    /** Радіус болота навколо схопленого (блоки): сусіди грузнуть, але в землю не йдуть. */
    const val EARTH_SWAMP_RADIUS = 4.0
    private const val EARTH_RANGE = 12.0
    private const val EARTH_RANGE_CAP = 24.0

    // --- Голос мертвих ---
    const val VOICE_COST = 100
    private const val VOICE_DEAD_COOLDOWN_SECONDS = 500
    private const val VOICE_DEAD_COOLDOWN_FLOOR_SECONDS = 40
    private const val VOICE_DEAD_RADIUS = 8.0
    private const val VOICE_DEAD_RADIUS_CAP = 16.0
    private const val VOICE_LIVING_COOLDOWN_SECONDS = 60
    private const val VOICE_LIVING_COOLDOWN_FLOOR_SECONDS = 20
    private const val VOICE_LIVING_RANGE = 16.0
    private const val VOICE_LIVING_RANGE_CAP = 32.0

    /** Розсудок за кожну Послідовність переваги цілі; сильніша воля відбиває допит боляче. */
    private const val VOICE_BACKLASH_PER_LEVEL = 8
    private const val VOICE_BACKLASH_CAP = 40

    // --- Трупна личина (тогл) ---
    const val GUISE_ACTIVATION_COST = 10

    /** Періодична ціна тогла за секунду; від Послідовності не залежить. */
    const val GUISE_PERIODIC_COST = 10

    /** Антиспам перемикача, не сила — тому стала. */
    const val GUISE_COOLDOWN_SECONDS = 5

    /** Радіус Сприйняття духів (блоки): Посл. 7 = 24, росте WEAK до стелі 48. */
    fun perceptionRadius(sequence: Sequence): Double =
        min(PERCEPTION_RADIUS_CAP, PERCEPTION_RADIUS * growth(sequence, ScalingStrategy.WEAK))

    /** Кулдаун Ілюзорного ока (сек): Посл. 7 = 45, коротшає WEAK, не нижче підлоги. */
    fun illusoryEyeCooldownSeconds(sequence: Sequence): Int =
        shrink(ILLUSORY_EYE_COOLDOWN_SECONDS, ILLUSORY_EYE_COOLDOWN_FLOOR_SECONDS, sequence)

    /** Скільки око світить цілі (сек): Посл. 7 = 15, росте MODERATE. */
    fun illusoryEyeDurationSeconds(sequence: Sequence): Int =
        (ILLUSORY_EYE_DURATION_SECONDS * growth(sequence, ScalingStrategy.MODERATE)).roundToInt()

    /** Радіус, у якому око підсвічує живе (блоки): Посл. 7 = 48, росте WEAK до стелі 64. */
    fun illusoryEyeRadius(sequence: Sequence): Double =
        min(ILLUSORY_EYE_RADIUS_CAP, ILLUSORY_EYE_RADIUS * growth(sequence, ScalingStrategy.WEAK))

    /** Кулдаун Морозної тіні (сек): Посл. 7 = 90, коротшає WEAK, не нижче підлоги. */
    fun frostCooldownSeconds(sequence: Sequence): Int =
        shrink(FROST_COOLDOWN_SECONDS, FROST_COOLDOWN_FLOOR_SECONDS, sequence)

    /** Скільки домен тримається (сек): Посл. 7 = 12, росте MODERATE. */
    fun frostDurationSeconds(sequence: Sequence): Int =
        (FROST_DURATION_SECONDS * growth(sequence, ScalingStrategy.MODERATE)).roundToInt()

    /** Радіус мороженого домену (блоки): Посл. 7 = 10, росте MODERATE до вікіної стелі 20. */
    fun frostRadius(sequence: Sequence): Double =
        min(FROST_RADIUS_CAP, FROST_RADIUS * growth(sequence, ScalingStrategy.MODERATE))

    /** Урон холоду за секунду в домені: Посл. 7 = 1.0 (пів серця), росте WEAK до стелі 3.0. */
    fun frostTickDamage(sequence: Sequence): Double =
        min(FROST_TICK_DAMAGE_CAP, FROST_TICK_DAMAGE * growth(sequence, ScalingStrategy.WEAK))

    /** Множник ближнього удару морозною косою: Посл. 7 = 1.4, надбавка росте WEAK до стелі 2.5. */
    fun scytheDamageMultiplier(sequence: Sequence): Double {
        val bonus = SCYTHE_DAMAGE_BONUS * growth(sequence, ScalingStrategy.WEAK)
        return min(SCYTHE_DAMAGE_MULTIPLIER_CAP, 1.0 + bonus)
    }

    /** Кулдаун Земляного духа (сек): Посл. 7 = 60, коротшає WEAK, не нижче підлоги. */
    fun earthCooldownSeconds(sequence: Sequence): Int =
        shrink(EARTH_COOLDOWN_SECONDS, EARTH_COOLDOWN_FLOOR_SECONDS, sequence)

    /** Скільки ціль тримається в землі (сек): Посл. 7 = 5, росте MODERATE. */
    fun earthHoldSeconds(sequence: Sequence): Int =
        (EARTH_HOLD_SECONDS * growth(sequence, ScalingStrategy.MODERATE)).roundToInt()

    /** Дальність прицілу Земляного духа (блоки): Посл. 7 = 12, росте WEAK до стелі 24. */
    fun earthRange(sequence: Sequence): Double =
        min(EARTH_RANGE_CAP, EARTH_RANGE * growth(sequence, ScalingStrategy.WEAK))

    /** Кулдаун допиту мертвого (сек): Посл. 7 = 120, коротшає WEAK, не нижче підлоги. */
    fun voiceDeadCooldownSeconds(sequence: Sequence): Int =
        shrink(VOICE_DEAD_COOLDOWN_SECONDS, VOICE_DEAD_COOLDOWN_FLOOR_SECONDS, sequence)

    /** Радіус пошуку душі навколо медіума (блоки): Посл. 7 = 8, росте WEAK до стелі 16. */
    fun voiceDeadRadius(sequence: Sequence): Double =
        min(VOICE_DEAD_RADIUS_CAP, VOICE_DEAD_RADIUS * growth(sequence, ScalingStrategy.WEAK))

    /** Кулдаун допиту живого (сек): Посл. 7 = 60, коротшає WEAK, не нижче підлоги. */
    fun voiceLivingCooldownSeconds(sequence: Sequence): Int =
        shrink(VOICE_LIVING_COOLDOWN_SECONDS, VOICE_LIVING_COOLDOWN_FLOOR_SECONDS, sequence)

    /** Дальність допиту живого (блоки): Посл. 7 = 16, росте WEAK до стелі 32. */
    fun voiceLivingRange(sequence: Sequence): Double =
        min(VOICE_LIVING_RANGE_CAP, VOICE_LIVING_RANGE * growth(sequence, ScalingStrategy.WEAK))

    /**
     * Втрата розсудку, коли живу ціль допитати не вдалось: 8 за кожну Послідовність, на яку
     * ціль сильніша за медіума, до стелі 40. Рівна чи слабша ціль відкату не дає (0).
     */
    fun voiceBacklashSanity(caster: Sequence, target: Sequence): Int {
        val levelsStronger = caster.level - target.level
        if (levelsStronger <= 0) {
            return 0
        }
        return min(VOICE_BACKLASH_CAP, VOICE_BACKLASH_PER_LEVEL * levelsStronger)
    }

    /** Кулдаун, що коротшає WEAK від бази до підлоги. */
    private fun shrink(baseSeconds: Int, floorSeconds: Int, sequence: Sequence): Int {
        val seconds = baseSeconds / growth(sequence, ScalingStrategy.WEAK)
        return max(floorSeconds.toLong(), seconds.roundToLong()).toInt()
    }

    /** Наскільки сила на заданій Послідовності перевищує силу на Посл. 7 (там рівно 1.0). */
    private fun growth(sequence: Sequence, strategy: ScalingStrategy): Double =
        SequenceScaler.calculateMultiplier(sequence.level, strategy) /
            SequenceScaler.calculateMultiplier(BASE_SEQUENCE, strategy)
}
