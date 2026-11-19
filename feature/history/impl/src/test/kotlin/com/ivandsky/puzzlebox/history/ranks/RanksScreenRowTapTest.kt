package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ComputeMatchLegs
import com.ivandsky.puzzlebox.domain.CreateMatch
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.lineup.ValidateExactPuzzles
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.history.CalendarState
import com.ivandsky.puzzlebox.history.HistoryTestSupport
import com.ivandsky.puzzlebox.history.domain.ranks.GetPlayerActions
import com.ivandsky.puzzlebox.history.domain.ranks.RankScope
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.resultui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.resultui.board.DailyBoardState
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchLocalRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeVisibilityRepository
import java.time.LocalDate
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Taps on the rows of a board on the hub: which player each row opens, and when a row takes no tap at all. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RanksScreenRowTapTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val maya = DailyBoardState.Ranked(listOf(DailyBoardRowModel(1, "Maya", 62_000L, 0, false, playerId = "maya")))
    private val drawn = RanksUiState(RanksBoardSlice("Zip · today", RankOrder.TimeThenHints, maya), position = 1, boardCount = 4)
    private val loading = drawn.copy(board = RanksBoardSlice("Zip · Thu, Nov 12", RankOrder.TimeThenHints, DailyBoardState.Loading))

    @Test
    fun `a row of the board kept on screen still opens its player while the picked day loads`() {
        val zipDrawn = drawn.copy(
            places = listOf(BoardPlace(RanksBoard.Overall, "Overall", Standing.Place(1)), BoardPlace(RanksBoard.Game(GameId("zip")), "Zip", Standing.Place(1))),
            calendar = CalendarState(selectedDay = today),
        )
        var state by mutableStateOf(zipDrawn)
        val sheet = playerSheet()
        composeRule.setContent {
            PuzzleBoxTheme {
                val open by sheet.state.collectAsState()
                RanksScreen(state, RanksActions({}, {}, onOpenPlayer = sheet::open), playerSheet = open)
            }
        }

        state = zipDrawn.copy(board = loading.board, calendar = CalendarState(selectedDay = today.minusDays(1)))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Maya").performClick()

        composeRule.onNodeWithTag("ranks.player.name").assertTextEquals("Maya")
        val target = sheet.state.value?.target
        assertEquals(GameId("zip"), target?.game)
        assertEquals(today, target?.day)
    }

    // The tap lands while the drawn board is still held: once the answer arrives the board is the live one and the
    // old gate would open the row too, so only a tap during the hold tells the two apart.
    @Test
    fun `a row tap while a scope change loads opens its player from the board on screen`() {
        var state by mutableStateOf(drawn)
        val sheet = playerSheet()
        composeRule.setContent {
            PuzzleBoxTheme {
                val open by sheet.state.collectAsState()
                RanksScreen(state, RanksActions({}, {}, onOpenPlayer = sheet::open), playerSheet = open)
            }
        }
        composeRule.mainClock.autoAdvance = false

        state = loading.copy(scope = RankScope.Friends)
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithText("Maya").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("ranks.player.name").assertTextEquals("Maya")
    }

    @Test
    fun `a board whose rows cannot open a player gives its rows no click action`() {
        composeRule.setContent { PuzzleBoxTheme { RanksScreen(drawn, RanksActions({}, {})) } }

        composeRule.onNodeWithTag("daily_board.row").assertHasNoClickAction()
    }

    @Test
    fun `a tap on another player's row opens the player sheet on them`() {
        val sheet = playerSheet()
        composeRule.setContent {
            PuzzleBoxTheme {
                val open by sheet.state.collectAsState()
                RanksScreen(drawn, RanksActions({}, onSwipe = {}, onOpenPlayer = sheet::open), playerSheet = open)
            }
        }

        composeRule.onNodeWithText("Maya").performClick()

        composeRule.onNodeWithTag("ranks.player.name").assertTextEquals("Maya")
    }

    private fun playerSheet(): PlayerSheetViewModel {
        val registry = HistoryTestSupport.registry
        val daily = FakeDailyRepository()
        val completions = FakeCompletionRepository()
        val createMatch = CreateMatch(
            FakeMatchRepository(),
            FakeMatchLocalRepository(),
            ComputeMatchLegs(registry, daily, FakeGameCalendar(today), ObserveGameHistory(registry, daily, completions), FakeReceivedPuzzleRepository(), Random(0)),
            FakeIdentityService(Identity(uid = "u1", displayName = "Ann", photoUrl = null)),
            Random(0),
            ValidateExactPuzzles(FakeSharedPuzzleRepository()),
        )
        return PlayerSheetViewModel(GetPlayerActions(FakeVisibilityRepository(), completions), createMatch, registry, GamePlayRegistry(emptyList()))
    }

    private companion object {
        val today: LocalDate = LocalDate.of(2026, 11, 13)
    }
}
