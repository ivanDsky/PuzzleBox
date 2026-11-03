package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi capture of the walking-skeleton board (DoD: device-free render proof).
 * Captures write to the review tree; gate evidence, not verify-pinned goldens
 * (feature/play precedent) — ticket 04 owns the real goldens.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
class TangoBoardScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `sample board renders device-free`() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                TangoBoardRenderer().Content(sampleBoard(), Modifier.fillMaxWidth())
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            File("build/reports/tango/tango-board-n6.png"),
        )
    }

    /** Mid-play state: givens + a few placements + all four markers. */
    private fun sampleBoard() = TangoRendererModel(
        n = 6,
        cells = (0 until 36).map { idx ->
            TangoRendererModel.CellRender(
                value = when (idx) {
                    in setOf(0, 1, 6, 7, 16, 29) -> TangoCell.SUN
                    in setOf(14, 22, 32, 33, 2) -> TangoCell.MOON
                    else -> null
                },
                isGiven = idx in setOf(0, 1, 6, 7, 14, 16, 22, 29, 32, 33),
            )
        },
        markers = listOf(
            TangoRendererModel.MarkerRender(3, TangoEdgeSide.DOWN, TangoMarkerKind.SAME),
            TangoRendererModel.MarkerRender(18, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
            TangoRendererModel.MarkerRender(18, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE),
            TangoRendererModel.MarkerRender(34, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
        ),
        rejection = null,
        status = TangoRendererModel.Status.PLAYING,
    )
}
