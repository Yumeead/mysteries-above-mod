plugins {
    kotlin("jvm") version "2.4.10" apply false
}

subprojects {
    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
    }
}
