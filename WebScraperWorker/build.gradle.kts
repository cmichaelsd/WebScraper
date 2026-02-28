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
    val coroutinesVersion = "1.8.1"
    val koinVersion = "3.5.6"

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${coroutinesVersion}")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("io.insert-koin:koin-test:${koinVersion}")
    testImplementation("io.ktor:ktor-client-mock:${ktorVersion}")

    // DI
    implementation("io.insert-koin:koin-core:${koinVersion}")

    // Ktor
    implementation("io.ktor:ktor-client-core:${ktorVersion}")
    implementation("io.ktor:ktor-client-cio:${ktorVersion}")
    implementation("io.ktor:ktor-client-content-negotiation:${ktorVersion}")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // PSQL access
    implementation("org.postgresql:postgresql:42.7.3")

    // ORM
    implementation("com.zaxxer:HikariCP:5.1.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${coroutinesVersion}")

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