package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.data.visibility.OwnTier
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.home.HomeLayout
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import com.ivandsky.puzzlebox.profile.api.SettingsKey
import com.ivandsky.puzzlebox.profile.api.WhatsNewKey
import com.ivandsky.puzzlebox.profile.settings.VisibilitySlot
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The What's new page: its key resolves with the flag off, and Settings' row opens it on the shell layer, one Back from Settings. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class WhatsNewNavTest {

    @Test
    fun `the What's new key resolves`() {
        val navigator = Navigator(puzzleBoxNavigationState())
        val entry = puzzleBoxEntryProvider(
            navigator = navigator,
            homeLayout = HomeLayout.SinglePane,
            playLayout = PlayLayout.SinglePane,
            historyLayout = HistoryLayout.SinglePane,
            custom = testCustomSlot(),
            makeOne = testMakeOne(navigator),
            visibility = VisibilitySlot(flowOf(OwnTier.Unset)) {},
        ).invoke(WhatsNewKey)
        assertTrue("got ${entry.contentKey}", entry.contentKey.toString().contains(WhatsNewKey.toString()))
    }

    @Test
    fun `the row opens What's new over Settings and back returns to Settings`() {
        val navigator = Navigator(puzzleBoxNavigationState())
        navigator.pushLayer(ProfileKey)
        navigator.pushLayer(SettingsKey)

        navigator.pushLayer(WhatsNewKey)
        assertEquals(listOf(ProfileKey, SettingsKey, WhatsNewKey), navigator.state.layer)

        navigator.pushLayer(WhatsNewKey)
        assertEquals(listOf(ProfileKey, SettingsKey, WhatsNewKey), navigator.state.layer)

        assertTrue(navigator.pop())
        assertEquals(listOf(ProfileKey, SettingsKey), navigator.state.layer)
    }
}
