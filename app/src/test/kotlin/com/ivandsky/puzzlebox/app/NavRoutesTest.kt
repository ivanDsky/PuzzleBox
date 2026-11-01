package com.ivandsky.puzzlebox.app

import android.app.Application
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.nav.Catalog
import com.ivandsky.puzzlebox.app.nav.History
import com.ivandsky.puzzlebox.app.nav.Home
import com.ivandsky.puzzlebox.app.nav.Play
import com.ivandsky.puzzlebox.app.nav.Profile
import com.ivandsky.puzzlebox.app.nav.ReplayZip
import com.ivandsky.puzzlebox.app.nav.Result
import com.ivandsky.puzzlebox.data.model.ThemeMode
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.home.HomeLayout
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Ticket 17 nav-route regression: [puzzleBoxEntryProvider] builds the route table as a
 * plain key→entry lookup, so these tests resolve every key the wired flow pushes and
 * fail on a missing entry — the exact gap that would strand a tap on an unregistered
 * destination (Home→Play, Home→History, history→ReplayZip, Home account→Profile,
 * Profile→Catalog) with no composition or Koin graph needed.
 *
 * Ticket 23 adds the result-route CONTRACT on real [NavBackStack]s: solving replaces
 * everything above Home with the result (so the system back — a plain pop — lands on
 * Home), and "Play again" swaps the result for a fresh play entry.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NavRoutesTest {

    /** A key a future surface might push before its entry is wired. */
    private data object Unregistered : NavKey

    private fun provider(): (NavKey) -> NavEntry<NavKey> =
        puzzleBoxEntryProvider(
            backStack = NavBackStack(Home),
            homeLayout = HomeLayout.SinglePane,
            playLayout = PlayLayout.SinglePane,
            historyLayout = HistoryLayout.SinglePane,
            themeMode = ThemeMode.SYSTEM,
            onSetThemeMode = {},
        )

    @Test
    fun `every primary-flow key resolves to a registered entry`() {
        val session = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3))
        val keys = listOf<NavKey>(Home, Play, History, ReplayZip(session), Result(95_000, 1), Profile, Catalog)

        keys.forEach { key ->
            val entry = provider().invoke(key)
            // contentKey derives from the resolved key — prove the requested key, not a
            // stand-in, reached the entry.
            assertTrue(
                "expected entry for $key, got contentKey=${entry.contentKey}",
                entry.contentKey.toString().contains(key.toString()),
            )
        }
    }

    @Test
    fun `solving routes to the result with home as the back exit`() {
        val stack = NavBackStack<NavKey>(Home)
        stack.add(Play)

        stack.solveToResult(Result(61_200, 2))

        assertTrue("expected the result on top, got ${stack.lastOrNull()}", stack.lastOrNull() == Result(61_200, 2))
        // System back from the result is a plain pop — it must land on Home.
        stack.removeLastOrNull()
        assertTrue("expected Home beneath the result, got ${stack.lastOrNull()}", stack.lastOrNull() == Home)
    }

    @Test
    fun `solving a replay day also exits to home, not history`() {
        val stack = NavBackStack<NavKey>(Home)
        stack.add(History)
        stack.add(ReplayZip("blob"))

        stack.solveToResult(Result(45_000, 0))

        assertTrue(stack.lastOrNull() is Result)
        stack.removeLastOrNull()
        assertTrue("replay result must not fall back to History", stack.lastOrNull() == Home)
    }

    @Test
    fun `solving from the play tab root still backs out to home`() {
        // Tab selection resets the stack to the tab root — there is no Home beneath.
        val stack = NavBackStack<NavKey>(Play)

        stack.solveToResult(Result(1_000, 0))

        assertTrue(stack.lastOrNull() is Result)
        stack.removeLastOrNull()
        assertTrue("expected a supplied Home beneath the result", stack.lastOrNull() == Home)
    }

    @Test
    fun `play again swaps the result for a fresh play entry`() {
        val stack = NavBackStack<NavKey>(Home)
        stack.add(Result(95_000, 1))

        stack.playAgainFromResult()

        assertTrue("expected Play on top, got ${stack.lastOrNull()}", stack.lastOrNull() == Play)
    }

    @Test
    fun `double solve push is idempotent`() {
        val stack = NavBackStack<NavKey>(Home)
        stack.add(Play)

        stack.solveToResult(Result(61_200, 2))
        stack.solveToResult(Result(61_200, 2))

        assertEquals(2, stack.size)
    }

    @Test
    fun `replay resolution keeps the day's session blob intact`() {
        // The history route base64-encodes the replay session into the key; the play
        // entry decodes it back. Resolution must not mangle the carrier string.
        val blob = byteArrayOf(0, 1, -1, 42, 7)
        val encoded = Base64.getEncoder().encodeToString(blob)

        val entry = provider().invoke(ReplayZip(encoded))

        assertTrue(entry.contentKey.toString().contains(encoded))
    }

    @Test
    fun `unregistered key fails fast instead of rendering a blank scene`() {
        val exception = assertThrows(IllegalStateException::class.java) {
            provider().invoke(Unregistered)
        }
        assertTrue(
            "expected an unknown-key failure, got: ${exception.message}",
            exception.message.orEmpty().contains("Unregistered"),
        )
    }
}
