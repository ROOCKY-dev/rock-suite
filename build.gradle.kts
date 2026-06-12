// Root build — all real configuration lives in the convention plugins
// under tooling/rock-conventions and in each subproject's build script.
tasks.register("printVersion") {
    val v = project.version.toString()
    doLast { println(v) }
}

// K3 packaging helper: prints "owner|group:name:version|/path/to.jar" for the
// third-party runtime deps each platform jar must nest when shipped as a mod.
tasks.register("depsList") {
    doLast {
        listOf("rock-core", "rock-data", "rock-migrate").forEach { name ->
            project(":$name").configurations.getByName("runtimeClasspath")
                .resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
                    val id = artifact.moduleVersion.id
                    println("$name|${id.group}:${id.name}:${id.version}|${artifact.file}")
                }
        }
    }
}
