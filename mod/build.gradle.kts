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

    // One widened field, for Fool's `Shapeshifting` — see the file itself for why. An access
    // widener changes visibility only; it injects nothing, so it sits below a mixin in the same
    // hook search order, not beside it.
    accessWidenerPath = file("src/main/resources/mysteries_above.accesswidener")

    mods {
        register("mysteries_above") {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets["client"])
        }
    }

    // Second client for testing abilities that need another live player (marks,
    // zones, targeting). Own game dir and username so it doesn't collide with the
    // default client on options.txt/logs/latest.log or the server's username check.
    runs {
        create("client2") {
            inherit(getByName("client"))
            runDir("run-client2")
            programArgs("--username", "Tester2")
        }
        // A fixed name for the first client too, so ops.json on the dev server keeps working
        // across launches. Set after client2 inherits, or client2 would get two --username.
        getByName("client") {
            programArgs("--username", "Tester1")
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

    // Юніт-тести без гри. Перевіряється те, що Minecraft і не потребує: черга тіків,
    // диспетчер корутин, семантика реєстру сесій. Ігровий світ тут не піднімається —
    // саме тому ці тести й швидкі.
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Pure client-side logic (the wheel's geometry) is tested without the game too. Only the test
// classpath sees `client`; the main source set still cannot.
sourceSets.test {
    compileClasspath += sourceSets["client"].output
    runtimeClasspath += sourceSets["client"].output
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "skipped", "failed") }
}

kotlin { jvmToolchain(25) }

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
