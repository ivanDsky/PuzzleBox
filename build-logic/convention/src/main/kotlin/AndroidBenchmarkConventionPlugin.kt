import com.android.build.api.dsl.TestExtension
import com.ivandsky.puzzlebox.TARGET_SDK
import com.ivandsky.puzzlebox.configureKotlinAndroid
import com.ivandsky.puzzlebox.configureModuleGraphGuard
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * The `benchmark` module: com.android.test + shared SDK/Java config, targetSdk, and the
 * module-graph guard. It reaches `:app` through `targetProjectPath` (not a project
 * dependency), so it never joins the app's module graph.
 */
class AndroidBenchmarkConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.test")

            extensions.configure<TestExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = TARGET_SDK
            }
            configureModuleGraphGuard()
        }
    }
}
