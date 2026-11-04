import com.ivandsky.puzzlebox.libs
import com.ivandsky.puzzlebox.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.withType
import org.gradle.process.CommandLineArgumentProvider

/**
 * Screenshot goldens through the Roborazzi RUNTIME (`captureRoboImage` under Robolectric).
 * The Roborazzi Gradle plugin cannot apply on AGP 9 (it looks up the removed
 * TestedExtension), so there are no `recordRoborazzi*`/`verifyRoborazzi*` tasks: the plain
 * unit-test task captures, and this plugin selects the mode through system properties.
 *
 * Golden policy — one flag for every module:
 * - default (no flag, or `-Proborazzi.verify=false`): RECORD — captures (re)write the
 *   goldens.
 * - `-Proborazzi.verify=true` (bare `-Proborazzi.verify` counts as true): VERIFY —
 *   re-capture and fail on any pixel drift against the committed goldens.
 *
 * Golden locations are owned by each test (e.g. `app/snapshots`, `games/<id>-ui/snapshots`,
 * `games/tango-ui/src/test/resources/goldens`), never by this plugin.
 *
 * Modules whose test harness owns the compare set [RoborazziGoldensExtension.harnessVerifyProperty]:
 * Roborazzi then always records (to the harness's scratch file) and the unified flag is
 * forwarded to the harness as that system property instead.
 */
class AndroidRoborazziConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val goldens = extensions.create<RoborazziGoldensExtension>("roborazziGoldens")
            val modeArgs = objects.newInstance<RoborazziModeArguments>().apply {
                verify.set(
                    providers.gradleProperty("roborazzi.verify").map { it != "false" }.orElse(false),
                )
                harnessVerifyProperty.set(goldens.harnessVerifyProperty)
            }

            tasks.withType<Test>().configureEach {
                jvmArgumentProviders.add(modeArgs)
            }

            dependencies {
                "testImplementation"(libs.library("roborazzi"))
                "testImplementation"(libs.library("roborazzi-compose"))
            }
        }
    }
}

abstract class RoborazziGoldensExtension {
    /**
     * System property the module's own golden harness reads to decide verify vs record.
     * Unset (default): Roborazzi itself records or verifies per the unified flag.
     */
    abstract val harnessVerifyProperty: Property<String>
}

abstract class RoborazziModeArguments : CommandLineArgumentProvider {
    @get:Input
    abstract val verify: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val harnessVerifyProperty: Property<String>

    override fun asArguments(): Iterable<String> {
        val verify = verify.get()
        val harnessProperty = harnessVerifyProperty.orNull
        return if (harnessProperty == null) {
            listOf("-Droborazzi.test.record=${!verify}", "-Droborazzi.test.verify=$verify")
        } else {
            listOf("-Droborazzi.test.record=true", "-D$harnessProperty=$verify")
        }
    }
}
