package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.history.CalendarState
import com.ivandsky.puzzlebox.history.HistoryDayRow
import com.ivandsky.puzzlebox.history.HistoryReplays
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The hub's calendar slot and Your solve row as drawn: local data only (A2, A3). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RanksCalendarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 13)
    private val yesterday = today.minusDays(1)
    private val zip = GameId("zip")

    private val week = CalendarState(
        shownDay = yesterday,
        earliestDay = today.minusDays(30),
        selectedDay = yesterday,
        daysWithRows = setOf(today, yesterday),
        gamesPerDay = 1,
        today = today,
    )
    private val stats = GameStats(4, 9, 31, 22, 95_000L, today.minusDays(30), best = null, rankOrder = RankOrder.TimeThenHints)
    private val solve = YourSolve(
        row = HistoryDayRow(
            day = yesterday,
            puzzleNumber = 594,
            gameId = zip,
            gameName = "Zip",
            record = CompletionRecord(
                puzzleRef = object : PuzzleRef {
                    override val gameId = zip
                    override val key = PuzzleKey.Daily(yesterday, 594)
                },
                time = 95_000L,
                hints = 1,
                stats = RecordStats(1, "{}"),
            ),
        ),
        replays = HistoryReplays.Hidden,
        playUi = null,
    )

    private fun show(state: RanksUiState, onPlayAgain: (GameId, LocalDate) -> Unit = { _, _ -> }) {
        composeRule.setContent { PuzzleBoxTheme { RanksScreen(state, onSignIn = {}, onSwipe = {}, onPlayAgain = onPlayAgain) } }
    }

    private fun state(calendar: CalendarState, yourSolve: YourSolve? = null) =
        RanksUiState(RanksBoardSlice("Zip · Nov 12", RankOrder.TimeThenHints, DailyBoardState.SignedOut), calendar = calendar, stats = stats, yourSolve = yourSolve)

    @Test
    fun `the week strip shows without the stats, the month brings them`() {
        show(state(week))
        composeRule.onNodeWithTag("history.calendar").assertIsDisplayed()
        composeRule.onNodeWithTag("history.stats").assertDoesNotExist()
    }

    @Test
    fun `the expanded month shows the stats strip`() {
        show(state(week.copy(expanded = true)))

        composeRule.onNodeWithTag("history.stats").assertIsDisplayed()
    }

    @Test
    fun `Your solve spells out the time and hints and plays the day again`() {
        var played: Pair<GameId, LocalDate>? = null
        show(state(week, solve)) { game, day -> played = game to day }

        composeRule.onNodeWithTag("ranks.yoursolve.score").assertTextEquals("1:35 · 1 hint")
        composeRule.onNodeWithTag("ranks.yoursolve.playagain").performClick()

        assertEquals(zip to yesterday, played)
    }
}
