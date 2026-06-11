plugins {
    `java-library`
    jacoco
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        addStringOption("Xdoclint:none", "-quiet")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed", "skipped")
        showStackTraces = true
    }
}

tasks.jacocoTestReport {
    reports {
        xml.required = true
        html.required = true
    }
}

// ---------------------------------------------------------------------------
// Architecture enforcement: loader APIs may only be referenced under loaders/,
// and JDBI may only be referenced inside rock-data. (REH §4, TRS §5)
// ---------------------------------------------------------------------------
val loaderForbidden = listOf("net.fabricmc", "net.neoforged", "org.bukkit", "net.minecraft")
val isLoaderProject = projectDir.absolutePath.contains("${File.separator}loaders${File.separator}")
val isDataProject = name == "rock-data"
val mainJavaDir = layout.projectDirectory.dir("src/main/java")

val verifyLoaderIsolation = tasks.register("verifyLoaderIsolation") {
    description = "Fails the build if loader-specific or JDBI imports appear outside their permitted projects."
    group = "verification"
    val srcDir = mainJavaDir.asFile
    val loaderProject = isLoaderProject
    val dataProject = isDataProject
    val forbidden = loaderForbidden
    doLast {
        if (!srcDir.exists()) return@doLast
        val violations = mutableListOf<String>()
        srcDir.walkTopDown().filter { it.isFile && it.extension == "java" }.forEach { file ->
            file.readLines().forEachIndexed { idx, line ->
                val trimmed = line.trim()
                if (!trimmed.startsWith("import ")) return@forEachIndexed
                if (!loaderProject && forbidden.any { trimmed.startsWith("import $it.") }) {
                    violations += "${file.relativeTo(srcDir)}:${idx + 1}  $trimmed"
                }
                if (!dataProject && trimmed.startsWith("import org.jdbi.")) {
                    violations += "${file.relativeTo(srcDir)}:${idx + 1}  $trimmed"
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Architecture violation — forbidden imports outside permitted projects:\n" +
                    violations.joinToString("\n")
            )
        }
    }
}

tasks.named("check") {
    dependsOn(verifyLoaderIsolation)
}
