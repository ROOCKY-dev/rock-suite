// Multi-version (Option C, MULTIVERSION_SUPPORT): the SAME real Fabric adapter
// source as packaging/fabric-mod, retargeted to the 1.20.x family. Building this
// measures the real per-version delta — the only place version-specific code
// lives. The platform (api/core/data/protocol) is byte-identical across targets.
plugins {
    id("fabric-loom") version "1.17.11"
    java
}

group = "dev.rock"
version = "1.7.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://maven.fabricmc.net/")
}

dependencies {
    minecraft("com.mojang:minecraft:1.20.6")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:0.16.10")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.100.8+1.20.6")

    // Provided at runtime by the rock-* library mods in mods/.
    implementation("dev.rock:rock-api:1.7.0")
    implementation("dev.rock:rock-core:1.7.0")
    implementation("dev.rock:rock-data:1.7.0")
    implementation("dev.rock:rock-protocol:1.7.0")
    implementation("com.google.inject:guice:7.0.0")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}
