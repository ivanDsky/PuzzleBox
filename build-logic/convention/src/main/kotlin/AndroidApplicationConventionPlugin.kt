import com.android.build.api.dsl.ApplicationExtension
import com.ivandsky.puzzlebox.TARGET_SDK
import com.ivandsky.puzzlebox.configureAndroidUnitTests
import com.ivandsky.puzzlebox.configureKotlinAndroid
import com.ivandsky.puzzlebox.configureModuleGraphGuard
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * The `app` module: com.android.application + shared SDK/Java config, targetSdk, unit tests,
 * and the module-graph guard.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = TARGET_SDK
                configureAndroidUnitTests(this)
            }
            configureModuleGraphGuard()
        }
    }
}
