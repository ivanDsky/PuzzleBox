package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.replay.ReplayCodec
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import com.ivandsky.puzzlebox.game.replay.ReplayRecord
import com.ivandsky.puzzlebox.game.replay.StampedMove
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.gameui.replay.ReplayBoardOptions
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoReplay
import com.ivandsky.puzzlebox.games.tango.TangoReplayMove
import com.ivandsky.puzzlebox.games.tango.TangoSession
import com.ivandsky.puzzlebox.model.SolveReplay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The replay board's violation marks through a real [Timeline]: a violation the player fixed or
 * re-tapped inside the settle window is never drawn at any position, a real one is drawn from the
 * settle window on. Counts the cells the board reports as violating through their semantics.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class TangoReplayDoubleTapTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val initial = TangoSession(ref = null, puzzle = TangoPlayFixture.puzzle)

    private fun at(atMs: Long, move: TangoReplayMove) = StampedMove(atMs, move.toReplayMove())

    private fun timeline(vararg moves: StampedMove, endMs: Long = 9_000): Timeline {
        val stored = checkNotNull(ReplayCodec.encode(ReplayRecord(TangoDefinition.id, ReplayOutcome.GaveUp, endMs, moves.toList())))
        return checkNotNull(Timeline.open(TangoDefinition.id, TangoReplay, initial, checkNotNull(SolveReplay.of(stored))))
    }

    private val violating = SemanticsMatcher("violating cell") {
        it.config.getOrNull(SemanticsProperties.StateDescription)?.contains("violates") ?: false
    }

    private fun violatingCells() = composeRule.onAllNodes(violating).fetchSemanticsNodes().size

    /** The number of violating cells the board draws at each of [times]. */
    private fun drawn(timeline: Timeline, times: List<Long>): List<Long> {
        val frame = mutableStateOf<ReplayFrame>(timeline.at(0))
        composeRule.setContent {
            PuzzleBoxTheme {
                Box { TangoReplayBoard.Board(frame.value, ReplayBoardOptions(animate = false, showErrors = true), Modifier.size(320.dp)) }
            }
        }
        return times.map { tMs ->
            composeRule.runOnIdle { frame.value = timeline.at(tMs) }
            composeRule.waitForIdle()
            violatingCells().toLong()
        }
    }

    private fun Timeline.sweep(): List<Long> = (0L..2_400L step 50L).toList() + listOf(moveTimesMs.last(), endMs)

    @Test
    fun `the board's settle window is the timeline's settle window`() {
        assertEquals(MotionTokens.SettleMillis, Timeline.MISTAKE_SETTLE_MS)
    }

    @Test
    fun `a double tap draws no violation at any position`() {
        val timeline = timeline(
            at(1_000, TangoReplayMove.Place(2, TangoCell.SUN)),
            at(1_000, TangoReplayMove.Rejection),
            at(1_300, TangoReplayMove.Place(2, TangoCell.MOON)),
        )

        assertEquals(List(timeline.sweep().size) { 0L }, drawn(timeline, timeline.sweep()))
    }

    @Test
    fun `a violation undone inside the settle window is never drawn`() {
        val timeline = timeline(
            at(1_000, TangoReplayMove.Place(2, TangoCell.SUN)),
            at(1_000, TangoReplayMove.Rejection),
            at(1_499, TangoReplayMove.Undo),
        )

        assertEquals(List(timeline.sweep().size) { 0L }, drawn(timeline, timeline.sweep()))
    }

    @Test
    fun `a real violation is hidden inside the settle window and drawn from it on`() {
        val timeline = timeline(
            at(1_000, TangoReplayMove.Place(2, TangoCell.SUN)),
            at(1_000, TangoReplayMove.Rejection),
            at(3_000, TangoReplayMove.Place(8, TangoCell.MOON)),
        )
        val times = listOf(999L, 1_000L, 1_250L, 1_499L, 1_500L, 2_000L, 2_999L, 3_000L)

        // Three suns in row 0: cells 0, 1 (givens) and 2.
        assertEquals(listOf(0L, 0L, 0L, 0L, 3L, 3L, 3L, 3L), drawn(timeline, times))
    }
}
