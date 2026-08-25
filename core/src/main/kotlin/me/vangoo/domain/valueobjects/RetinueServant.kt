package me.vangoo.domain.valueobjects

/**
 * Один слуга почту нежиті (Death, Посл. 6), достатньо описаний, щоб відновити його ефект-шаром
 * (`UndeadRetinue.spawnFromRecord`) після relog/рестарту чи показу з тіні.
 *
 * [typeOrTag] — ванільний `EntityType` (RESURRECTED/SUBJUGATED) або internal id
 * MythicMobs-істоти Світу Духів (PACT). `SUBJUGATED` живе в цьому переліку заради
 * приховання/показу в межах ОДНІЄЇ сесії — на диск (переживання relog/рестарту) такі записи
 * свідомо не пишуться, бо підкорена нежить — довільний ванільний моб без власної ідентичності.
 */
data class RetinueServant(
    val kind: Kind,
    val typeOrTag: String,
    val customName: String?,
    val at: RetinuePoint,
) {
    enum class Kind { RESURRECTED, SUBJUGATED, PACT }
}
