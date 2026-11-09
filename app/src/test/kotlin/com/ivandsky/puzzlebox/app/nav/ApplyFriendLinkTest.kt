package com.ivandsky.puzzlebox.app.nav

import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.compete.api.DuelKey
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A tapped link on the back stack: a code selects Play and puts its card on top, keeping
 * every stack beneath; a code already on Play's stack only selects Play; text that isn't a
 * code selects Play's root, where the code sheet lives.
 */
class ApplyFriendLinkTest {

    @Test
    fun `a code selects Play and pushes its card from every tab`() {
        listOf(
            stacks(HomeKey, home = listOf(HomeKey, HistoryKey())),
            stacks(PlayKey),
            stacks(CreateKey, create = listOf(CreateKey, EditorKey("castle"))),
            stacks(ProfileKey, profile = listOf(ProfileKey, HistoryKey("zip"))),
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
    fun `a match link with matches disabled changes nothing`() {
        val before = stacks(CreateKey, create = listOf(CreateKey, EditorKey("castle")))

        assertEquals(before, before.applying(MATCH, matchesEnabled = false))
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

    private fun NavigationState.applying(link: FriendLink, matchesEnabled: Boolean = true): NavigationState =
        Navigator(this).apply { applyFriendLink(link, matchesEnabled) }.state

    private fun stacks(
        tab: NavKey,
        home: List<NavKey> = listOf(HomeKey),
        play: List<NavKey> = listOf(PlayKey),
        create: List<NavKey> = listOf(CreateKey),
        profile: List<NavKey> = listOf(ProfileKey),
    ) = NavigationState(tab = tab, stacks = listOf(home, play, create, profile))

    private companion object {
        fun code(value: String): ShareCode = checkNotNull(ShareCode.parse(value))
        val K7Q2MX = FriendLink.Code(code("K7Q2MX"))
        val MATCH = FriendLink.Match(code("K7Q2MX"))
        val DUEL = DuelKey("K7Q2MX")
        val CARD = FriendPuzzleKey("K7Q2MX")
    }
}
