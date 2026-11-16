package com.ivandsky.puzzlebox.app.nav

import android.content.Intent
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.GamesTab
import com.ivandsky.puzzlebox.play.LibraryRequest
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The shared-text intake helpers: what a `SEND` share offers, and where it lands: Create's root with
 * the four tabs, Games' Custom with Custom in Games.
 */
class SharedTextTest {

    @Test
    fun `send with plain text returns the text`() {
        val text = SharedText.of(Intent.ACTION_SEND, "text/plain", "a puzzle seed")

        assertEquals("a puzzle seed", text)
    }

    @Test
    fun `another action is ignored`() {
        listOf(Intent.ACTION_SEND_MULTIPLE, Intent.ACTION_VIEW, null).forEach { action ->
            assertEquals("action $action", null, SharedText.of(action, "text/plain", "text"))
        }
    }

    @Test
    fun `a non plain type is ignored`() {
        listOf("image/*", "video/mp4", null).forEach { type ->
            assertEquals("type $type", null, SharedText.of(Intent.ACTION_SEND, type, "text"))
        }
    }

    @Test
    fun `a missing or blank extra is ignored`() {
        listOf(null, "", "   ", "\n\t").forEach { extra ->
            assertEquals("extra ${extra?.length}", null, SharedText.of(Intent.ACTION_SEND, "text/plain", extra))
        }
    }

    @Test
    fun `the text lands on Create's root from every tab`() {
        listOf(
            stacks(HomeKey, home = listOf(HomeKey, HistoryKey())),
            stacks(PlayKey),
            stacks(PlayKey, play = listOf(PlayKey, EditorKey("castle"))),
            stacks(HistoryRootKey, history = listOf(HistoryRootKey, HistoryKey())),
            stacks(PlayKey, create = listOf(CreateKey, EditorKey("castle"))),
            stacks(HomeKey, create = listOf(CreateKey, EditorKey("castle"), EditorKey("other"))),
        ).forEach { before ->
            val after = before.applied()

            assertEquals("from $before", CreateKey, after.tab)
            assertEquals("from $before", listOf<NavKey>(CreateKey), after.stackOf(CreateKey))
            assertEquals(
                "other stacks kept, from $before",
                before.stacks - setOf(before.stackOf(CreateKey)),
                after.stacks - setOf(after.stackOf(CreateKey)),
            )
        }
    }

    @Test
    fun `a deep Create stack pops to its root`() {
        val before = stacks(CreateKey, create = listOf(CreateKey, EditorKey("castle")))

        assertEquals(stacks(CreateKey), before.applied())
    }

    @Test
    fun `the text closes the layer open over Create's root`() {
        val covered = stacks(CreateKey).copy(layer = listOf(ProfileKey))

        assertEquals(stacks(CreateKey), covered.applied())
    }

    @Test
    fun `with Custom in Games the text opens Games' root with Custom asked for, from every tab`() {
        listOf(
            threeTabs(HomeKey, home = listOf(HomeKey, HistoryKey())),
            threeTabs(PlayKey),
            threeTabs(PlayKey, play = listOf(PlayKey, EditorKey("castle"))),
            threeTabs(HistoryRootKey, history = listOf(HistoryRootKey, HistoryKey())),
        ).forEach { before ->
            val requests = LibraryRequests()
            val after = before.applied(TabSet.CustomInGames, requests)

            assertEquals("from $before", PlayKey, after.tab)
            assertEquals("from $before", listOf<NavKey>(PlayKey), after.stackOf(PlayKey))
            assertEquals("from $before", LibraryRequest.Section(GamesTab.Custom), requests.pending)
            assertEquals(
                "other stacks kept, from $before",
                before.stacks - setOf(before.stackOf(PlayKey)),
                after.stacks - setOf(after.stackOf(PlayKey)),
            )
        }
    }

    @Test
    fun `with Custom in Games the text closes the layer open over Games`() {
        val requests = LibraryRequests()
        val covered = threeTabs(PlayKey).copy(layer = listOf(ProfileKey))

        assertEquals(threeTabs(PlayKey), covered.applied(TabSet.CustomInGames, requests))
        assertEquals(LibraryRequest.Section(GamesTab.Custom), requests.pending)
    }

    @Test
    fun `with Custom in Games back from where the text landed reaches Today`() {
        val navigator = Navigator(threeTabs(PlayKey, play = listOf(PlayKey, EditorKey("castle"))))

        navigator.applySharedText(TabSet.CustomInGames, LibraryRequests())
        navigator.pop()

        assertEquals(HomeKey, navigator.state.tab)
    }

    @Test
    fun `with the four tabs the text asks for no Games section`() {
        val requests = LibraryRequests()

        stacks(PlayKey).applied(TabSet.FourTabs, requests)

        assertNull(requests.pending)
    }

    private fun NavigationState.applied(
        tabSet: TabSet = TabSet.FourTabs,
        requests: LibraryRequests = LibraryRequests(),
    ): NavigationState = Navigator(this).apply { applySharedText(tabSet, requests) }.state

    private fun threeTabs(
        tab: NavKey,
        home: List<NavKey> = listOf(HomeKey),
        play: List<NavKey> = listOf(PlayKey),
        history: List<NavKey> = listOf(HistoryRootKey),
    ) = NavigationState(tab = tab, stacks = listOf(home, play, history))

    private fun stacks(
        tab: NavKey,
        home: List<NavKey> = listOf(HomeKey),
        play: List<NavKey> = listOf(PlayKey),
        history: List<NavKey> = listOf(HistoryRootKey),
        create: List<NavKey> = listOf(CreateKey),
    ) = NavigationState(tab = tab, stacks = listOf(home, play, history, create))
}
