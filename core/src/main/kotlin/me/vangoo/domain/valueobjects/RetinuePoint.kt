package me.vangoo.domain.valueobjects

/**
 * Позиція в світі для персистентного почту нежиті (Death, Посл. 6) — світ і координати
 * примітивами, як [Waypoint]; конвертацію в `Location` робить ефект-шар.
 */
data class RetinuePoint(
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
)
