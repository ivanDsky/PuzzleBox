package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Ticket 64 (S7): the four presence cases of the compare, and one transport driving both boards. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ReplayCompareTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 6 moves, one a second: ends at 6 000. */
    private val mine = ReplayFixtures.timeline(6)

    /** 12 moves, one a second: ends at 12 000. */
    private val theirs = ReplayFixtures.timeline(12)

    private val names = CompareNames(mine = "You", theirs = "Maya")

    private val board = object : GameReplayBoard {
        override val pieces = "pieces"

        @Composable
        override fun Board(frame: ReplayFrame, options: ReplayBoardOptions, modifier: Modifier) {
            Text("facts ${frame.facts.correct}", modifier.testTag(BOARD))
        }
    }

    private fun show(mine: Timeline?, theirs: Timeline?, layout: CompareLayout = CompareLayout.Twin) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                ReplayCompare(mine, theirs, board, names, layout, Modifier.fillMaxSize())
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun boards(): List<String> {
        val nodes = composeRule.onAllNodesWithTag(BOARD)
        return nodes.fetchSemanticsNodes().indices.map { index ->
            nodes[index].fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)
                ?.joinToString("") { it.text }.orEmpty()
        }
    }

    private fun tap(tag: String) {
        composeRule.onNodeWithTag(tag).performClick()
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun playFor(ms: Long) = composeRule.mainClock.advanceTimeBy(ms + FIRST_FRAME_SLACK_MS)

    private fun seekTo(ms: Long) {
        composeRule.onNodeWithTag(ReplayTags.SEEK).performSemanticsAction(SemanticsActions.SetProgress) { it(ms.toFloat()) }
        composeRule.mainClock.advanceTimeByFrame()
    }

    @Test
    fun `both present shows two boards, the lead line and one transport`() {
        show(mine, theirs)

        assertEquals(listOf("facts 6", "facts 12"), boards())
        // The fixtures place 12 pieces to my 6, so the pieces rule names Maya the winner.
        composeRule.onNodeWithText("Maya won", substring = true).assertExists()
        composeRule.onAllNodesWithText(ReplayCopy.NO_REPLAY).assertCountEquals(0)
        composeRule.onAllNodesWithTag(ReplayTags.PLAY).assertCountEquals(1)
        composeRule.onAllNodesWithTag(ReplayTags.SEEK).assertCountEquals(1)
        // Each name appears over its board and in the seek strip's legend.
        composeRule.onAllNodesWithText("You").assertCountEquals(2)
        composeRule.onAllNodesWithText("Maya").assertCountEquals(2)
    }

    @Test
    fun `only mine plays my board and says No replay for the other side without a lead line`() {
        show(mine, null)

        assertEquals(listOf("facts 6"), boards())
        composeRule.onNodeWithText(ReplayCopy.NO_REPLAY).assertExists()
        composeRule.onAllNodesWithText("won", substring = true).assertCountEquals(0)
        composeRule.onNodeWithTag(ReplayTags.POSITION).assertTextEquals("0:06 / 0:06")

        seekTo(0)
        tap(ReplayTags.PLAY)
        playFor(2_000)
        assertEquals(listOf("facts 2"), boards())
    }

    @Test
    fun `only theirs plays their board and says No replay in my place without a lead line`() {
        show(null, theirs)

        assertEquals(listOf("facts 12"), boards())
        composeRule.onNodeWithText(ReplayCopy.NO_REPLAY).assertExists()
        composeRule.onAllNodesWithText("won", substring = true).assertCountEquals(0)
        composeRule.onNodeWithTag(ReplayTags.POSITION).assertTextEquals("0:12 / 0:12")

        seekTo(0)
        tap(ReplayTags.PLAY)
        playFor(3_000)
        assertEquals(listOf("facts 3"), boards())
    }

    @Test
    fun `neither draws nothing`() {
        show(null, null)

        composeRule.onAllNodesWithTag(BOARD).assertCountEquals(0)
        composeRule.onAllNodesWithTag(ReplayTags.PLAY).assertCountEquals(0)
        composeRule.onAllNodesWithTag(ReplayTags.SEEK).assertCountEquals(0)
        composeRule.onAllNodesWithText(ReplayCopy.NO_REPLAY).assertCountEquals(0)
    }

    @Test
    fun `one play tap advances both boards`() {
        show(mine, theirs)
        seekTo(0)
        assertEquals(listOf("facts 0", "facts 0"), boards())

        tap(ReplayTags.PLAY)
        playFor(3_000)

        assertEquals(listOf("facts 3", "facts 3"), boards())
    }

    @Test
    fun `the shorter side stops at its end while the longer goes on`() {
        show(mine, theirs)
        seekTo(0)

        tap(ReplayTags.PLAY)
        playFor(9_000)

        assertEquals(listOf("facts 6", "facts 9"), boards())
    }

    @Test
    fun `a side that gave up stays on its give-up board and the line says so`() {
        val gaveUp = ReplayFixtures.timeline(6, outcome = ReplayOutcome.GaveUp)
        show(gaveUp, theirs)
        seekTo(0)

        tap(ReplayTags.PLAY)
        playFor(9_000)

        assertEquals(listOf("facts 6", "facts 9"), boards())
        composeRule.onNodeWithText("You gave up at 0:06 · Maya at 9 pieces").assertExists()
    }

    @Test
    fun `flip and peek pages both boards under the one shared transport`() {
        show(mine, theirs, CompareLayout.FlipPeek)

        assertEquals(listOf("facts 6", "facts 12"), boards())
        composeRule.onAllNodesWithTag(ReplayTags.PLAY).assertCountEquals(1)

        seekTo(0)
        tap(ReplayTags.PLAY)
        playFor(2_000)
        assertEquals(listOf("facts 2", "facts 2"), boards())
    }

    @Test
    fun `flip and peek with only theirs shows their board`() {
        show(null, theirs, CompareLayout.FlipPeek)

        assertEquals(listOf("facts 12"), boards())
    }

    private companion object {
        const val BOARD = "fake-board"

        /** The first frame after play only sets the clock's baseline, so it moves nothing. */
        const val FIRST_FRAME_SLACK_MS = 50L
    }
}
