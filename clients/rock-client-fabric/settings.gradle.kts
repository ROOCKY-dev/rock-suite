// rock-client (Fabric) — the native client companion (RFC-001). Standalone Loom
// build, seed for the separate ROOCKY-dev/rock-client repo: client-only toolchain
// (Loom client runs), optional install, its own cadence. Consumes the shared
// rock-protocol wire model from mavenLocal.
pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "rock-client-fabric"
