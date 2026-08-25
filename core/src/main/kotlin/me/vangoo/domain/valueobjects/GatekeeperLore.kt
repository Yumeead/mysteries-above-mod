package me.vangoo.domain.valueobjects

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.services.SequenceScaler.ScalingStrategy
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Балансні числа Послідовності 5 шляху Смерті (Воротар).
 *
 * Посл. 5 — перша для цього тіру; числа слабших тірів живуть у [SpiritGuideLore],
 * [SpiritMediumLore], [GravediggerLore], [CorpseCollectorLore]. Клас наповнюється по мірі
 * мілстоунів плану — числа й [growth] додаються разом із першою здібністю, якій вони справді
 * потрібні (на Посл. 5 множник 1.0).
 */
object GatekeeperLore {

    /** Послідовність, на якій тір з'являється; усі базові числа задані для неї. */
    private const val BASE_SEQUENCE = 5

    // --- Тіло воротаря (PhysicalEnhancement, спільний identity "death_physique") ---
    const val PHYSIQUE_HP_BASE = 10

    // --- Крок Воротаря (ривок уперед) ---
    const val STEP_COST = 20
    private const val STEP_COOLDOWN_SECONDS = 8
    private const val STEP_COOLDOWN_FLOOR_SECONDS = 4
    private const val STEP_DISTANCE = 8.0
    private const val STEP_DISTANCE_CAP = 12.0
    private const val STEP_IMPACT_DAMAGE = 5

    /** Кулдаун Кроку (сек): Посл. 5 = 8, коротшає WEAK, не нижче підлоги. */
    fun stepCooldownSeconds(sequence: Sequence): Int =
        shrink(STEP_COOLDOWN_SECONDS, STEP_COOLDOWN_FLOOR_SECONDS, sequence)

    /** Дальність ривка (блоки): Посл. 5 = 8, росте WEAK до стелі 12. */
    fun stepDistance(sequence: Sequence): Double =
        min(STEP_DISTANCE_CAP, STEP_DISTANCE * growth(sequence, ScalingStrategy.WEAK))

    /** Шкода при приземленні: Посл. 5 = 5, росте MODERATE. */
    fun stepImpactDamage(sequence: Sequence): Int =
        (STEP_IMPACT_DAMAGE * growth(sequence, ScalingStrategy.MODERATE)).roundToInt()

    // --- Двері в Загробний Світ (спільні ціна й кулдаун на всі 4 режими) ---
    // Ability.getSpiritualityCost()/getCooldown(Sequence) не бачать ні кастера, ні поточного
    // режиму — фреймворк фізично не годен дати чотирьом режимам чотири різні ціни/кулдауни
    // без ручного дублювання AbilityResourceConsumer. Той самий компроміс уже прийнятий
    // Вердиктом (Justiciar, Посл. 6): один каст = одна ціна незалежно від обраного режиму.
    // Числа беремо від DRAG — найважчого й першого реалізованого режиму.
    const val DOOR_COST = 200
    private const val DOOR_COOLDOWN_SECONDS = 40
    private const val DOOR_COOLDOWN_FLOOR_SECONDS = 10

    // --- Дальність, на яку можна поставити двері (дивлячись у точку) — спільна для всіх
    // чотирьох режимів, не лише DRAG: жоден режим більше не бере ціль-сутність, кожен
    // натомість ставить браму в точці, куди дивиться гравець. ---
    private const val DOOR_PLACEMENT_RANGE = 20.0
    private const val DOOR_PLACEMENT_RANGE_CAP = 32.0

    // --- DRAG: радіус всмоктування біля брами, шкода, поріг страти ---
    private const val DRAG_GATE_AOE_RADIUS = 8.0
    private const val DRAG_GATE_AOE_RADIUS_CAP = 14.0
    private const val DRAG_DAMAGE = 8

    /** Поріг страти — фіксований, не росте від Послідовності. */
    const val DRAG_EXECUTE_HP_FRACTION = 0.25

    /** Тління на цілі (сек) — темп ефекту, не сила, тому стала. */
    const val DRAG_WITHER_SECONDS = 4

    /** Повільність біля брами (сек) — та сама причина сталості. */
    const val DRAG_SLOWNESS_SECONDS = 3

    /** Кулдаун Дверей (сек): Посл. 5 = 60, коротшає WEAK, не нижче підлоги. */
    fun doorCooldownSeconds(sequence: Sequence): Int =
        shrink(DOOR_COOLDOWN_SECONDS, DOOR_COOLDOWN_FLOOR_SECONDS, sequence)

