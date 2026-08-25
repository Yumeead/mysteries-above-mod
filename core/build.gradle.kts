plugins {
    kotlin("jvm")
}

// НАВМИСНО без fabric-loom і без залежності на minecraft: якщо сюди колись
// протече платформний тип, модуль просто не збереться. Це заміна
// ArchitectureTest'у з плагіна — інваріант стереже компілятор.
dependencies {
    // JUnit 5, а не kotlin.test: тести переїжджають із плагіна майже дослівно,
    // і збережений API прибирає цілий клас помилок перекладу.
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin { jvmToolchain(25) }

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "skipped", "failed") }
}
