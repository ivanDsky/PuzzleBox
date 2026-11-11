package com.ivandsky.puzzlebox.app.nav

import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import com.ivandsky.puzzlebox.profile.api.SettingsKey
import org.junit.Assert.assertEquals
import org.junit.Test

/** The rail's avatar always leaves one Profile on a stack: it pushes it, or returns to the one already there. */
class OpenProfileTest {

    @Test
    fun `from a tab root it pushes Profile`() {
        assertEquals(listOf(HomeKey, ProfileKey), opening(HomeKey, listOf(HomeKey)).currentStack)
    }

    @Test
    fun `on Profile itself it changes nothing`() {
        val before = stacks(HomeKey, home = listOf(HomeKey, ProfileKey))

        assertEquals(before, opening(before))
    }

    @Test
    fun `from Settings it pops back to the Profile below, never stacking a second`() {
        val before = stacks(HomeKey, home = listOf(HomeKey, ProfileKey, SettingsKey))

        assertEquals(listOf(HomeKey, ProfileKey), opening(before).currentStack)
    }

    @Test
    fun `Profile on another tab's stack does not stop it opening on this one`() {
        val before = stacks(PlayKey, home = listOf(HomeKey, ProfileKey))

        val after = opening(before)

        assertEquals(listOf(PlayKey, ProfileKey), after.currentStack)
        assertEquals(before.stackOf(HomeKey), after.stackOf(HomeKey))
    }

    private fun opening(tab: NavKey, stack: List<NavKey>): NavigationState = opening(stacks(tab, home = stack))

    private fun opening(state: NavigationState): NavigationState = Navigator(state).apply { openProfile() }.state

    private fun stacks(
        tab: NavKey,
        home: List<NavKey> = listOf(HomeKey),
    ) = NavigationState(tab = tab, stacks = listOf(home, listOf(PlayKey), listOf(HistoryRootKey), listOf(CreateKey)))
}