    /** Дальність розміщення брами (блоки): Посл. 5 = 20, росте WEAK до стелі 32. */
    fun doorPlacementRange(sequence: Sequence): Double =
        min(DOOR_PLACEMENT_RANGE_CAP, DOOR_PLACEMENT_RANGE * growth(sequence, ScalingStrategy.WEAK))

    /** Радіус всмоктування довкола брами (блоки): Посл. 5 = 8, росте WEAK до стелі 14. */
    fun dragGateAoeRadius(sequence: Sequence): Double =
        min(DRAG_GATE_AOE_RADIUS_CAP, DRAG_GATE_AOE_RADIUS * growth(sequence, ScalingStrategy.WEAK))

    /** Шкода DRAG по головній цілі: Посл. 5 = 8, росте MODERATE. */
    fun dragDamage(sequence: Sequence): Int =
        (DRAG_DAMAGE * growth(sequence, ScalingStrategy.MODERATE)).roundToInt()

    // --- PURGE: радіус очищення хмар/ефектів/вогню довкола кастера ---
    private const val PURGE_RADIUS = 10.0
    private const val PURGE_RADIUS_CAP = 18.0

    /** Радіус Очищення (блоки): Посл. 5 = 10, росте WEAK до стелі 18. */
    fun purgeRadius(sequence: Sequence): Double =
        min(PURGE_RADIUS_CAP, PURGE_RADIUS * growth(sequence, ScalingStrategy.WEAK))

    // --- BIND: радіус скутих рук довкола кастера, тривалість і сила ефектів (фіксовані) ---
    private const val BIND_RADIUS = 6.0
    private const val BIND_RADIUS_CAP = 11.0

    /** Темп контролю, не сила — фіксовано, як DRAG_SLOWNESS_SECONDS. */
    const val BIND_DURATION_SECONDS = 4

    /** Рівень-індекс: 4 = Повільність V. */
    const val BIND_SLOWNESS_AMPLIFIER = 4

    /** Радіус Скутих рук (блоки): Посл. 5 = 6, росте WEAK до стелі 11. */
    fun bindRadius(sequence: Sequence): Double =
        min(BIND_RADIUS_CAP, BIND_RADIUS * growth(sequence, ScalingStrategy.WEAK))

    // --- DESCEND: радіус/тривалість зони ростуть; надбавка нежиті й лікування — фіксовані
    // числа самого дизайну, не залежать від Послідовності ---
    private const val DESCEND_RADIUS = 12.0
    private const val DESCEND_RADIUS_CAP = 20.0
    private const val DESCEND_DURATION_SECONDS = 12
    private const val DESCEND_DURATION_CAP_SECONDS = 20L

    /** Надбавка до удару власної нежиті всередині зони: +20%. */
    const val DESCEND_UNDEAD_DAMAGE_BONUS = 0.20

    /** Лікування власника за секунду, поки він у зоні: 1 HP/s. */
    const val DESCEND_OWNER_HEAL_PER_SECOND = 1.0

    /** Радіус зони Сходження (блоки): Посл. 5 = 12, росте WEAK до стелі 20. */
    fun descendRadius(sequence: Sequence): Double =
        min(DESCEND_RADIUS_CAP, DESCEND_RADIUS * growth(sequence, ScalingStrategy.WEAK))

    /** Тривалість зони Сходження (сек): Посл. 5 = 12, росте MODERATE до стелі 20. */
    fun descendDurationSeconds(sequence: Sequence): Int = min(
        DESCEND_DURATION_CAP_SECONDS,
        (DESCEND_DURATION_SECONDS * growth(sequence, ScalingStrategy.MODERATE)).roundToLong(),
    ).toInt()

    // --- Внутрішній Загробний Світ: доплата за окупанта — база-найдешевший + різниця вручну,
    // той самий прийом, що SpiritPact.pay(); кулдаун касту спільний на всіх шістьох.
    // Поглинання/виселення — безкоштовні й без кулдауну: єдиний слот окупанта сам собою не
    // дає їх спамити. ---
    const val UNDERWORLD_CAST_BASE_COST = 300
    private const val UNDERWORLD_CAST_COOLDOWN_SECONDS = 100
    private const val UNDERWORLD_CAST_COOLDOWN_FLOOR_SECONDS = 30

    const val DEATH_KNIGHT_CAST_COST = 40
    const val SHADOW_PYTHON_CAST_COST = 45
    const val LIVING_SHADOW_CAST_COST = 35
    const val LAKE_GODDESS_CAST_COST = 50
    const val WANDERING_SPIRIT_CAST_COST = 30
    const val RESURRECTED_SERVANT_CAST_COST = 30

