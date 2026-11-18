package com.ivandsky.puzzlebox.games.strandsui.chrome

import android.provider.Settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import com.ivandsky.puzzlebox.designsystem.CelebrationMotion
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strandsui.StrandsPlayController
import com.ivandsky.puzzlebox.games.strandsui.StrandsPlayScreen
import com.ivandsky.puzzlebox.games.strandsui.StrandsUiFixtures
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.LocalSolveRestOnBoard
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Nav-rethink 104: with the rest on, a Strands solve stays on its board for the solve moment; a tap skips it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class StrandsSolveRestTest {

    @get:Rule
    val rule = createComposeRule()

    private var handOffs = 0
    private val buzzes = ArrayList<HapticFeedbackType>()

    /** Records the haptics asked of it: the solve buzz goes through [LocalHapticFeedback], where the Vibration switch gates it. */
    private val haptics = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            buzzes += hapticFeedbackType
        }
    }

    @After
    fun restoreMotion() = setAnimatorScale(1f)

    private fun setAnimatorScale(scale: Float) {
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    private fun adopted() = StrandsPlayController(
        PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {}),
    ).also { assertEquals(Adopt.Adopted, it.adoptToday(StrandsUiFixtures.envelope(), StrandsUiFixtures.ref)) }

    private fun show(controller: StrandsPlayController, rest: Boolean) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalSolveRestOnBoard provides rest, LocalHapticFeedback provides haptics) {
                PuzzleBoxTheme(darkTheme = false) {
                    StrandsPlayScreen(
                        controller = controller,
                        status = PlayBoardStatus.Ready,
                        layout = PlayLayout.SinglePane,
                        sidePanel = null,
                        onRetry = {},
                        onSolved = { _, _ -> handOffs++ },
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(16)
    }

    // Frame by frame: with nothing animating, one big step would skip past the timer's wake-up.
    private fun advance(ms: Long) = repeat((ms / STEP_MS).toInt()) { rule.mainClock.advanceTimeBy(STEP_MS) }

    private fun StrandsPlayController.solve() = (0 until 4).forEach { row ->
        StrandsUiFixtures.row(row).also { path ->
            path.forEach(::tap)
            tap(path.last())
        }
    }

    @Test
    fun `flag off, the hand-off is at once`() {
        val controller = adopted()
        show(controller, rest = false)
        rule.runOnIdle { controller.solve() }
        advance(500)
        assertEquals(1, handOffs)
    }

    @Test
    fun `with the rest on the solved board stays for the solve moment, then hands off once`() {
        val controller = adopted()
        show(controller, rest = true)
        rule.runOnIdle { controller.solve() }
        advance(CelebrationMotion.SolveMomentMs / 2)
        assertEquals("still resting", 0, handOffs)
        rule.onNodeWithTag("play.solve_rest").assertExists()
        rule.onNodeWithTag("play.solve_stamp").assertExists()

        advance(CelebrationMotion.SolveMomentMs)
        assertEquals(1, handOffs)
        rule.onNodeWithTag("play.solve_rest").assertDoesNotExist()
        rule.onNodeWithTag("play.solve_stamp").assertDoesNotExist()
        advance(5_000)
        assertEquals("one solve, one hand-off", 1, handOffs)
    }

    @Test
    fun `with the rest on the success buzz comes once as the solve starts`() {
        val controller = adopted()
        show(controller, rest = true)
        rule.runOnIdle { controller.solve() }
        advance(200)
        assertEquals(listOf(HapticFeedbackType.Confirm), buzzes)
        advance(CelebrationMotion.SolveMomentMs * 2)
        assertEquals("once, not again at the hand-off", 1, buzzes.size)
    }

    @Test
    fun `with the rest off a solve confirms once`() {
        val controller = adopted()
        show(controller, rest = false)
        rule.runOnIdle { controller.solve() }
        advance(1_000)
        assertEquals(listOf(HapticFeedbackType.Confirm), buzzes)
    }

    @Test
    fun `a restored solved board does not buzz`() {
        val controller = adopted()
        controller.solve()
        show(controller, rest = true)
        advance(500)
        assertEquals(emptyList<HapticFeedbackType>(), buzzes)
    }

    @Test
    fun `a tap on the board during the rest hands off at once`() {
        val controller = adopted()
        show(controller, rest = true)
        rule.runOnIdle { controller.solve() }
        advance(600)
        rule.onNodeWithTag("play.solve_stamp").performClick()
        rule.mainClock.advanceTimeBy(16)
        assertEquals("a tap on the stamp's area passes to the layer under it", 1, handOffs)
        advance(5_000)
        assertEquals(1, handOffs)
    }

    @Test
    fun `reduced motion keeps the same rest`() {
        setAnimatorScale(0f)
        val controller = adopted()
        show(controller, rest = true)
        rule.runOnIdle { controller.solve() }
        advance(CelebrationMotion.SolveMomentMs / 2)
        assertEquals(0, handOffs)
        rule.onNodeWithTag("play.solve_rest").assertExists()
        rule.onNodeWithTag("play.solve_stamp").assertDoesNotExist()
        advance(CelebrationMotion.SolveMomentMs)
        assertEquals(1, handOffs)
    }

    @Test
    fun `a restored solved board hands off at once even with the rest on`() {
        val controller = adopted()
        controller.solve()
        show(controller, rest = true)
        rule.mainClock.advanceTimeBy(16)
        assertEquals(1, handOffs)
        rule.onNodeWithTag("play.solve_rest").assertDoesNotExist()
    }

    private fun boardWidth(): Float = rule.onNodeWithTag("strands.board").fetchSemanticsNode().boundsInRoot.width

    private fun boardPixels(): PixelMap = rule.onNodeWithTag("strands.board").captureToImage().toPixelMap()

    // Inside the 3 dp frame line: the middle of the top edge, the right edge, and the left edge near its top (the lap's last stretch).
    private fun PixelMap.top(): Color = this[width / 2, 2]
    private fun PixelMap.right(): Color = this[width - 3, height / 2]
    private fun PixelMap.left(): Color = this[2, height / 4]

    /** Bare board just inside the frame line, between two letters of the top row. */
    private fun PixelMap.insideTop(): Color = this[width / 2, 12]

    /** The board's width at every 16 ms frame for [ms] after the solve. */
    private fun boardWidths(ms: Int): List<Float> = List(ms / 16) {
        rule.mainClock.advanceTimeBy(16)
        boardWidth()
    }

    /** Whole frames, idle after each: bare clock steps were measured to show the rest's hand-off late (Sudoku: 1696 ms against 1520 ms). */
    private fun frames(count: Int) = repeat(count) {
        rule.mainClock.advanceTimeBy(FRAME_MS.toLong())
        rule.waitForIdle()
    }

    @Test
    fun `with the rest on the board pops as the band leaves and is back at rest before the frame closes`() {
        val controller = adopted()
        show(controller, rest = true)
        val atRest = boardWidth()
        rule.runOnIdle { controller.solve() }
        val widths = boardWidths(1_296)

        assertEquals("still at rest 400 ms in", atRest, widths[400 / 16 - 1])
        val widest = widths.max()
        assertEquals("the pop's peak", CelebrationMotion.BoardPopScale, widest / atRest, 0.002f)
        val peakMs = (widths.indexOf(widest) + 1) * 16
        assertTrue("peak at $peakMs ms", peakMs in 540..620)
        assertEquals("nothing moves by 1300 ms", atRest, widths.last())
    }

    @Test
    fun `with the rest on the accent line laps the board clockwise and is closed by 1300 ms`() {
        val controller = adopted()
        show(controller, rest = true)
        val before = boardPixels()
        rule.runOnIdle { controller.solve() }
        frames(1_000 / FRAME_MS)
        val lapping = boardPixels()
        frames(350 / FRAME_MS)
        val closed = boardPixels()

        assertEquals("bare board on the edge before the solve", before.insideTop(), before.top())
        assertTrue("the line is on the top edge at 1000 ms", lapping.top() != before.top())
        assertEquals("and down the right edge", lapping.top(), lapping.right())
        assertEquals("the left edge is the lap's last stretch", before.left(), lapping.left())
        assertEquals(lapping.top(), closed.top())
        assertEquals("the frame is closed", closed.top(), closed.left())
        assertEquals("the line stays on the edge", before.insideTop(), closed.insideTop())
    }

    @Test
    fun `flag off, the board neither pops nor takes a frame line`() {
        val controller = adopted()
        show(controller, rest = false)
        val atRest = boardWidth()
        val before = boardPixels()
        rule.runOnIdle { controller.solve() }
        boardWidths(1_296).forEachIndexed { frame, width -> assertEquals("frame $frame", atRest, width) }
        advance(100)
        val after = boardPixels()
        assertEquals(before.top(), after.top())
        assertEquals(before.left(), after.left())
    }

    @Test
    fun `reduced motion shows the closed frame at once and does not pop`() {
        setAnimatorScale(0f)
        val controller = adopted()
        show(controller, rest = true)
        val atRest = boardWidth()
        val before = boardPixels()
        rule.runOnIdle { controller.solve() }
        frames(3)
        val closed = boardPixels()

        assertTrue("the line is there", closed.top() != before.top())
        assertEquals(closed.top(), closed.right())
        assertEquals(closed.top(), closed.left())
        boardWidths(1_296).forEachIndexed { frame, width -> assertEquals("frame $frame", atRest, width) }
    }

    @Test
    fun `a restored solved board shows no frame line`() {
        val controller = adopted()
        controller.solve()
        show(controller, rest = true)
        advance(100)
        val pixels = boardPixels()
        assertEquals("bare board on the top edge", pixels.insideTop(), pixels.top())
        assertEquals("and the left", pixels.insideTop(), pixels.left())
    }

    private companion object {
        const val STEP_MS = 50L
        const val FRAME_MS = 16
    }
}
