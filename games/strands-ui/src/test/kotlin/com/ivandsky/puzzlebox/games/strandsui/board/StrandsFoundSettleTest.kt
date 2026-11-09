package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strandsui.RealDay
import com.ivandsky.puzzlebox.games.strandsui.StrandsMotion
import com.ivandsky.puzzlebox.games.strandsui.StrandsPlayController
import com.ivandsky.puzzlebox.games.strandsui.StrandsPlayScreen
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A found word's settle (dip 0.62 → overshoot 1.13 → rest) on a phone-sized 6 × 8 board, the
 * clock paused and stepped frame by frame. The link between two letters carries on the trace
 * line the player drew until its letters start, then is as thick as they are scaled: never
 * absent, never a fat bar behind a shrunken tile. The bottom row's tiles keep their whole lip
 * at the dip and at the peak. PINECONE ends on the bottom row: O at (7, 1), then N at (7, 0).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class StrandsFoundSettleTest {

    @get:Rule
    val rule = createComposeRule()

    private val word = RealDay.themeWords[5]
    private val settle = StrandsMotion.FoundSettle.enterDurationMillis
    private val stagger = StrandsMotion.SettleStaggerMs

    /** The bottom row's step: [from] is the word's sixth letter, [to] its seventh. */
    private val from = word.indexOf(Coord(7, 1))
    private val to = word.indexOf(Coord(7, 0))

    private var density = 1f
    private var pitch = 0f
    private var left = 0f
    private var top = 0f
    private var tapMs = 0L

    /** A captured frame and how long into the settle's own clock it is. */
    private class Shot(val pixels: PixelMap, val localMs: Int)

    /** Shows the board and finds [word]; the clock stays paused on the tap's frame. */
    private fun found() {
        val controller = StrandsPlayController(PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {})).also {
            assertEquals(Adopt.Adopted, it.adoptToday(RealDay.fetched.puzzle, RealDay.fetched.ref))
        }
        rule.mainClock.autoAdvance = false
        rule.setContent {
            density = LocalDensity.current.density
            PuzzleBoxTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    StrandsPlayScreen(controller, PlayBoardStatus.Ready(null), PlayLayout.SinglePane, null, {}, { _, _ -> })
                }
            }
        }
        rule.mainClock.advanceTimeBy(100)
        val bounds = rule.onNodeWithTag("strands.board").fetchSemanticsNode().boundsInRoot
        pitch = bounds.width / 6
        left = bounds.left
        top = bounds.top
        word.forEach(controller::tap)
        controller.tap(word.last())
        tapMs = rule.mainClock.currentTime
    }

    /**
     * The last frame at or before [localMs] into the settle, whose own clock starts [SettleStartMs] after the tap.
     * The frames just before it are captured on the way: after a long jump under the paused clock a capture can still show an earlier frame (measured).
     */
    private fun shot(localMs: Int): Shot {
        val frames = ((tapMs + SettleStartMs + localMs - rule.mainClock.currentTime) / FrameMs).toInt()
        val jump = maxOf(frames - PrimedFrames, 0)
        rule.mainClock.advanceTimeBy(jump * FrameMs)
        repeat(frames - jump) {
            rule.onRoot().captureToImage()
            rule.mainClock.advanceTimeBy(FrameMs)
        }
        val sampled = (rule.mainClock.currentTime - tapMs - SettleStartMs).toInt()
        return Shot(rule.onRoot().captureToImage().toPixelMap(), sampled)
    }

    private fun scale(order: Int, shot: Shot) =
        FoundThemeSpec.frame(shot.localMs, StrandsMotionCue(order, word.size), reduceMotion = false).scale

    private fun differs(a: Color, b: Color) = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue) > 0.15f

    private fun centre(cell: Coord) = (left + (cell.col + 0.5f) * pitch) to (top + (cell.row + 0.5f) * pitch)

    /** The board's own background, sampled in the empty top-left corner of its first cell. */
    private fun PixelMap.bare(): Color = this[(left + 2).toInt(), (top + 2).toInt()]

    /** Pixels of the vertical run through ([x], [y]) that differ from the board's background. */
    private fun PixelMap.run(x: Float, y: Float): Int {
        val bare = bare()
        var first = y.toInt()
        while (differs(this[x.toInt(), first - 1], bare)) first--
        var last = y.toInt()
        while (differs(this[x.toInt(), last + 1], bare)) last++
        return last - first + 1
    }

    /** The bar's thickness mid-way between the bottom row's two letters. */
    private fun PixelMap.bar(): Int {
        val (x0, y) = centre(word[from])
        val (x1, _) = centre(word[to])
        return run((x0 + x1) / 2, y)
    }

    private fun restBar() = StrandsBoardSpec.ConnectorWidth.value * density

    @Test
    fun `from the frame after the tap until its letters start every step of the link is drawn`() {
        found()
        for (localMs in -SettleStartMs + FrameMs.toInt()..to * stagger step FrameMs.toInt()) {
            val frame = shot(localMs)
            for (step in 0 until word.lastIndex) {
                val (x0, y0) = centre(word[step])
                val (x1, y1) = centre(word[step + 1])
                val middle = frame.pixels[((x0 + x1) / 2).toInt(), ((y0 + y1) / 2).toInt()]
                assertTrue("step $step has no link at ${frame.localMs} ms", differs(middle, frame.pixels.bare()))
            }
        }
    }

    @Test
    fun `at the deepest dip the bar is thin, not gone`() {
        found()
        val dip = shot(to * stagger + settle / 4)
        assertEquals(StrandsMotion.SettleDipScale, scale(to, dip), 0.01f)
        val bar = dip.pixels.bar()
        assertTrue("bar ${bar}px at the dip, rest is ${restBar()}px", bar > restBar() * 0.5f && bar < restBar() * 0.9f)
    }

    @Test
    fun `at the peak overshoot the bar is thicker than at rest, no thicker than the tiles`() {
        found()
        // Between the two letters' peaks, 50 ms apart: both are at their largest.
        val peak = shot(to * stagger + settle / 2 - stagger / 2)
        val bar = peak.pixels.bar()
        assertTrue("bar ${bar}px at the peak, rest is ${restBar()}px", bar > restBar() * 1.05f && bar <= restBar() * StrandsMotion.SettleOvershootScale + 2f)
    }

    @Test
    fun `once settled the bar is its rest thickness`() {
        found()
        val rested = shot(to * stagger + settle + 100)
        assertEquals(restBar(), rested.pixels.bar().toFloat(), 1.5f)
    }

    /** The lowest tile pixel under [cell]'s centre column. */
    private fun PixelMap.lipBottom(cell: Coord): Float {
        val (x, y) = centre(cell)
        var last = y.toInt()
        while (differs(this[x.toInt(), last + 1], bare())) last++
        return last.toFloat()
    }

    private fun assertWholeLip(shot: Shot, cell: Coord, scale: Float) {
        val half = pitch * StrandsBoardSpec.TokenFraction / 2
        val expected = centre(cell).second + (half + half * 2 * StrandsBoardSpec.TokenLipFraction) * scale
        assertEquals("lip of $cell at scale $scale", expected, shot.pixels.lipBottom(cell), 1f)
    }

    @Test
    fun `the bottom row keeps its whole lip at the deepest dip`() {
        found()
        val dip = shot(to * stagger + settle / 4)
        assertEquals(StrandsMotion.SettleDipScale, scale(to, dip), 0.01f)
        assertWholeLip(dip, word[to], scale(to, dip))
    }

    @Test
    fun `the bottom row keeps its whole lip at the peak overshoot`() {
        found()
        val peak = shot(to * stagger + settle / 2)
        assertEquals(StrandsMotion.SettleOvershootScale, scale(to, peak), 0.01f)
        assertWholeLip(peak, word[to], scale(to, peak))
    }

    private companion object {
        const val FrameMs = 16L
        const val PrimedFrames = 4

        /** The settle's own clock starts five frames after the tap (measured on the paused clock: the found event, then each tile's and the link's frame loop start). */
        const val SettleStartMs = 5 * FrameMs.toInt()
    }
}
