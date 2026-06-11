// Root build — all real configuration lives in the convention plugins
// under tooling/rock-conventions and in each subproject's build script.
tasks.register("printVersion") {
    val v = project.version.toString()
    doLast { println(v) }
}
