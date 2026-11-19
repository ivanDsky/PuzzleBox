package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.history.CalendarState
import com.ivandsky.puzzlebox.history.HistoryDayRow
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.history.HistoryReplays
import com.ivandsky.puzzlebox.history.domain.ranks.RankScope
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.resultui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.resultui.board.DailyBoardState
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
 * The Ranks hub's doors (nav rethink N2, N3, N6): the Tune icon opens the Tune sheet, the hero card jumps to the
 * viewer's row, and a game's unplayed day offers Play this day.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RanksDoorsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 13)
    private val pastDay = today.minusDays(3)
    private val zip = GameId("zip")
    private val places = listOf(
        BoardPlace(RanksBoard.Overall, "Overall", Standing.Place(4)),
        BoardPlace(RanksBoard.Game(zip), "Zip", Standing.Place(30)),
    )
    private val calendar = CalendarState(
        shownDay = pastDay,
        earliestDay = today.minusDays(30),
        selectedDay = pastDay,
        daysWithRows = setOf(today),
        gamesPerDay = 1,
        today = today,
    )

    private fun rows(self: Boolean = true) =
        (1..30).map { DailyBoardRowModel(it, "Player $it", it * 1_000L, 0, isSelf = self && it == 30, playerId = "p$it") }

    private fun solve() = YourSolve(
        HistoryDayRow(
            pastDay,
            594,
            zip,
            "Zip",
            record = CompletionRecord(
                object : PuzzleRef {
                    override val gameId = zip
                    override val key = PuzzleKey.Daily(pastDay, 594)
                },
                time = 75_000L,
                hints = 1,
                stats = RecordStats(1, "{}"),
            ),
        ),
        HistoryReplays.Hidden,
        playUi = null,
    )

    private fun state(
        position: Int = 1,
        scope: RankScope = RankScope.Friends,
        self: Boolean = true,
        yourSolve: YourSolve? = null,
        playThisDay: PlayThisDay? = null,
        calendar: CalendarState = this.calendar,
    ) = RanksUiState(
        RanksBoardSlice("Zip · $pastDay", RankOrder.TimeThenHints, DailyBoardState.Ranked(rows(self))),
        position = position,
        boardCount = places.size,
        calendar = calendar,
        places = places,
        scope = scope,
        yourSolve = yourSolve,
        playThisDay = playThisDay,
    )

    private fun show(state: RanksUiState, actions: RanksActions, layout: HistoryLayout = HistoryLayout.SinglePane) {
        composeRule.setContent { PuzzleBoxTheme { RanksScreen(state, actions, layout = layout, tuneDefaults = previewTuneDefaults()) } }
    }

    private val actions = RanksActions(onSignIn = {}, onSwipe = {})

    // --- Tune icon (N6) ---

    @Test
    fun `the Tune icon opens the Tune sheet and is a 48 dp button`() {
        show(state(), actions)

        composeRule.onNodeWithTag("ranks.tune").assertHasClickAction()
        // The icon's layout box is under 48 dp; its tap target is the 48 dp Material asks for.
        assertTrue(composeRule.onNodeWithTag("ranks.tune").fetchSemanticsNode().touchBoundsInRoot.height >= 48 * composeRule.density.density)
        composeRule.onNodeWithTag("ranks.tune").performClick()

        composeRule.onNodeWithTag("ranks.tune.sheet").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TWO_PANE)
    fun `on the tablet the Tune icon opens the Tune sheet too`() {
        show(state(), actions, HistoryLayout.TwoPane)

        composeRule.onNodeWithTag("ranks.tune").performClick()

        composeRule.onNodeWithTag("ranks.tune.sheet").assertIsDisplayed()
    }

    // --- Hero card (N6) ---

    @Test
    @Config(qualifiers = TWO_PANE)
    fun `the hero card jumps the board to the viewer's row`() {
        show(state(), actions, HistoryLayout.TwoPane)
        composeRule.onAllNodesWithTag("leaderboard.dock").assertCountEquals(1)

        composeRule.onNodeWithTag("ranks.hero").assertHasClickAction()
        composeRule.onNodeWithTag("ranks.hero").performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag("leaderboard.dock").assertCountEquals(0)
        composeRule.onNodeWithTag("daily_board.row.self").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TWO_PANE)
    fun `the hero card is plain when the viewer has no row`() {
        show(state(self = false), actions, HistoryLayout.TwoPane)

        composeRule.onNodeWithTag("ranks.hero").assertHasNoClickAction()
    }

    // --- Play this day (N2) ---

    @Test
    fun `an unplayed day offers Play this day, which opens that game on that day`() {
        val opened = mutableListOf<Pair<GameId, LocalDate>>()
        show(state(playThisDay = PlayThisDay(zip, pastDay)), actions.copy(onPlayThisDay = { game, day -> opened += game to day }))

        composeRule.onNodeWithTag("ranks.playthisday").assertIsDisplayed().assertHasClickAction()
        assertTrue(composeRule.onNodeWithTag("ranks.playthisday").getBoundsInRoot().let { it.bottom - it.top } >= 48.dp)
        composeRule.onNodeWithTag("ranks.playthisday").performClick()

        assertEquals(listOf(zip to pastDay), opened)
    }

    @Test
    fun `a day with no offer shows no Play this day`() {
        show(state(playThisDay = null), actions)

        composeRule.onAllNodesWithTag("ranks.playthisday").assertCountEquals(0)
    }

    @Test
    fun `a solved day shows Your solve as it does today`() {
        show(state(yourSolve = solve()), actions)

        composeRule.onNodeWithTag("ranks.yoursolve").assertIsDisplayed()
        composeRule.onAllNodesWithTag("ranks.playthisday").assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = TWO_PANE)
    fun `on the tablet an unplayed day offers Play this day under the hero card`() {
        show(state(playThisDay = PlayThisDay(zip, pastDay)), actions, HistoryLayout.TwoPane)

        composeRule.onNodeWithTag("ranks.playthisday").assertIsDisplayed()
    }

    // --- Calendar entry (N3) ---

    @Test
    fun `a hub opened with the month shows the month on the phone`() {
        show(state(calendar = calendar.copy(expanded = true)), actions)

        composeRule.onNodeWithTag("history.calendar.month", useUnmergedTree = true).assertTextEquals("November 2026")
    }

    @Test
    @Config(qualifiers = TWO_PANE)
    fun `a hub opened with the month shows the month on the tablet`() {
        show(state(calendar = calendar.copy(expanded = true)), actions, HistoryLayout.TwoPane)

        composeRule.onNodeWithTag("history.calendar.month", useUnmergedTree = true).assertTextEquals("November 2026")
    }

    private companion object {
        const val TWO_PANE = "w1280dp-h800dp-land-mdpi"
    }
}
