package com.ivandsky.puzzlebox.app.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.compete.api.DuelKey
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.api.PublishedPuzzleKey
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.CommunityKey
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * M10-1: Community is its own tab while CustomInGames and CommunityPuzzles are both on: Today, Games,
 * Community, Ranks. Either flag alone keeps today's sets, and the links and shared text land on the
 * Community tab, where Custom's list lives, instead of a Games section.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class CommunityTabTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `with both flags on the tabs are today, games, community, ranks`() {
        val tabSet = TabSetSource(FakeReleaseFlags(ReleaseFlag.CustomInGames, ReleaseFlag.CommunityPuzzles)).tabSet

        assertEquals(listOf("Today", "Games", "Community", "Ranks"), tabSet.destinations.map { it.label })
        assertEquals(listOf(HomeKey, PlayKey, CommunityKey, HistoryRootKey), tabSet.destinations.map { it.key })
        assertEquals(listOf("nav.tab.home", "nav.tab.play", "nav.tab.community", "nav.tab.ranks"), tabSet.destinations.map { it.tag })
        assertTrue(tabSet.hasCommunityTab)
    }

    @Test
    fun `with both flags on the Studio is immersive on Community and the published page keeps the bar`() {
        val tabSet = TabSetSource(FakeReleaseFlags(ReleaseFlag.CustomInGames, ReleaseFlag.CommunityPuzzles)).tabSet

        assertTrue(isImmersive(EditorKey("d1"), tabSet.placement))
        assertEquals(Placement(Home.PushedOn(Tab.Community), Chrome.Immersive), placementOf(EditorKey("d1"), tabSet.placement))
        assertEquals(Placement(Home.PushedOn(Tab.Community), Chrome.Bar), placementOf(PublishedPuzzleKey("K7Q2MX"), tabSet.placement))
        assertEquals(Placement(Home.PushedOn(Tab.Community), Chrome.Bar), placementOf(FriendPuzzleKey("K7Q2MX"), tabSet.placement))
    }

    @Test
    fun `CommunityPuzzles alone leaves the four tabs and CustomInGames alone the three`() {
        val communityOnly = TabSetSource(FakeReleaseFlags(ReleaseFlag.CommunityPuzzles)).tabSet
        val customOnly = TabSetSource(FakeReleaseFlags(ReleaseFlag.CustomInGames)).tabSet

        assertEquals(listOf("Today", "Games", "Ranks", "Create"), communityOnly.destinations.map { it.label })
        assertEquals(listOf("Today", "Games", "Ranks"), customOnly.destinations.map { it.label })
        assertFalse(communityOnly.hasCommunityTab)
        assertFalse(customOnly.hasCommunityTab)
    }

    @Test
    fun `the bar shows four tabs and highlights the selected one`() {
        var selected: NavKey = CommunityKey
        composeRule.setContent {
            TopLevelNavBar(selected = selected, onSelect = {}, destinations = TabSet.CommunityTabs.destinations)
        }

        TabSet.CommunityTabs.destinations.forEach { composeRule.onAllNodesWithTag(it.tag).assertCountEquals(1) }
        composeRule.onAllNodesWithTag("nav.tab.create").assertCountEquals(0)
        composeRule.onNodeWithTag("nav.tab.community").assertIsSelected()
    }

    @Test
    fun `the rail shows the same four tabs`() {
        composeRule.setContent {
            TopLevelNavRail(
                selected = CommunityKey,
                onSelect = {},
                destinations = TabSet.CommunityTabs.destinations,
                bottomActions = { Box(Modifier.size(48.dp)) },
            )
        }

        TabSet.CommunityTabs.destinations.forEach { composeRule.onAllNodesWithTag(it.tag).assertCountEquals(1) }
        composeRule.onNodeWithTag("nav.tab.community").assertIsSelected()
    }

    @Test
    fun `a code selects Community and pushes its card there, keeping Games' stack`() {
        val before = fourTabs(HomeKey, play = listOf(PlayKey, GameKey("zip")))
        val requests = LibraryRequests()

        val after = before.applyingLink(FriendLink.Code(code("K7Q2MX")), requests)

        assertEquals(CommunityKey, after.tab)
        assertEquals(listOf(CommunityKey, CARD), after.stackOf(CommunityKey))
        assertEquals(before.stackOf(PlayKey), after.stackOf(PlayKey))
        assertNull(requests.pending)
    }

    @Test
    fun `a duplicate code link is a no-op on Community`() {
        val once = fourTabs(HomeKey).applyingLink(FriendLink.Code(code("K7Q2MX")))

        assertEquals(once, once.applyingLink(FriendLink.Code(code("K7Q2MX"))))
    }

    @Test
    fun `a match link still opens the duel on Games`() {
        val after = fourTabs(CommunityKey).applyingLink(FriendLink.Match(code("K7Q2MX")))

        assertEquals(PlayKey, after.tab)
        assertEquals(listOf(PlayKey, DuelKey("K7Q2MX")), after.stackOf(PlayKey))
    }

    @Test
    fun `text that is not a code selects Community's root, where the code sheet lives`() {
        listOf(
            fourTabs(HomeKey, home = listOf(HomeKey, HistoryKey())),
            fourTabs(PlayKey, play = listOf(PlayKey, GameKey("zip"))),
            fourTabs(CommunityKey, community = listOf(CommunityKey, CARD)),
            fourTabs(HistoryRootKey).copy(layer = listOf(ProfileKey)),
        ).forEach { before ->
            val requests = LibraryRequests()
            val after = before.applyingLink(FriendLink.Invalid("K7Q2M"), requests)

            assertEquals("from $before", CommunityKey, after.tab)
            assertEquals("from $before", listOf<NavKey>(CommunityKey), after.stackOf(CommunityKey))
            assertEquals("from $before", emptyList<NavKey>(), after.layer)
            assertNull("from $before", requests.pending)
        }
    }

    @Test
    fun `shared text lands on Community's root from every tab, asking for no Games section`() {
        listOf(
            fourTabs(HomeKey),
            fourTabs(PlayKey, play = listOf(PlayKey, EditorKey("castle"))),
            fourTabs(CommunityKey, community = listOf(CommunityKey, EditorKey("castle"))),
            fourTabs(HistoryRootKey).copy(layer = listOf(ProfileKey)),
        ).forEach { before ->
            val requests = LibraryRequests()
            val after = Navigator(before).apply { applySharedText(TabSet.CommunityTabs, requests) }.state

            assertEquals("from $before", CommunityKey, after.tab)
            assertEquals("from $before", listOf<NavKey>(CommunityKey), after.stackOf(CommunityKey))
            assertEquals("from $before", emptyList<NavKey>(), after.layer)
            assertNull("from $before", requests.pending)
        }
    }

    @Test
    fun `the Studio opened from Community goes back to Community`() {
        val navigator = Navigator(fourTabs(CommunityKey))

        navigator.pushOnce(EditorKey("castle"))
        navigator.pop()

        assertEquals(CommunityKey, navigator.state.tab)
        assertEquals(listOf<NavKey>(CommunityKey), navigator.state.currentStack)
    }

    @Test
    fun `a state saved with four tabs is reconciled when Community replaces Create`() {
        val saved = NavigationState(
            tab = CreateKey,
            stacks = listOf(listOf(HomeKey), listOf(PlayKey), listOf(HistoryRootKey), listOf(CreateKey, EditorKey("castle"))),
        )

        val restored = saved.reconciledWith(puzzleBoxNavigationState(TabSet.CommunityTabs.destinations))

        assertEquals(listOf(HomeKey, PlayKey, CommunityKey, HistoryRootKey), restored.tabs)
        assertEquals(HomeKey, restored.tab)
    }

    private fun NavigationState.applyingLink(link: FriendLink, requests: LibraryRequests = LibraryRequests()): NavigationState =
        Navigator(this).apply { applyFriendLink(link, TabSet.CommunityTabs, requests) }.state

    private fun fourTabs(
        tab: NavKey,
        home: List<NavKey> = listOf(HomeKey),
        play: List<NavKey> = listOf(PlayKey),
        community: List<NavKey> = listOf(CommunityKey),
        history: List<NavKey> = listOf(HistoryRootKey),
    ) = NavigationState(tab = tab, stacks = listOf(home, play, community, history))

    private companion object {
        fun code(value: String): ShareCode = checkNotNull(ShareCode.parse(value))
        val CARD = FriendPuzzleKey("K7Q2MX")
    }
}
