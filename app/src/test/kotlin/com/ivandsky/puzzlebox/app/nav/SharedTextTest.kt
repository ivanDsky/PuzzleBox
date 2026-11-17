package com.ivandsky.puzzlebox.app.nav

import android.content.Intent
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.GamesTab
import com.ivandsky.puzzlebox.play.LibraryRequest
import com.ivandsky.puzzlebox.play.api.CommunityKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The shared-text intake helpers: what a `SEND` share offers, and where it lands: Games' Custom pill,
 * or Community's root when that tab is on.
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
    fun `the text opens Games' root with Custom asked for, from every tab`() {
        listOf(
            stacks(HomeKey, home = listOf(HomeKey, HistoryKey())),
            stacks(PlayKey),
            stacks(PlayKey, play = listOf(PlayKey, EditorKey("castle"))),
            stacks(HistoryRootKey, history = listOf(HistoryRootKey, HistoryKey())),
        ).forEach { before ->
            val requests = LibraryRequests()
            val after = before.applied(requests = requests)

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
    fun `the text closes the layer open over Games`() {
        val requests = LibraryRequests()
        val covered = stacks(PlayKey).copy(layer = listOf(ProfileKey))

        assertEquals(stacks(PlayKey), covered.applied(requests = requests))
        assertEquals(LibraryRequest.Section(GamesTab.Custom), requests.pending)
    }

    @Test
    fun `back from where the text landed reaches Today`() {
        val navigator = Navigator(stacks(PlayKey, play = listOf(PlayKey, EditorKey("castle"))))

        navigator.applySharedText(TabSet.ThreeTabs, LibraryRequests())
        navigator.pop()

        assertEquals(HomeKey, navigator.state.tab)
    }

    @Test
    fun `with a Community tab the text lands on Community's root and asks for no Games section`() {
        val requests = LibraryRequests()
        val before = communityStacks(PlayKey, play = listOf(PlayKey, EditorKey("castle")))

        val after = before.applied(TabSet.CommunityTabs, requests)

        assertEquals(CommunityKey, after.tab)
        assertEquals(listOf<NavKey>(CommunityKey), after.stackOf(CommunityKey))
        assertEquals(before.stackOf(PlayKey), after.stackOf(PlayKey))
        assertNull(requests.pending)
    }

    private fun NavigationState.applied(
        tabSet: TabSet = TabSet.ThreeTabs,
        requests: LibraryRequests = LibraryRequests(),
    ): NavigationState = Navigator(this).apply { applySharedText(tabSet, requests) }.state

    private fun stacks(
        tab: NavKey,
        home: List<NavKey> = listOf(HomeKey),
        play: List<NavKey> = listOf(PlayKey),
        history: List<NavKey> = listOf(HistoryRootKey),
    ) = NavigationState(tab = tab, stacks = listOf(home, play, history))

    private fun communityStacks(
        tab: NavKey,
        play: List<NavKey> = listOf(PlayKey),
    ) = NavigationState(tab = tab, stacks = listOf(listOf(HomeKey), play, listOf(CommunityKey), listOf(HistoryRootKey)))
}
