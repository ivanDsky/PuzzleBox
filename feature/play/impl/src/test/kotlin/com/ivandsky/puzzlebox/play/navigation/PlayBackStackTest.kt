package com.ivandsky.puzzlebox.play.navigation

import android.app.Application
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ReplayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Ticket 23 result-route CONTRACT on real [NavBackStack]s: solving replaces everything
 * above Home with the result (so the system back — a plain pop — lands on Home), and
 * "Play again" swaps the result for a fresh play entry.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PlayBackStackTest {

    private fun navigatorOf(vararg keys: NavKey) = Navigator(NavBackStack(*keys))

    @Test
    fun `solving routes to the result with home as the back exit`() {
        val navigator = navigatorOf(HomeKey)
        navigator.navigate(PlayKey)

        navigator.solveToResult(ResultKey(61_200, 2))

        val stack = navigator.backStack
        assertTrue("expected the result on top, got ${stack.lastOrNull()}", stack.lastOrNull() == ResultKey(61_200, 2))
        // System back from the result is a plain pop — it must land on Home.
        stack.removeLastOrNull()
        assertTrue("expected Home beneath the result, got ${stack.lastOrNull()}", stack.lastOrNull() == HomeKey)
    }

    @Test
    fun `solving a replay day also exits to home, not history`() {
        val navigator = navigatorOf(HomeKey)
        navigator.navigate(HistoryKey)
        navigator.navigate(ReplayKey("blob"))

        navigator.solveToResult(ResultKey(45_000, 0))

        val stack = navigator.backStack
        assertTrue(stack.lastOrNull() is ResultKey)
        stack.removeLastOrNull()
        assertTrue("replay result must not fall back to History", stack.lastOrNull() == HomeKey)
    }

    @Test
    fun `solving from the play tab root still backs out to home`() {
        // Tab selection resets the stack to the tab root — there is no Home beneath.
        val navigator = navigatorOf(PlayKey)

        navigator.solveToResult(ResultKey(1_000, 0))

        val stack = navigator.backStack
        assertTrue(stack.lastOrNull() is ResultKey)
        stack.removeLastOrNull()
        assertTrue("expected a supplied Home beneath the result", stack.lastOrNull() == HomeKey)
    }

    @Test
    fun `back from a pushed play pops onto Home`() {
        val navigator = navigatorOf(HomeKey)
        navigator.navigate(PlayKey)

        navigator.leavePlay()

        assertEquals(listOf<NavKey>(HomeKey), navigator.backStack.toList())
    }

    @Test
    fun `back from the Play tab root lands on Home instead of emptying the stack`() {
        // Tab selection clears the stack, so the tab root is the only entry; an empty
        // stack crashes NavDisplay.
        val navigator = navigatorOf(PlayKey)

        navigator.leavePlay()

        assertEquals(listOf<NavKey>(HomeKey), navigator.backStack.toList())
    }

    @Test
    fun `play again swaps the result for a fresh play entry`() {
        val navigator = navigatorOf(HomeKey)
        navigator.navigate(ResultKey(95_000, 1))

        navigator.playAgainFromResult()

        assertTrue("expected Play on top, got ${navigator.currentKey}", navigator.currentKey == PlayKey)
    }

    @Test
    fun `double solve push is idempotent`() {
        val navigator = navigatorOf(HomeKey)
        navigator.navigate(PlayKey)

        navigator.solveToResult(ResultKey(61_200, 2))
        navigator.solveToResult(ResultKey(61_200, 2))

        assertEquals(2, navigator.backStack.size)
    }
}
