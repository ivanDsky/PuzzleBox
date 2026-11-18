import com.ivandsky.puzzlebox.libs
import com.ivandsky.puzzlebox.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/**
 * Screenshot renders through the Roborazzi RUNTIME (`captureRoboImage` under Robolectric).
 * The Roborazzi Gradle plugin cannot apply on AGP 9 (it looks up the removed
 * TestedExtension), so there are no `recordRoborazzi*` tasks: the plain unit-test task
 * captures, and this plugin switches capturing on through a system property.
 *
 * Screenshot policy (ADR-0033): renders are review evidence, never a gate, and never committed.
 * - default (no flag): test classes whose name contains `Golden` or `Screenshot` are excluded
 *   from every module's test task, so `./gradlew test` (the merge gate) renders nothing.
 * - `-Proborazzi.record=true` (bare `-Proborazzi.record` counts as true): RENDER. Those classes
 *   run and write their PNGs, mostly into the module's gitignored `snapshots/` directory, for
 *   the author and reviewer to look at. Nothing is compared, so a render fails only when the
 *   composition itself throws. The task never counts as up to date or comes from the build
 *   cache in this mode: the PNGs are not declared outputs, and a render is asked for to see
 *   the current code.
 *
 * Capture paths are owned by each test (e.g. `app/snapshots`, `games/<id>-ui/snapshots`),
 * never by this plugin.
 */
class AndroidRoborazziConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val render = providers.gradleProperty("roborazzi.record").map { it != "false" }.orElse(false)

            tasks.withType<Test>().configureEach {
                if (render.get()) {
                    systemProperty("roborazzi.test.record", "true")
                    outputs.upToDateWhen { false }
                    outputs.cacheIf("screenshot renders write undeclared PNGs") { false }
                } else {
                    filter {
                        excludeTestsMatching("*Golden*")
                        excludeTestsMatching("*Screenshot*")
                    }
                }
            }

            dependencies {
                "testImplementation"(libs.library("roborazzi"))
                "testImplementation"(libs.library("roborazzi-compose"))
            }
        }
    }
}
