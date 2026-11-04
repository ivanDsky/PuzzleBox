import com.ivandsky.puzzlebox.configureKotlinJvm
import com.ivandsky.puzzlebox.configureModuleGraphGuard
import com.ivandsky.puzzlebox.libs
import com.ivandsky.puzzlebox.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Pure-Kotlin JVM modules (zero Android, zero Compose). Kotlin JVM is applied version-less:
 * AGP 9's built-in Kotlin already puts the plugin on the classpath (same embedded compiler
 * the Android modules use). Also applies the module-graph guard.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            configureKotlinJvm()
            configureModuleGraphGuard()

            dependencies {
                "testImplementation"(libs.library("junit"))
                "testImplementation"(libs.library("kotlinx-coroutines-test"))
            }
        }
    }
}
