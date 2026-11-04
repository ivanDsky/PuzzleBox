import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Feature navigation contracts (`:feature:<name>:api`): an Android library holding the
 * feature's serializable NavKeys, compiled with the kotlinx-serialization plugin against
 * `:core:navigation` — nothing else, so any impl may depend on any api.
 */
class AndroidFeatureApiConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("puzzlebox.android.library")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

            dependencies {
                "api"(project(":core:navigation"))
            }
        }
    }
}
