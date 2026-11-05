import com.ivandsky.puzzlebox.libs
import com.ivandsky.puzzlebox.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Feature screens (`:feature:<name>:impl`): compose library + what every feature compiles
 * against — the design system, Koin's compose ViewModel injection, the core icon set, and
 * window size classes for adaptive layouts.
 */
class AndroidFeatureImplConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("puzzlebox.android.library.compose")

            dependencies {
                "implementation"(project(":core:designsystem"))
                "implementation"(libs.library("koin-androidx-compose"))
                "implementation"(libs.library("androidx-compose-material3-windowsizeclass"))
            }
        }
    }
}
