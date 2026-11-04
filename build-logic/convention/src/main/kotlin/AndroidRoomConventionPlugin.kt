import com.google.devtools.ksp.gradle.KspExtension
import com.ivandsky.puzzlebox.libs
import com.ivandsky.puzzlebox.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Room via KSP. Schemas export to `<module>/schemas`: the committed JSONs are the
 * migration contract, bumped with every database version and replayed by migration tests.
 */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")

            extensions.configure<KspExtension> {
                arg("room.schemaLocation", "$projectDir/schemas")
            }

            dependencies {
                "implementation"(libs.library("androidx-room-runtime"))
                "implementation"(libs.library("androidx-room-ktx"))
                "ksp"(libs.library("androidx-room-compiler"))
            }
        }
    }
}
