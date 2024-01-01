plugins {
    kotlin("jvm") version "1.9.21"
    application
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "io.github.gaming32"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.fabricmc:mapping-io:0.5.0")

    implementation("com.google.code.gson:gson:2.10.1")

    val ktorVersion = "2.3.7"
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-java:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-gson:$ktorVersion")

    implementation("net.sourceforge.argparse4j:argparse4j:0.9.0")

    implementation("io.github.oshai:kotlin-logging:5.1.0")
    implementation("org.slf4j:slf4j-simple:2.0.10")
}

application {
    mainClass = "io.github.gaming32.yarntomojmap.main.MainKt"
}

kotlin {
    jvmToolchain(17)
}
