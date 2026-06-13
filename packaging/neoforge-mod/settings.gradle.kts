// K4 packaging build (standalone): the REAL NeoForge adapter, compiled against
// Mojang-mapped Minecraft + NeoForge via ModDevGradle. Kept outside the main
// monorepo build so the platform stays hermetic; ROCK artifacts come from
// mavenLocal (publishToMavenLocal).
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases")
    }
}

rootProject.name = "rock-neoforge-mod"
