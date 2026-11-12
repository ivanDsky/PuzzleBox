package com.ivandsky.puzzlebox.gameui.replay

import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Ticket 65: the phone compare pages between the two boards under one shared transport. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ReplayFlipPeekTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 6 moves, one a second. */
    private val mine = ReplayFixtures.timeline(6)

    /** 8 moves, one a second, a hint with every second one. */
    private val theirs = ReplayFixtures.timeline(8, hintEvery = 2)

    private val names = CompareNames(mine = "You", theirs = "Maya")

    private val board = object : GameReplayBoard {
        override val pieces = "pieces"

        @Composable
        override fun Board(frame: ReplayFrame, modifier: Modifier) {
            Text("facts ${frame.facts.correct} hints ${frame.facts.hints}", modifier.testTag(BOARD))
        }
    }

    private fun show(mine: Timeline? = this.mine, theirs: Timeline? = this.theirs, reducedMotion: Boolean = false) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, if (reducedMotion) 0f else 1f)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                ReplayCompare(mine, theirs, board, names, CompareLayout.FlipPeek, Modifier.fillMaxSize())
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun boardTexts(): List<String> {
        val nodes = composeRule.onAllNodesWithTag(BOARD)
        return nodes.fetchSemanticsNodes().indices.map { index ->
            nodes[index].fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)
                ?.joinToString("") { it.text }.orEmpty()
        }
    }

    private fun firstBoardLeft(): Float =
        composeRule.onAllNodesWithTag(BOARD)[0].getBoundsInRoot().left.value

    private fun tap(tag: String) {
        composeRule.onNodeWithTag(tag).performClick()
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun seekTo(ms: Long) {
        composeRule.onNodeWithTag(ReplayTags.SEEK).performSemanticsAction(SemanticsActions.SetProgress) { it(ms.toFloat()) }
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun settle() = composeRule.mainClock.advanceTimeBy(SETTLE_MS)

    @Test
    fun `both sides show their tabs with the first selected and the other board peeking in`() {
        show()

        composeRule.onNodeWithTag(ReplayFlipTags.TAB_MINE).assertIsSelected()
        composeRule.onNodeWithTag(ReplayFlipTags.TAB_THEIRS).assertIsNotSelected()
        assertEquals(2, boardTexts().size)
        composeRule.onAllNodesWithTag(ReplayTags.PLAY).assertCountEquals(1)
    }

    @Test
    fun `a tab tap flips the board and keeps the position and the paused state`() {
        show()
        settle()
        seekTo(3_000)

        tap(ReplayFlipTags.TAB_THEIRS)
        settle()

        composeRule.onNodeWithTag(ReplayFlipTags.TAB_THEIRS).assertIsSelected()
        composeRule.onNodeWithTag(ReplayFlipTags.TAB_MINE).assertIsNotSelected()
        composeRule.onNodeWithTag(ReplayTags.POSITION).assertTextEquals("0:03 / 0:08")
        composeRule.onNodeWithTag(ReplayTags.PLAY).assertContentDescriptionEquals(ReplayCopy.PLAY)
    }

    @Test
    fun `a flip while playing keeps playing`() {
        show()
        settle()
        seekTo(0)
        tap(ReplayTags.PLAY)

        tap(ReplayFlipTags.TAB_THEIRS)
        composeRule.mainClock.advanceTimeBy(300)

        composeRule.onNodeWithTag(ReplayFlipTags.TAB_THEIRS).assertIsSelected()
        composeRule.onNodeWithTag(ReplayTags.PLAY).assertContentDescriptionEquals(ReplayCopy.PAUSE)
    }

    @Test
    fun `the first open nudges toward the other board once and settles back`() {
        show()
        val rest = firstBoardLeft()

        composeRule.mainClock.advanceTimeBy(NUDGE_HALF_MS)
        assertTrue("nudged left of rest", firstBoardLeft() < rest - 1f)

        settle()
        assertEquals(rest, firstBoardLeft(), 1f)

        tap(ReplayFlipTags.TAB_THEIRS)
        settle()
        tap(ReplayFlipTags.TAB_MINE)
        settle()
        assertEquals(rest, firstBoardLeft(), 1f)
    }

    @Test
    fun `reduced motion has no nudge`() {
        show(reducedMotion = true)
        val rest = firstBoardLeft()

        composeRule.mainClock.advanceTimeBy(NUDGE_HALF_MS)

        assertEquals(rest, firstBoardLeft(), 0.5f)
    }

    @Test
    fun `a hint on the hidden side flashes its tab`() {
        show()
        settle()
        seekTo(0)

        tap(ReplayTags.PLAY)
        composeRule.mainClock.advanceTimeBy(2_000 + FIRST_FRAME_SLACK_MS)

        composeRule.onNodeWithText(ReplayCopy.hintGain(1)).assertExists()
    }

    @Test
    fun `a hint on the visible side does not flash`() {
        show()
        settle()
        tap(ReplayFlipTags.TAB_THEIRS)
        settle()
        seekTo(0)

        tap(ReplayTags.PLAY)
        composeRule.mainClock.advanceTimeBy(2_000 + FIRST_FRAME_SLACK_MS)

        composeRule.onAllNodesWithText(ReplayCopy.hintGain(1)).assertCountEquals(0)
    }

    @Test
    fun `a seek past a hint on the hidden side does not flash but the count updates`() {
        show()
        settle()

        seekTo(3_000)
        composeRule.mainClock.advanceTimeBy(300)

        composeRule.onAllNodesWithText(ReplayCopy.hintGain(1)).assertCountEquals(0)
        assertEquals("facts 3 hints 1", boardTexts()[1])
    }

    @Test
    fun `the tabs are at least a touch target tall`() {
        show()

        composeRule.onNodeWithTag(ReplayFlipTags.TAB_MINE).assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag(ReplayFlipTags.TAB_THEIRS).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `reduced motion keeps the hint count current without a flash`() {
        show(reducedMotion = true)
        seekTo(0)

        tap(ReplayTags.PLAY)
        composeRule.mainClock.advanceTimeBy(2_000 + FIRST_FRAME_SLACK_MS)

        composeRule.onAllNodesWithText(ReplayCopy.hintGain(1)).assertCountEquals(0)
        assertEquals("facts 2 hints 1", boardTexts()[1])
    }

    @Test
    fun `only mine shows my board and a disabled No replay tab`() {
        show(theirs = null)

        assertEquals(1, boardTexts().size)
        composeRule.onNodeWithTag(ReplayFlipTags.TAB_MINE).assertIsSelected()
        composeRule.onNodeWithTag(ReplayFlipTags.TAB_THEIRS).assertIsNotEnabled()
        composeRule.onNodeWithText(ReplayCopy.NO_REPLAY).assertExists()
    }

    @Test
    fun `only theirs shows their board with theirs selected and mine disabled`() {
        show(mine = null)

        assertEquals(listOf("facts 8 hints 4"), boardTexts())
        composeRule.onNodeWithTag(ReplayFlipTags.TAB_THEIRS).assertIsSelected()
        composeRule.onNodeWithTag(ReplayFlipTags.TAB_MINE).assertIsNotEnabled()
        composeRule.onNodeWithText(ReplayCopy.NO_REPLAY).assertExists()
    }

    private companion object {
        const val BOARD = "fake-board"
        const val FIRST_FRAME_SLACK_MS = 50L

        /** Past the nudge out and back plus a flip. */
        const val SETTLE_MS = 1_500L

        /** Mid-way through the nudge's way out. */
        const val NUDGE_HALF_MS = 120L
    }
}
