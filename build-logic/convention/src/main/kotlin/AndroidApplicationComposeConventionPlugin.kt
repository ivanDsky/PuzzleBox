import com.android.build.api.dsl.ApplicationExtension
import com.ivandsky.puzzlebox.configureAndroidCompose
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

/** Compose on top of `puzzlebox.android.application`. */
class AndroidApplicationComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("puzzlebox.android.application")
            configureAndroidCompose(extensions.getByType<ApplicationExtension>())
        }
    }
}
