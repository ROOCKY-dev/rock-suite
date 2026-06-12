// Standard build setup for ROCK feature modules (modules/ directory).
// Feature modules may see rock-api ONLY — never rock-core, rock-data,
// loader projects, or each other. (AVD Principle 1, REH §7)
plugins {
    id("dev.rock.java-conventions")
    id("dev.rock.publish-conventions")
}

dependencies {
    "api"(project(":rock-api"))
    "implementation"("jakarta.inject:jakarta.inject-api:2.0.1")
    "implementation"("com.google.inject:guice:7.0.0")
    "implementation"("org.slf4j:slf4j-api:2.0.13")

    "testImplementation"(platform("org.junit:junit-bom:5.10.2"))
    "testImplementation"("org.junit.jupiter:junit-jupiter")
    "testImplementation"("org.mockito:mockito-core:5.12.0")
    "testImplementation"("org.mockito:mockito-junit-jupiter:5.12.0")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    "testRuntimeOnly"("org.slf4j:slf4j-simple:2.0.13")
}
