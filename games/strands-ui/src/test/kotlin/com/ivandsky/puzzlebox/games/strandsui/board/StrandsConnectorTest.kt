package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.DarkBoardTokens
import com.ivandsky.puzzlebox.designsystem.LightBoardTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.strands.ActiveHint
import com.ivandsky.puzzlebox.games.strands.HintStage
import com.ivandsky.puzzlebox.games.strands.StrandsState
import com.ivandsky.puzzlebox.games.strands.render.StrandsRendererModel
import com.ivandsky.puzzlebox.games.strandsui.StrandsBoardContent
import com.ivandsky.puzzlebox.games.strandsui.StrandsUiFixtures
import com.ivandsky.puzzlebox.games.strandsui.Trace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The hint-order link sits behind the letters (03: NYT's connector layer is under the
 * grid; ledger ruling on R-V-02): inside a hinted token the board shows bare, exactly as
 * an idle letter's does, so the letter keeps its idle contrast; between tokens the link
 * is drawn in the hint colour. PIES (row 0) is the hinted word on the 4 × 4 fixture.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class StrandsConnectorTest {

    @get:Rule
    val rule = createComposeRule()

    private fun board(dark: Boolean): PixelMap {
        val state = StrandsState(StrandsUiFixtures.puzzle, hintsUsed = 2, activeHint = ActiveHint("PIES", HintStage.OrderRevealed))
        rule.setContent {
            PuzzleBoxTheme(darkTheme = dark) {
                Box(Modifier.size(256.dp)) {
                    StrandsBoardContent(
                        model = StrandsRendererModel.of(state, emptyList()),
                        found = null,
                        reduceMotion = true,
                        trace = { Trace() },
                        onTrace = {},
                    )
                }
            }
        }
        return rule.onNodeWithTag("strands.board").captureToImage().toPixelMap()
    }

    /** The pixel [dx] pitches right of cell ([row], [col])'s centre, on its centre line. */
    private fun PixelMap.at(row: Int, col: Int, dx: Float): Color {
        val pitch = width / 4f
        return this[(pitch * (col + 0.5f + dx)).toInt(), (pitch * (row + 0.5f)).toInt()]
    }

    private fun assertLinkBehindLetters(dark: Boolean) {
        val pixels = board(dark)
        val hint = (if (dark) DarkBoardTokens else LightBoardTokens).strandsHintConnector
        // Between the letter and its ring, on the link's line: bare board, as beside an idle "A".
        assertEquals(pixels.at(row = 2, col = 1, dx = 0.3f), pixels.at(row = 0, col = 1, dx = 0.3f))
        assertNotEquals(hint, pixels.at(row = 0, col = 1, dx = 0.3f))
        // Halfway between I and E the link still joins the rings.
        assertEquals(hint, pixels.at(row = 0, col = 1, dx = 0.5f))
    }

    @Test
    fun `the hint link never crosses a letter, light`() = assertLinkBehindLetters(dark = false)

    @Test
    fun `the hint link never crosses a letter, dark`() = assertLinkBehindLetters(dark = true)

    @Test
    fun `each hint piece starts and ends at a token's edge`() {
        val centers = listOf(Offset(0f, 0f), Offset(64f, 0f), Offset(128f, 64f))
        val segments = hintSegments(centers, tokenRadius = 26f)
        assertEquals(2, segments.size)
        segments.forEachIndexed { i, (from, to) ->
            assertEquals(26f, (from - centers[i]).getDistance(), 1e-3f)
            assertEquals(26f, (to - centers[i + 1]).getDistance(), 1e-3f)
        }
    }
}
