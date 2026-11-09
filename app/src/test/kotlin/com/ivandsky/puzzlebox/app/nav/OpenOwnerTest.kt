package com.ivandsky.puzzlebox.app.nav

import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Go to owner: a tab-root key selects its owner tab and resets it to the root, pushing
 * nothing; every other key, and an owner tab missing from the live set, is a plain push.
 */
class OpenOwnerTest {

    @Test
    fun `a tab root from another tab selects it at its root and leaves the stack behind`() {
        val before = flagOn(PlayKey, play = listOf(PlayKey, GameKey("zip")))

        val after = before.opening(HistoryRootKey)

        assertEquals(HistoryRootKey, after.tab)
        assertEquals(listOf<NavKey>(HistoryRootKey), after.currentStack)
        assertEquals(before.stackOf(PlayKey), after.stackOf(PlayKey))
    }

    @Test
    fun `a tab already selected deeper than its root is reset to the root`() {
        val before = flagOn(HistoryRootKey, history = listOf(HistoryRootKey, HistoryKey("zip")))

        assertEquals(flagOn(HistoryRootKey), before.opening(HistoryRootKey))
    }

    @Test
    fun `a tab root from a deeper unselected stack resets that stack too`() {
        val before = flagOn(PlayKey, history = listOf(HistoryRootKey, HistoryKey("zip")))

        assertEquals(listOf<NavKey>(HistoryRootKey), before.opening(HistoryRootKey).stackOf(HistoryRootKey))
    }

    @Test
    fun `the root of the selected tab changes nothing`() {
        val before = flagOn(PlayKey)

        assertEquals(before, before.opening(PlayKey))
    }

    @Test
    fun `back from the owner root reaches Today`() {
        val navigator = Navigator(flagOn(PlayKey, play = listOf(PlayKey, GameKey("zip"))))

        navigator.openOwner(HistoryRootKey)
        navigator.pop()

        assertEquals(HomeKey, navigator.state.tab)
    }

    @Test
    fun `a pushed key is a plain push on the current tab`() {
        listOf<NavKey>(GameKey("zip"), HistoryKey("zip")).forEach { key ->
            val before = flagOn(CreateKey)

            val after = before.opening(key)

            assertEquals("tab for $key", CreateKey, after.tab)
            assertEquals("stack for $key", listOf(CreateKey, key), after.currentStack)
        }
    }

    @Test
    fun `a debug-only key is a plain push on the current tab`() {
        val after = flagOn(PlayKey).opening(CatalogKey)

        assertEquals(PlayKey, after.tab)
        assertEquals(listOf(PlayKey, CatalogKey), after.currentStack)
    }

    @Test
    fun `an owner tab that is not live is a plain push on the current tab`() {
        val before = puzzleBoxNavigationState(navV2 = false)

        val after = before.opening(HistoryRootKey)

        assertEquals(before.tab, after.tab)
        assertEquals(before.currentStack + HistoryRootKey, after.currentStack)
    }

    private fun NavigationState.opening(key: NavKey): NavigationState =
        Navigator(this).apply { openOwner(key) }.state

    private fun flagOn(
        tab: NavKey,
        home: List<NavKey> = listOf(HomeKey),
        play: List<NavKey> = listOf(PlayKey),
        history: List<NavKey> = listOf(HistoryRootKey),
        create: List<NavKey> = listOf(CreateKey),
    ) = NavigationState(tab = tab, stacks = listOf(home, play, history, create))
}
