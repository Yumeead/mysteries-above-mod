plugins {
    kotlin("jvm")
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
}

// Імена властивостей — snake_case, як у fabric-example-mod; делегат `by project`
// вимагав би точного збігу імені, тож читаємо явно.
val minecraftVersion = providers.gradleProperty("minecraft_version").get()
val loaderVersion = providers.gradleProperty("loader_version").get()
val fabricApiVersion = providers.gradleProperty("fabric_api_version").get()
val fabricKotlinVersion = providers.gradleProperty("fabric_kotlin_version").get()

loom {
    // Мод клієнт-серверний, тож межа між сторонами тримається розкладкою, а не
    // дисципліною: `src/client` бачить net.minecraft.client, `src/main` — ні.
    // Клієнтський тип, що протік у спільний код, тепер не рантайм-падіння на
    // виділеному сервері, а помилка компіляції.
    splitEnvironmentSourceSets()

    mods {
        register("mysteries_above") {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets["client"])
        }
    }
}

dependencies {
    // Рядка `mappings` тут навмисно немає: на 26.x гра вже не обфускована
    // (yarn зупинився на 1.21.11, intermediary віддає сентинел 0.0.0), і loom
    // розбирається сам. Звірено з fabric-example-mod@26.2, який збирається так само.
    minecraft("com.mojang:minecraft:$minecraftVersion")

    implementation("net.fabricmc:fabric-loader:$loaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    implementation("net.fabricmc:fabric-language-kotlin:$fabricKotlinVersion")

    implementation(project(":core"))
    // core їде всередину джара мода — окремо його ніхто не ставитиме.
    include(project(":core"))
}

kotlin { jvmToolchain(25) }

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
