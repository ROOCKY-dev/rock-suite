plugins {
    id("dev.rock.module-conventions")
}

dependencies {
    testImplementation(project(":rock-core"))
    testImplementation(project(":rock-data"))
    testImplementation(libs.sqlite.jdbc)
    testImplementation(libs.hikaricp)
    testImplementation(libs.jdbi3.core)
}
