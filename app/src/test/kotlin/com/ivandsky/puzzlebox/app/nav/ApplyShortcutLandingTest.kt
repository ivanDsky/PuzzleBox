package com.ivandsky.puzzlebox.app.nav

import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.shortcut.ShortcutLanding
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.CommunityKey
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A resolved launcher shortcut on the back stack: a board lands on the Games tab with the list beneath it from every
 * tab and from under the Profile layer, once however often it is applied, and keeps other boards alive; Today lands on
 * the Today root; Back from a board walks Games, Today, then out.
 */
class ApplyShortcutLandingTest {

    @Test
    fun `a board lands on top of the Games list from every tab`() {
        listOf(HomeKey, PlayKey, CommunityKey, HistoryRootKey).forEach { tab ->
            val after = stacks(tab).applying(ZIP_BOARD)

            assertEquals("from $tab", PlayKey, after.tab)
            assertEquals("from $tab", listOf(PlayKey, ZIP), after.stackOf(PlayKey))
        }
    }

    @Test
    fun `a board leaves the other stacks as they were`() {
        val before = stacks(HomeKey, home = listOf(HomeKey, HistoryKey()), community = listOf(CommunityKey, CARD))

        val after = before.applying(ZIP_BOARD)

        assertEquals(before.stackOf(HomeKey), after.stackOf(HomeKey))
        assertEquals(before.stackOf(CommunityKey), after.stackOf(CommunityKey))
    }

    @Test
    fun `a board closes the Profile layer, also over the Games tab itself`() {
        listOf(HomeKey, PlayKey).forEach { tab ->
            val after = stacks(tab).copy(layer = listOf(ProfileKey)).applying(ZIP_BOARD)

            assertEquals("from $tab", PlayKey, after.tab)
            assertEquals("from $tab", emptyList<NavKey>(), after.layer)
            assertEquals("from $tab", listOf(PlayKey, ZIP), after.stackOf(PlayKey))
        }
    }

    @Test
    fun `the same board twice is stacked once`() {
        val once = stacks(HomeKey).applying(ZIP_BOARD)

        assertEquals(once, once.applying(ZIP_BOARD))
    }

    @Test
    fun `a board already open beneath another is not stacked again`() {
        val playing = stacks(HomeKey, play = listOf(PlayKey, ZIP, TANGO))

        assertEquals(playing.copy(tab = PlayKey), playing.applying(ZIP_BOARD))
    }

    @Test
    fun `a board of another game stays alive beneath the new one`() {
        val playing = stacks(PlayKey, play = listOf(PlayKey, TANGO))

        assertEquals(listOf(PlayKey, TANGO, ZIP), playing.applying(ZIP_BOARD).stackOf(PlayKey))
    }

    @Test
    fun `Back from a board goes to the Games list, then Today, then out`() {
        val navigator = Navigator(stacks(HistoryRootKey))
        navigator.applyShortcutLanding(ZIP_BOARD)

        assertEquals(listOf(PlayKey, ZIP), navigator.state.currentStack)
        navigator.pop()
        assertEquals(listOf(PlayKey), navigator.state.currentStack)
        navigator.pop()
        assertEquals(HomeKey, navigator.state.tab)
        assertFalse(navigator.pop())
    }

    @Test
    fun `Today selects the Today tab from every tab`() {
        listOf(PlayKey, CommunityKey, HistoryRootKey).forEach { tab ->
            assertEquals("from $tab", HomeKey, stacks(tab).applying(ShortcutLanding.Today).tab)
        }
    }

    @Test
    fun `Today pops the Today stack to its root and closes the layer`() {
        val deep = listOf(HomeKey, HistoryKey())
        listOf(
            stacks(HomeKey, home = deep),
            stacks(PlayKey, home = deep),
            stacks(HomeKey, home = deep).copy(layer = listOf(ProfileKey)),
            stacks(PlayKey, play = listOf(PlayKey, ZIP)).copy(layer = listOf(ProfileKey)),
        ).forEach { before ->
            val after = before.applying(ShortcutLanding.Today)

            assertEquals("from $before", HomeKey, after.tab)
            assertEquals("from $before", listOf<NavKey>(HomeKey), after.stackOf(HomeKey))
            assertEquals("from $before", emptyList<NavKey>(), after.layer)
            assertEquals("from $before", before.stackOf(PlayKey), after.stackOf(PlayKey))
        }
    }

    @Test
    fun `Today on a fresh Today root changes nothing`() {
        assertEquals(stacks(HomeKey), stacks(HomeKey).applying(ShortcutLanding.Today))
        assertNull(stacks(HomeKey).applying(ShortcutLanding.Today).returnTo)
    }

    private fun NavigationState.applying(landing: ShortcutLanding): NavigationState =
        Navigator(this).apply { applyShortcutLanding(landing) }.state

    private fun stacks(
        tab: NavKey,
        home: List<NavKey> = listOf(HomeKey),
        play: List<NavKey> = listOf(PlayKey),
        community: List<NavKey> = listOf(CommunityKey),
        history: List<NavKey> = listOf(HistoryRootKey),
    ) = NavigationState(tab = tab, stacks = listOf(home, play, community, history))

    private companion object {
        val ZIP_BOARD = ShortcutLanding.Board(GameId("zip"))
        val ZIP = GameKey("zip")
        val TANGO = GameKey("tango")
        val CARD = FriendPuzzleKey("K7Q2MX")
    }
}
