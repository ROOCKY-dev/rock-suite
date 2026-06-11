plugins {
    id("dev.rock.java-conventions")
    id("dev.rock.publish-conventions")
}

dependencies {
    api(project(":rock-api"))
    implementation(project(":rock-core"))
    implementation(libs.guice)
    implementation(libs.jakarta.inject)
    implementation(libs.slf4j.api)
    implementation(libs.hikaricp)
    implementation(libs.jdbi3.core)
    implementation(libs.flyway.core)
    // PostgreSQL support is a Flyway 10 plugin artifact; SQLite remains in core.
    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.sqlite.jdbc)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.bundles.test)
    testImplementation(libs.sqlite.jdbc)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.slf4j.simple)
}
