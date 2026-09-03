package me.vangoo.domain.valueobjects

/**
 * A trace of a past event from CoreProtect. A pure VO — the world and the coordinates are
 * kept as primitives, like [Waypoint] and [RetinuePoint]; the effect layer converts them
 * into a `Location`.
 */
data class RecordedEvent(
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val description: String,
    val type: EventType,
    val timestamp: Long,
    /** Raw name of the culprit from CoreProtect (may be null or an internal "#tnt"). */
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
