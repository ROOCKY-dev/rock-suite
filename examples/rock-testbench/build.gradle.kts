plugins {
    id("dev.rock.java-conventions")
    application
}

application {
    mainClass = "dev.rock.testbench.TestBench"
}

// The testbench is a composition root (like a loader adapter): it may see
// everything. Feature modules land on the runtime classpath only, so they are
// discovered via ServiceLoader exactly as on a real server.
dependencies {
    implementation(project(":rock-api"))
    implementation(project(":rock-core"))
    implementation(project(":rock-data"))
    implementation(libs.guice)
    implementation(libs.slf4j.api)
    runtimeOnly(project(":rock-permissions"))
    runtimeOnly(project(":rock-claims"))
    runtimeOnly(project(":rock-economy"))
    runtimeOnly(project(":rock-discord"))
    runtimeOnly(project(":rock-logging"))
    runtimeOnly(libs.slf4j.simple)
}
