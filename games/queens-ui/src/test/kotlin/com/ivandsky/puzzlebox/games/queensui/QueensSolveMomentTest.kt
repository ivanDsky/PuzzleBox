package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.designsystem.CelebrationMotion
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.LocalSolveRestOnBoard
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock

/**
 * Nav-rethink 130: Queens' solve moment starts at the solve (the crowns run inside it) and hands off
 * [CelebrationMotion.SolveMomentMs] later. [LocalSolveRestOnBoard] is the play chrome's, which squeezes the crowns' step.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class QueensSolveMomentTest {

    @get:Rule
    val rule = createComposeRule()

    private var handOffs = 0
    private val buzzes = ArrayList<HapticFeedbackType>()

    private val haptics = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            buzzes += hapticFeedbackType
        }
    }

    /** The 8×8 daily, one queen short of solved, on the play screen. */
    private fun oneQueenShort(): QueensBoardState {
        val controller = QueensPlayController(
            env = PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {}),
            settings = InMemoryQueensSettings(),
            hintMode = QueensPlayFixtures.hintMode,
        )
        assertEquals(Adopt.Adopted, controller.adoptToday(QueensPlayFixtures.envelope(), QueensPlayFixtures.ref()))
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(
                LocalSolveRestOnBoard provides true,
                LocalHapticFeedback provides haptics,
            ) {
                PuzzleBoxTheme(darkTheme = false) {
                    QueensPlayScreen(
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
        val board = rule.runOnIdle { checkNotNull(controller.board) }
        commit { QueensPlayFixtures.solution.dropLast(1).forEach { board.place(it) } }
        rule.mainClock.advanceTimeBy(1_000)
        // The move ticks keep the device's clock, which the compose clock does not move: the setup's tick is a second back too.
        ShadowSystemClock.advanceBy(Duration.ofMillis(1_000))
        buzzes.clear()
        return board
    }

    private fun commit(block: () -> Unit) = rule.runOnIdle {
        block()
        Snapshot.sendApplyNotifications()
    }

    private fun QueensBoardState.place(cell: Cell) {
        while (cell !in session.queens) onTap(cell.idx)
    }

    // Frame by frame, idle after each: bare clock steps were measured to show the rest's hand-off late (Sudoku: 1696 ms against 1520 ms).
    private fun advance(ms: Long) = repeat((ms / STEP_MS).toInt()) {
        rule.mainClock.advanceTimeBy(STEP_MS)
        rule.waitForIdle()
    }

    private fun confirms() = buzzes.count { it == HapticFeedbackType.Confirm }

    @Test
    fun `the moment starts at the solve, with the queens running inside it`() {
        val board = oneQueenShort()

        commit { board.place(QueensPlayFixtures.solution.last()) }
        advance(200)
        rule.onNodeWithTag("play.solve_rest").assertExists()
        assertEquals("the buzz came with the solve", 1, confirms())

        rule.mainClock.advanceTimeBy(CelebrationMotion.SolveMomentMs - CLOCK_MARGIN_MS - 200)
        rule.waitForIdle()
        assertEquals("still resting before the moment ends", 0, handOffs)
        rule.mainClock.advanceTimeBy(2 * CLOCK_MARGIN_MS)
        rule.waitForIdle()
        assertEquals("handed off after the solve moment", 1, handOffs)
        rule.onNodeWithTag("play.solve_rest").assertDoesNotExist()

        advance(3_000)
        assertEquals("one solve, one hand-off", 1, handOffs)
        assertEquals("one solve, one buzz", 1, confirms())
    }

    @Test
    fun `the solving queen ticks, then the solve confirms once`() {
        val board = oneQueenShort()

        commit { board.place(QueensPlayFixtures.solution.last()) }
        advance(3_000)

        assertEquals(listOf(HapticFeedbackType.TextHandleMove, HapticFeedbackType.Confirm), buzzes)
    }

    private companion object {
        const val STEP_MS = 16L
        /** Clock margin around [CelebrationMotion.SolveMomentMs]: whole frames, so the clock's steps can't straddle it. */
        const val CLOCK_MARGIN_MS = 50L
    }
}
