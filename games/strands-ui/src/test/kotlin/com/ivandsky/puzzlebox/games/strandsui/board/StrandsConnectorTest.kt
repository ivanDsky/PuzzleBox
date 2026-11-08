package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.DarkBoardTokens
import com.ivandsky.puzzlebox.designsystem.LightBoardTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.strands.ActiveHint
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.HintStage
import com.ivandsky.puzzlebox.games.strands.StrandsState
import com.ivandsky.puzzlebox.games.strands.render.StrandsRendererModel
import com.ivandsky.puzzlebox.games.strandsui.StrandsBoardContent
import com.ivandsky.puzzlebox.games.strandsui.StrandsBoardLayout
import com.ivandsky.puzzlebox.games.strandsui.StrandsUiFixtures
import com.ivandsky.puzzlebox.games.strandsui.Trace
import com.ivandsky.puzzlebox.gameui.LocalSnapTrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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

    /**
     * The live trace connector alone on a 4 x 4 board, with a finger held 0.45 cell east of the
     * tail and the tip free to stretch toward the letter there.
     */
    private fun trace(path: List<Coord>, snap: Boolean): PixelMap {
        val tail = path.last()
        val finger = TraceFinger(centreOf(tail) + Offset(0.45f, 0f), Offset(0.45f, 0f), armed = true)
        rule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.size(256.dp).testTag("trace")) {
                    StrandsConnectors(4, 4, emptyList(), { Trace(path) }, LightBoardTokens, { finger }, animate = false, snap = snap)
                }
            }
        }
        return rule.onNodeWithTag("trace").captureToImage().toPixelMap()
    }

    /** The pixel at ([col], [row]) in cells, [dpX] dp further right. */
    private fun PixelMap.atCell(col: Float, row: Float, dpX: Float = 0f, dpY: Float = 0f): Color {
        val pitch = width / 4f
        val density = rule.density.density
        return this[(pitch * col + dpX * density).toInt(), (pitch * row + dpY * density).toInt()]
    }

    private val traceColor = LightBoardTokens.strandsTraceConnector
    private val headRadius = StrandsBoardSpec.ConnectorWidth.value * MotionTokens.Trace.HeadScale / 2

    @Test
    fun `under snap the connector ends on the last letter centre, however far the finger is past it`() {
        val pixels = trace(listOf(Coord(1, 0), Coord(1, 1)), snap = true)
        assertEquals("the end is on the centre", traceColor, pixels.atCell(1.5f, 1.5f))
        assertEquals("the line runs back to the first letter", traceColor, pixels.atCell(0.5f, 1.5f))
        assertNotEquals("no tip past the head", traceColor, pixels.atCell(1.5f, 1.5f, dpX = headRadius + 4f))
    }

    @Test
    fun `under snap the head disc is at resting size`() {
        val pixels = trace(listOf(Coord(1, 0), Coord(1, 1)), snap = true)
        assertEquals(traceColor, pixels.atCell(1.5f, 1.5f, dpY = -(headRadius - 1f)))
        assertNotEquals(traceColor, pixels.atCell(1.5f, 1.5f, dpY = -(headRadius + 2f)))
    }

    @Test
    fun `under snap a one-letter path draws no line and no head disc`() {
        val pixels = trace(listOf(Coord(1, 1)), snap = true)
        assertNotEquals(traceColor, pixels.atCell(1.5f, 1.5f))
        assertNotEquals(traceColor, pixels.atCell(1.5f, 1.5f, dpX = 6f))
    }

    @Test
    fun `with the flag off the tip still stretches toward the finger`() {
        val pixels = trace(listOf(Coord(1, 0), Coord(1, 1)), snap = false)
        assertEquals(traceColor, pixels.atCell(1.5f, 1.5f, dpX = headRadius + 4f))
    }

    /** The whole board with a drag held on a two-letter trace, finger 0.45 cell east of the tail; the pixel in the gap past the tail. */
    private fun gapPastTail(snap: Boolean): Color {
        val path = listOf(Coord(1, 0), Coord(1, 1))
        val finger = TraceFinger(centreOf(Coord(1, 1)) + Offset(0.45f, 0f), Offset(0.45f, 0f), armed = true)
        rule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                CompositionLocalProvider(LocalSnapTrace provides snap) {
                    Box(Modifier.size(256.dp)) {
                        StrandsBoardLayout(
                            model = StrandsRendererModel.of(StrandsState(StrandsUiFixtures.puzzle), emptyList()),
                            found = null,
                            reduceMotion = true,
                            trace = { Trace(path) },
                            onTrace = {},
                            finger = remember { mutableStateOf(finger) },
                        )
                    }
                }
            }
        }
        val pixels = rule.onNodeWithTag("strands.board").captureToImage().toPixelMap()
        return pixels.atCell(1.98f, 1.5f)
    }

    @Test
    fun `the board draws the tip into the gap with the flag off`() = assertEquals(traceColor, gapPastTail(snap = false))

    @Test
    fun `the board draws no tip with LocalSnapTrace on`() = assertNotEquals(traceColor, gapPastTail(snap = true))
}
