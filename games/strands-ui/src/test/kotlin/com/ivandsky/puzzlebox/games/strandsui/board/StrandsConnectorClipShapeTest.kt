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
        val model = StrandsRendererModel.of(StrandsState(StrandsUiFixtures.puzzle), emptyList()).copy(links = links)
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
