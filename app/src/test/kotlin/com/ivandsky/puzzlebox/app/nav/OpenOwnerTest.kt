package com.ivandsky.puzzlebox.app.nav

import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import com.ivandsky.puzzlebox.profile.api.SettingsKey
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Go to owner: a tab-root key selects its owner tab and resets it to the root, pushing
 * nothing; every other key is a plain push. With the Profile layer open, either way closes it first.
 */
class OpenOwnerTest {

    @Test
    fun `a tab root from another tab selects it at its root and leaves the stack behind`() {
        val before = stacks(PlayKey, play = listOf(PlayKey, GameKey("zip")))

        val after = before.opening(HistoryRootKey)

        assertEquals(HistoryRootKey, after.tab)
        assertEquals(listOf<NavKey>(HistoryRootKey), after.currentStack)
        assertEquals(before.stackOf(PlayKey), after.stackOf(PlayKey))
    }

    @Test
    fun `a tab already selected deeper than its root is reset to the root`() {
        val before = stacks(HistoryRootKey, history = listOf(HistoryRootKey, HistoryKey("zip")))

        assertEquals(stacks(HistoryRootKey), before.opening(HistoryRootKey))
    }

    @Test
    fun `a tab root from a deeper unselected stack resets that stack too`() {
        val before = stacks(PlayKey, history = listOf(HistoryRootKey, HistoryKey("zip")))

        assertEquals(listOf<NavKey>(HistoryRootKey), before.opening(HistoryRootKey).stackOf(HistoryRootKey))
    }

    @Test
    fun `the root of the selected tab changes nothing`() {
        val before = stacks(PlayKey)

        assertEquals(before, before.opening(PlayKey))
    }

    @Test
    fun `back from the owner root returns to the screen the link was on`() {
        val before = stacks(PlayKey, play = listOf(PlayKey, GameKey("zip")))
        val navigator = Navigator(before)

        navigator.openOwner(HistoryRootKey, TabSet.ThreeTabs)
        navigator.pop()

        assertEquals(before, navigator.state)
    }

    @Test
    fun `a pushed key is a plain push on the current tab`() {
        listOf<NavKey>(GameKey("zip"), HistoryKey("zip")).forEach { key ->
            val before = stacks(HomeKey)

            val after = before.opening(key)

            assertEquals("tab for $key", HomeKey, after.tab)
            assertEquals("stack for $key", listOf(HomeKey, key), after.currentStack)
        }
    }

    @Test
    fun `a debug-only key is a plain push on the current tab`() {
        val after = stacks(PlayKey).opening(CatalogKey)

        assertEquals(PlayKey, after.tab)
        assertEquals(listOf(PlayKey, CatalogKey), after.currentStack)
    }

    @Test
    fun `with the layer open a tab root leaves an empty layer on its tab`() {
        val under = stacks(PlayKey, play = listOf(PlayKey, GameKey("zip")))

        val after = under.copy(layer = listOf(ProfileKey, SettingsKey)).opening(HistoryRootKey)

        assertEquals(under.copy(tab = HistoryRootKey, returnTo = NavigationState.Return(PlayKey, depth = 1)), after)
    }

    @Test
    fun `with the layer open over the owner tab it closes the layer and resets that tab`() {
        val before = stacks(HistoryRootKey, history = listOf(HistoryRootKey, HistoryKey("zip")))
            .copy(layer = listOf(ProfileKey))

        assertEquals(stacks(HistoryRootKey), before.opening(HistoryRootKey))
    }

    @Test
    fun `with the layer open a pushed key closes it and lands on the tab under it`() {
        val before = stacks(HomeKey).copy(layer = listOf(ProfileKey, SettingsKey))

        assertEquals(stacks(HomeKey, home = listOf(HomeKey, GameKey("zip"))), before.opening(GameKey("zip")))
    }

    @Test
    fun `a pushed-on key switches to its tab over its root and one back returns to the origin`() {
        val before = stacks(
            PlayKey,
            play = listOf(PlayKey, GameKey("zip")),
            history = listOf(HistoryRootKey, HistoryKey("old")),
        )
        val navigator = Navigator(before)

        navigator.openOnOwnerTab(HistoryKey("zip"), TabSet.ThreeTabs)

        assertEquals(HistoryRootKey, navigator.state.tab)
        assertEquals(listOf<NavKey>(HistoryRootKey, HistoryKey("zip")), navigator.state.currentStack)
        navigator.pop()
        val linked = listOf(HistoryRootKey, HistoryKey("zip"))
        assertEquals(
            before.copy(stacks = before.stacks.map { if (it.first() == HistoryRootKey) linked else it }),
            navigator.state,
        )
    }

    @Test
    fun `a double tap on a pushed-on key switches once`() {
        val navigator = Navigator(stacks(PlayKey))

        navigator.openOnOwnerTab(HistoryKey("zip"), TabSet.ThreeTabs)
        val once = navigator.state
        navigator.openOnOwnerTab(HistoryKey("zip"), TabSet.ThreeTabs)

        assertEquals(once, navigator.state)
    }

    private fun NavigationState.opening(key: NavKey): NavigationState =
        Navigator(this).apply { openOwner(key, TabSet.ThreeTabs) }.state

    private fun stacks(
        tab: NavKey,
        home: List<NavKey> = listOf(HomeKey),
        play: List<NavKey> = listOf(PlayKey),
        history: List<NavKey> = listOf(HistoryRootKey),
    ) = NavigationState(tab = tab, stacks = listOf(home, play, history))
}
