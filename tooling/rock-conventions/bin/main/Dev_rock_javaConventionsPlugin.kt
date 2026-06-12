/**
 * Precompiled [dev.rock.java-conventions.gradle.kts][Dev_rock_java_conventions_gradle] script plugin.
 *
 * @see Dev_rock_java_conventions_gradle
 */
public
class Dev_rock_javaConventionsPlugin : org.gradle.api.Plugin<org.gradle.api.Project> {
    override fun apply(target: org.gradle.api.Project) {
        try {
            Class
                .forName("Dev_rock_java_conventions_gradle")
                .getDeclaredConstructor(org.gradle.api.Project::class.java, org.gradle.api.Project::class.java)
                .newInstance(target, target)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.targetException
        }
    }
}
