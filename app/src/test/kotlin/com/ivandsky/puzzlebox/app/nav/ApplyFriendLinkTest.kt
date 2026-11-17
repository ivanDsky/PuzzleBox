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
import com.ivandsky.puzzlebox.play.api.CommunityKey
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A tapped link on the back stack: a code selects Community and puts its card on top, keeping
 * every stack beneath; a code already on Community's stack only selects Community; text that isn't a
 * code selects Community's root, where the code sheet lives; a match is a duel and goes to Play.
 */
class ApplyFriendLinkTest {

    @Test
    fun `a code selects Community and pushes its card from every tab`() {
        listOf(
            stacks(HomeKey, home = listOf(HomeKey, HistoryKey())),
            stacks(PlayKey),
            stacks(PlayKey, play = listOf(PlayKey, EditorKey("castle"))),
            stacks(CommunityKey, community = listOf(CommunityKey, EditorKey("castle"))),
            stacks(HistoryRootKey, history = listOf(HistoryRootKey, HistoryKey("zip"))),
        ).forEach { before ->
            val after = before.applying(K7Q2MX)

            assertEquals("from $before", CommunityKey, after.tab)
            assertEquals("from $before", before.stackOf(CommunityKey) + CARD, after.stackOf(CommunityKey))
            assertEquals(
                "other stacks kept, from $before",
                before.stacks - setOf(before.stackOf(CommunityKey)),
                after.stacks - setOf(after.stackOf(CommunityKey)),
            )
        }
    }

    @Test
    fun `a daily in play stays on its stack`() {
        val before = stacks(PlayKey, play = listOf(PlayKey, GameKey("zip")))

        assertEquals(
            stacks(CommunityKey, play = listOf(PlayKey, GameKey("zip")), community = listOf(CommunityKey, CARD)),
            before.applying(K7Q2MX),
        )
    }

    @Test
    fun `a duplicate link is a no-op`() {
        val once = stacks(HistoryRootKey).applying(K7Q2MX)

        assertEquals(once, once.applying(K7Q2MX))
    }

    @Test
    fun `a code whose card is already on Community's stack only selects Community`() {
        val open = stacks(HomeKey, community = listOf(CommunityKey, CARD, CustomGameKey("queens", "K7Q2MX")))

        assertEquals(open.copy(tab = CommunityKey), open.applying(K7Q2MX))
    }

    @Test
    fun `another code's card goes on top of the first`() {
        val other = FriendPuzzleKey("ABCDEF")
        val before = stacks(CommunityKey, community = listOf(CommunityKey, CARD))

        assertEquals(
            stacks(CommunityKey, community = listOf(CommunityKey, CARD, other)),
            before.applying(FriendLink.Code(code("ABCDEF"))),
        )
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
    fun `a link closes the layer open over Community, also when its card is already there or the text is no code`() {
        val under = stacks(CommunityKey, community = listOf(CommunityKey, CARD))
        val covered = under.copy(layer = listOf(ProfileKey))

        assertEquals(under, covered.applying(K7Q2MX))
        assertEquals(
            stacks(CommunityKey),
            stacks(CommunityKey).copy(layer = listOf(ProfileKey)).applying(FriendLink.Invalid("K7Q2M")),
        )
    }

    @Test
    fun `text that is not a code selects Community's root and keeps every other stack`() {
        listOf(
            stacks(HomeKey, home = listOf(HomeKey, HistoryKey())),
            stacks(PlayKey),
            stacks(PlayKey, play = listOf(PlayKey, GameKey("zip"))),
            stacks(CommunityKey, community = listOf(CommunityKey, EditorKey("castle"), CARD)),
            stacks(HistoryRootKey, play = listOf(PlayKey, CARD)),
        ).forEach { before ->
            val after = before.applying(FriendLink.Invalid("K7Q2M"))

            assertEquals("from $before", CommunityKey, after.tab)
            assertEquals("from $before", listOf<NavKey>(CommunityKey), after.stackOf(CommunityKey))
            assertEquals(
                "other stacks kept, from $before",
                before.stacks - setOf(before.stackOf(CommunityKey)),
                after.stacks - setOf(after.stackOf(CommunityKey)),
            )
        }
    }

    private fun NavigationState.applying(link: FriendLink): NavigationState =
        Navigator(this).apply { applyFriendLink(link) }.state

    private fun stacks(
        tab: NavKey,
        home: List<NavKey> = listOf(HomeKey),
        play: List<NavKey> = listOf(PlayKey),
        community: List<NavKey> = listOf(CommunityKey),
        history: List<NavKey> = listOf(HistoryRootKey),
    ) = NavigationState(tab = tab, stacks = listOf(home, play, community, history))

    private companion object {
        fun code(value: String): ShareCode = checkNotNull(ShareCode.parse(value))
        val K7Q2MX = FriendLink.Code(code("K7Q2MX"))
        val MATCH = FriendLink.Match(code("K7Q2MX"))
        val DUEL = DuelKey("K7Q2MX")
        val CARD = FriendPuzzleKey("K7Q2MX")
    }
}
