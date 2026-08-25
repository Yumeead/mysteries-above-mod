pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "mysteries-above-mod"

// Два модулі — і це не декорація:
//   core — чисті правила, збирається БЕЗ Minecraft. Компілятор, а не тест, стереже,
//          що прогресія/баланс/стан не залежать від платформи.
//   mod  — усе, що торкається гри (Fabric, міксини, ефекти, персистентність).
include("core", "mod")
