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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * M10-1: Community is its own tab Today, Games, Community, Ranks. The links
 * and shared text land on the Community tab, where Custom's list lives, instead of a Games section.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class CommunityTabTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the tabs are today, games, community, ranks`() {
        assertEquals(listOf("Today", "Games", "Community", "Ranks"), topLevelDestinations.map { it.label })
        assertEquals(listOf(HomeKey, PlayKey, CommunityKey, HistoryRootKey), topLevelDestinations.map { it.key })
        assertEquals(listOf("nav.tab.home", "nav.tab.play", "nav.tab.community", "nav.tab.ranks"), topLevelDestinations.map { it.tag })
    }

    @Test
    fun `the Studio is immersive on Community and the published page keeps the bar`() {
        assertTrue(isImmersive(EditorKey("d1")))
        assertEquals(Placement(Home.PushedOn(Tab.Community), Chrome.Immersive), placementOf(EditorKey("d1")))
        assertEquals(Placement(Home.PushedOn(Tab.Community), Chrome.Bar), placementOf(PublishedPuzzleKey("K7Q2MX")))
        assertEquals(Placement(Home.PushedOn(Tab.Community), Chrome.Bar), placementOf(FriendPuzzleKey("K7Q2MX")))
    }

    @Test
    fun `the bar shows four tabs and highlights the selected one`() {
        var selected: NavKey = CommunityKey
        composeRule.setContent {
            TopLevelNavBar(selected = selected, onSelect = {}, destinations = topLevelDestinations)
        }

        topLevelDestinations.forEach { composeRule.onAllNodesWithTag(it.tag).assertCountEquals(1) }
        composeRule.onAllNodesWithTag("nav.tab.create").assertCountEquals(0)
        composeRule.onNodeWithTag("nav.tab.community").assertIsSelected()
    }

    @Test
    fun `the rail shows the same four tabs`() {
        composeRule.setContent {
            TopLevelNavRail(
                selected = CommunityKey,
                onSelect = {},
                destinations = topLevelDestinations,
                bottomActions = { Box(Modifier.size(48.dp)) },
            )
        }

        topLevelDestinations.forEach { composeRule.onAllNodesWithTag(it.tag).assertCountEquals(1) }
        composeRule.onNodeWithTag("nav.tab.community").assertIsSelected()
    }

    @Test
    fun `a code selects Community and pushes its card there, keeping Games' stack`() {
        val before = fourTabs(HomeKey, play = listOf(PlayKey, GameKey("zip")))

        val after = before.applyingLink(FriendLink.Code(code("K7Q2MX")))

        assertEquals(CommunityKey, after.tab)
        assertEquals(listOf(CommunityKey, CARD), after.stackOf(CommunityKey))
        assertEquals(before.stackOf(PlayKey), after.stackOf(PlayKey))
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
            val after = before.applyingLink(FriendLink.Invalid("K7Q2M"))

            assertEquals("from $before", CommunityKey, after.tab)
            assertEquals("from $before", listOf<NavKey>(CommunityKey), after.stackOf(CommunityKey))
            assertEquals("from $before", emptyList<NavKey>(), after.layer)
        }
    }

    @Test
    fun `shared text lands on Community's root from every tab, `() {
        listOf(
            fourTabs(HomeKey),
            fourTabs(PlayKey, play = listOf(PlayKey, EditorKey("castle"))),
            fourTabs(CommunityKey, community = listOf(CommunityKey, EditorKey("castle"))),
            fourTabs(HistoryRootKey).copy(layer = listOf(ProfileKey)),
        ).forEach { before ->
            val after = Navigator(before).apply { applySharedText() }.state

            assertEquals("from $before", CommunityKey, after.tab)
            assertEquals("from $before", listOf<NavKey>(CommunityKey), after.stackOf(CommunityKey))
            assertEquals("from $before", emptyList<NavKey>(), after.layer)
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
    fun `a legacy state saved with a Create stack is reconciled`() {
        val saved = NavigationState(
            tab = CreateKey,
            stacks = listOf(listOf(HomeKey), listOf(PlayKey), listOf(HistoryRootKey), listOf(CreateKey, EditorKey("castle"))),
        )

        val restored = saved.reconciledWith(puzzleBoxNavigationState())

        assertEquals(listOf(HomeKey, PlayKey, CommunityKey, HistoryRootKey), restored.tabs)
        assertEquals(HomeKey, restored.tab)
    }

    private fun NavigationState.applyingLink(link: FriendLink): NavigationState =
        Navigator(this).apply { applyFriendLink(link) }.state

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
