// K4 packaging build: the REAL NeoForge adapter, compiled against Mojang-mapped
// Minecraft + NeoForge via ModDevGradle. The NeoForge counterpart to
// packaging/fabric-mod. ROCK jars are NOT bundled here — they ship as separate
// library mods in mods/ (the modular install flow), produced by modwrap.py.
plugins {
    id("net.neoforged.moddev") version "2.0.141"
}

group = "dev.rock"
version = "1.7.0"

repositories {
    mavenLocal()
    mavenCentral()
}

neoForge {
    // NeoForge 21.11.x targets Minecraft 1.21.11 — exact parity with the K3
    // Fabric server, so cross-loader behaviour is compared on equal ground.
    version = "21.11.42"

    runs {
        register("server") {
            server()
        }
    }

    mods {
        register("rock_suite") {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    // Provided at runtime by the rock-* library mods in mods/ (NOT bundled).
    compileOnly("dev.rock:rock-api:1.7.0")
    compileOnly("dev.rock:rock-core:1.7.0")
    compileOnly("dev.rock:rock-data:1.7.0")
    compileOnly("dev.rock:rock-protocol:1.7.0")
    compileOnly("com.google.inject:guice:7.0.0")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}
