package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.StrandsState
import com.ivandsky.puzzlebox.games.strands.render.CellState
import com.ivandsky.puzzlebox.games.strands.render.LinkKind
import com.ivandsky.puzzlebox.games.strands.render.StrandsLink
import com.ivandsky.puzzlebox.games.strands.render.StrandsRendererModel
import com.ivandsky.puzzlebox.games.strandsui.StrandsBoardContent
import com.ivandsky.puzzlebox.games.strandsui.StrandsMotion
import com.ivandsky.puzzlebox.games.strandsui.StrandsUiFixtures
import com.ivandsky.puzzlebox.games.strandsui.Trace
import com.ivandsky.puzzlebox.games.strandsui.TraceEnd
import com.ivandsky.puzzlebox.games.strandsui.TraceEndKind
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A connector cut out of faded tiles (a dimmed link, a rejected path dissolving) is cut by the
 * shape the tile paints: face and lip, rounded. A diagonal's stroke passes the tile's rounded
 * corner, where no tile paints, so there it stays on the bare board; cutting a plain square
 * wipes it out and leaves a notch.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class StrandsConnectorClipShapeTest {

    @get:Rule
    val rule = createComposeRule()

    private val diagonal = listOf(Coord(0, 0), Coord(1, 1))

    private fun show(links: List<StrandsLink>, dim: StrandsDim, end: TraceEnd?, reduceMotion: Boolean) {
        rule.mainClock.autoAdvance = false
        val base = StrandsRendererModel.of(StrandsState(StrandsUiFixtures.puzzle), emptyList())
        // The editor's tiles are found-theme ones: fill and lip, which an idle cell has neither of.
        val linked = links.flatMap { it.cells }.toSet()
        val model = base.copy(
            links = links,
            cells = base.cells.map { if (it.coord in linked) it.copy(state = CellState.FoundTheme) else it },
        )
        rule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.size(256.dp)) {
                    StrandsBoardContent(
                        model = model,
                        found = null,
                        reduceMotion = reduceMotion,
                        trace = { Trace() },
                        onTrace = {},
                        traceEnd = { end },
                        dim = dim,
                    )
                }
            }
        }
    }

    /** The pixel on the diagonal at the first tile's square corner, outside its rounded corner and lip. */
    private fun PixelMap.corner(): Color {
        val pitch = width / 4f
        val reach = pitch * StrandsBoardSpec.TokenFraction / 2 * 0.98f
        return this[(pitch * 0.5f + reach).toInt(), (pitch * 0.5f + reach).toInt()]
    }

    private fun board(): PixelMap = rule.onNodeWithTag("strands.board").captureToImage().toPixelMap()

    /**
     * A faded tile's lip hangs below the layer's face box: the pixel one row under the face,
     * mid-column, must not be the bare board. Driven by the pitch alone, so it never repeats the lip's depth.
     */
    private fun assertLipPainted(pixels: PixelMap) {
        val pitch = pixels.width / 4f
        val half = pitch * StrandsBoardSpec.TokenFraction / 2
        val x = (pitch * 0.5f + half * 0.3f).toInt()
        val y = (pitch * 0.5f + half).toInt() + 1
        assertNotEquals("bare board where the lip is", pixels[1, 1], pixels[x, y])
    }

    @Test
    fun `a dimmed tile paints its whole lip`() {
        val alone = listOf(Coord(0, 0))
        val link = StrandsLink(alone, LinkKind.Theme)
        show(listOf(link), StrandsDim(alone.toSet(), setOf(link), alpha = 0.4f), end = null, reduceMotion = true)
        rule.mainClock.advanceTimeByFrame()
        assertLipPainted(board())
    }

    @Test
    fun `a dissolving rejected tile paints its whole lip`() {
        show(emptyList(), StrandsDim.None, TraceEnd(listOf(Coord(0, 0)), TraceEndKind.Short, 1), reduceMotion = false)
        rule.mainClock.advanceTimeBy(StrandsMotion.ShortHoldMs + 40L)
        assertLipPainted(board())
    }

    @Test
    fun `a dimmed vertical link leaves no bare board between its tiles, whatever lip they paint`() {
        val vertical = listOf(Coord(0, 0), Coord(1, 0))
        val link = StrandsLink(vertical, LinkKind.Theme)
        show(listOf(link), StrandsDim(vertical.toSet(), setOf(link), alpha = 0.4f), end = null, reduceMotion = true)
        rule.mainClock.advanceTimeByFrame()
        val pixels = board()
        assertNoBareBoardBetweenTiles(pixels, pixels[1, 1])
    }

    @Test
    fun `a dissolving rejected vertical path leaves no bare board between its tiles`() {
        show(emptyList(), StrandsDim.None, TraceEnd(listOf(Coord(0, 0), Coord(1, 0)), TraceEndKind.Short, 1), reduceMotion = false)
        rule.mainClock.advanceTimeBy(StrandsMotion.ShortHoldMs + 40L)
        val pixels = board()
        assertNoBareBoardBetweenTiles(pixels, pixels[1, 1])
    }

    /** Down the column through two stacked tiles' centres, tile, stroke and tile run on: no pixel is the bare [board]. */
    private fun assertNoBareBoardBetweenTiles(pixels: PixelMap, board: Color) {
        val pitch = pixels.width / 4f
        val x = (pitch * 0.5f).toInt()
        for (y in (pitch * 0.5f).toInt()..(pitch * 1.5f).toInt()) assertNotEquals("bare board at y=$y", board, pixels[x, y])
    }

    @Test
    fun `a dimmed diagonal link stays on the bare board past the tile's rounded corner`() {
        val link = StrandsLink(diagonal, LinkKind.Theme)
        show(listOf(link), StrandsDim(diagonal.toSet(), setOf(link), alpha = 0.4f), end = null, reduceMotion = true)
        rule.mainClock.advanceTimeByFrame()
        val pixels = board()
        assertNotEquals(pixels[1, 1], pixels.corner())
    }

    @Test
    fun `a dissolving rejected diagonal stays on the bare board past the tile's rounded corner`() {
        show(emptyList(), StrandsDim.None, TraceEnd(diagonal, TraceEndKind.Short, 1), reduceMotion = false)
        rule.mainClock.advanceTimeBy(StrandsMotion.ShortHoldMs + 40L)
        val pixels = board()
        assertNotEquals(pixels[1, 1], pixels.corner())
    }
}
