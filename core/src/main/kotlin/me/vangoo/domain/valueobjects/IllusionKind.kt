package me.vangoo.domain.valueobjects

/**
 * Види локальних ілюзій Фокусника (Seq 7 шляху Блазня — Illusion Creation).
 *
 * Чистий VO: id + вартість духовності. Конкретні звуки/партикли/дим —
 * у шарі pathways (`IllusionCreation`).
 */
enum class IllusionKind(val displayName: String, val spiritualityCost: Int) {

    /** Фальшивий вибух — гучний звук + дим. */
    EXPLOSION("Ілюзія вибуху", 40),

    /** Фальшиві кроки — тихі звуки поблизу цілі. */
    FOOTSTEPS("Ілюзія кроків", 20),

    /** Фальшива пожежа — вогняні партикли + тріск. */
    FIRE("Ілюзія пожежі", 30),
}
