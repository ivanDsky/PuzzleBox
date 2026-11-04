import com.android.build.api.dsl.LibraryExtension
import com.ivandsky.puzzlebox.configureAndroidCompose
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

/** Compose on top of `puzzlebox.android.library`. */
class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("puzzlebox.android.library")
            configureAndroidCompose(extensions.getByType<LibraryExtension>())
        }
    }
}
