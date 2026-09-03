package me.vangoo.domain.market

/** The phases of a gathering event. The only allowed cycle: IDLE → ANNOUNCED → OPEN → CLOSING → IDLE. */
enum class GatheringPhase {
    IDLE,
    ANNOUNCED,
    OPEN,
    CLOSING;

    fun canTransitionTo(next: GatheringPhase): Boolean = when (this) {
        IDLE -> next == ANNOUNCED
        // ANNOUNCED → IDLE: cancellation if nobody agreed to come
        ANNOUNCED -> next == OPEN || next == IDLE
        OPEN -> next == CLOSING
        CLOSING -> next == IDLE
    }
}
