package me.vangoo.domain.creatures

/**
 * Резонансне джерело, що тяжіє до найближчого кревного Beyonder'а: впала Характеристика, рештка
 * або міфічна істота. Ключоване шляхом+послідовністю.
 *
 * @property pathway назва шляху джерела (порівняння без регістру)
 * @property group назва групи шляхів джерела
 * @property sequence послідовність джерела
 */
data class ConvergenceSource(
    val pathway: String?,
    val group: String?,
    val sequence: Int,
    val x: Double,
    val z: Double,
)
