package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.size
import androidx.compose.ui.unit.width
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.MorphAlpha
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.TutorialStep
import com.ivandsky.puzzlebox.gameui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.gameui.replay.GameReplayBoard
import com.ivandsky.puzzlebox.gameui.replay.ReplayBoardOptions
import com.ivandsky.puzzlebox.gameui.replay.ReplayTags
import com.ivandsky.puzzlebox.gameui.replay.ReplayUnavailable
import com.ivandsky.puzzlebox.history.HistoryDayRow
import com.ivandsky.puzzlebox.history.HistoryReplays
import com.ivandsky.puzzlebox.history.ReplaySolve
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The hub's Your solve card as approved on build .5: one line of one height whatever the day's replay (F19), two icon
 * buttons (F14), a solve that rolls and frosts in place when the game changes (F16), and no change during a drag (A40).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class YourSolveRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val day = LocalDate.of(2026, 11, 12)
    private val zip = GameId("zip")
    private val zipBrand = GameBrand(AccentSlot.Blue, GameGlyphs.Zip)
    private val watchable = HistoryReplays.One(ReplayFixtures.timeline(ReplayFixtures.SHORT_MOVES), ReplaySolve.Record)
    private val imported = HistoryReplays.Unavailable(ReplayUnavailable.ImportedFromLinkedIn)

    private var solve by mutableStateOf<YourSolve?>(null)
    private val opened = mutableListOf<ReplaySolve>()

    private fun solve(
        replays: HistoryReplays = HistoryReplays.Hidden,
        time: Long = 95_000L,
        hints: Int = 1,
        brand: GameBrand = zipBrand,
        playUi: GamePlayUi? = ReplayUi(withBoard = true),
    ): YourSolve {
        val ref = object : PuzzleRef {
            override val gameId = zip
            override val key = PuzzleKey.Daily(day, 594)
        }
        val row = HistoryDayRow(day, 594, zip, "Zip", brand = brand, record = CompletionRecord(ref, time, hints, RecordStats(1, "{}")))
        return YourSolve(row, replays, playUi)
    }

    private fun show(first: YourSolve) {
        solve = first
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                YourSolveRow(solve, onPlayAgain = { _, _ -> }, onOpenReplay = { _, which -> opened += which }, onCompare = {})
            }
        }
    }

    private fun switchTo(next: YourSolve) {
        solve = next
        // The clock is stepped by hand, so flush the write before the next frame.
        Snapshot.sendApplyNotifications()
    }

    private fun bounds(tag: String) = composeRule.onNodeWithTag(tag).getBoundsInRoot()

    private fun count(tag: String) = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    // The layers of the card's parts that are part-way through frosting in or out.
    private fun frosting(): Int {
        val midway = SemanticsMatcher("is mid-frost") { node -> node.config.getOrNull(MorphAlpha)?.let { it < 1f } == true }
        return composeRule.onAllNodes(midway, useUnmergedTree = true).fetchSemanticsNodes().size
    }

    @Test
    fun `the card is as tall with a replay, with a solve that has none, and with none to show`() {
        show(solve(watchable))

        val heights = listOf<HistoryReplays>(watchable, imported, HistoryReplays.Hidden).map {
            solve = solve(it)
            bounds("ranks.yoursolve").height
        }

        // 8 dp over and under the 48 dp buttons.
        assertEquals(listOf(64.dp, 64.dp, 64.dp), heights)
    }

    @Test
    fun `a solve with no replay says so on the label, and its button is off and says why`() {
        show(solve(imported))

        composeRule.onNodeWithText("Your solve · No replay").assertIsDisplayed()
        composeRule.onNodeWithTag(ReplayTags.UNAVAILABLE)
            .assertIsNotEnabled()
            .assertContentDescriptionEquals("Watch replay, No replay · imported from LinkedIn")

        solve = solve(watchable)
        composeRule.onNodeWithText("Your solve").assertIsDisplayed()
        composeRule.onAllNodesWithText("Your solve · No replay").assertCountEquals(0)
        assertEquals(0, count(ReplayTags.UNAVAILABLE))
    }

    @Test
    fun `the reason a day has no replay names the import or the solve, and is nothing for a day with one or none to show`() {
        assertEquals("No replay · imported from LinkedIn", imported.unavailableText())
        assertEquals("No replay for this solve", HistoryReplays.Unavailable(ReplayUnavailable.NotRecorded).unavailableText())
        assertEquals(null, watchable.unavailableText())
        assertEquals(null, HistoryReplays.Hidden.unavailableText())
    }

    @Test
    fun `watch and play again are 48 dp icon buttons on the card's one line, with no text of their own`() {
        show(solve(watchable))

        val watch = bounds(ReplayTags.WATCH_ROW)
        val again = bounds("ranks.yoursolve.playagain")
        assertEquals(DpSize(48.dp, 48.dp), watch.size)
        assertEquals(DpSize(48.dp, 48.dp), again.size)
        assertEquals(watch.top, again.top)
        assertEquals(8.dp, again.left - watch.right)
        composeRule.onNodeWithTag(ReplayTags.WATCH_ROW).assertContentDescriptionEquals("Watch replay, 12 moves · 0:12")
        composeRule.onNodeWithTag("ranks.yoursolve.playagain").assertContentDescriptionEquals("Play again")
        composeRule.onAllNodesWithText("Watch replay", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Play again").assertCountEquals(0)

        composeRule.onNodeWithTag(ReplayTags.WATCH_ROW).performClick()
        assertEquals(listOf(ReplaySolve.Record), opened)
    }

    @Test
    fun `the score spells out no hints, one hint and several`() {
        show(solve(hints = 0))
        composeRule.onNodeWithTag("ranks.yoursolve.score").assertTextEquals("1:35 · no hints")

        solve = solve(hints = 1)
        composeRule.onNodeWithTag("ranks.yoursolve.score").assertTextEquals("1:35 · 1 hint")

        solve = solve(hints = 3)
        composeRule.onNodeWithTag("ranks.yoursolve.score").assertTextEquals("1:35 · 3 hints")
    }

    @Test
    fun `a game with no replay board shows no way into a replay and no word about one`() {
        show(solve(watchable, playUi = null))
        assertEquals(0, count(ReplayTags.WATCH_ROW))

        solve = solve(imported, playUi = ReplayUi(withBoard = false))
        assertEquals(0, count(ReplayTags.UNAVAILABLE))
        composeRule.onNodeWithText("Your solve").assertIsDisplayed()

        solve = solve(watchable)
        assertEquals(1, count(ReplayTags.WATCH_ROW))
    }

    @Test
    fun `another game's solve frosts the glyph and the play again button over while the card stays one card`() {
        show(solve(brand = zipBrand))
        composeRule.mainClock.autoAdvance = false

        switchTo(solve(brand = GameBrand(AccentSlot.Orange, GameGlyphs.Tango)))
        composeRule.mainClock.advanceTimeBy(100)

        assertEquals(1, count("ranks.yoursolve"))
        // The glyph and the button: each has the old layer on its way out and the new one on its way in.
        assertEquals(4, frosting())

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertEquals(0, frosting())
    }

    @Test
    fun `a new time rolls to its width and does not jump there`() {
        show(solve(time = 95_000L))
        val before = bounds("ranks.yoursolve.score").width
        composeRule.mainClock.autoAdvance = false

        switchTo(solve(time = 760_000L))
        composeRule.mainClock.advanceTimeBy(50)
        composeRule.onNodeWithTag("ranks.yoursolve.score").assertTextEquals("12:40 · 1 hint")
        val rolling = bounds("ranks.yoursolve.score").width

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        val after = bounds("ranks.yoursolve.score").width
        assertTrue("12:40 is wider than 1:35: $after > $before", after > before)
        assertTrue("50 ms in, the minutes are still rolling up from one digit: $rolling < $after", rolling < after)
    }

    @Test
    fun `during a drag Your solve stays the starting board's, and is the next board's once the swipe commits`() {
        val solves = listOf(solve(time = 95_000L, hints = 1), solve(time = 48_000L, hints = 0))
        val board = RanksBoardSlice(
            "Zip · Nov 12",
            RankOrder.TimeThenHints,
            DailyBoardState.Ranked(listOf(DailyBoardRowModel(1, "Maya", 62_000L, 0, isSelf = false, playerId = "maya"))),
        )
        var at by mutableIntStateOf(0)
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                // The hub goes to the board a swipe asks for, and its solve with it, as the view model has it.
                RanksScreen(RanksUiState(board, position = at, boardCount = 2, yourSolve = solves[at]), onSignIn = {}, onSwipe = { at += it })
            }
        }

        composeRule.onNodeWithTag("ranks.board").performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(-30f, 0f)) }
        }
        composeRule.onNodeWithTag("ranks.yoursolve.score").assertTextEquals("1:35 · 1 hint")

        composeRule.onNodeWithTag("ranks.board").performTouchInput { up() }
        composeRule.onNodeWithTag("ranks.yoursolve.score").assertTextEquals("0:48 · no hints")
    }
}

/** A game's play UI of which only [replayBoard] matters to the card; every other member is unused here. */
private class ReplayUi(withBoard: Boolean) : GamePlayUi {
    override val gameId = GameId("zip")
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
