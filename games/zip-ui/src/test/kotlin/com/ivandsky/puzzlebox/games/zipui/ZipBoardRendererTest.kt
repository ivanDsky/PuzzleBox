package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.zip.WallDirection
import com.ivandsky.puzzlebox.games.zip.render.ZipRendererModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ZipBoardRendererTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 3x3 sample: waypoint 1 at (0,0), wall under (0,1), trace 0->1->4, hint on 4. */
    private fun sampleModel(
        path: List<Int> = listOf(0, 1, 4),
        hintHighlight: Int? = 4,
    ) = ZipRendererModel(
        n = 3,
        path = path,
        cells = (0 until 9).map { idx ->
            ZipRendererModel.CellRender(
                numberLabel = if (idx == 0) 1 else null,
                isWaypoint = idx == 0,
                isStart = idx == 0,
            )
        },
        walls = listOf(ZipRendererModel.WallRender(1, WallDirection.DOWN)),
        hintHighlight = hintHighlight,
        rejection = null,
        status = if (path.isEmpty()) ZipRendererModel.Status.IDLE else ZipRendererModel.Status.PLAYING,
    )

    private fun setContent(model: ZipRendererModel) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                ZipBoardRenderer().Content(model, Modifier.fillMaxWidth())
            }
        }
    }

    @Test
    fun `renders the sample board without throwing`() {
        setContent(sampleModel())
        composeRule.waitForIdle()
        // Waypoint label painted; board laid out with its cells.
        composeRule.onNodeWithText("1").assertExists()
    }

    @Test
    fun `empty trace (idle board) composes without throwing`() {
        setContent(sampleModel(path = emptyList(), hintHighlight = null))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("1").assertExists()
    }
}
