plugins {
    id("dev.rock.module-conventions")
}

dependencies {
    // REST JSON + Argon2id password hashing. HTTP server + JWT are JDK-native.
    implementation(libs.jackson.databind)
    implementation(libs.password4j)

    testImplementation(project(":rock-core"))
    testImplementation(project(":rock-data"))
    testImplementation(project(":rock-protocol"))
    testImplementation(libs.sqlite.jdbc)
    testImplementation(libs.hikaricp)
    testImplementation(libs.jdbi3.core)
}
