package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.history.CalendarState
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.history.RanksTuneDefaults
import com.ivandsky.puzzlebox.history.domain.ranks.RankScope
import com.ivandsky.puzzlebox.history.ranks.RanksSelection.Window
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

/** Ranks' Who and When as chips (epic picker-filters, ticket 07), through the real [RanksScreen]. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-mdpi")
class RanksFilterChipsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 13)
    private val zip = RanksBoard.Game(GameId("zip"))
    private val places = listOf(BoardPlace(RanksBoard.Overall, "Overall", Standing.Place(4)), BoardPlace(zip, "Zip", Standing.Place(2)))
    private val rows = listOf(
        DailyBoardRowModel(1, "Maya", 62_000L, 0, isSelf = false, playerId = "maya"),
        DailyBoardRowModel(2, "You", 75_000L, 1, isSelf = true, playerId = "you"),
    )
    private val scopePicks = mutableListOf<RankScope>()
    private val windowPicks = mutableListOf<Window>()
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
    private val actions = RanksActions(
        onSignIn = {},
        onSwipe = {},
        onScope = { scopePicks += it; state = state.copy(scope = it) },
        onWindow = { windowPicks += it; state = state.copy(window = it) },
    )

    private fun show(layout: HistoryLayout = HistoryLayout.SinglePane, defaults: RanksTuneDefaults? = previewTuneDefaults(), fontScale: Float = 1f) {
        composeRule.setContent { Screen(layout, defaults, fontScale) }
    }

    @Composable
    private fun Screen(layout: HistoryLayout, defaults: RanksTuneDefaults?, fontScale: Float = 1f) {
        PuzzleBoxTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                RanksScreen(state, actions, layout = layout, tuneDefaults = defaults)
            }
        }
    }

    private fun node(tag: String) = composeRule.onNodeWithTag(tag)

    private fun count(tag: String) = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    private fun tap(tag: String) = node(tag).performClick()

    private fun settle() {
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun `the chips sit between the top bar and the calendar`() {
        show()

        node("ranks.filter.who").assertIsDisplayed().assertTextEquals("Who")
        node("ranks.filter.when").assertIsDisplayed().assertTextEquals("When")
        assertTrue(node("ranks.tune").getBoundsInRoot().bottom <= node("ranks.filter.who").getBoundsInRoot().top)
        assertTrue(node("ranks.filter.who").getBoundsInRoot().bottom <= node("ranks.calendar").getBoundsInRoot().top)
    }

    @Test
    fun `the chips and their rows are at least 48 dp`() {
        show()
        assertTrue(node("ranks.filter.who").fetchSemanticsNode().touchBoundsInRoot.height >= 48 * composeRule.density.density)
        tap("ranks.filter.who")

        node("ranks.tune.scope.Global").assertHeightIsAtLeast(48.dp)
        node("ranks.tune.scope.Friends").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `at font scale 1_3 both chips are still on screen`() {
        show(fontScale = 1.3f)

        node("ranks.filter.who").assertIsDisplayed()
        node("ranks.filter.when").assertIsDisplayed()
    }

    @Test
    fun `Who offers Global then Friends, and Friends sets the scope, closes the sheet and reads on the chip`() {
        show()
        tap("ranks.filter.who")

        node("ranks.filter.who.sheet").assertIsDisplayed()
        composeRule.onNode(hasText("Who") and hasAnyAncestor(hasTestTag("ranks.filter.who.sheet"))).assertIsDisplayed()
        node("ranks.tune.scope.Global").assertIsSelected()
        node("ranks.tune.scope.Friends").assertIsNotSelected()

        tap("ranks.tune.scope.Friends")
        settle()

        assertEquals(listOf(RankScope.Friends), scopePicks)
        assertEquals(0, count("ranks.filter.who.sheet"))
        node("ranks.filter.who").assertTextEquals("Friends")
    }

    @Test
    fun `picking Global again sets the scope back and the chip reads Who`() {
        state = state.copy(scope = RankScope.Friends)
        show()
        node("ranks.filter.who").assertTextEquals("Friends")
        tap("ranks.filter.who")
        node("ranks.tune.scope.Friends").assertIsSelected()

        tap("ranks.tune.scope.Global")
        settle()

        assertEquals(listOf(RankScope.Global), scopePicks)
        node("ranks.filter.who").assertTextEquals("Who")
    }

    @Test
    fun `When offers Day, Week and Month, and Month sets the window and reads on the chip, Day puts it back`() {
        show()
        tap("ranks.filter.when")

        composeRule.onNode(hasText("When") and hasAnyAncestor(hasTestTag("ranks.filter.when.sheet"))).assertIsDisplayed()
        node("ranks.tune.window.Day").assertIsSelected()
        node("ranks.tune.window.Week").assertIsNotSelected()
        node("ranks.tune.window.Month").assertIsNotSelected()

        tap("ranks.tune.window.Month")
        settle()
        node("ranks.filter.when").assertTextEquals("Month")

        tap("ranks.filter.when")
        tap("ranks.tune.window.Day")
        settle()

        assertEquals(listOf(Window.Month, Window.Day), windowPicks)
        node("ranks.filter.when").assertTextEquals("When")
    }

    @Test
    fun `while tuned the tune icon opens a sheet with the Defaults block and no toggles`() {
        state = state.copy(scope = RankScope.Friends, window = Window.Week)
        show()

        node("ranks.filter.who").assertTextEquals("Friends")
        node("ranks.filter.when").assertTextEquals("Week")

        tap("ranks.tune")

        node("ranks.tune.sheet").assertIsDisplayed()
        node("ranks.tune.defaults").assertExists()
        assertEquals(0, count("ranks.tune.scope.Friends") + count("ranks.tune.window.Week"))
    }

    @Test
    fun `with no Defaults block the chips are all of the tuning, so no tune icon`() {
        show(defaults = null)

        node("ranks.filter.who").assertIsDisplayed()
        assertEquals(0, count("ranks.tune"))
    }

    @Test
    fun `a Defaults pick in the tune sheet leaves the board's own scope alone`() {
        show()
        tap("ranks.tune")

        tap("ranks.tune.defaults.scope.Friends")
        composeRule.waitForIdle()

        node("ranks.tune.sheet").assertIsDisplayed()
        assertEquals(emptyList<RankScope>(), scopePicks)
    }

    @Test
    fun `an open sheet survives a rotation`() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { Screen(HistoryLayout.SinglePane, previewTuneDefaults()) }
        tap("ranks.filter.when")
        node("ranks.filter.when.sheet").assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        node("ranks.filter.when.sheet").assertIsDisplayed()
    }

    @Test
    fun `on a phone the chip row takes at most 56 dp above the calendar`() {
        show()

        // The row and the padding above it: what the board's start used to lose when the chips came on.
        val taken = node("ranks.calendar").getBoundsInRoot().top - node("ranks.filter.who").getBoundsInRoot().top + Spacing.sm
        assertTrue("took $taken", taken > 0.dp && taken <= 56.dp)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun `on the tablet the chips sit under the right pane's top bar, above the board, with no sideways scrolling`() {
        show(HistoryLayout.TwoPane)

        val pane = node("ranks.rightpane").getBoundsInRoot()
        val who = node("ranks.filter.who").getBoundsInRoot()
        assertTrue(node("ranks.tune").getBoundsInRoot().bottom <= who.top)
        assertTrue(who.bottom <= node("ranks.board").getBoundsInRoot().top)
        assertTrue(node("ranks.filter.when").getBoundsInRoot().right <= pane.right)
    }
}
