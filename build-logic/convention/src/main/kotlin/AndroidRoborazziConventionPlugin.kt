import com.ivandsky.puzzlebox.libs
import com.ivandsky.puzzlebox.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitivity
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
 * Golden policy — one flag for every module, three modes:
 * - default (no flag): COMPARE — re-capture, write the diff images under `build/` and never
 *   fail on drift or touch a golden. A plain `test` run (the merge gate, on any OS) leaves
 *   the worktree clean.
 * - `-Proborazzi.verify=true` (bare `-Proborazzi.verify` counts as true): VERIFY — fail on
 *   any pixel drift against the committed goldens. The strict local check.
 * - `-Proborazzi.record=true` (bare `-Proborazzi.record` counts as true): RECORD — captures
 *   (re)write the goldens. Only this flag writes into a tracked golden directory.
 *
 * Golden locations are owned by each test (e.g. `app/snapshots`, `games/<id>-ui/snapshots`,
 * `games/tango-ui/src/test/resources/goldens`), never by this plugin.
 *
 * Modules whose test harness owns the compare set [RoborazziGoldensExtension.harnessModeProperty]:
 * Roborazzi then always records (to the harness's scratch file) and the unified mode
 * (`record`, `verify` or `compare`) is forwarded to the harness as that system property.
 * `-Pgoldens.pixelCompare=false`
 * (verify on Linux, where the macOS-recorded goldens cannot match) reaches those harnesses
 * as the `goldens.pixelCompare` system property: they skip the pixel diff, nothing else.
 */
class AndroidRoborazziConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val goldens = extensions.create<RoborazziGoldensExtension>("roborazziGoldens")
            val modeArgs = objects.newInstance<RoborazziModeArguments>().apply {
                val record = providers.gradleProperty("roborazzi.record")
                val verify = providers.gradleProperty("roborazzi.verify")
                mode.set(
                    providers.provider {
                        when {
                            record.orNull?.let { it != "false" } == true -> "record"
                            verify.orNull?.let { it != "false" } == true -> "verify"
                            else -> "compare"
                        }
                    },
                )
                harnessModeProperty.set(goldens.harnessModeProperty)
                pixelCompare.set(
                    providers.gradleProperty("goldens.pixelCompare").map { it != "false" }.orElse(true),
                )
            }

            tasks.withType<Test>().configureEach {
                jvmArgumentProviders.add(modeArgs)
                // The golden tests read `<module>/snapshots/` through `user.dir`, which Gradle cannot
                // see; undeclared, a changed golden PNG is served FROM-CACHE as a stale green.
                // fileTree, not dir: a module without a snapshots/ dir (tango-ui) adds nothing.
                inputs.files(fileTree(layout.projectDirectory.dir("snapshots")))
                    .withPropertyName("goldenSnapshots")
                    .withPathSensitivity(PathSensitivity.RELATIVE)
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
     * System property the module's own golden harness reads for the mode (`record`,
     * `verify` or `compare`). Unset (default): Roborazzi itself acts per the unified flag.
     */
    abstract val harnessModeProperty: Property<String>
}

abstract class RoborazziModeArguments : CommandLineArgumentProvider {
    /** `record`, `verify` or `compare`. */
    @get:Input
    abstract val mode: Property<String>

    @get:Input
    @get:Optional
    abstract val harnessModeProperty: Property<String>

    @get:Input
    abstract val pixelCompare: Property<Boolean>

    override fun asArguments(): Iterable<String> {
        val mode = mode.get()
        val harnessProperty = harnessModeProperty.orNull
        return if (harnessProperty == null) {
            listOf(
                "-Droborazzi.test.record=${mode == "record"}",
                "-Droborazzi.test.verify=${mode == "verify"}",
                "-Droborazzi.test.compare=${mode == "compare"}",
            )
        } else {
            listOf(
                "-Droborazzi.test.record=true",
                "-D$harnessProperty=$mode",
                "-Dgoldens.pixelCompare=${pixelCompare.get()}",
            )
        }
    }
}
