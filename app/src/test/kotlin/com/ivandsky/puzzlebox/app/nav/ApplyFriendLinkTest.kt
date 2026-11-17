package com.ivandsky.puzzlebox.app.nav

import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.compete.api.DuelKey
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.GamesTab
import com.ivandsky.puzzlebox.play.LibraryRequest
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A tapped link on the back stack: a code selects Play and puts its card on top, keeping
 * every stack beneath; a code already on Play's stack only selects Play; text that isn't a
 * code selects Games' root and asks for its Custom section, where Enter code lives.
 */
class ApplyFriendLinkTest {

    @Test
    fun `a code selects Play and pushes its card from every tab`() {
        listOf(
            stacks(HomeKey, home = listOf(HomeKey, HistoryKey())),
            stacks(PlayKey),
            stacks(PlayKey, play = listOf(PlayKey, EditorKey("castle"))),
            stacks(HistoryRootKey, history = listOf(HistoryRootKey, HistoryKey("zip"))),
        ).forEach { before ->
            val after = before.applying(K7Q2MX)

            assertEquals("from $before", PlayKey, after.tab)
            assertEquals("from $before", before.stackOf(PlayKey) + CARD, after.stackOf(PlayKey))
            assertEquals("other stacks kept, from $before", before.stacks - setOf(before.stackOf(PlayKey)), after.stacks - setOf(after.stackOf(PlayKey)))
        }
    }

    @Test
    fun `a daily in play beneath stays on the stack`() {
        val before = stacks(PlayKey, play = listOf(PlayKey, GameKey("zip")))

        assertEquals(stacks(PlayKey, play = listOf(PlayKey, GameKey("zip"), CARD)), before.applying(K7Q2MX))
    }

    @Test
    fun `a duplicate link is a no-op`() {
        val once = stacks(HistoryRootKey).applying(K7Q2MX)

        assertEquals(once, once.applying(K7Q2MX))
    }

    @Test
    fun `a code whose card is already on Play's stack only selects Play`() {
        val playing = stacks(HomeKey, play = listOf(PlayKey, CARD, CustomGameKey("queens", "K7Q2MX")))

        assertEquals(playing.copy(tab = PlayKey), playing.applying(K7Q2MX))
    }

    @Test
    fun `another code's card goes on top of the first`() {
        val other = FriendPuzzleKey("ABCDEF")
        val before = stacks(PlayKey, play = listOf(PlayKey, CARD))

        assertEquals(stacks(PlayKey, play = listOf(PlayKey, CARD, other)), before.applying(FriendLink.Code(code("ABCDEF"))))
    }

    @Test
    fun `a match link selects Play and pushes the duel`() {
        val before = stacks(HistoryRootKey, history = listOf(HistoryRootKey, HistoryKey("zip")), play = listOf(PlayKey, GameKey("zip")))

        val after = before.applying(MATCH)

        assertEquals(PlayKey, after.tab)
        assertEquals(listOf(PlayKey, GameKey("zip"), DUEL), after.stackOf(PlayKey))
        assertEquals(before.stackOf(HistoryRootKey), after.stackOf(HistoryRootKey))
    }

    @Test
    fun `a match link already on Play's stack only selects Play`() {
        val playing = stacks(HomeKey, play = listOf(PlayKey, DUEL, GameKey("zip")))

        assertEquals(playing.copy(tab = PlayKey), playing.applying(MATCH))
    }

    @Test
    fun `a link closes the layer open over Play, also when its card is already there or the text is no code`() {
        val under = stacks(PlayKey, play = listOf(PlayKey, CARD))
        val covered = under.copy(layer = listOf(ProfileKey))

        assertEquals(under, covered.applying(K7Q2MX))
        assertEquals(stacks(PlayKey), stacks(PlayKey).copy(layer = listOf(ProfileKey)).applying(FriendLink.Invalid("K7Q2M")))
    }

    @Test
    fun `a code or a match is pushed on Games, asking for no section`() {
        listOf(K7Q2MX, MATCH).forEach { link ->
            val key = if (link is FriendLink.Code) CARD else DUEL
            val before = stacks(HomeKey, play = listOf(PlayKey, GameKey("zip")))
            val requests = LibraryRequests()

            val after = before.applying(link, TabSet.ThreeTabs, requests)

            assertEquals(PlayKey, after.tab)
            assertEquals(listOf(PlayKey, GameKey("zip"), key), after.stackOf(PlayKey))
            assertNull(requests.pending)
        }
    }

    @Test
    fun `text that is not a code selects Games' root with Custom asked for and keeps every other stack`() {
        listOf(
            stacks(HomeKey, home = listOf(HomeKey, HistoryKey())),
            stacks(PlayKey),
            stacks(PlayKey, play = listOf(PlayKey, GameKey("zip"))),
            stacks(PlayKey, play = listOf(PlayKey, EditorKey("castle"), CARD)),
            stacks(HistoryRootKey, play = listOf(PlayKey, CARD)),
        ).forEach { before ->
            val requests = LibraryRequests()
            val after = before.applying(FriendLink.Invalid("K7Q2M"), TabSet.ThreeTabs, requests)

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
    fun `text that is not a code closes the layer open over Games`() {
        val requests = LibraryRequests()
        val covered = stacks(PlayKey).copy(layer = listOf(ProfileKey))

        assertEquals(stacks(PlayKey), covered.applying(FriendLink.Invalid("K7Q2M"), TabSet.ThreeTabs, requests))
        assertEquals(LibraryRequest.Section(GamesTab.Custom), requests.pending)
    }

    private fun NavigationState.applying(
        link: FriendLink,
        tabSet: TabSet = TabSet.ThreeTabs,
        requests: LibraryRequests = LibraryRequests(),
    ): NavigationState = Navigator(this).apply { applyFriendLink(link, tabSet, requests) }.state

    private fun stacks(
        tab: NavKey,
        home: List<NavKey> = listOf(HomeKey),
        play: List<NavKey> = listOf(PlayKey),
        history: List<NavKey> = listOf(HistoryRootKey),
    ) = NavigationState(tab = tab, stacks = listOf(home, play, history))

    private companion object {
        fun code(value: String): ShareCode = checkNotNull(ShareCode.parse(value))
        val K7Q2MX = FriendLink.Code(code("K7Q2MX"))
        val MATCH = FriendLink.Match(code("K7Q2MX"))
        val DUEL = DuelKey("K7Q2MX")
        val CARD = FriendPuzzleKey("K7Q2MX")
    }
}