    /** -2…-8 max HP залежно від сили окупанта; від Послідовності не залежить. */
    const val DEATH_KNIGHT_EROSION_HP = 5.0
    const val SHADOW_PYTHON_EROSION_HP = 5.0
    const val LIVING_SHADOW_EROSION_HP = 3.0
    const val LAKE_GODDESS_EROSION_HP = 8.0
    const val WANDERING_SPIRIT_EROSION_HP = 2.0
    const val RESURRECTED_SERVANT_EROSION_HP = 3.0

    /** Дренаж розсудку, поки окупант живе всередині: 1 щоразу за цей період. */
    const val UNDERWORLD_SANITY_DRAIN_PERIOD_SECONDS = 15
    const val UNDERWORLD_SANITY_DRAIN_AMOUNT = 1

    /** Кулдаун касту сили окупанта (сек): Посл. 5 = 30, коротшає WEAK, не нижче підлоги. */
    fun underworldCastCooldownSeconds(sequence: Sequence): Int =
        shrink(UNDERWORLD_CAST_COOLDOWN_SECONDS, UNDERWORLD_CAST_COOLDOWN_FLOOR_SECONDS, sequence)

    // --- Четверо нових духів Світу Духів — ціна ДОМОВЛЕНОСТІ (SpiritPact, почет), окрема від
    // ціни КАСТУ окупантом вище. Посланець і Морська Примара — бойові активи (дорожче за
    // DEATH_KNIGHT_COST=70); Гній і Бліда Дівчинка — пасивні, дешевші за LIVING_SHADOW_COST=50
    // (SpiritGuideLore). Точних чисел вікі не дає — інтерпретація. ---
    const val DEATH_ENVOY_RECRUIT_COST = 90
    const val PUS_OF_MAN_RECRUIT_COST = 60
    const val PALE_GIRL_RECRUIT_COST = 60
    const val SEA_BEASTS_RECRUIT_COST = 85

    // --- Ті самі четверо як окупанти ВЗС: castCost 0 = пасивний (діє сама сесія,
    // performExecution на каст не заходить), інакше — з design table плану (60/55). ---
    const val DEATH_ENVOY_CAST_COST = 60
    const val SEA_BEASTS_CAST_COST = 55
    const val PUS_OF_MAN_CAST_COST = 0
    const val PALE_GIRL_CAST_COST = 0

    const val DEATH_ENVOY_EROSION_HP = 7.0
    const val SEA_BEASTS_EROSION_HP = 6.0
    const val PUS_OF_MAN_EROSION_HP = 4.0
    const val PALE_GIRL_EROSION_HP = 4.0

    // --- Гній Людини: рятує від смертельного удару раз на 5 хв. ---
    const val PUS_OF_MAN_WARD_COOLDOWN_SECONDS = 5 * 60
    const val PUS_OF_MAN_INVISIBILITY_SECONDS = 5
    const val PUS_OF_MAN_WEAKNESS_SECONDS = 10

    // --- Посилене Прикликання духів — стеля почту 10 -> 12 (slightly increased, but it
    // doesn't change much). Місце методу — тут, а не в SpiritGuideLore: перехід стається САМЕ
    // на Посл. 5, і GatekeeperLore уже дивиться на слабші тіри як на свою базу, тоді як
    // зворотна залежність (SpiritGuideLore -> GatekeeperLore) була б рухом проти шару тірів.
    // SpiritGuideLore.RETINUE_CAP лишається як є — це база для Посл. 6 і слабших. ---
    const val RETINUE_CAP = 12

    /** Стеля почту (слуг): 10 для Посл. 6+ ([SpiritGuideLore.RETINUE_CAP]), 12 від Посл. 5. */
    fun retinueCap(sequence: Sequence): Int =
        if (sequence.level <= BASE_SEQUENCE) RETINUE_CAP else SpiritGuideLore.RETINUE_CAP

    /** Кулдаун, що коротшає WEAK від бази до підлоги. */
    private fun shrink(baseSeconds: Int, floorSeconds: Int, sequence: Sequence): Int {
        val seconds = baseSeconds / growth(sequence, ScalingStrategy.WEAK)
        return max(floorSeconds.toLong(), seconds.roundToLong()).toInt()
    }

    /** Наскільки сила на заданій Послідовності перевищує силу на Посл. 5 (там рівно 1.0). */
    private fun growth(sequence: Sequence, strategy: ScalingStrategy): Double =
        SequenceScaler.calculateMultiplier(sequence.level, strategy) /
            SequenceScaler.calculateMultiplier(BASE_SEQUENCE, strategy)
}
