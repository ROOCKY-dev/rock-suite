plugins {
    id("dev.rock.module-conventions")
}

dependencies {
    // Integration tests exercise the real DataService over SQLite; the module's
    // main source sees rock-api only (REH §7).
    testImplementation(project(":rock-core"))
    testImplementation(project(":rock-data"))
    testImplementation(libs.sqlite.jdbc)
    testImplementation(libs.hikaricp)
    testImplementation(libs.jdbi3.core)
}
