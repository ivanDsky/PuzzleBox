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

/**
 * The avatar's two verbs. Pushed (the old form): it always leaves one Profile on a stack, pushing it or
 * returning to the one already there. As a layer: it opens Profile above the tabs, and closes the layer when it is open.
 */
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

    @Test
    fun `as a layer it opens Profile above any tab and leaves every stack alone`() {
        val under = stacks(PlayKey)

        assertEquals(under.copy(layer = listOf(ProfileKey)), toggling(under))
    }

    @Test
    fun `as a layer a second tap closes it from Settings, back on the screen under it`() {
        val under = stacks(PlayKey)

        assertEquals(under, toggling(under.copy(layer = listOf(ProfileKey, SettingsKey))))
    }

    private fun toggling(state: NavigationState): NavigationState = Navigator(state).apply { toggleProfileLayer() }.state

    private fun opening(tab: NavKey, stack: List<NavKey>): NavigationState = opening(stacks(tab, home = stack))

    private fun opening(state: NavigationState): NavigationState = Navigator(state).apply { openProfile() }.state

    private fun stacks(
        tab: NavKey,
        home: List<NavKey> = listOf(HomeKey),
    ) = NavigationState(tab = tab, stacks = listOf(home, listOf(PlayKey), listOf(HistoryRootKey), listOf(CreateKey)))
}
