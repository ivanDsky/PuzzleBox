package com.ivandsky.puzzlebox.app

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.nav.puzzleBoxEntryProvider
import com.ivandsky.puzzlebox.app.nav.puzzleBoxNavigationState
import com.ivandsky.puzzlebox.app.nav.topLevelDestinations
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.home.HomeLayout
import com.ivandsky.puzzlebox.navigation.Navigator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Nav-route regression: [puzzleBoxEntryProvider] builds the route table as a
 * plain key→entry lookup, so these tests resolve every key the wired flow pushes and
 * fail on a missing entry — the exact gap that would strand a tap on an unregistered
 * destination (Home→History, history→Game, Play→Result, Profile→Catalog, and every
 * tab root) with no composition or Koin graph needed. Stack behaviour (per-tab stacks,
 * back, reselect) is pinned by NavigationStateTest and PerTabShellTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class NavRoutesTest {

    /** A key a future surface might push before its entry is wired. */
    private data object Unregistered : NavKey

    private fun provider(): (NavKey) -> NavEntry<NavKey> =
        puzzleBoxEntryProvider(
            navigator = Navigator(puzzleBoxNavigationState()),
            homeLayout = HomeLayout.SinglePane,
            playLayout = PlayLayout.SinglePane,
            historyLayout = HistoryLayout.SinglePane,
        )

    @Test
    fun `every primary-flow key resolves to a registered entry`() {
        val keys = PlacementSamples.everyRow()

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
    fun `every tab root resolves to a registered entry`() {
        val roots = puzzleBoxNavigationState().tabs

        assertEquals(topLevelDestinations.map { it.key }, roots)
        roots.forEach { root ->
            assertTrue(provider().invoke(root).contentKey.toString().contains(root.toString()))
        }
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
