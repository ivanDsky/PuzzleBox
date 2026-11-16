package com.ivandsky.puzzlebox.app.nav

import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.compete.api.DuelKey
import com.ivandsky.puzzlebox.create.api.CreateKey
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
 * code selects Play's root, where the code sheet lives. With Custom in Games the same text
 * selects Games' root and asks for its Custom section, where Enter code lives.
 */
class ApplyFriendLinkTest {

    @Test
    fun `a code selects Play and pushes its card from every tab`() {
        listOf(
            stacks(HomeKey, home = listOf(HomeKey, HistoryKey())),
            stacks(PlayKey),
            stacks(CreateKey, create = listOf(CreateKey, EditorKey("castle"))),
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
        val once = stacks(CreateKey).applying(K7Q2MX)

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
        val before = stacks(CreateKey, create = listOf(CreateKey, EditorKey("castle")), play = listOf(PlayKey, GameKey("zip")))

        val after = before.applying(MATCH)

        assertEquals(PlayKey, after.tab)
        assertEquals(listOf(PlayKey, GameKey("zip"), DUEL), after.stackOf(PlayKey))
        assertEquals(before.stackOf(CreateKey), after.stackOf(CreateKey))
    }

    @Test
    fun `a match link already on Play's stack only selects Play`() {
        val playing = stacks(HomeKey, play = listOf(PlayKey, DUEL, GameKey("zip")))

        assertEquals(playing.copy(tab = PlayKey), playing.applying(MATCH))
    }

    @Test
    fun `text that is not a code selects Play's root and keeps every other stack`() {
        listOf(
            stacks(HomeKey, home = listOf(HomeKey, HistoryKey())),
            stacks(PlayKey),
            stacks(PlayKey, play = listOf(PlayKey, GameKey("zip"))),
            stacks(CreateKey, create = listOf(CreateKey, EditorKey("castle")), play = listOf(PlayKey, CARD)),
        ).forEach { before ->
            val after = before.applying(FriendLink.Invalid("K7Q2M"))

            assertEquals("from $before", PlayKey, after.tab)
            assertEquals("from $before", listOf<NavKey>(PlayKey), after.stackOf(PlayKey))
            assertEquals("other stacks kept, from $before", before.stacks - setOf(before.stackOf(PlayKey)), after.stacks - setOf(after.stackOf(PlayKey)))
        }
    }

    @Test
    fun `a link closes the layer open over Play, also when its card is already there or the text is no code`() {
        val under = stacks(PlayKey, play = listOf(PlayKey, CARD))
        val covered = under.copy(layer = listOf(ProfileKey))

        assertEquals(under, covered.applying(K7Q2MX))
        assertEquals(stacks(PlayKey), stacks(PlayKey).copy(layer = listOf(ProfileKey)).applying(FriendLink.Invalid("K7Q2M")))
    }

    @Test
    fun `with Custom in Games a code or a match is pushed on Games as with four tabs, asking for no section`() {
        listOf(K7Q2MX, MATCH).forEach { link ->
            val key = if (link is FriendLink.Code) CARD else DUEL
            val before = threeTabs(HomeKey, play = listOf(PlayKey, GameKey("zip")))
            val requests = LibraryRequests()

            val after = before.applying(link, TabSet.CustomInGames, requests)

            assertEquals(PlayKey, after.tab)
            assertEquals(listOf(PlayKey, GameKey("zip"), key), after.stackOf(PlayKey))
            assertNull(requests.pending)
        }
    }

    @Test
    fun `with Custom in Games text that is not a code opens Games' root with Custom asked for`() {
        listOf(
            threeTabs(HomeKey, home = listOf(HomeKey, HistoryKey())),
            threeTabs(PlayKey),
            threeTabs(PlayKey, play = listOf(PlayKey, GameKey("zip"))),
            threeTabs(HistoryRootKey, play = listOf(PlayKey, CARD)),
        ).forEach { before ->
            val requests = LibraryRequests()
            val after = before.applying(FriendLink.Invalid("K7Q2M"), TabSet.CustomInGames, requests)

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
    fun `with Custom in Games text that is not a code closes the layer open over Games`() {
        val requests = LibraryRequests()
        val covered = threeTabs(PlayKey).copy(layer = listOf(ProfileKey))

        assertEquals(threeTabs(PlayKey), covered.applying(FriendLink.Invalid("K7Q2M"), TabSet.CustomInGames, requests))
        assertEquals(LibraryRequest.Section(GamesTab.Custom), requests.pending)
    }

    @Test
    fun `with the four tabs text that is not a code asks for no Games section`() {
        val requests = LibraryRequests()

        stacks(CreateKey).applying(FriendLink.Invalid("K7Q2M"), TabSet.FourTabs, requests)

        assertNull(requests.pending)
    }

    private fun NavigationState.applying(
        link: FriendLink,
        tabSet: TabSet = TabSet.FourTabs,
        requests: LibraryRequests = LibraryRequests(),
    ): NavigationState = Navigator(this).apply { applyFriendLink(link, tabSet, requests) }.state

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

    private companion object {
        fun code(value: String): ShareCode = checkNotNull(ShareCode.parse(value))
        val K7Q2MX = FriendLink.Code(code("K7Q2MX"))
        val MATCH = FriendLink.Match(code("K7Q2MX"))
        val DUEL = DuelKey("K7Q2MX")
        val CARD = FriendPuzzleKey("K7Q2MX")
    }
}
