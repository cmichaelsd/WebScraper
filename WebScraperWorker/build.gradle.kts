plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    val ktorVersion = "2.3.8"

    testImplementation(kotlin("test"))

    // Ktor client core
    implementation("io.ktor:ktor-client-core:${ktorVersion}")

    // Pick ONE engine (CIO is fine)
    implementation("io.ktor:ktor-client-cio:${ktorVersion}")

    // Helpful extras
    implementation("io.ktor:ktor-client-content-negotiation:${ktorVersion}")

    // Ktor openapi
    implementation("io.ktor:ktor-server-openapi:${ktorVersion}")

    // HTML parsing later
    implementation("org.jsoup:jsoup:1.17.2")

    // Logging (optional but useful)
    implementation("ch.qos.logback:logback-classic:1.4.14")



    // PSQL access
    implementation("org.postgresql:postgresql:42.7.3")

    // ORM
    implementation("com.zaxxer:HikariCP:5.1.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("org.example.MainKt")
}