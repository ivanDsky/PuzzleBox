package com.ivandsky.puzzlebox.games.zipui

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.LightBoardTokens
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.LocalZipDotPulse
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import java.time.LocalDate
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
 * The ring on the next number to reach, on the real play screen (nav-rethink B3, ticket 42): it pulses in a loop while the board is idle, holds still while a
 * drag is held and under reduced motion. With ZipDotPulse (backlog #1151) there is no ring: the number's circle holds the trace colour and a halo
 * ripples out of it, and the halo never stills for a finger: it runs while drawing too.
 *
 * The fixture's start cell is traced before the screen shows, so the next number is "2" in the
 * middle cell (2, 2) throughout. The clock is paused and stepped by hand.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ZipNextNumberPulseTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val controller = ZipPlayController(PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {})).also {
        check(it.adoptToday(ZipPlayFixture.envelope(), ZipPlayFixture.ref(LocalDate.of(2026, 11, 16))) == Adopt.Adopted)
        it.onDragEvent(DragEvent.Extend(GridCell(0, 0)))
    }

    private var cellComposed: (Int) -> Unit = {}

    private fun show(reducedMotion: Boolean = false, dotPulse: Boolean = false) {
        if (reducedMotion) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                CompositionLocalProvider(
                    LocalZipCellComposed provides { cellComposed(it) },
                    LocalZipDotPulse provides dotPulse,
                ) {
                    ZipPlayScreen(
                        controller = controller,
                        status = PlayBoardStatus.Ready,
                        layout = PlayLayout.SinglePane,
                        sidePanel = null,
                        onRetry = {},
                        onSolved = { _, _ -> },
                    )
                }
            }
        }
        // No frame is run here: the board is laid out and at rest, the pulse has not taken a step.
    }

    /**
     * The board's pixel where the ring of cell ([row], [col]) would be: on the line through the
     * cell's centre, around [RING_RADIUS] of a cell to the right, the pixel deepest inside the
     * stroke (the least red one, the target colour having no red), so an anti-aliased edge
     * pixel is never the sample.
     */
    private fun ringPixel(row: Int = 2, col: Int = 2): Color {
        val pixels = composeRule.onNodeWithTag("play.board").captureToImage().toPixelMap()
        val pitch = pixels.width / 5f
        val y = (pitch * (row + 0.5f)).toInt()
        val from = (pitch * (col + 0.5f + RING_RADIUS - RING_SEARCH)).toInt()
        val to = (pitch * (col + 0.5f + RING_RADIUS + RING_SEARCH)).toInt()
        return (from..to).map { pixels[it, y] }.minBy { it.red }
    }

    /** The ring pixel every [STEP_MS] over a little more than one pulse cycle. */
    private fun ringOverACycle(): List<Color> = List(14) {
        composeRule.mainClock.advanceTimeBy(STEP_MS)
        ringPixel()
    }

    /** Every pixel of the next number's cell (2, 2): any swell or colour shift of its dot changes it. */
    private fun dotCell(): List<Int> {
        val pixels = composeRule.onNodeWithTag("play.board").captureToImage().toPixelMap()
        val pitch = pixels.width / 5
        return (2 * pitch until 3 * pitch).flatMap { y -> (2 * pitch until 3 * pitch).map { x -> pixels[x, y].toArgb() } }
    }

    /** The next number's cell every [STEP_MS] over a little more than one pulse cycle. */
    private fun dotOverACycle(): List<List<Int>> = List(14) {
        composeRule.mainClock.advanceTimeBy(STEP_MS)
        dotCell()
    }

    private fun cellAt(width: Int, height: Int, row: Int, col: Int) =
        Offset(width * (col + 0.5f) / 5, height * (row + 0.5f) / 5)

    @Test
    fun `the idle ring pulses in the target colour and no cell recomposes for it`() {
        show()
        assertEquals("at rest the ring is the full target colour", LightBoardTokens.boardTarget, ringPixel())
        val recomposed = mutableListOf<Int>()
        cellComposed = { recomposed += it }

        val seen = ringOverACycle()

        assertTrue("the ring should fade and return over a cycle, saw $seen", seen.distinct().size > 3)
        assertEquals("the pulse is drawn, never recomposed", emptyList<Int>(), recomposed)
    }

    @Test
    fun `the pulse holds still while a drag is held and starts again when the finger lifts`() {
        show()
        composeRule.mainClock.advanceTimeBy(300) // into the fade, so a pause has something to stop

        composeRule.onNodeWithTag("play.board").performTouchInput {
            down(cellAt(width, height, 0, 0))
            moveTo(cellAt(width, height, 0, 1))
        }
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        val held = ringOverACycle()

        assertEquals("a held drag shows the full, still ring", listOf(LightBoardTokens.boardTarget), held.distinct())

        composeRule.onNodeWithTag("play.board").performTouchInput { up() }
        val lifted = ringOverACycle()

        assertTrue("the pulse should run again after the lift, saw $lifted", lifted.distinct().size > 3)
    }

    @Test
    fun `with reduced motion the ring is there and never pulses`() {
        show(reducedMotion = true)

        val seen = ringOverACycle()

        assertEquals(listOf(LightBoardTokens.boardTarget), seen.distinct())
    }

    @Test
    fun `with ZipDotPulse a halo ripples round the dot, no ring is drawn and no cell recomposes for it`() {
        show(dotPulse = true)
        composeRule.onNodeWithTag("play.next_number_ring").assertDoesNotExist()
        composeRule.onNodeWithTag("play.next_number_halo").assertExists()
        val recomposed = mutableListOf<Int>()
        cellComposed = { recomposed += it }

        val seen = dotOverACycle()

        assertTrue("the halo should ripple over a cycle, saw ${seen.distinct().size} looks", seen.distinct().size > 3)
        assertEquals("the pulse is drawn, never recomposed", emptyList<Int>(), recomposed)
        composeRule.onNodeWithTag("play.next_number_ring").assertDoesNotExist()
    }

    @Test
    fun `with ZipDotPulse the halo keeps rippling while a finger is down and drawing`() {
        show(dotPulse = true)
        composeRule.mainClock.advanceTimeBy(300)

        composeRule.onNodeWithTag("play.board").performTouchInput {
            down(cellAt(width, height, 0, 0))
            moveTo(cellAt(width, height, 0, 1))
        }
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.onNodeWithTag("play.next_number_halo").assertExists()
        val held = dotOverACycle()

        assertTrue("the halo should keep rippling under a held finger, saw ${held.distinct().size} looks", held.distinct().size > 3)

        composeRule.onNodeWithTag("play.board").performTouchInput { up() }
        val lifted = dotOverACycle()

        assertTrue("and after the lift", lifted.distinct().size > 3)
    }

    @Test
    fun `with ZipDotPulse and reduced motion there is no halo and the circle is still`() {
        show(reducedMotion = true, dotPulse = true)
        composeRule.onNodeWithTag("play.next_number_halo").assertDoesNotExist()
        composeRule.onNodeWithTag("play.next_number_ring").assertDoesNotExist()

        assertEquals(1, dotOverACycle().distinct().size)
    }

    private companion object {
        const val RING_RADIUS = 0.42f
        const val RING_SEARCH = 0.04f
        const val STEP_MS = 100L
        const val SETTLE_MS = 400L
    }
}
