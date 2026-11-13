package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.PlayKey
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Today's rank row, the game page's summary row and Result's rank chip all push the hub's [HistoryKey] for a game and day. */
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
}
