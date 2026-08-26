package me.vangoo.domain.creatures

/** Чисте правило: чи достатньо далеко (по горизонталі) точка від спавну світу для появи істот. */
object SpawnDistanceGate {

    /**
     * @param dx різниця X від точки спавну світу
     * @param dz різниця Z від точки спавну світу
     * @param minDistance мінімальна горизонтальна відстань у блоках
     * @return true, якщо sqrt(dx^2 + dz^2) >= minDistance
     */
    fun isFarEnough(dx: Double, dz: Double, minDistance: Double): Boolean =
        minDistance <= 0.0 || (dx * dx + dz * dz) >= (minDistance * minDistance)
}
