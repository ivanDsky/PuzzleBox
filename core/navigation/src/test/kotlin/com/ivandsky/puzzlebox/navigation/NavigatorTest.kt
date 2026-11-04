package com.ivandsky.puzzlebox.navigation

import android.app.Application
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The shared stack moves every feature entry drives, over a real [NavBackStack]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NavigatorTest {

    private data object Root : NavKey
    private data object Tab : NavKey
    private data class Page(val id: Int) : NavKey

    @Test
    fun `navigate pushes even a key already on top`() {
        val navigator = Navigator(NavBackStack(Root))

        navigator.navigate(Page(1))
        navigator.navigate(Page(1))

        assertEquals(listOf<NavKey>(Root, Page(1), Page(1)), navigator.backStack.toList())
        assertEquals(Page(1), navigator.currentKey)
    }

    @Test
    fun `navigateOnce ignores a double tap on the same destination`() {
        val navigator = Navigator(NavBackStack(Root))

        navigator.navigateOnce(Page(1))
        navigator.navigateOnce(Page(1))
        navigator.navigateOnce(Page(2))

        assertEquals(listOf<NavKey>(Root, Page(1), Page(2)), navigator.backStack.toList())
    }

    @Test
    fun `navigateToTopLevel resets the stack to that root`() {
        val navigator = Navigator(NavBackStack(Root))
        navigator.navigate(Page(1))

        navigator.navigateToTopLevel(Tab)

        assertEquals(listOf<NavKey>(Tab), navigator.backStack.toList())
    }

    @Test
    fun `goBack pops and is a no-op on an empty stack`() {
        val navigator = Navigator(NavBackStack(Root))

        navigator.goBack()
        navigator.goBack()

        assertEquals(emptyList<NavKey>(), navigator.backStack.toList())
        assertNull(navigator.currentKey)
    }
}
