package com.ivandsky.puzzlebox.play

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.gameui.replay.CompareLayout
import com.ivandsky.puzzlebox.gameui.replay.CompareNames
import com.ivandsky.puzzlebox.gameui.replay.GameReplayBoard
import com.ivandsky.puzzlebox.gameui.replay.ReplayBoardOptions
import com.ivandsky.puzzlebox.gameui.replay.ReplayCopy
import com.ivandsky.puzzlebox.gameui.replay.ReplayFlipTags
import com.ivandsky.puzzlebox.gameui.replay.ReplayTags
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The compare page: Twin at Expanded width, FlipPeek below it, the empty page for no replay, and the way back. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ReplayCompareScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val board = object : GameReplayBoard {
        override val pieces = "pieces"

        @Composable
        override fun Board(frame: ReplayFrame, options: ReplayBoardOptions, modifier: Modifier) {
            Text("facts ${frame.facts.correct}", modifier.testTag("board"))
        }
    }
    private val names = CompareNames("You", "Maya")
    private val ready = ReplayCompareState.Ready(ReplayFixtures.timeline(6), ReplayFixtures.timeline(12))

    private fun show(
        state: ReplayCompareState,
        widthDp: Int,
        board: GameReplayBoard? = this.board,
        onBack: () -> Unit = {},
    ) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                ReplayCompareScreen(state, board, names, finalLead = null, context = "Fixture · Duel", widthDp = widthDp, onBack = onBack, showErrors = true, onShowErrorsChange = {})
            }
        }
    }

    @Test
    fun `expanded width is twin and anything narrower is flip peek`() {
        assertEquals(CompareLayout.Twin, compareLayoutAt(840))
        assertEquals(CompareLayout.Twin, compareLayoutAt(1280))
        assertEquals(CompareLayout.FlipPeek, compareLayoutAt(839))
        assertEquals(CompareLayout.FlipPeek, compareLayoutAt(411))
    }

    @Test
    fun `at expanded width both boards show side by side with no side tabs`() {
        show(ready, widthDp = 1_000)

        composeRule.onAllNodesWithTag("board").assertCountEquals(2)
        composeRule.onAllNodesWithTag(ReplayFlipTags.TAB_MINE).assertCountEquals(0)
        composeRule.onNodeWithText("Fixture · Duel").assertExists()
    }

    @Test
    fun `below expanded width the sides flip behind two tabs`() {
        show(ready, widthDp = 411)

        composeRule.onNodeWithTag(ReplayFlipTags.TAB_MINE).assertExists()
        composeRule.onNodeWithTag(ReplayFlipTags.TAB_THEIRS).assertExists()
    }

    @Test
    fun `a side without a replay says No replay in its place`() {
        show(ReplayCompareState.Ready(ReplayFixtures.timeline(6), null), widthDp = 1_000)

        composeRule.onNodeWithText(ReplayCopy.NO_REPLAY).assertExists()
    }

    @Test
    fun `no replay on either side is the empty page`() {
        show(ReplayCompareState.None, widthDp = 411)
        composeRule.onNodeWithTag(ReplayTags.NONE).assertExists()
    }

    @Test
    fun `a game without a replay board is the empty page`() {
        show(ready, widthDp = 411, board = null)

        composeRule.onNodeWithTag(ReplayTags.NONE).assertExists()
        composeRule.onAllNodesWithTag("board").assertCountEquals(0)
    }

    @Test
    fun `the back arrow leaves`() {
        var left = false
        show(ready, widthDp = 411, onBack = { left = true })

        composeRule.onNodeWithTag(ReplayTags.PLAY).assertExists()
        composeRule.onNodeWithContentDescription(ReplayCopy.BACK).performClick()

        assertTrue(left)
    }
}
