plugins {
    id("dev.rock.module-conventions")
}

dependencies {
    testImplementation(project(":rock-core"))
    testImplementation(project(":rock-data"))
    // Cross-module integration test only (team-owned claims); main source
    // still sees rock-api alone (REH §7).
    testImplementation(project(":rock-teams"))
    testImplementation(libs.sqlite.jdbc)
    testImplementation(libs.hikaricp)
    testImplementation(libs.jdbi3.core)
}
