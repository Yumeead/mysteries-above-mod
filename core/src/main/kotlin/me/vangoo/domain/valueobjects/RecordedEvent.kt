package me.vangoo.domain.valueobjects

/**
 * Слід минулої події з CoreProtect. Чистий VO — світ і координати зберігаємо
 * примітивами, як [Waypoint] і [RetinuePoint]; конвертацію в `Location` робить ефект-шар.
 */
data class RecordedEvent(
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val description: String,
    val type: EventType,
    val timestamp: Long,
    /** Сире ім'я винуватця з CoreProtect (може бути null або службове "#tnt"). */
    val actor: String?,
) {
    enum class EventType {
        BLOCK_BREAK,
        BLOCK_PLACE,
        CONTAINER_OPEN,
        CONTAINER_TRANSACTION,
        DEATH,
    }
}
