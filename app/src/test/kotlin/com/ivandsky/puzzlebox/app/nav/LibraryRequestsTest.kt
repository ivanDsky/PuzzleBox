package com.ivandsky.puzzlebox.app.nav

import androidx.compose.runtime.saveable.SaverScope
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.GamesTab
import com.ivandsky.puzzlebox.play.LibraryRequest
import com.ivandsky.puzzlebox.play.api.PlayKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The app's holder for a Games request: go to Games first, then remember what to open. */
class LibraryRequestsTest {

    private fun todayNavigator() = Navigator(puzzleBoxNavigationState(navV2 = true))

    @Test
    fun `openGamePage lands on Games root with the page pending and back returns to Today`() {
        val navigator = todayNavigator()
        val requests = LibraryRequests()

        requests.openGamePage(navigator, "zip")

        assertEquals(PlayKey, navigator.state.tab)
        assertEquals(listOf<Any>(PlayKey), navigator.state.currentStack)
        assertEquals(LibraryRequest.GamePage("zip"), requests.pending)
        navigator.pop()
        assertEquals(HomeKey, navigator.state.tab)
    }

    @Test
    fun `openGamesSection lands on Games root with the section pending and back returns to Today`() {
        val navigator = todayNavigator()
        val requests = LibraryRequests()

        requests.openGamesSection(navigator, GamesTab.Duels)

        assertEquals(PlayKey, navigator.state.tab)
        assertEquals(listOf<Any>(PlayKey), navigator.state.currentStack)
        assertEquals(LibraryRequest.Section(GamesTab.Duels), requests.pending)
        navigator.pop()
        assertEquals(HomeKey, navigator.state.tab)
    }

    @Test
    fun `take clears the pending request`() {
        val requests = LibraryRequests()
        requests.openGamePage(todayNavigator(), "zip")

        requests.take()

        assertNull(requests.pending)
    }

    @Test
    fun `the saver round-trips a page, every section, and none`() {
        val requests = listOf<LibraryRequest?>(LibraryRequest.GamePage("zip"), null) +
            GamesTab.entries.map { LibraryRequest.Section(it) }
        for (request in requests) {
            val saved = with(LibraryRequests.Saver) { SaverScope { true }.save(LibraryRequests(request)) }
            val restored = LibraryRequests.Saver.restore(saved ?: "none")
            assertNotNull(request.toString(), restored)
            assertEquals(request, restored!!.pending)
        }
    }
}
