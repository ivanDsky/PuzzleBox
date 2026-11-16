package com.ivandsky.puzzlebox.app.nav

import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.savedState
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.PlayKey
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Today's rank row, the game page's summary row and Result's rank chip all push the hub's [HistoryKey] for a game and day;
 * with `NavDoors` on, Today's day and streak summaries push it too (Overall at the day; the calendar open).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class OpenRanksTest {

    private fun navigator() = Navigator(puzzleBoxNavigationState()).also { it.selectTab(PlayKey) }

    @Test
    fun `pushes the game and day on the current stack`() {
        val navigator = navigator()
        val root = navigator.state.currentStack
        openRanks(navigator)(GameId("zip"), LocalDate.of(2026, 11, 5))
        assertEquals(root + HistoryKey("zip", "2026-11-05"), navigator.state.currentStack)
    }

    @Test
    fun `a null day is today`() {
        val navigator = navigator()
        openRanks(navigator)(GameId("zip"), null)
        assertEquals(HistoryKey("zip", null), navigator.state.currentStack.last())
    }

    @Test
    fun `a double tap pushes once`() {
        val navigator = navigator()
        val open = openRanks(navigator)
        open(GameId("zip"), null)
        val stack = navigator.state.currentStack
        open(GameId("zip"), null)
        assertEquals(stack, navigator.state.currentStack)
    }

    @Test
    fun `a key saved before the calendar field decodes with it folded`() {
        val old = savedState { putString("gameId", "zip"); putString("day", "2026-11-05") }
        assertEquals(HistoryKey("zip", "2026-11-05", calendarExpanded = false), decodeFromSavedState(HistoryKey.serializer(), old))
    }

    @Test
    fun `with NavDoors on Today's day opens Overall at the day and its streak opens the calendar`() {
        val navigator = navigator()
        val summaries = homeSummaries(navigator, navDoors = true, duels = null, libraryRequests = LibraryRequests())
        summaries.onOpenDay!!("2026-11-05")
        assertEquals(HistoryKey(null, "2026-11-05"), navigator.state.currentStack.last())
        navigator.pop()
        summaries.onOpenStreak!!()
        assertEquals(HistoryKey(calendarExpanded = true), navigator.state.currentStack.last())
    }

    @Test
    fun `with NavDoors off Today's day and streak slots are empty`() {
        val summaries = homeSummaries(navigator(), navDoors = false, duels = null, libraryRequests = LibraryRequests())
        assertNull(summaries.onOpenDay)
        assertNull(summaries.onOpenStreak)
    }
}
