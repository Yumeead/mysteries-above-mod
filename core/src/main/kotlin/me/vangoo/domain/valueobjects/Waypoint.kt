package me.vangoo.domain.valueobjects

/**
 * Збережена морська мітка (Морська Пам'ять, Tyrant). Чистий VO — світ і координати
 * зберігаємо примітивами, конвертацію в `Location` робить ефект-шар.
 */
data class Waypoint(
    val name: String,
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
) {
    fun withName(newName: String): Waypoint = copy(name = newName)
}
