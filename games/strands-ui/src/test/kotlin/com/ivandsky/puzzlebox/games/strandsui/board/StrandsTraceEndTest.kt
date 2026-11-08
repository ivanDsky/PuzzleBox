package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.LocalSnapTrace
import com.ivandsky.puzzlebox.games.strands.StrandsState
import com.ivandsky.puzzlebox.games.strands.render.StrandsRendererModel
import com.ivandsky.puzzlebox.games.strandsui.StrandsBoardContent
import com.ivandsky.puzzlebox.games.strandsui.StrandsUiFixtures
import com.ivandsky.puzzlebox.games.strandsui.Trace
import com.ivandsky.puzzlebox.games.strandsui.TraceEnd
import com.ivandsky.puzzlebox.games.strandsui.TraceEndKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A submitted word that found no theme word stays on the board, on the board's own frame
 * clock: a refused one red and shaking, then dissolving, a set-aside one neutral; reduced
 * motion holds the colour still; a touch ends it at once. Read from pixels of the light board.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class StrandsTraceEndTest {

    @get:Rule
    val rule = createComposeRule()

    private val end = mutableStateOf<TraceEnd?>(null)

    private fun show(reduceMotion: Boolean, snap: Boolean = false) {
        rule.mainClock.autoAdvance = false
        val state = StrandsState(StrandsUiFixtures.puzzle)
        rule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                CompositionLocalProvider(LocalSnapTrace provides snap) {
                    Box(Modifier.size(256.dp)) {
                        StrandsBoardContent(
                            model = StrandsRendererModel.of(state, emptyList()),
                            found = null,
                            reduceMotion = reduceMotion,
                            trace = { Trace() },
                            onTrace = {},
                            traceEnd = { end.value },
                        )
                    }
                }
            }
        }
    }

    private fun MutableState<TraceEnd?>.post(value: TraceEnd?) {
        this.value = value
        Snapshot.sendApplyNotifications()
    }

    private fun refused(seq: Int = 1) = TraceEnd(StrandsUiFixtures.row(0), TraceEndKind.Error, seq)

    /** True when some pixel is the refusal's red (the light theme's 0xBA1A1A); no other board colour is that red and that dark in green and blue. */
    private fun redOnBoard(): Boolean {
        val pixels: PixelMap = rule.onNodeWithTag("strands.board").captureToImage().toPixelMap()
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) if (pixels[x, y].isRefusalRed()) return true
        return false
    }

    private fun Color.isRefusalRed() = red > 0.55f && green < 0.25f && blue < 0.25f

    @Test
    fun `a refused word turns its path red, then leaves the board when the 320 ms are up`() {
        show(reduceMotion = false)
        assertFalse(redOnBoard())

        end.post(refused())
        rule.mainClock.advanceTimeBy(100)
        assertTrue(redOnBoard())

        rule.mainClock.advanceTimeBy(100)
        assertTrue(redOnBoard())

        rule.mainClock.advanceTimeBy(300)
        assertFalse(redOnBoard())
    }

    @Test
    fun `a refusal twice in a row plays twice`() {
        show(reduceMotion = false)
        end.post(refused(seq = 1))
        rule.mainClock.advanceTimeBy(400)
        assertFalse(redOnBoard())

        end.post(refused(seq = 2))
        rule.mainClock.advanceTimeBy(50)
        assertTrue(redOnBoard())
    }

    @Test
    fun `a set-aside word is never red`() {
        show(reduceMotion = false)
        end.post(TraceEnd(StrandsUiFixtures.row(0), TraceEndKind.Short, 1))
        rule.mainClock.advanceTimeBy(50)
        assertFalse(redOnBoard())
    }

    @Test
    fun `under reduce motion the red holds for 600 ms without moving, then clears`() {
        show(reduceMotion = true)
        end.post(refused())
        rule.mainClock.advanceTimeBy(100)
        val first = rule.onNodeWithTag("strands.board").captureToImage().toPixelMap().buffer
        rule.mainClock.advanceTimeBy(400)
        val later = rule.onNodeWithTag("strands.board").captureToImage().toPixelMap().buffer
        assertTrue(redOnBoard())
        assertEquals(first.toList(), later.toList())

        rule.mainClock.advanceTimeBy(150)
        assertFalse(redOnBoard())
    }

    @Test
    fun `a touch on the board ends the red at once`() {
        show(reduceMotion = false)
        end.post(refused())
        rule.mainClock.advanceTimeBy(60)
        assertTrue(redOnBoard())

        rule.onNodeWithTag("strands.board").performTouchInput { down(Offset(width * 0.9f, height * 0.9f)) }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        assertFalse(redOnBoard())
    }

    @Test
    fun `with SnapTrace on a refused word still turns red, shakes and leaves when the 320 ms are up`() {
        show(reduceMotion = false, snap = true)
        assertFalse(redOnBoard())

        end.post(refused())
        rule.mainClock.advanceTimeBy(100)
        assertTrue(redOnBoard())

        rule.mainClock.advanceTimeBy(100)
        assertTrue(redOnBoard())

        rule.mainClock.advanceTimeBy(300)
        assertFalse(redOnBoard())
    }

    @Test
    fun `with SnapTrace on reduce motion holds the red for 600 ms, then clears`() {
        show(reduceMotion = true, snap = true)
        end.post(refused())
        rule.mainClock.advanceTimeBy(500)
        assertTrue(redOnBoard())

        rule.mainClock.advanceTimeBy(250)
        assertFalse(redOnBoard())
    }

    @Test
    fun `with SnapTrace on a touch on the board ends the red at once`() {
        show(reduceMotion = false, snap = true)
        end.post(refused())
        rule.mainClock.advanceTimeBy(60)
        assertTrue(redOnBoard())

        rule.onNodeWithTag("strands.board").performTouchInput { down(Offset(width * 0.9f, height * 0.9f)) }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        assertFalse(redOnBoard())
    }
}
