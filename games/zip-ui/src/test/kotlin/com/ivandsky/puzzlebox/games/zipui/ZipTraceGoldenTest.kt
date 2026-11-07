package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens for the Zip line at rest (spec A1): the round-joined stroke with its head disc
 * on a plain cell, and a lone start cell showing only the head, each light and dark.
 * Recorded by default, verified with `-Proborazzi.verify=true`; goldens live in
 * `games/zip-ui/snapshots/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class ZipTraceGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val waypoints = listOf(0, 5, 18, 30, 35)

    private fun capture(name: String, dark: Boolean, path: List<Int>) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = dark) {
                Surface {
                    ZipBoard(zipPreviewModel(6, waypoints, path), Modifier.padding(24.dp))
                }
            }
        }
        val out = File(checkNotNull(System.getProperty("user.dir")))
            .resolve("snapshots/$name-${if (dark) "dark" else "light"}.png")
            .canonicalFile
            .also { checkNotNull(it.parentFile).mkdirs() }
        composeRule.onRoot().captureRoboImage(out.absolutePath)
    }

    @Test fun traceHeadLight() = capture("trace-head", dark = false, path = listOf(0, 6, 7, 8, 14))

    @Test fun traceHeadDark() = capture("trace-head", dark = true, path = listOf(0, 6, 7, 8, 14))

    @Test fun traceStartLight() = capture("trace-start", dark = false, path = listOf(0))

    @Test fun traceStartDark() = capture("trace-start", dark = true, path = listOf(0))
}
