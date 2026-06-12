pluginManagement {
    includeBuild("tooling/rock-conventions")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "rock-suite"

// Platform layer
include(":rock-api", ":rock-core", ":rock-data", ":rock-protocol")
project(":rock-api").projectDir = file("platform/rock-api")
project(":rock-core").projectDir = file("platform/rock-core")
project(":rock-data").projectDir = file("platform/rock-data")
project(":rock-protocol").projectDir = file("platform/rock-protocol")

// Loader layer — the ONLY projects permitted to reference loader APIs
include(":loader-stubs", ":rock-loader-fabric", ":rock-loader-neoforge")
project(":loader-stubs").projectDir = file("loaders/loader-stubs")
project(":rock-loader-fabric").projectDir = file("loaders/rock-loader-fabric")
project(":rock-loader-neoforge").projectDir = file("loaders/rock-loader-neoforge")

// Examples — composition-root demos and the runtime testbench
include(":rock-testbench")
project(":rock-testbench").projectDir = file("examples/rock-testbench")

// Module layer — feature modules, rock-api access only
include(":rock-permissions", ":rock-claims", ":rock-economy", ":rock-discord", ":rock-logging", ":rock-teams", ":rock-essentials", ":rock-moderation",
        ":rock-backup", ":rock-metrics", ":rock-migrate")
project(":rock-logging").projectDir = file("modules/rock-logging")
project(":rock-teams").projectDir = file("modules/rock-teams")
project(":rock-essentials").projectDir = file("modules/rock-essentials")
project(":rock-moderation").projectDir = file("modules/rock-moderation")
project(":rock-backup").projectDir = file("modules/rock-backup")
project(":rock-metrics").projectDir = file("modules/rock-metrics")
project(":rock-migrate").projectDir = file("modules/rock-migrate")
project(":rock-permissions").projectDir = file("modules/rock-permissions")
project(":rock-claims").projectDir = file("modules/rock-claims")
project(":rock-economy").projectDir = file("modules/rock-economy")
project(":rock-discord").projectDir = file("modules/rock-discord")
