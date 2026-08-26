package me.vangoo.domain.abilities

import me.vangoo.domain.valueobjects.SanityPenalty
import java.util.UUID

/**
 * Життєвий цикл одного касту: **дозвіл → квиток → розрахунок**.
 *
 * У плагіні це був один метод `Beyonder.useAbility`, у якому ресурси списувались
 * одразу, а «відкладені» здібності (ті, що чекають вибору в меню) мусили цього
 * уникнути. Уникали коментарем:
 *
 * ```java
 * // КРИТИЧНО: НЕ встановлювати кулдаун якщо результат deferred!
 * ```
 *
 * Тут відкладеність — це просто **невитрачений квиток**. [CastTicket] видає лише
 * `Beyonder.authorize`, погашає лише `Beyonder.settle`, і рівно один раз. Здібність,
 * що чекає вводу, тримає квиток у себе; не дочекавшись — не гасить, і гравець нічого
 * не платить. Погасити двічі неможливо: це `check()`, а не дисципліна.
 */
sealed interface CastAuthorization {

    data class Granted(val ticket: CastTicket) : CastAuthorization

    data class Refused(val reason: CastRefusal) : CastAuthorization
}

/** Чому `core` відмовив у касті. Кулдауни живуть у моді — їх тут навмисно немає. */
sealed interface CastRefusal {

    /** Здібності немає ні в шляху, ні серед позашляхових. */
    data object NotOwned : CastRefusal

    data class InsufficientSpirituality(val required: Int, val available: Int) : CastRefusal

    /** Пасивку не кастують — її вмикає менеджер пасивок. */
    data object NotCastable : CastRefusal
}

/**
 * Дозвіл на один каст. Створюється тільки з `Beyonder.authorize`, гаситься тільки
 * через `Beyonder.settle`.
 */
class CastTicket internal constructor(
    val spec: AbilitySpec,
    val cost: Int,
    internal val casterId: UUID,
) {
    var isSettled: Boolean = false
        private set

    internal fun consume() {
        check(!isSettled) { "Квиток на ${spec.identity} вже погашено" }
        isSettled = true
    }

    override fun toString(): String = "CastTicket(${spec.identity}, cost=$cost, settled=$isSettled)"
}

/**
 * Що саме списалось при погашенні квитка. [penalty] типу `SPIRITUALITY_LOSS` уже
 * застосовано до Потойбічного; `DAMAGE` та `EXTREME` — ефекти у світі, їх накладає мод.
 */
data class CastReceipt(
    val spentSpirituality: Int,
    val masteryGained: Double,
    val cooldownSeconds: Int,
    val penalty: SanityPenalty,
)
