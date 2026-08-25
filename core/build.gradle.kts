plugins {
    kotlin("jvm")
}

// НАВМИСНО без fabric-loom і без залежності на minecraft: якщо сюди колись
// протече платформний тип, модуль просто не збереться. Це заміна
// ArchitectureTest'у з плагіна — інваріант стереже компілятор.
dependencies {
    testImplementation(kotlin("test"))
}

kotlin { jvmToolchain(25) }

tasks.test { useJUnitPlatform() }
