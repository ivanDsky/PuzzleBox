package com.ivandsky.puzzlebox.app.nav

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.savedState
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.GamesTab
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every Ranks link (Today's rank, day and streak rows, the game page's rank row, Result's chip) switches to the Ranks
 * tab over the hub's [HistoryKey]; one back returns to the screen it was tapped on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class OpenRanksTest {

    /** Games with a game page and a Result on its stack: where the game page's and Result's rank links are tapped. */
    private fun onResult() = Navigator(puzzleBoxNavigationState()).also {
        it.selectTab(PlayKey)
        it.push(GameKey("zip"))
        it.push(ResultKey("zip", "2026-11-05", 1000L, 0))
    }

    private fun Navigator.assertOnRanks(vararg above: NavKey) {
        assertEquals(HistoryRootKey, state.tab)
        assertEquals(listOf<NavKey>(HistoryRootKey, *above), state.currentStack)
    }

    private fun Navigator.assertBackAt(origin: NavigationState) {
        assertEquals(origin.tab, state.tab)
        fun List<List<NavKey>>.withoutRanks() = filter { it.first() != HistoryRootKey }
        assertEquals(origin.stacks.withoutRanks(), state.stacks.withoutRanks())
    }

    @Test
    fun `the game page's rank rows switch to Ranks at the game and day, and back restores the page`() {
        val navigator = Navigator(puzzleBoxNavigationState()).also { it.selectTab(PlayKey); it.push(GameKey("zip")) }
        val before = navigator.state

        openRanks(navigator)(GameId("zip"), LocalDate.of(2026, 11, 5))

        navigator.assertOnRanks(HistoryKey("zip", "2026-11-05"))
        assertEquals(before.currentStack, navigator.state.stackOf(PlayKey))
        navigator.pop()
        assertEquals(PlayKey, navigator.state.tab)
        assertEquals(before.currentStack, navigator.state.currentStack)
    }

    @Test
    fun `Result's rank chip switches to Ranks and back restores Result on the Games stack`() {
        val navigator = onResult()
        val games = navigator.state.currentStack

        openRanks(navigator)(GameId("zip"), LocalDate.of(2026, 11, 5))

        navigator.assertOnRanks(HistoryKey("zip", "2026-11-05"))
        navigator.pop()
        assertEquals(PlayKey, navigator.state.tab)
        assertEquals(games, navigator.state.currentStack)
        assertEquals(ResultKey("zip", "2026-11-05", 1000L, 0), navigator.state.currentStack.last())
    }

    @Test
    fun `a null day is today`() {
        val navigator = onResult()
        openRanks(navigator)(GameId("zip"), null)
        navigator.assertOnRanks(HistoryKey("zip", null))
    }

    @Test
    fun `a double tap switches once`() {
        val navigator = onResult()
        val open = openRanks(navigator)
        open(GameId("zip"), null)
        val state = navigator.state
        open(GameId("zip"), null)
        assertEquals(state, navigator.state)
    }

    @Test
    fun `a rail tap on Ranks keeps Back to Today from a tab root`() {
        val navigator = Navigator(puzzleBoxNavigationState())
        navigator.selectTab(PlayKey)
        navigator.selectTab(HistoryRootKey)

        navigator.pop()

        assertEquals(HomeKey, navigator.state.tab)
    }

    @Test
    fun `Ranks from the rail later shows Ranks as the link left it`() {
        val navigator = onResult()
        openRanks(navigator)(GameId("zip"), null)
        navigator.pop()

        navigator.selectTab(HistoryRootKey)

        navigator.assertOnRanks(HistoryKey("zip", null))
    }

    @Test
    fun `a key saved before the calendar field decodes with it folded`() {
        val old = savedState { putString("gameId", "zip"); putString("day", "2026-11-05") }
        val decoded = decodeFromSavedState(HistoryKey.serializer(), old)
        assertEquals(HistoryKey("zip", "2026-11-05", calendarExpanded = false), decoded)
    }

    @Test
    fun `Today's rank row switches to Ranks at the game and back restores Today`() {
        val navigator = Navigator(puzzleBoxNavigationState())
        val before = navigator.state

        val summaries = homeSummaries(navigator, duels = null, libraryRequests = LibraryRequests())
        summaries.onOpenRank?.invoke(GameId("zip"))

        navigator.assertOnRanks(HistoryKey("zip", null))
        navigator.pop()
        navigator.assertBackAt(before)
    }

    @Test
    fun `Today's day opens Overall at the day and its streak opens the calendar, each switching to Ranks`() {
        val navigator = Navigator(puzzleBoxNavigationState())
        val before = navigator.state
        val summaries = homeSummaries(navigator, duels = null, libraryRequests = LibraryRequests())

        summaries.onOpenDay("2026-11-05")
        navigator.assertOnRanks(HistoryKey(null, "2026-11-05"))
        navigator.pop()
        navigator.assertBackAt(before)

        summaries.onOpenStreak()
        navigator.assertOnRanks(HistoryKey(calendarExpanded = true))
        navigator.pop()
        navigator.assertBackAt(before)
    }

    @Test
    fun `Today's duels row switches to Games on Duels and back restores Today`() {
        val navigator = Navigator(puzzleBoxNavigationState())
        val before = navigator.state
        val requests = LibraryRequests()

        requests.openGamesSection(navigator, GamesTab.Duels)

        assertEquals(PlayKey, navigator.state.tab)
        navigator.pop()
        navigator.assertBackAt(before)
    }
}
