// K3 packaging build: the REAL Fabric adapter, compiled against Mojang-mapped
// Minecraft via Loom. Kept outside the main monorepo build so the platform
// stays hermetic; ROCK artifacts come from mavenLocal (publishToMavenLocal).
// ROCK jars are NOT bundled here — they ship as separate library mods in
// mods/ (the modular install flow), produced by packaging/modwrap.py.
plugins {
    id("fabric-loom") version "1.17.11"
    java
}

group = "dev.rock"
version = "1.5.0"

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
    minecraft("com.mojang:minecraft:1.21.11")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:0.19.3")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.141.4+1.21.11")

    // Provided at runtime by the rock-* library mods in mods/.
    implementation("dev.rock:rock-api:1.5.0")
    implementation("dev.rock:rock-core:1.5.0")
    implementation("dev.rock:rock-data:1.5.0")
    implementation("com.google.inject:guice:7.0.0")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}
