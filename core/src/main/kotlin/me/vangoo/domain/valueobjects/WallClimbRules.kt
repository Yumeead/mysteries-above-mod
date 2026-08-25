package me.vangoo.domain.valueobjects

import me.vangoo.domain.services.SequenceScaler
import kotlin.math.min

/**
 * Балансні числа лазіння по стінах (Seq 8 Клоуна шляху Блазня — акробатика).
 *
 * Коли клоун притиснутий до вертикальної стіни й дивиться в неї, він
 * повільно дереться вгору. Швидкість підйому зростає зі силою. Чиста
 * математика — ефект (перевірка стіни, `setVelocity`) у
 * `pathways.fool.abilities.ClownAgility`.
 */
object WallClimbRules {

    /** Базова вертикальна швидкість лазіння (блоків/тік) на Seq 9. */
    private const val BASE_CLIMB_SPEED = 0.18

    /** Максимальна дистанція до стіни, щоб рахувати «притиснутий» (блоки). */
    const val WALL_REACH = 0.9

    /** Вертикальна швидкість підйому по стіні за поточною послідовністю (з м'якою стелею). */
    fun climbSpeed(sequence: Sequence): Double {
        val multiplier = SequenceScaler.calculateMultiplier(
            sequence.level, SequenceScaler.ScalingStrategy.WEAK,
        )
        return min(0.35, BASE_CLIMB_SPEED * multiplier)
    }
}
