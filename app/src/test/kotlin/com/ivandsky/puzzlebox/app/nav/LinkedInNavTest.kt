package com.ivandsky.puzzlebox.app.nav

import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.home.HomeLayout
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.profile.api.LinkedInLoginKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The LinkedIn login route under both flag states, and the stack behaviour its two
 * entry points rely on: a plain push on the current tab, ignored on a double tap, and
 * popped back to the screen that opened it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class LinkedInNavTest {

    private fun provider(navigator: Navigator, linkedIn: Boolean) = puzzleBoxEntryProvider(
        navigator = navigator,
        homeLayout = HomeLayout.SinglePane,
        playLayout = PlayLayout.SinglePane,
        historyLayout = HistoryLayout.SinglePane,
        linkedIn = linkedIn,
    )

    @Test
    fun `the login key resolves with the flag on and off`() {
        val navigator = Navigator(puzzleBoxNavigationState())
        val on = provider(navigator, linkedIn = true).invoke(LinkedInLoginKey)
        val off = puzzleBoxEntryProvider(
            navigator = navigator,
            homeLayout = HomeLayout.SinglePane,
            playLayout = PlayLayout.SinglePane,
            historyLayout = HistoryLayout.SinglePane,
        ).invoke(LinkedInLoginKey)
        assertTrue("flag on: got ${on.contentKey}", on.contentKey.toString().contains(LinkedInLoginKey.toString()))
        assertTrue("flag off: got ${off.contentKey}", off.contentKey.toString().contains(LinkedInLoginKey.toString()))
    }

    @Test
    fun `reconnect from the games tab opens login on top and back returns to the game page`() {
        val navigator = Navigator(puzzleBoxNavigationState())
        navigator.selectTab(PlayKey)
        val gameStack: List<NavKey> = navigator.state.currentStack
        val otherStacks = navigator.state.stacks.filter { it.first() != PlayKey }

        navigator.pushOnce(LinkedInLoginKey)
        val opened = navigator.state.currentStack
        assertEquals(PlayKey, navigator.state.tab)
        assertEquals(gameStack + LinkedInLoginKey, opened)

        navigator.pushOnce(LinkedInLoginKey)
        assertEquals(opened, navigator.state.currentStack)

        assertTrue(navigator.pop())
        assertEquals(gameStack, navigator.state.currentStack)
        assertEquals(otherStacks, navigator.state.stacks.filter { it.first() != PlayKey })
    }

    @Test
    fun `login opened from profile goes back to profile`() {
        val navigator = Navigator(puzzleBoxNavigationState())
        navigator.selectTab(HomeKey)
        navigator.push(ProfileKey)
        val profileStack: List<NavKey> = navigator.state.currentStack

        navigator.pushOnce(LinkedInLoginKey)
        assertEquals(profileStack + LinkedInLoginKey, navigator.state.currentStack)

        assertTrue(navigator.pop())
        assertEquals(profileStack, navigator.state.currentStack)
        assertEquals(ProfileKey, navigator.state.currentStack.last())
    }
}
