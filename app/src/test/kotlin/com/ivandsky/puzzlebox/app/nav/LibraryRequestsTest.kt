package com.ivandsky.puzzlebox.app.nav

import androidx.compose.runtime.saveable.SaverScope
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.DuelPrefill
import com.ivandsky.puzzlebox.play.GamesTab
import com.ivandsky.puzzlebox.play.LibraryRequest
import com.ivandsky.puzzlebox.play.api.PlayKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The app's holder for a Games request: go to Games first, then remember what to open. */
class LibraryRequestsTest {

    private fun todayNavigator() = Navigator(puzzleBoxNavigationState())

    @Test
    fun `openGamesSection lands on Games root with the section pending and back returns to the origin`() {
        val navigator = todayNavigator()
        val requests = LibraryRequests()

        requests.openGamesSection(navigator, TabSet.FourTabs, GamesTab.Duels)

        assertEquals(PlayKey, navigator.state.tab)
        assertEquals(listOf<Any>(PlayKey), navigator.state.currentStack)
        assertEquals(LibraryRequest.Section(GamesTab.Duels), requests.pending)
        navigator.pop()
        assertEquals(HomeKey, navigator.state.tab)
    }

    @Test
    fun `openCustom with Custom in Games lands on Games root with Custom pending and back returns to the origin`() {
        val navigator = Navigator(puzzleBoxNavigationState(TabSet.CustomInGames.destinations))
        navigator.push(EditorKey("castle"))
        val before = navigator.state
        val requests = LibraryRequests()

        requests.openCustom(navigator, TabSet.CustomInGames)

        assertEquals(PlayKey, navigator.state.tab)
        assertEquals(listOf<Any>(PlayKey), navigator.state.currentStack)
        assertEquals(LibraryRequest.Section(GamesTab.Custom), requests.pending)
        navigator.pop()
        assertEquals(before, navigator.state)
    }

    @Test
    fun `openChallenge lands on Games root with the challenge pending and back returns to the origin`() {
        val navigator = todayNavigator()
        val requests = LibraryRequests()

        requests.openChallenge(navigator, TabSet.FourTabs, ADA_IN_ZIP)

        assertEquals(PlayKey, navigator.state.tab)
        assertEquals(listOf<Any>(PlayKey), navigator.state.currentStack)
        assertEquals(LibraryRequest.Challenge(ADA_IN_ZIP), requests.pending)
        navigator.pop()
        assertEquals(HomeKey, navigator.state.tab)
    }

    @Test
    fun `take clears the pending request`() {
        val requests = LibraryRequests()
        requests.openGamesSection(todayNavigator(), TabSet.FourTabs, GamesTab.Duels)

        requests.take()

        assertNull(requests.pending)
    }

    @Test
    fun `the saver round-trips every section, and none`() {
        val requests = listOf<LibraryRequest?>(null) +
            GamesTab.entries.map { LibraryRequest.Section(it) }
        for (request in requests) {
            val saved = with(LibraryRequests.Saver) { SaverScope { true }.save(LibraryRequests(request)) }
            val restored = LibraryRequests.Saver.restore(saved ?: "none")
            assertNotNull(request.toString(), restored)
            assertEquals(request, restored!!.pending)
        }
    }

    @Test
    fun `the saver round-trips a challenge, including a name with colons or none`() {
        for (name in listOf("Ada", "", "Ada: the Great", ":", "  Ada  ")) {
            val request = LibraryRequest.Challenge(DuelPrefill("uid-1", name, GameId("zip")))
            val saved = with(LibraryRequests.Saver) { SaverScope { true }.save(LibraryRequests(request)) }
            val restored = LibraryRequests.Saver.restore(saved!!)
            assertEquals("name '$name'", request, restored!!.pending)
        }
    }

    @Test
    fun `an old saved section value still restores`() {
        for (tab in GamesTab.entries) {
            assertEquals(LibraryRequest.Section(tab), LibraryRequests.Saver.restore("section:${tab.name}")!!.pending)
        }
        assertEquals(LibraryRequest.Section(GamesTab.Duels), LibraryRequests.Saver.restore("section:Duels")!!.pending)
    }

    @Test
    fun `an unreadable saved value restores as no request`() {
        for (saved in listOf("", "section:Nope", "challenge:", "challenge:zip", "challenge:zip:uid", "challenge::uid:Ada", "challenge:zip::Ada", "duel:x")) {
            assertNull("'$saved'", LibraryRequests.Saver.restore(saved)!!.pending)
        }
    }

    private companion object {
        val ADA_IN_ZIP = DuelPrefill(friendUid = "uid-ada", friendName = "Ada", game = GameId("zip"))
    }
}
