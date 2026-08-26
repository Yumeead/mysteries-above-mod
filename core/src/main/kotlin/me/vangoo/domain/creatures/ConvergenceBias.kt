package me.vangoo.domain.creatures

/**
 * Закон конвергенції: схиляє ВИБІР істоти на природному спавні до шляху гравця та послідовності,
 * яка йому скоро знадобиться. Не змінює загальну ймовірність спавну — лише перерозподіляє її.
 *
 * @property pathway назва шляху гравця (порівняння без урахування регістру з [CreatureDefinition.pathway])
 * @property sequenceLevel поточний рівень послідовності гравця (9 = найслабший, 0 = найсильніший)
 */
data class ConvergenceBias(val pathway: String?, val sequenceLevel: Int)
