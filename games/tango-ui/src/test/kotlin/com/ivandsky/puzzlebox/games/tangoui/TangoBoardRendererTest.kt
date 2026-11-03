package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Device-free board render (A9): the sample board composes through the shared BoardGrid. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TangoBoardRendererTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleModel() = TangoRendererModel(
        n = 6,
        cells = (0 until 36).map { idx ->
            TangoRendererModel.CellRender(
                value = if (idx in setOf(0, 1, 6, 7, 16, 29)) {
                    com.ivandsky.puzzlebox.games.tango.TangoCell.SUN
                } else {
                    null
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

    @Test
    fun `renders the sample board without throwing`() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                TangoBoardRenderer().Content(sampleModel(), Modifier.fillMaxWidth())
            }
        }
        composeRule.waitForIdle()
        // Board composed and painted (marker chips are Canvas-drawn, not semantics text).
        composeRule.onRoot().assertExists()
    }
}
