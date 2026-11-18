package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
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
 * The result-screen panes of the T25 variant matrix (spec UIUX-01 A6/A7/A12): the T23
 * route surface in both themes, with the record badge shown because the accent moment is
 * what needs judging. They used to live in zip-ui's `VariantMatrixScreenshotTest`; they
 * sit next to [ResultScreen] so no game UI compiles against the result screen.
 *
 * Captures write straight to the review-page diagrams tree (`design/diagrams/matrix/`)
 * under the same file names as before. They are review evidence, not goldens (ADR-0033).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-420dpi")
class ResultScreenMatrixScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test fun `result dark`() = resultPane(dark = true)

    @Test fun `result light`() = resultPane(dark = false)

    private fun resultPane(dark: Boolean) {
        capture("result-${if (dark) "dark" else "light"}.png", dark) {
            ResultScreen(
                timeMs = 95_000,
                hintsUsed = 1,
                onDone = {},
                onShare = {},
                onPlayAgain = {},
                verdict = RecordVerdict.NewBest,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String, darkTheme: Boolean, content: @Composable () -> Unit) {
        MATRIX_DIR.mkdirs()
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = darkTheme) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center,
                ) {
                    content()
                }
            }
        }
        // Review evidence in the gitignored scratch matrix dir, recorded whatever Roborazzi's mode.
        composeRule.onRoot().captureRoboImage(
            File(MATRIX_DIR, name).absolutePath,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
        )
    }

    companion object {
        // The review-page tree served by the review site (user.dir = the module dir under Gradle).
        private val MATRIX_DIR = File(checkNotNull(System.getProperty("user.dir")) { "user.dir system property is not set" })
            .resolve("../../.scratch/puzzlebox/design/diagrams/matrix")
            .canonicalFile
    }
}
