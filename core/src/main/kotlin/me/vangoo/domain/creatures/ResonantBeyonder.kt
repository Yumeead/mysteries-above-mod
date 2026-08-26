package me.vangoo.domain.creatures

import java.util.UUID

/**
 * Кандидат-магніт Закону Конвергенції: онлайн-Beyonder, до якого може тяжіти резонансне джерело.
 *
 * @property id UUID гравця
 * @property pathway назва шляху (порівняння без регістру)
 * @property group назва групи шляхів (для резонансу «сусід»)
 * @property sequenceLevel поточний рівень послідовності (9 = найслабший, 0 = найсильніший)
 */
data class ResonantBeyonder(
    val id: UUID,
    val pathway: String?,
    val group: String?,
    val sequenceLevel: Int,
    val x: Double,
    val z: Double,
)
