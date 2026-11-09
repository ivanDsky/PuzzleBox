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
 * clock paused and stepped frame by frame: the connector between two letters is as thick as
 * the letters have shrunk or grown, and not there at all before they start, so no bar shows
 * behind a shrunken tile; and the bottom row's tiles keep their whole lip at the dip and at the
 * peak. PINECONE ends on the bottom row: N at (7, 1), then E at (7, 0).
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

    /** The letters of the bottom row's two steps' ends: (7, 1) is the word's sixth letter, (7, 0) its seventh. */
    private val last = word.indexOf(Coord(7, 0))
    private val before = last - 1

    private var density = 1f
    private var pitch = 0f
    private var left = 0f
    private var top = 0f

    /** Shows the board, finds [word] and leaves the clock paused on the frame that starts its settle. */
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
        word.forEach(controller::tap)
        controller.tap(word.last())
        rule.mainClock.advanceTimeBy(32)
        val bounds = rule.onNodeWithTag("strands.board").fetchSemanticsNode().boundsInRoot
        pitch = bounds.width / 6
        left = bounds.left
        top = bounds.top
    }

    private fun frame(elapsedMs: Int): PixelMap {
        rule.mainClock.advanceTimeBy(elapsedMs.toLong())
        return rule.onRoot().captureToImage().toPixelMap()
    }

    private fun differs(a: Color, b: Color) = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue) > 0.15f

    private fun centre(cell: Coord) = (left + (cell.col + 0.5f) * pitch) to (top + (cell.row + 0.5f) * pitch)

    /** The board's own background, sampled in the empty top-left corner of its first cell. */
    private fun PixelMap.bare(): Color = this[(left + 2).toInt(), (top + 2).toInt()]

    /** Pixels of the vertical run through ([x], [y]) that differ from the board's background. */
    private fun PixelMap.run(x: Float, y: Float): Int {
        val bare = bare()
        var from = y.toInt()
        while (differs(this[x.toInt(), from - 1], bare)) from--
        var to = y.toInt()
        while (differs(this[x.toInt(), to + 1], bare)) to++
        return to - from + 1
    }

    /** The bar between the two letters, mid-way between their centres, as thick as the bar is. */
    private fun PixelMap.bar(): Int {
        val (x0, y) = centre(word[before])
        val (x1, _) = centre(word[last])
        return run((x0 + x1) / 2, y)
    }

    private fun px(dp: Dp) = dp.value * density

    @Test
    fun `no bar is drawn between two letters before they start`() {
        found()
        // The seventh letter waits 300 ms; until then the board shows no link between it and its neighbour.
        val early = frame(100)
        val (x0, y) = centre(word[before])
        val (x1, _) = centre(word[last])
        val between = early[((x0 + x1) / 2).toInt(), y.toInt()]
        assertTrue("a bar shows between letters that have not started", !differs(between, early.bare()))
    }

    @Test
    fun `at the deepest dip the bar is as thin as the letters`() {
        found()
        val dip = frame(last * stagger + settle / 4)
        val rest = px(StrandsBoardSpec.ConnectorWidth)
        // Both letters are at or near the 0.62 dip: nothing near the rest thickness.
        assertTrue("bar ${dip.bar()}px at the dip, rest is ${rest}px", dip.bar() <= rest * 0.8f)
    }

    @Test
    fun `at the peak overshoot the bar is as thick as the letters have grown`() {
        found()
        val peak = frame(last * stagger + settle / 2)
        val rest = px(StrandsBoardSpec.ConnectorWidth)
        assertTrue("bar ${peak.bar()}px at the peak, rest is ${rest}px", peak.bar() >= rest * 1.05f)
    }

    @Test
    fun `once settled the bar is its rest thickness`() {
        found()
        val rested = frame(last * stagger + settle + 100)
        assertEquals(px(StrandsBoardSpec.ConnectorWidth), rested.bar().toFloat(), 1.5f)
    }

    /** The lowest tile pixel under [cell]'s centre column, against where the lip's edge belongs at [scale]. */
    private fun PixelMap.lipBottom(cell: Coord): Float {
        val (x, y) = centre(cell)
        var to = y.toInt()
        while (differs(this[x.toInt(), to + 1], bare())) to++
        return to.toFloat()
    }

    private fun assertWholeLip(frame: PixelMap, cell: Coord, scale: Float) {
        val half = pitch * StrandsBoardSpec.TokenFraction / 2
        val expected = centre(cell).second + (half + half * 2 * StrandsBoardSpec.TokenLipFraction) * scale
        assertEquals("lip of $cell at scale $scale", expected, frame.lipBottom(cell), 2.5f)
    }

    @Test
    fun `the bottom row keeps its whole lip at the deepest dip`() {
        found()
        val dip = frame(last * stagger + settle / 4)
        assertWholeLip(dip, word[last], StrandsMotion.SettleDipScale)
    }

    @Test
    fun `the bottom row keeps its whole lip at the peak overshoot`() {
        found()
        val peak = frame(last * stagger + settle / 2)
        assertWholeLip(peak, word[last], StrandsMotion.SettleOvershootScale)
    }
}
