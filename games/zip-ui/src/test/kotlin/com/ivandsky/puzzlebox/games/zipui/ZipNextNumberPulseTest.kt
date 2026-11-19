package com.ivandsky.puzzlebox.games.zipui

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
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
 * The halo on the next number to reach, on the real play screen (nav-rethink B3, ticket 42; backlog #1151).
 * With ZipDotPulse the number's circle holds the trace colour and a halo ripples out of it, never stilling for
 * a finger: it runs while drawing too. Without ZipDotPulse, and on static boards, there is no next-number mark.
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
    fun `without ZipDotPulse the live board draws no next-number mark`() {
        show()
        composeRule.onNodeWithTag("play.next_number_ring").assertDoesNotExist()
        composeRule.onNodeWithTag("play.next_number_halo").assertDoesNotExist()
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
        const val STEP_MS = 100L
        const val SETTLE_MS = 400L
    }
}
