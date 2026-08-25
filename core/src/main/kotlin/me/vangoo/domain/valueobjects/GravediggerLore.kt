package me.vangoo.domain.valueobjects

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.services.SequenceScaler.ScalingStrategy
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Балансні числа Послідовності 8 шляху Смерті (Могильник).
 *
 * Одне джерело правди для тіру: Спілкування з духами (вербування/прикликання почту й хват
 * духа) та Око Смерті (вузол — один посилений удар). Чиста математика — без платформи; ефекти
 * живуть у `pathways.death.abilities`. Стати самого духа (HP, урон, швидкість) тут НЕ
 * лежать — вони належать MythicMobs-паку (`mythic-pack/Mobs/death.yml`).
 *
 * Посл. 8 — перша для цього тіру, тож усі базові числа задані саме для неї, а
 * [growth] лише піднімає їх для сильніших Послідовностей (на Посл. 8 множник рівно 1.0).
 * Числа Посл. 9 живуть окремо, у [CorpseCollectorLore].
 */
object GravediggerLore {

    /** Послідовність, на якій тір з'являється; усі базові числа задані для неї. */
    private const val BASE_SEQUENCE = 8

    // --- Тіло могильника (PhysicalEnhancement, спільний identity "death_physique") ---
    /** База розрахунку бонусного HP; удвічі більше за Посл. 9 — stronger and more agile. */
    const val PHYSIQUE_HP_BASE = 6

    // --- Спілкування з духами: ціни ---
    /** Прикликати духа зі Світу Духів (нікого немає поблизу). */
    const val SPIRIT_SUMMON_COST = 75

    /** Домовитись із духом, що вже блукає поруч — дешевше за прикликання. */
    const val SPIRIT_RECRUIT_COST = 25

    private const val SPIRIT_COOLDOWN_SECONDS = 30
    private const val SPIRIT_COOLDOWN_FLOOR_SECONDS = 15L

    // --- Спілкування з духами: почет ---
    private const val SPIRIT_LIMIT = 2
    private const val SPIRIT_LIMIT_CAP = 6L
    private const val SPIRIT_RECRUIT_RADIUS = 16.0
    private const val SPIRIT_RECRUIT_RADIUS_CAP = 48.0

    /** Радіус кола, яким дух в'ється НАД головою власника — щоб не ловити його удари. */
    const val SPIRIT_FOLLOW_DISTANCE = 2.0

    /** Відстав далі — не біжить, а перескакує (інакше загубився б за стінами й водою). */
    const val SPIRIT_TELEPORT_DISTANCE = 32.0

    // --- Хват духа (реакція почту на удар власника) ---
    /** Скільки триває хват після удару; КОЖЕН новий удар поновлює відлік із нуля. */
    private const val GRASP_SECONDS = 10

    /**
     * Довжина «рук» духа по ГОРИЗОНТАЛІ: дух висить над головою жертви, руки тягнуться згори,
     * тож висота у відстань не рахується. Від Послідовності не залежить.
     */
    const val GRASP_REACH = 3.0

    /** Рівень Повільності при хваті (0 = I), тобто Повільність IV. Це сила хвату, не сила духа. */
    const val GRASP_SLOWNESS_AMPLIFIER = 3

    // --- Око Смерті (вузол) ---
    const val EYE_COST = 50
    private const val EYE_COOLDOWN_SECONDS = 14
    private const val EYE_COOLDOWN_FLOOR_SECONDS = 5L
    private const val EYE_RANGE = 24.0
    private const val EYE_RANGE_CAP = 64.0

    /** Надбавка до урону понад 1.0 по МЕРТВОМУ (нежить, дух, реймпейджер): Посл. 8 = +120%. */
    private const val EYE_DAMAGE_BONUS = 1.2
    private const val EYE_DAMAGE_MULTIPLIER_CAP = 3.5

    /** Вузол один на кастера — повторний каст переносить його на нову ціль. */
    const val MAX_CONCURRENT_NODES = 1

    /**
     * Технічна стеля життя вузла. Гравцеві він видається безстроковим (доки не витрачений),
     * але підписка на удар мусить мати TTL: без нього релогін лишав би висіти слухача назавжди.
     */
    const val NODE_TTL_SECONDS = 30 * 60

    /** Кулдаун Спілкування з духами (сек): Посл. 8 = 15, коротшає WEAK, не нижче підлоги. */
    fun spiritCooldownSeconds(sequence: Sequence): Int {
        val seconds = SPIRIT_COOLDOWN_SECONDS / growth(sequence, ScalingStrategy.WEAK)
        return max(SPIRIT_COOLDOWN_FLOOR_SECONDS, seconds.roundToLong()).toInt()
    }

    /** Скільки духів тримається водночас: Посл. 8 = 2, росте MODERATE до стелі 6. */
    fun spiritLimit(sequence: Sequence): Int {
        val limit = SPIRIT_LIMIT * growth(sequence, ScalingStrategy.MODERATE)
        return min(SPIRIT_LIMIT_CAP, limit.roundToLong()).toInt()
    }

    /** Радіус пошуку вільного духа для вербування (блоки): Посл. 8 = 16, росте WEAK до стелі 48. */
    fun spiritRecruitRadius(sequence: Sequence): Double = min(
        SPIRIT_RECRUIT_RADIUS_CAP,
        SPIRIT_RECRUIT_RADIUS * growth(sequence, ScalingStrategy.WEAK),
    )

    /** Скільки дух тримає одну ціль (сек): Посл. 8 = 6, росте MODERATE. */
    fun graspSeconds(sequence: Sequence): Int =
        (GRASP_SECONDS * growth(sequence, ScalingStrategy.MODERATE)).roundToInt()

    /** Кулдаун Ока Смерті (сек): Посл. 8 = 14, коротшає WEAK, не нижче підлоги. */
    fun eyeCooldownSeconds(sequence: Sequence): Int {
        val seconds = EYE_COOLDOWN_SECONDS / growth(sequence, ScalingStrategy.WEAK)
        return max(EYE_COOLDOWN_FLOOR_SECONDS, seconds.roundToLong()).toInt()
    }

    /** Дальність погляду, яким ставиться вузол (блоки): Посл. 8 = 24, росте WEAK до стелі 64. */
    fun eyeRange(sequence: Sequence): Double =
        min(EYE_RANGE_CAP, EYE_RANGE * growth(sequence, ScalingStrategy.WEAK))

    /**
     * Множник удару у вузол по МЕРТВОМУ (нежить, дух, реймпейджер): Посл. 8 = 2.2,
     * надбавка росте WEAK до стелі 3.5.
     */
    fun eyeDeadDamageMultiplier(sequence: Sequence): Double {
        val bonus = EYE_DAMAGE_BONUS * growth(sequence, ScalingStrategy.WEAK)
        return min(EYE_DAMAGE_MULTIPLIER_CAP, 1.0 + bonus)
    }

    /**
     * Множник удару по ЖИВОМУ: половина надбавки мертвого (Посл. 8 = 1.6). Виводиться з
     * [eyeDeadDamageMultiplier], щоб дві цифри не роз'їхались при зміні балансу.
     */
    fun eyeLivingDamageMultiplier(sequence: Sequence): Double =
        1.0 + (eyeDeadDamageMultiplier(sequence) - 1.0) / 2.0

    /** Наскільки сила на заданій Послідовності перевищує силу на Посл. 8 (там рівно 1.0). */
    private fun growth(sequence: Sequence, strategy: ScalingStrategy): Double =
        SequenceScaler.calculateMultiplier(sequence.level, strategy) /
            SequenceScaler.calculateMultiplier(BASE_SEQUENCE, strategy)
}
