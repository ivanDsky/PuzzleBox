package com.ivandsky.puzzlebox.app.nav

import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import com.ivandsky.puzzlebox.profile.api.SettingsKey
import org.junit.Assert.assertEquals
import org.junit.Test

/** The avatar's verb: it opens Profile above the tabs, and closes the layer when it is open. */
class OpenProfileTest {

    @Test
    fun `it opens Profile above any tab and leaves every stack alone`() {
        val under = stacks(PlayKey)

        assertEquals(under.copy(layer = listOf(ProfileKey)), toggling(under))
    }

    @Test
    fun `a second tap closes it from Settings, back on the screen under it`() {
        val under = stacks(PlayKey)

        assertEquals(under, toggling(under.copy(layer = listOf(ProfileKey, SettingsKey))))
    }

    private fun toggling(state: NavigationState): NavigationState = Navigator(state).apply { toggleProfileLayer() }.state

    private fun stacks(
        tab: NavKey,
        home: List<NavKey> = listOf(HomeKey),
    ) = NavigationState(tab = tab, stacks = listOf(home, listOf(PlayKey), listOf(HistoryRootKey)))
}
