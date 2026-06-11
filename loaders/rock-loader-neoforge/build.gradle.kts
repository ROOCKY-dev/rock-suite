plugins {
    id("dev.rock.java-conventions")
    id("dev.rock.publish-conventions")
}

dependencies {
    implementation(project(":rock-api"))
    implementation(project(":rock-core"))
    implementation(project(":rock-data"))
    implementation(libs.guice)
    implementation(libs.slf4j.api)
    // Compile-time mirror of the NeoForge/Minecraft API surface; the real
    // classes are provided by the NeoForge runtime (ModDevGradle packaging step).
    compileOnly(project(":loader-stubs"))
}
