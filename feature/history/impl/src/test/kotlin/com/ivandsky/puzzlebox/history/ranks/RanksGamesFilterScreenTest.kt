package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.history.CalendarState
import com.ivandsky.puzzlebox.history.GOLDEN_PHONE
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.history.domain.ranks.RankScope
import com.ivandsky.puzzlebox.history.ranks.RanksSelection.Games
import com.ivandsky.puzzlebox.model.GameId
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

/** GamesFilter (#1707): the Games chip in the filter row, its sheet, the large bar's sub line and the How ranks work line, on the phone and the tablet. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class RanksGamesFilterScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 13)
    private val zip = RanksBoard.Game(GameId("zip"))
    private val places = listOf(BoardPlace(RanksBoard.Overall, "Overall", Standing.Place(4)), BoardPlace(zip, "Zip", Standing.Place(2)))
    private val rows = listOf(
        DailyBoardRowModel(1, "Maya", 62_000L, 0, isSelf = false, playerId = "maya"),
        DailyBoardRowModel(2, "You", 75_000L, 1, isSelf = true, playerId = "you"),
    )
    private val picks = mutableListOf<Games>()
    private var state by mutableStateOf(
        RanksUiState(
            RanksBoardSlice("Zip · today", RankOrder.TimeThenHints, DailyBoardState.Ranked(rows)),
            position = 1,
            boardCount = places.size,
            calendar = CalendarState(shownDay = today, earliestDay = today.minusDays(30), daysWithRows = setOf(today), gamesPerDay = 1, today = today),
            places = places,
        ),
    )

    // A pick feeds back into the state, as the view model's selection does.
    private val actions = RanksActions(onSignIn = {}, onSwipe = {}, onGames = { picks += it; state = state.copy(games = it) })

    private fun show(layout: HistoryLayout = HistoryLayout.SinglePane) {
        composeRule.setContent { PuzzleBoxTheme { RanksScreen(state, actions, layout = layout) } }
    }

    private fun node(tag: String) = composeRule.onNodeWithTag(tag)

    private fun count(tag: String) = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    private fun settle() {
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.mainClock.autoAdvance = true
    }

    private fun openHowRanksWork() {
        node("ranks.title").performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        node("ranks.menu.how").performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
    }

    @Test
    fun `the Games chip sits after Who and When and reads Games`() {
        show()

        node("ranks.filter.games").assertIsDisplayed().assertTextEquals("Games")
        assertTrue(node("ranks.filter.when").getBoundsInRoot().right <= node("ranks.filter.games").getBoundsInRoot().left)
    }

    @Test
    fun `Games offers All games then LinkedIn games, LinkedIn sets the pick, closes the sheet and reads on the chip, All puts it back`() {
        show()
        node("ranks.filter.games").performClick()

        node("ranks.filter.games.sheet").assertIsDisplayed()
        node("ranks.tune.games.All").assertIsSelected()
        node("ranks.tune.games.LinkedIn").assertIsNotSelected()

        node("ranks.tune.games.LinkedIn").performClick()
        settle()
        assertEquals(0, count("ranks.filter.games.sheet"))
        node("ranks.filter.games").assertIsDisplayed().assertTextEquals("LinkedIn games")

        node("ranks.filter.games").performClick()
        node("ranks.tune.games.LinkedIn").assertIsSelected()
        node("ranks.tune.games.All").performClick()
        settle()

        assertEquals(listOf(Games.LinkedIn, Games.All), picks)
        node("ranks.filter.games").assertTextEquals("Games")
    }

    @Test
    fun `the Games sheet rows are at least 48 dp tall`() {
        show()
        node("ranks.filter.games").performClick()

        node("ranks.tune.games.All").assertHeightIsAtLeast(48.dp)
        node("ranks.tune.games.LinkedIn").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `the large bar's sub line adds LinkedIn games only when LinkedIn games is picked`() {
        assertEquals("Global · Day", rankSubLine(RankScope.Global, RanksSelection.Window.Day))
        assertEquals("Friends · Week", rankSubLine(RankScope.Friends, RanksSelection.Window.Week, Games.All))
        assertEquals("Global · Day · LinkedIn games", rankSubLine(RankScope.Global, RanksSelection.Window.Day, Games.LinkedIn))

        state = state.copy(games = Games.LinkedIn)
        show()
        node("ranks.tune.label").assertTextEquals("Global · Day · LinkedIn games")
    }

    @Test
    fun `How ranks work has one Games line`() {
        show()
        openHowRanksWork()
        composeRule.onNodeWithText("LinkedIn games adds up only the LinkedIn games in Overall", substring = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun `on the tablet the Games chip sits in the right pane's filter row and a pick reads on it`() {
        show(HistoryLayout.TwoPane)

        val pane = node("ranks.rightpane").getBoundsInRoot()
        val games = node("ranks.filter.games").assertIsDisplayed().getBoundsInRoot()
        assertTrue(games.right <= pane.right)
        assertTrue(games.bottom <= node("ranks.board").getBoundsInRoot().top)

        node("ranks.filter.games").performClick()
        node("ranks.tune.games.LinkedIn").performClick()
        settle()

        assertEquals(listOf(Games.LinkedIn), picks)
        node("ranks.filter.games").assertTextEquals("LinkedIn games")
    }
}
