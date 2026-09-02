pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        gradlePluginPortal()
        mavenCentral()
    }
}

// MC 26.2 вимагає Java 25 (див. CLAUDE.md, пастка 3), а її на машині може просто не бути.
// Резолвер Foojay дає Gradle звідки її взяти самому — інакше збірка падає з
// «Cannot find a Java installation … languageVersion=25», і виглядає це як поломка
// проєкту, а не як відсутній JDK.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "mysteries-above-mod"

// Два модулі — і це не декорація:
//   core — чисті правила, збирається БЕЗ Minecraft. Компілятор, а не тест, стереже,
//          що прогресія/баланс/стан не залежать від платформи.
//   mod  — усе, що торкається гри (Fabric, міксини, ефекти, персистентність).
include("core", "mod")
