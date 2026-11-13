package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
    fun `unavailable draws the imported line and no row`() {
        show(ReplaySectionState.Unavailable(ReplayUnavailable.ImportedFromLinkedIn), board)

        composeRule.onNodeWithTag(ReplayTags.UNAVAILABLE).assertExists()
        composeRule.onNodeWithText("Replay not available · imported from LinkedIn").assertExists()
        composeRule.onNodeWithTag(ROW).assertDoesNotExist()
    }

    @Test
    fun `unavailable draws the generic line`() {
        show(ReplaySectionState.Unavailable(ReplayUnavailable.NotRecorded), board)
        composeRule.onNodeWithText("No replay for this solve").assertExists()
    }

    @Test
    fun `unavailable with no board draws nothing`() {
        show(ReplaySectionState.Unavailable(ReplayUnavailable.NotRecorded), replayBoard = null)

        composeRule.onNodeWithTag(ReplayTags.UNAVAILABLE).assertDoesNotExist()
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
