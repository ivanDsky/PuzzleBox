package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import java.time.Duration
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
import org.robolectric.shadows.ShadowSystemClock

/**
 * S2 on the real Zip board: each committed read ticks, a refused step buzzes, the solve is one
 * confirm, and nothing plays on open, undo or hint.
 *
 * The seam reads the board once per frame, so the steps one gesture injection lands in a single frame are one read
 * and tick once. A gesture only starts once the finger moves, so each stroke opens with a move in the same injection.
 * Each injection is [PACE] after the last on the device's clock, slower than the shared tick gap (#1686), so every
 * read that ticks is heard; the gap itself is MoveHapticsEffectTest's, on a clock it holds.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
class ZipBoardMoveHapticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val played = ArrayList<HapticFeedbackType>()
    private val haptics = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            played += hapticFeedbackType
        }
    }

    private val controller = ZipPlayController(PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {})).also {
        check(it.adoptToday(ZipPlayFixture.envelope(), ZipPlayFixture.ref(LocalDate.of(2026, 11, 16))) == Adopt.Adopted)
    }

    private fun show() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
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
        composeRule.waitForIdle()
    }

    /** One gesture injection per call, [PACE] after the last, so each count below is exact. */
    private fun drag(block: TouchInjectionScope.() -> Unit) {
        ShadowSystemClock.advanceBy(PACE)
        composeRule.onNodeWithTag("play.board").performTouchInput(block)
        composeRule.waitForIdle()
    }

    private fun TouchInjectionScope.at(cell: Int) =
        Offset(width * (cell % 5 + 0.5f) / 5, height * (cell / 5 + 0.5f) / 5)

    private fun path(): List<Int> = composeRule.runOnIdle { controller.session!!.path.map { it.idx } }

    private fun grid(idx: Int) = GridCell(idx / 5, idx % 5)

    @Test
    fun `each step ticks and a drag back ticks`() {
        show()

        drag { down(at(0)); moveTo(at(1)) }
        assertEquals(listOf(HapticFeedbackType.TextHandleMove), played)
        drag { moveTo(at(2)) }
        assertEquals(2, played.size)
        drag { moveTo(at(1)) } // back along the drawn line: a retract
        assertEquals(3, played.size)
        assertEquals(listOf(0, 1), path())
        drag { up() }
    }

    @Test
    fun `a single move that jumps two cells is one tick`() {
        show()
        drag { down(at(0)); moveTo(at(1)) }
        assertEquals(1, played.size)

        drag { moveTo(at(3)) } // cells 2 and 3 in one read

        assertEquals("one read, one tick", 2, played.size)
        assertEquals(listOf(0, 1, 2, 3), path())
        drag { up() }
    }

    @Test
    fun `a refused start buzzes on every refused step`() {
        show()

        drag { down(at(1)); moveTo(at(2)) }
        val first = played.size
        assertTrue("a refused start buzzes", first > 0)
        assertTrue(played.all { it == HapticFeedbackType.Reject })
        drag { up() }

        drag { down(at(1)); moveTo(at(2)) }
        assertTrue("a second refused stroke buzzes again", played.size > first)
        assertTrue(played.all { it == HapticFeedbackType.Reject })
        drag { up() }
    }

    @Test
    fun `a stray across a wall onto a traced cell buzzes once`() {
        show()
        // Snake: 0..4 along row 0, 9..7 back along row 1. Cell 2 has a wall below it, so the stray from 7 is an
        // explained refusal (WallBetween): the one buzz a refused step makes.
        drag { down(at(0)); moveTo(at(1)) }
        listOf(2, 3, 4, 9, 8, 7).forEach { idx -> drag { moveTo(at(idx)) } }
        val ticks = played.size
        assertEquals(listOf(0, 1, 2, 3, 4, 9, 8, 7), path())

        drag { moveTo(at(2)) }

        assertEquals("one buzz for the refused step", ticks + 1, played.size)
        assertEquals(HapticFeedbackType.Reject, played.last())
        assertEquals(listOf(0, 1, 2, 3, 4, 9, 8, 7), path())
        drag { up() }
    }

    @Test
    fun `solving by a drag is one confirm and no tick for the last step`() {
        show()
        composeRule.runOnIdle {
            ZipPlayFixture.solution.dropLast(1).forEach { controller.onDragEvent(DragEvent.Extend(grid(it.idx))) }
        }
        composeRule.waitForIdle()
        played.clear()

        drag {
            down(at(23))
            moveTo(at(24))
            up()
        }

        assertTrue(controller.isComplete)
        assertEquals(1, played.count { it == HapticFeedbackType.Confirm })
        assertEquals(0, played.count { it == HapticFeedbackType.TextHandleMove })
    }

    @Test
    fun `a board that opens with a trace on it plays nothing`() {
        listOf(0, 1, 2).forEach { composeRule.runOnIdle { controller.onDragEvent(DragEvent.Extend(grid(it))) } }

        show()

        assertEquals(emptyList<HapticFeedbackType>(), played)
    }

    @Test
    fun `undo and hint play nothing`() {
        show()
        drag { down(at(0)); moveTo(at(1)) }
        val ticks = played.toList()
        assertEquals(1, ticks.size)

        composeRule.runOnIdle { controller.hint() }
        composeRule.waitForIdle()
        assertEquals(ticks, played)

        composeRule.runOnIdle { controller.undo() }
        composeRule.waitForIdle()
        assertEquals(ticks, played)
    }

    private companion object {
        /** A finger's pace, slower than the shared tick gap. */
        val PACE: Duration = Duration.ofMillis(100)
    }
}
