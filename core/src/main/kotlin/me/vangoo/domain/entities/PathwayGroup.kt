package me.vangoo.domain.entities

/** The deity at the top of a pathway group; inside a group pathways are interchangeable on ascension. */
enum class PathwayGroup(val displayName: String) {
    LordOfMysteries("Володар Таємниць"),
    DemonOfKnowledge("Демон Знання"),
    GodAlmighty("Бог Всемогутній"),
    TheAnarchy("Анархія"),
    EternalDarkness("Вічна Темрява"),
    GoddessOfOrigin("Богиня Витоків"),
    CalamityOfDestruction("Лихо Руйнування"),
    FatherOfDevils("Батько Дияволів"),
    KeyOfLight("Ключ Світла"),
}
