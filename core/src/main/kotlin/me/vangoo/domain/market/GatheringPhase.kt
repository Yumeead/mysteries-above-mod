package me.vangoo.domain.market

/** Фази події-збору. Єдиний дозволений цикл: IDLE → ANNOUNCED → OPEN → CLOSING → IDLE. */
enum class GatheringPhase {
    IDLE,
    ANNOUNCED,
    OPEN,
    CLOSING;

    fun canTransitionTo(next: GatheringPhase): Boolean = when (this) {
        IDLE -> next == ANNOUNCED
        // ANNOUNCED → IDLE: скасування, якщо ніхто не погодився прийти
        ANNOUNCED -> next == OPEN || next == IDLE
        OPEN -> next == CLOSING
        CLOSING -> next == IDLE
    }
}
