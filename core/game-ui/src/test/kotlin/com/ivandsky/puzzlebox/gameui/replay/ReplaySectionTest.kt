package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.TutorialStep
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tickets 45 and 48: the three cases of the one replay section every single-board surface shows: the Watch replay row, or nothing. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ReplaySectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val timeline = ReplayFixtures.timeline(ReplayFixtures.SHORT_MOVES)

    private val board = object : GameReplayBoard {
        override val pieces = "pieces"

        @Composable
        override fun Board(frame: ReplayFrame, options: ReplayBoardOptions, modifier: Modifier) {
            Text("facts ${frame.facts.correct}", modifier.testTag(BOARD))
        }
    }

    private var opened = 0

    private fun show(state: ReplaySectionState, replayBoard: GameReplayBoard?) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                ReplaySection(state, FakePlayUi(GameId("fake"), replayBoard), onOpen = { opened++ })
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
    }

    @Test
    fun `hidden draws nothing even with a board`() {
        show(ReplaySectionState.Hidden, board)

        composeRule.onNodeWithTag(ROW).assertDoesNotExist()
    }

    @Test
    fun `ready with no board draws nothing`() {
        show(ReplaySectionState.Ready(timeline), replayBoard = null)

        composeRule.onNodeWithTag(ROW).assertDoesNotExist()
    }

    @Test
    fun `ready with a board draws the row with the move count and the play time, and no player`() {
        show(ReplaySectionState.Ready(timeline), board)

        composeRule.onNodeWithTag(ROW).assertExists()
        composeRule.onNodeWithText("Watch replay").assertExists()
        composeRule.onNodeWithText("12 moves").assertExists()
        composeRule.onNodeWithText("0:12").assertExists()
        composeRule.onNodeWithTag(BOARD).assertDoesNotExist()
        composeRule.onNodeWithTag(ReplayTags.PLAY).assertDoesNotExist()
    }

    @Test
    fun `unavailable draws a disabled Watch replay row with the imported reason`() {
        show(ReplaySectionState.Unavailable(ReplayUnavailable.ImportedFromLinkedIn), board)

        composeRule.onNodeWithTag(ReplayTags.UNAVAILABLE).assertExists().assertIsNotEnabled().assertHasNoClickAction()
        composeRule.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag(ReplayTags.UNAVAILABLE))).assertCountEquals(0)
        composeRule.onNodeWithText("Watch replay").assertExists()
        composeRule.onNodeWithText("Not available · imported from LinkedIn").assertExists()
        composeRule.onNodeWithTag(ROW).assertDoesNotExist()
    }

    @Test
    fun `unavailable draws the generic reason`() {
        show(ReplaySectionState.Unavailable(ReplayUnavailable.NotRecorded), board)

        composeRule.onNodeWithText("Watch replay").assertExists()
        composeRule.onNodeWithText("Not available for this solve").assertExists()
    }

    @Test
    fun `unavailable with no board draws nothing`() {
        show(ReplaySectionState.Unavailable(ReplayUnavailable.NotRecorded), replayBoard = null)

        composeRule.onNodeWithTag(ReplayTags.UNAVAILABLE).assertDoesNotExist()
    }

    @Test
    fun `the Ready row is enabled and clickable`() {
        show(ReplaySectionState.Ready(timeline), board)

        composeRule.onNodeWithTag(ROW).assertIsEnabled()
        composeRule.onNode(hasClickAction() and hasAnyAncestor(hasTestTag(ROW))).assertIsEnabled().assertHasClickAction()
    }

    @Test
    fun `the unavailable row is the ready row drawn at 38 percent`() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Column {
                    ReplaySection(ReplaySectionState.Ready(timeline), FakePlayUi(GameId("fake"), board), onOpen = {})
                    ReplaySection(ReplaySectionState.Unavailable(ReplayUnavailable.NotRecorded), FakePlayUi(GameId("fake"), board), onOpen = {})
                }
            }
        }
        val ready = composeRule.onNodeWithTag(ROW).captureToImage().toPixelMap()
        val unavailable = composeRule.onNodeWithTag(ReplayTags.UNAVAILABLE).captureToImage().toPixelMap()
        assertEquals("the two rows share one layout", ready.height, unavailable.height)

        // On the play disc, clear of its glyph: the disc's colour at full strength, and the same disc faded over the surface.
        val x = (5 * composeRule.density.density).toInt()
        val y = ready.height / 2
        val surface = ready[ready.width - 1, y]
        val full = ready[x, y]
        val faded = unavailable[x, y]
        val channels = listOf(surface.red to (full.red to faded.red), surface.green to (full.green to faded.green), surface.blue to (full.blue to faded.blue))
        val (back, pair) = channels.maxBy { (back, pair) -> kotlin.math.abs(back - pair.first) }
        val alpha = (back - pair.second) / (back - pair.first)
        assertEquals("disabled alpha, from $surface over $full to $faded", 0.38f, alpha, 0.03f)
    }

    @Test
    fun `tapping the row opens the replay`() {
        show(ReplaySectionState.Ready(timeline), board)

        composeRule.onNodeWithTag(ROW).performClick()

        assertEquals(1, opened)
    }

    private companion object {
        const val BOARD = "replay-section-board"
        const val ROW = ReplayTags.WATCH_ROW
    }
}

/** Only [replayBoard] matters to the section; every other member is unused here. */
private class FakePlayUi(override val gameId: GameId, override val replayBoard: GameReplayBoard?) : GamePlayUi {
    override fun createController(env: PlayEnvironment): GamePlayController = error("unused")

    override val tutorial: List<TutorialStep> get() = emptyList()

    @Composable
    override fun Board(controller: GamePlayController, modifier: Modifier) = Unit

    @Composable
    override fun Screen(
        controller: GamePlayController,
        status: PlayBoardStatus,
        layout: PlayLayout,
        sidePanel: (@Composable ColumnScope.() -> Unit)?,
        onRetry: () -> Unit,
        onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
        modifier: Modifier,
    ) = Unit
}
