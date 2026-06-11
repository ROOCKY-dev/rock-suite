plugins {
    id("dev.rock.java-conventions")
    id("dev.rock.publish-conventions")
}

dependencies {
    api(project(":rock-api"))
    implementation(libs.guice)
    implementation(libs.jakarta.inject)
    implementation(libs.tomlj)
    implementation(libs.slf4j.api)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.slf4j.simple)
}
