plugins {
    id("dev.rock.java-conventions")
    id("dev.rock.publish-conventions")
}

// IMMUTABLE LAW: rock-api has ZERO dependencies beyond the Java 21 standard
// library. Any import added here is forced onto every module in the ecosystem.
// Test-only dependencies do not ship with the artifact and are permitted.
listOf(configurations.api, configurations.implementation, configurations.compileOnly).forEach { conf ->
    conf.get().dependencies.whenObjectAdded {
        throw GradleException("rock-api must have zero compile dependencies (attempted to add: $this)")
    }
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
