package com.ivandsky.puzzlebox.navigation

import android.app.Application
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The observable facade: every verb publishes the [NavigationState] reducer's result. The
 * per-tab rules themselves are pinned in NavigationStateTest; this pins the delegation and
 * [Navigator.pop]'s exit signal.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NavigatorTest {

    private data object Home : NavKey
    private data object Play : NavKey
    private data object Profile : NavKey
    private data class Page(val id: Int) : NavKey

    private fun navigator() = Navigator(navigationState(Home, Play, Profile))

    @Test
    fun `selectTab publishes the selected tab and keeps the other stacks`() {
        val navigator = navigator()
        navigator.push(Page(1))

        navigator.selectTab(Profile)

        assertEquals(navigationState(Home, Play, Profile).push(Page(1)).selectTab(Profile), navigator.state)
        assertEquals(Profile, navigator.state.tab)
        assertEquals(listOf<NavKey>(Home, Page(1)), navigator.state.stackOf(Home))
    }

    @Test
    fun `reselecting the current tab pops it to its root`() {
        val navigator = navigator()
        navigator.selectTab(Profile)
        navigator.push(Page(1))
        navigator.push(Page(2))

        navigator.selectTab(Profile)

        assertEquals(listOf<NavKey>(Profile), navigator.state.currentStack)
    }

    @Test
    fun `push grows only the current stack`() {
        val navigator = navigator()
        navigator.selectTab(Play)

        navigator.push(Page(1))
        navigator.push(Page(1))

        assertEquals(listOf<NavKey>(Play, Page(1), Page(1)), navigator.state.stackOf(Play))
        assertEquals(listOf<NavKey>(Home), navigator.state.stackOf(Home))
    }

    @Test
    fun `pushOnce ignores a double tap on the same destination`() {
        val navigator = navigator()

        navigator.pushOnce(Page(1))
        navigator.pushOnce(Page(1))
        navigator.pushOnce(Page(2))

        assertEquals(listOf<NavKey>(Home, Page(1), Page(2)), navigator.state.currentStack)
    }

    @Test
    fun `pop removes the top key`() {
        val navigator = navigator()
        navigator.push(Page(1))

        assertTrue(navigator.pop())

        assertEquals(navigationState(Home, Play, Profile), navigator.state)
    }

    @Test
    fun `pop at a non-start root selects the start tab and keeps both stacks`() {
        val navigator = navigator()
        navigator.push(Page(1))
        navigator.selectTab(Profile)

        assertTrue(navigator.pop())

        assertEquals(Home, navigator.state.tab)
        assertEquals(listOf<NavKey>(Home, Page(1)), navigator.state.stackOf(Home))
        assertEquals(listOf<NavKey>(Profile), navigator.state.stackOf(Profile))
    }

    @Test
    fun `pop at the start root returns false and leaves the state unchanged`() {
        val navigator = navigator()
        val before = navigator.state

        assertFalse(navigator.pop())

        assertEquals(before, navigator.state)
    }

    @Test
    fun `replaceTop swaps the top key and is a no-op at a root`() {
        val navigator = navigator()
        navigator.replaceTop(Page(9))
        assertEquals(listOf<NavKey>(Home), navigator.state.currentStack)

        navigator.push(Page(1))
        navigator.replaceTop(Page(2))

        assertEquals(listOf<NavKey>(Home, Page(2)), navigator.state.currentStack)
    }
}
