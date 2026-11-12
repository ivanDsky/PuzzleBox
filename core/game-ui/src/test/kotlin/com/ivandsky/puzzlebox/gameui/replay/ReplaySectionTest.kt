package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Ticket 45: the three cases of the one replay section every single-board surface shows. */
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
        override fun Board(frame: ReplayFrame, modifier: Modifier) {
            Text("facts ${frame.facts.correct}", modifier.testTag(BOARD))
        }
    }

    private fun show(state: ReplaySectionState, replayBoard: GameReplayBoard?) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                ReplaySection(state, FakePlayUi(GameId("fake"), replayBoard))
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
    }

    @Test
    fun `hidden draws nothing even with a board`() {
        show(ReplaySectionState.Hidden, board)

        composeRule.onNodeWithTag(BOARD).assertDoesNotExist()
        composeRule.onNodeWithTag(ReplayTags.PLAY).assertDoesNotExist()
    }

    @Test
    fun `ready with no board draws nothing`() {
        show(ReplaySectionState.Ready(timeline), replayBoard = null)

        composeRule.onNodeWithTag(BOARD).assertDoesNotExist()
        composeRule.onNodeWithTag(ReplayTags.PLAY).assertDoesNotExist()
    }

    @Test
    fun `ready with a board draws the board and the transport`() {
        show(ReplaySectionState.Ready(timeline), board)

        composeRule.onNodeWithTag(BOARD).assertExists()
        composeRule.onNodeWithTag(ReplayTags.PLAY).assertExists()
    }

    private companion object {
        const val BOARD = "replay-section-board"
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
