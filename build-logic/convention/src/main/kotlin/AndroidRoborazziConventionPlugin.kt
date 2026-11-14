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
 * Golden policy — a golden is either a real gate or not run. One flag family, every module:
 * - default (no flag): goldens do NOT run. Test classes whose name contains `Golden` or
 *   `Screenshot` are excluded from every module's test task, so `./gradlew test` (the merge
 *   gate, ADR-0023) never spends time on a capture that cannot fail.
 * - `-Proborazzi.verify=true` (bare `-Proborazzi.verify` counts as true): VERIFY — the golden
 *   classes run and fail on any pixel drift or missing golden against the committed images.
 *   The only mode that gates a golden.
 * - `-Proborazzi.record=true` (bare `-Proborazzi.record` counts as true): RECORD — the golden
 *   classes run and (re)write the goldens. Only this flag writes into a tracked golden
 *   directory. Record wins when both flags are set.
 *
 * Merge gate decision: the local merge gate stays `./gradlew assembleDebug test` and runs no
 * goldens. Goldens are macOS-recorded and drift on any other OS, so they cannot gate CI, and
 * a UI ticket's own definition of done runs `-Proborazzi.verify=true` on the modules it
 * changed (e.g. `./gradlew :games:zip-ui:testDebugUnitTest -Proborazzi.verify=true`); a red
 * verify there is fixed or re-recorded in that ticket, not discovered later by someone else.
 *
 * Golden locations are owned by each test (e.g. `app/snapshots`, `games/<id>-ui/snapshots`,
 * `games/tango-ui/src/test/resources/goldens`), never by this plugin.
 *
 * Modules whose test harness owns the compare set [RoborazziGoldensExtension.harnessModeProperty]:
 * Roborazzi then always records (to the harness's scratch file) and the mode (`record` or
 * `verify`) is forwarded to the harness as that system property.
 * `-Pgoldens.pixelCompare=false` (verify on Linux, where the macOS-recorded goldens cannot
 * match) reaches those harnesses as the `goldens.pixelCompare` system property: they skip the
 * pixel diff, nothing else. CI runs no golden; see ADR-0023.
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
                            else -> "off"
                        }
                    },
                )
                harnessModeProperty.set(goldens.harnessModeProperty)
                pixelCompare.set(
                    providers.gradleProperty("goldens.pixelCompare").map { it != "false" }.orElse(true),
                )
            }

            // Golden classes run only under -Proborazzi.verify or -Proborazzi.record; their class
            // names contain `Golden` or `Screenshot`, which is what the filter matches.
            val runGoldens = modeArgs.mode.map { it != "off" }

            tasks.withType<Test>().configureEach {
                if (!runGoldens.get()) {
                    filter {
                        excludeTestsMatching("*Golden*")
                        excludeTestsMatching("*Screenshot*")
                    }
                }
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
     * System property the module's own golden harness reads for the mode (`record` or
     * `verify`). Unset (default): Roborazzi itself acts per the unified flag.
     */
    abstract val harnessModeProperty: Property<String>
}

abstract class RoborazziModeArguments : CommandLineArgumentProvider {
    /** `record`, `verify`, or `off` when neither flag is set (golden classes are not run). */
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
                "-Droborazzi.test.compare=false",
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
