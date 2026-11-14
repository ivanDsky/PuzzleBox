package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What sits under the board (the Friends hint, the note) between two boards: it leaves from and lands where it rests. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RanksBoardSlotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val me = DailyBoardRowModel(1, "Ann", 61_000, 0, isSelf = true, playerId = "u1")
    private val friend = DailyBoardRowModel(2, "Maya", 75_000, 0, isSelf = false, playerId = "maya")

    // Friends with nobody on it yet: the viewer's row and the hint. And Friends with connections: two rows and the note.
    private val alone = RanksBoardSlice("Strands · today", RankOrder.TimeThenHints, DailyBoardState.Ranked(listOf(me)), friendsAlone = true)
    private val noted = RanksBoardSlice(
        "Queens · today",
        RankOrder.TimeThenHints,
        DailyBoardState.Ranked(listOf(me, friend)),
        note = "with LinkedIn connections",
    )

    private var morph by mutableStateOf<RanksBoardMorph?>(null)
    private var slice by mutableStateOf(alone)
    private var progress by mutableFloatStateOf(0f)

    private fun show() = composeRule.setContent { PuzzleBoxTheme { RanksBoardSlot(slice, RanksSignIn.Idle, onSignIn = {}, morph = morph) } }

    private fun top(tag: String): Dp = composeRule.onNodeWithTag(tag).getBoundsInRoot().top

    // Where [tag] is on [board] at rest, and where it is at [at] of the way from [from] to [to].
    private fun restAndMorph(tag: String, board: RanksBoardSlice, from: RanksBoardSlice, to: RanksBoardSlice, at: Float): Pair<Dp, Dp> {
        morph = null
        slice = board
        val rest = top(tag)
        progress = at
        morph = RanksBoardMorph(from, to) { progress }
        return rest to top(tag)
    }

    @Test
    fun `the note and the hint land where they rest`() {
        show()

        val (noteRest, noteLanded) = restAndMorph("ranks.board.note", noted, from = alone, to = noted, at = 1f)
        val (hintRest, hintLanded) = restAndMorph("ranks.board.friendsHint", alone, from = noted, to = alone, at = 1f)

        assertEquals(noteRest, noteLanded)
        assertEquals(hintRest, hintLanded)
    }

    @Test
    fun `the note and the hint leave from where they rest`() {
        show()

        val (noteRest, noteLeaving) = restAndMorph("ranks.board.note", noted, from = noted, to = alone, at = 0f)
        val (hintRest, hintLeaving) = restAndMorph("ranks.board.friendsHint", alone, from = alone, to = noted, at = 0f)

        assertEquals(noteRest, noteLeaving)
        assertEquals(hintRest, hintLeaving)
    }
}
