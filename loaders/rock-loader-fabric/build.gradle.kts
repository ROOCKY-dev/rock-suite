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
    // Compile-time mirror of the Fabric/Minecraft API surface; the real
    // classes are provided by the Fabric runtime (Loom packaging step).
    compileOnly(project(":loader-stubs"))
}
