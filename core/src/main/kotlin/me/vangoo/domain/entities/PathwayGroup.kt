package me.vangoo.domain.entities

/** Божество на вершині групи шляхів; усередині групи шляхи взаємозамінні при піднесенні. */
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
