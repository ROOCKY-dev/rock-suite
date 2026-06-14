// rock-client Fabric build (RFC-001 Tier 1). Compiles the client companion
// against Mojang-mapped Minecraft via Loom. The shared wire model
// (ProtocolCodec/ProtocolMessage) comes from rock-protocol on mavenLocal — the
// client never depends on rock-core/data; it only speaks the protocol.
plugins {
    id("fabric-loom") version "1.17.11"
    java
}

group = "dev.rock"
version = "2.0.0"

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

    // Shared, loader-agnostic wire model (pure Java, zero-dep). The codec and
    // message records are the contract between rock-client and the server.
    implementation("dev.rock:rock-protocol:2.0.0")
    implementation("dev.rock:rock-api:2.0.0")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}
