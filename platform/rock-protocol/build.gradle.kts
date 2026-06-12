plugins {
    id("dev.rock.java-conventions")
    id("dev.rock.publish-conventions")
}

// rock-protocol depends ONLY on rock-api (zero third-party deps) — the payload
// model + codec must be shareable by both the loader custom-payload channel
// and the web dashboard's WebSocket feed (RFC-001). It also reaches rock-core
// only for the EventBus subscription wiring at the hub.
dependencies {
    api(project(":rock-api"))
    implementation(project(":rock-core"))
    implementation(libs.guice)
    implementation(libs.jakarta.inject)
    implementation(libs.slf4j.api)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.slf4j.simple)
}
