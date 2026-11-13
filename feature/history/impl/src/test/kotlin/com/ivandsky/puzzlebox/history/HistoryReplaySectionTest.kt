package com.ivandsky.puzzlebox.history

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
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
import com.ivandsky.puzzlebox.gameui.replay.GameReplayBoard
import com.ivandsky.puzzlebox.gameui.replay.ReplayBoardOptions
import com.ivandsky.puzzlebox.gameui.replay.ReplayTags
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.api.ReplayCompareKey
import com.ivandsky.puzzlebox.play.api.ReplayKey
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Ticket 67: which replay rows a History day shows, and which solve each one opens. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HistoryReplaySectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val short = ReplayFixtures.timeline(ReplayFixtures.SHORT_MOVES)
    private val long = ReplayFixtures.timeline(ReplayFixtures.SHORT_MOVES, stepMs = 2_000)
    private val opened = mutableListOf<ReplaySolve>()
    private var compares = 0

    private fun show(replays: HistoryReplays, ui: GamePlayUi? = ReplayUi(withBoard = true)) {
        opened.clear()
        compares = 0
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                HistoryReplaySection(replays, ui, onOpen = { opened += it }, onCompare = { compares++ })
            }
        }
    }

    private fun rows() = composeRule.onAllNodesWithTag(ReplayTags.WATCH_ROW)

    @Test
    fun `hidden draws nothing`() {
        show(HistoryReplays.Hidden)

        rows().assertCountEquals(0)
        composeRule.onNodeWithText("First solve").assertDoesNotExist()
    }

    @Test
    fun `one draws one row and no labels`() {
        show(HistoryReplays.One(short, solve = ReplaySolve.Latest))

        rows().assertCountEquals(1)
        composeRule.onNodeWithText("12 moves").assertExists()
        composeRule.onNodeWithText("0:12").assertExists()
        composeRule.onNodeWithText("First solve").assertDoesNotExist()
        composeRule.onNodeWithText("Latest").assertDoesNotExist()
    }

    @Test
    fun `one holding the record solve opens the record`() {
        show(HistoryReplays.One(short, solve = ReplaySolve.Record))

        rows()[0].performClick()

        assertEquals(listOf(ReplaySolve.Record), opened)
    }

    @Test
    fun `one holding the latest solve opens the latest`() {
        show(HistoryReplays.One(short, solve = ReplaySolve.Latest))

        rows()[0].performClick()

        assertEquals(listOf(ReplaySolve.Latest), opened)
    }

    @Test
    fun `first and latest draw two labelled rows with their own times`() {
        show(HistoryReplays.FirstAndLatest(short, long))

        rows().assertCountEquals(2)
        composeRule.onNodeWithText("First solve").assertExists()
        composeRule.onNodeWithText("Latest").assertExists()
        composeRule.onNodeWithText("0:12").assertExists()
        composeRule.onNodeWithText("0:24").assertExists()
    }

    @Test
    fun `each of the two rows opens its own solve`() {
        show(HistoryReplays.FirstAndLatest(short, long))

        rows()[0].performClick()
        rows()[1].performClick()

        assertEquals(listOf(ReplaySolve.Record, ReplaySolve.Latest), opened)
    }

    @Test
    fun `two different replays offer Compare and it reports a tap`() {
        show(HistoryReplays.FirstAndLatest(short, long))

        composeRule.onNodeWithContentDescription("Compare first solve with latest").performClick()

        assertEquals(1, compares)
    }

    @Test
    fun `one replay offers no Compare`() {
        show(HistoryReplays.One(short, solve = ReplaySolve.Record))

        composeRule.onNodeWithText("Compare").assertDoesNotExist()
    }

    @Test
    fun `no replay offers no Compare`() {
        show(HistoryReplays.Hidden)

        composeRule.onNodeWithText("Compare").assertDoesNotExist()
    }

    @Test
    fun `a game without a replay board offers no Compare`() {
        show(HistoryReplays.FirstAndLatest(short, long), ui = ReplayUi(withBoard = false))

        composeRule.onNodeWithText("Compare").assertDoesNotExist()
    }

    @Test
    fun `the compare key sets the latest solve against the first and names both`() {
        val row = HistoryDayRow(
            day = java.time.LocalDate.of(2026, 11, 4),
            puzzleNumber = 7,
            gameId = GameId("queens"),
            gameName = "Queens",
            record = null,
            currentYear = 2026,
        )

        assertEquals(
            ReplayCompareKey.of(
                mine = ReplayKey.daily("queens", "2026-11-04", 7, latest = true),
                theirs = ReplayKey.daily("queens", "2026-11-04", 7, latest = false),
                myName = "Latest",
                theirName = "First solve",
            ),
            historyCompareKey(row),
        )
    }

    @Test
    fun `a game without a replay board shows no rows and no labels`() {
        show(HistoryReplays.FirstAndLatest(short, long), ui = ReplayUi(withBoard = false))

        rows().assertCountEquals(0)
        composeRule.onNodeWithText("First solve").assertDoesNotExist()
        composeRule.onNodeWithText("Latest").assertDoesNotExist()
    }

    @Test
    fun `a game with no play UI shows nothing`() {
        show(HistoryReplays.One(short, solve = ReplaySolve.Latest), ui = null)

        rows().assertCountEquals(0)
    }
}

/** Only [replayBoard] matters to the section; every other member is unused here. */
private class ReplayUi(withBoard: Boolean) : GamePlayUi {
    override val gameId = GameId("fake")
    override val replayBoard: GameReplayBoard? = if (withBoard) FakeBoard else null

    override val tutorial: List<TutorialStep> get() = emptyList()

    override fun createController(env: PlayEnvironment): GamePlayController = error("unused")

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

    private object FakeBoard : GameReplayBoard {
        override val pieces = "pieces"

        @Composable
        override fun Board(frame: ReplayFrame, options: ReplayBoardOptions, modifier: Modifier) = Text("board", modifier)
    }
}
