plugins {
    id("dev.rock.module-conventions")
}

dependencies {
    // Reads SOURCE databases of incumbent plugins (LuckPerms exports, etc.).
    // The TRS DriverManager ban protects ROCK's own storage; reading foreign
    // files during migration is exactly this module's job.
    implementation(libs.sqlite.jdbc)

    testImplementation(project(":rock-core"))
    testImplementation(project(":rock-data"))
    // Importers are exercised against the real service implementations.
    testImplementation(project(":rock-permissions"))
    testImplementation(project(":rock-economy"))
    testImplementation(libs.hikaricp)
    testImplementation(libs.jdbi3.core)
}
