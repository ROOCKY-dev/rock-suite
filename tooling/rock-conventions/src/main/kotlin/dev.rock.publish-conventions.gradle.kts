plugins {
    `java-library`
    `maven-publish`
    signing
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name = project.name
                description = "ROCK SUITE — cross-platform Minecraft server management platform (${project.name})"
                url = "https://github.com/ROOCKY-dev/rock-suite"
                licenses {
                    license {
                        name = "Apache License 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0"
                    }
                }
                developers {
                    developer {
                        id = "rock-suite"
                        name = "ROCK SUITE Founding Developer Team"
                    }
                }
                scm {
                    connection = "scm:git:https://github.com/ROOCKY-dev/rock-suite.git"
                    developerConnection = "scm:git:ssh://git@github.com/ROOCKY-dev/rock-suite.git"
                    url = "https://github.com/ROOCKY-dev/rock-suite"
                }
            }
        }
    }
    repositories {
        // Stable releases → Maven Central (via Sonatype OSSRH)
        maven {
            name = "sonatype"
            val releases = "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
            val snapshots = "https://s01.oss.sonatype.org/content/repositories/snapshots/"
            url = uri(if (version.toString().endsWith("SNAPSHOT")) snapshots else releases)
            credentials {
                username = providers.environmentVariable("OSSRH_USERNAME").orNull
                password = providers.environmentVariable("OSSRH_PASSWORD").orNull
            }
        }
        // Pre-release / snapshot builds → GitHub Packages
        maven {
            name = "githubPackages"
            url = uri("https://maven.pkg.github.com/ROOCKY-dev/rock-suite")
            credentials {
                username = providers.environmentVariable("GITHUB_ACTOR").orNull
                password = providers.environmentVariable("GITHUB_TOKEN").orNull
            }
        }
    }
}

// Sign only when a key is supplied (CI release); local builds stay unsigned.
signing {
    val signingKey = providers.environmentVariable("SIGNING_KEY").orNull
    val signingPassword = providers.environmentVariable("SIGNING_PASSWORD").orNull
    isRequired = signingKey != null
    if (signingKey != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["maven"])
    }
}
